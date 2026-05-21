package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Phase 8 stretch — HDR bloom: extract bright pixels, Gaussian-blur them,
 * composite back additively over the original scene.
 *
 * <h3>Pipeline placement</h3>
 *
 * Runs AFTER {@link HDRTonemapPass} in the pass list — but reads the scene
 * texture BEFORE tonemap had a chance to clip highlights, so it allocates
 * its own pre-tonemap copy at the start of execute(). That's accomplished
 * by being scheduled to copy from scene early; in this implementation we
 * simplify: bloom samples the current sceneColorTex (which is the post-
 * tonemap LDR result), threshold-clamps, blurs, and composites. This loses
 * "true HDR" bloom — bright values >1.0 — but still produces a soft glow
 * on near-white pixels which is the user-visible win. Real HDR bloom would
 * require running before tonemap; flagged in §13 of the master plan.
 *
 * <h3>Implementation</h3>
 *
 * Three-stage:
 * <ol>
 *   <li>Bright-pass extract — sample scene, output {@code max(0, color-threshold)}
 *       into a half-res bloom target.</li>
 *   <li>Two-pass separable Gaussian blur — horizontal then vertical, each
 *       9-tap with fixed sigma. Half-res by design (cheap + soft).</li>
 *   <li>Additive composite — blend the blurred bloom over the scene at
 *       {@link LDOGConfig#bloomIntensity}.</li>
 * </ol>
 *
 * Allocates two half-res FBOs to ping-pong the blur across; both freed on
 * dispose. RCAS pattern of glCopyTexSubImage2D is unused here — the bloom
 * intermediate doesn't need to read the same target it writes to since the
 * blur stages already ping-pong.
 */
public final class BloomPass implements PostProcessPass {

    // --- Shaders (GLSL 120 to stay compatible with our GL 3.0 baseline) ---

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    v_texCoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    private static final String BRIGHT_PASS_FRAG =
        "#version 120\n" +
        "uniform sampler2D u_srcTex;\n" +
        "uniform float u_threshold;\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    vec3 c = texture2D(u_srcTex, v_texCoord).rgb;\n" +
        "    float l = dot(c, vec3(0.299, 0.587, 0.114));\n" +
        "    float w = max(0.0, l - u_threshold);\n" +
        "    // Square the weight so well-above-threshold pixels dominate the\n" +
        "    // bloom over just-barely-above ones.\n" +
        "    gl_FragColor = vec4(c * w * w, 1.0);\n" +
        "}\n";

    private static final String BLUR_FRAG =
        "#version 120\n" +
        "uniform sampler2D u_srcTex;\n" +
        "uniform vec2 u_invDim;\n" +
        "uniform vec2 u_dir;\n" +  // (1,0) for H, (0,1) for V
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    // 9-tap Gaussian, sigma ~= 2.5 px.\n" +
        "    float w0 = 0.227027;\n" +
        "    float w1 = 0.194595;\n" +
        "    float w2 = 0.121622;\n" +
        "    float w3 = 0.054054;\n" +
        "    float w4 = 0.016216;\n" +
        "    vec2 off1 = u_dir * u_invDim * 1.0;\n" +
        "    vec2 off2 = u_dir * u_invDim * 2.0;\n" +
        "    vec2 off3 = u_dir * u_invDim * 3.0;\n" +
        "    vec2 off4 = u_dir * u_invDim * 4.0;\n" +
        "    vec3 c = texture2D(u_srcTex, v_texCoord).rgb * w0;\n" +
        "    c += texture2D(u_srcTex, v_texCoord + off1).rgb * w1;\n" +
        "    c += texture2D(u_srcTex, v_texCoord - off1).rgb * w1;\n" +
        "    c += texture2D(u_srcTex, v_texCoord + off2).rgb * w2;\n" +
        "    c += texture2D(u_srcTex, v_texCoord - off2).rgb * w2;\n" +
        "    c += texture2D(u_srcTex, v_texCoord + off3).rgb * w3;\n" +
        "    c += texture2D(u_srcTex, v_texCoord - off3).rgb * w3;\n" +
        "    c += texture2D(u_srcTex, v_texCoord + off4).rgb * w4;\n" +
        "    c += texture2D(u_srcTex, v_texCoord - off4).rgb * w4;\n" +
        "    gl_FragColor = vec4(c, 1.0);\n" +
        "}\n";

    private static final String COMPOSITE_FRAG =
        "#version 120\n" +
        "uniform sampler2D u_sceneTex;\n" +
        "uniform sampler2D u_bloomTex;\n" +
        "uniform float u_intensity;\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    vec3 scene = texture2D(u_sceneTex, v_texCoord).rgb;\n" +
        "    vec3 bloom = texture2D(u_bloomTex, v_texCoord).rgb;\n" +
        "    gl_FragColor = vec4(scene + bloom * u_intensity, 1.0);\n" +
        "}\n";

    private ShaderProgram brightPassShader;
    private ShaderProgram blurShader;
    private ShaderProgram compositeShader;
    private ShaderProgram sceneCopyShader; // simple passthrough for source copy
    private boolean shaderFailed;

    // Half-res bloom ping-pong targets. Allocated lazily in execute() so they
    // size to the scene target's actual scaled dimensions, not the GUI's full
    // resolution.
    private int bloomFboA;
    private int bloomFboB;
    private int bloomTexA;
    private int bloomTexB;
    private int sceneCopyTex;
    private int bloomW;
    private int bloomH;
    private int sceneCopyW;
    private int sceneCopyH;
    private boolean loggedFirstExecute;

    @Override public String id() { return "bloom"; }

    @Override
    public void init(int width, int height) {
        try {
            brightPassShader = new ShaderProgram("ldog_bloom_bright", VERT_SOURCE, BRIGHT_PASS_FRAG);
            blurShader       = new ShaderProgram("ldog_bloom_blur",   VERT_SOURCE, BLUR_FRAG);
            compositeShader  = new ShaderProgram("ldog_bloom_comp",   VERT_SOURCE, COMPOSITE_FRAG);
            LDOGMod.LOGGER.info("LDOG: Bloom shaders compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: Bloom shader compile failed; pass will no-op", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // Targets re-ensured lazily in execute().
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (shaderFailed) return;
        if (!ctx.bindingActive()) return;
        if (!LDOGConfig.enableBloom) return;
        if (!LDOGConfig.enableHDRPipeline) return; // bloom only meaningful with HDR

        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        if (!rtm.isReady()) return;

        int sceneW = rtm.getScaledWidth();
        int sceneH = rtm.getScaledHeight();
        if (sceneW <= 0 || sceneH <= 0) return;

        int halfW = Math.max(1, sceneW / 2);
        int halfH = Math.max(1, sceneH / 2);
        boolean isHDR = rtm.isHDR();

        ensureBloomTargets(halfW, halfH, isHDR);
        ensureSceneCopy(sceneW, sceneH, isHDR);
        if (bloomFboA == 0 || bloomFboB == 0 || sceneCopyTex == 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        // Capture the current scene-color texture so we can both read from
        // it (for bright-pass + composite source) and write back to it (for
        // composite output) without violating the same-attachment rule.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.sceneFbo());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneCopyTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, sceneW, sceneH);

        // Stage 1: bright pass — sceneCopy → bloomA.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bloomFboA);
        GlStateManager.viewport(0, 0, halfW, halfH);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneCopyTex);
        brightPassShader.bind();
        brightPassShader.setUniform1i("u_srcTex", 0);
        brightPassShader.setUniform1f("u_threshold", (float) LDOGConfig.bloomThreshold);
        drawFullscreen();

        // Stage 2H: horizontal blur — bloomA → bloomB.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bloomFboB);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bloomTexA);
        blurShader.bind();
        blurShader.setUniform1i("u_srcTex", 0);
        blurShader.setUniform2f("u_invDim", 1.0f / halfW, 1.0f / halfH);
        blurShader.setUniform2f("u_dir", 1.0f, 0.0f);
        drawFullscreen();

        // Stage 2V: vertical blur — bloomB → bloomA.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bloomFboA);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bloomTexB);
        blurShader.setUniform2f("u_dir", 0.0f, 1.0f);
        drawFullscreen();

        // Stage 3: composite — scene + (bloom * intensity) → scene.
        // Bind scene FBO as draw, sample both sceneCopy (unit 0) + bloomTexA (unit 1).
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.sceneFbo());
        GlStateManager.viewport(0, 0, sceneW, sceneH);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bloomTexA);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneCopyTex);
        compositeShader.bind();
        compositeShader.setUniform1i("u_sceneTex", 0);
        compositeShader.setUniform1i("u_bloomTex", 1);
        compositeShader.setUniform1f("u_intensity", (float) LDOGConfig.bloomIntensity);
        drawFullscreen();

        ShaderProgram.unbind();
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glPopAttrib();

        if (!loggedFirstExecute) {
            loggedFirstExecute = true;
            LDOGMod.LOGGER.info("LDOG: Bloom pass live ({}x{} scene, {}x{} bloom)",
                sceneW, sceneH, halfW, halfH);
        }
    }

    private void drawFullscreen() {
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();
    }

    @Override
    public void dispose() {
        if (brightPassShader != null) { brightPassShader.dispose(); brightPassShader = null; }
        if (blurShader != null)       { blurShader.dispose();       blurShader = null; }
        if (compositeShader != null)  { compositeShader.dispose();  compositeShader = null; }
        if (sceneCopyShader != null)  { sceneCopyShader.dispose();  sceneCopyShader = null; }
        disposeBloomTargets();
        if (sceneCopyTex != 0) {
            GL11.glDeleteTextures(sceneCopyTex);
            sceneCopyTex = 0;
            sceneCopyW = 0;
            sceneCopyH = 0;
        }
    }

    @Override
    public boolean isEnabled() {
        return true; // inline gate in execute()
    }

    private void ensureBloomTargets(int w, int h, boolean hdr) {
        if (bloomFboA != 0 && bloomW == w && bloomH == h) return;
        disposeBloomTargets();

        bloomTexA = makeColorTex(w, h, hdr);
        bloomTexB = makeColorTex(w, h, hdr);
        bloomFboA = bindFBO(bloomTexA);
        bloomFboB = bindFBO(bloomTexB);
        bloomW = w;
        bloomH = h;
        LDOGMod.LOGGER.info("LDOG: Bloom targets allocated at {}x{} ({})", w, h, hdr ? "RGBA16F" : "RGBA8");
    }

    private void ensureSceneCopy(int w, int h, boolean hdr) {
        if (sceneCopyTex != 0 && sceneCopyW == w && sceneCopyH == h) return;
        if (sceneCopyTex != 0) GL11.glDeleteTextures(sceneCopyTex);
        sceneCopyTex = makeColorTex(w, h, hdr);
        sceneCopyW = w;
        sceneCopyH = h;
    }

    private static int makeColorTex(int w, int h, boolean hdr) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        int internal = hdr ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
        int type = hdr ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0,
            GL11.GL_RGBA, type, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int bindFBO(int colorTex) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, colorTex, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: Bloom FBO incomplete (0x{})", Integer.toHexString(status));
            GL30.glDeleteFramebuffers(fbo);
            return 0;
        }
        return fbo;
    }

    private void disposeBloomTargets() {
        if (bloomFboA != 0) { GL30.glDeleteFramebuffers(bloomFboA); bloomFboA = 0; }
        if (bloomFboB != 0) { GL30.glDeleteFramebuffers(bloomFboB); bloomFboB = 0; }
        if (bloomTexA != 0) { GL11.glDeleteTextures(bloomTexA);     bloomTexA = 0; }
        if (bloomTexB != 0) { GL11.glDeleteTextures(bloomTexB);     bloomTexB = 0; }
        bloomW = bloomH = 0;
    }
}
