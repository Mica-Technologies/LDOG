package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Post-upscale contrast-adaptive sharpen. Runs AFTER whichever upscaler is
 * active and operates at native (main-framebuffer) resolution. Works at any
 * render scale including 1.0 — at 1.0 there's no upscale happening and RCAS
 * acts as a pure sharpening pass over whatever the world renderer produced.
 *
 * Design note on the ping-pong problem: a fragment shader can't sample from
 * the same color attachment it's rendering to. Rather than expanding
 * RenderTargetManager with a main-dims ping-pong target (which would have
 * been the "clean" solution flagged in POST_9A4_RESEARCH.md), this pass
 * uses the simpler glCopyTexSubImage2D trick — copy the current main-FB
 * contents into an owned texture, then bind main FB as the draw target
 * and sample from the copy. Same visual result, one fewer FBO, and
 * self-contained.
 *
 * LDOG-original implementation inspired by AMD's public RCAS spec — no
 * code copied. The shader uses a diamond 4-tap kernel (N/S/E/W) like RCAS;
 * omits the full peak-limiting math in favor of a simpler contrast-gated
 * unsharp mask that preserves the essential character.
 */
public final class RCASSharpenPass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    v_texCoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    private static final String FRAG_SOURCE =
        "#version 120\n" +
        "uniform sampler2D u_srcTex;\n" +
        "uniform vec2 u_invMainDim;\n" +
        "uniform float u_sharpness;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "float luma(vec3 c) {\n" +
        "    return dot(c, vec3(0.299, 0.587, 0.114));\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 off = u_invMainDim;\n" +
        "    vec3 c = texture2D(u_srcTex, v_texCoord).rgb;\n" +
        "    vec3 n = texture2D(u_srcTex, v_texCoord + vec2(0.0, -off.y)).rgb;\n" +
        "    vec3 s = texture2D(u_srcTex, v_texCoord + vec2(0.0,  off.y)).rgb;\n" +
        "    vec3 e = texture2D(u_srcTex, v_texCoord + vec2( off.x, 0.0)).rgb;\n" +
        "    vec3 w = texture2D(u_srcTex, v_texCoord + vec2(-off.x, 0.0)).rgb;\n" +
        "\n" +
        "    // Contrast-adaptive strength: mild on flat regions, full on edges.\n" +
        "    float lc = luma(c), ln = luma(n), ls = luma(s), le = luma(e), lw = luma(w);\n" +
        "    float lMin = min(min(min(ln, ls), min(le, lw)), lc);\n" +
        "    float lMax = max(max(max(ln, ls), max(le, lw)), lc);\n" +
        "    float contrast = lMax - lMin;\n" +
        "\n" +
        "    // Sharpness is 0..1 user-facing; scale to a reasonable k range.\n" +
        "    // At u_sharpness=1.0 and high contrast, k caps at 0.5 which keeps\n" +
        "    // overshoot bounded without clipping.\n" +
        "    float k = clamp(contrast * u_sharpness, 0.0, u_sharpness * 0.5);\n" +
        "\n" +
        "    // Diamond 4-tap unsharp mask.\n" +
        "    vec3 sharpened = c * (1.0 + 4.0 * k) - (n + s + e + w) * k;\n" +
        "\n" +
        "    gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private int sourceTex;
    private int sourceTexWidth;
    private int sourceTexHeight;
    private boolean loggedFirstExecute;

    @Override
    public String id() {
        return "rcas_sharpen";
    }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_rcas", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: RCAS sharpen shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: RCAS shader compile failed; pass will no-op", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // Source texture reallocated lazily on dimension change in execute().
    }

    @Override
    public void execute(PostProcessContext ctx) {
        // Runs on every pipeline frame (if bindingActive), but the gate below
        // keeps it a no-op unless the user opted in. Unlike the upscalers, it
        // doesn't require bindingActive — at scale=1.0 the main FB still has
        // a freshly-rendered world from vanilla's blit path, and RCAS sharpens
        // that. But the pipeline itself runs bindingActive-gated (via
        // PostProcessPipeline.onFrame's early-exit when pipeline disabled),
        // so this pass is only reachable when the pipeline is live anyway.
        if (shaderFailed || shader == null) return;
        if (!LDOGConfig.enableRcasSharpen) return;

        int w = ctx.mainWidth();
        int h = ctx.mainHeight();
        if (w <= 0 || h <= 0) return;

        ensureSourceTexture(w, h);
        if (sourceTex == 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        // Copy current main-FB contents into our source texture. Main FB
        // should already be bound from the upscaler; also set as READ for
        // copyTexSubImage to work unambiguously.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

        // Bind main FB as draw target. Scratch texture remains bound on
        // unit 0 for the shader to sample from.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
        GlStateManager.viewport(0, 0, w, h);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);

        shader.bind();
        shader.setUniform1i("u_srcTex", 0);
        shader.setUniform2f("u_invMainDim", 1.0f / w, 1.0f / h);
        shader.setUniform1f("u_sharpness", (float) LDOGConfig.rcasSharpness);

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();

        ShaderProgram.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glPopAttrib();

        if (!loggedFirstExecute) {
            loggedFirstExecute = true;
            LDOGMod.LOGGER.info("LDOG: RCAS sharpen pass live at {}x{}", w, h);
        }
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (sourceTex != 0) {
            GL11.glDeleteTextures(sourceTex);
            sourceTex = 0;
            sourceTexWidth = 0;
            sourceTexHeight = 0;
        }
    }

    @Override
    public boolean isEnabled() {
        // Gate inline in execute() (cheaper early-out) since the upscaler
        // gates also happen there. isEnabled() just keeps us in the chain.
        return true;
    }

    private void ensureSourceTexture(int w, int h) {
        if (sourceTex != 0 && sourceTexWidth == w && sourceTexHeight == h) return;

        if (sourceTex != 0) {
            GL11.glDeleteTextures(sourceTex);
            sourceTex = 0;
        }

        sourceTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        sourceTexWidth = w;
        sourceTexHeight = h;
        LDOGMod.LOGGER.info("LDOG: RCAS source texture allocated at {}x{}", w, h);
    }
}
