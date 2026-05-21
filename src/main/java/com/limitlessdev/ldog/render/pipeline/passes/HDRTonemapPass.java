package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Phase 8 stretch / HDR pipeline: maps HDR scene values into displayable
 * LDR range using one of four tonemapping curves (aces / reinhard /
 * uncharted2 / linear) and a user-tunable exposure multiplier.
 *
 * <h3>Where it sits in the pipeline</h3>
 *
 * Runs FIRST among the registered passes so that:
 * <ul>
 *   <li>upscalers (Bilinear/FSR1/FSR1-Quality) see LDR-clamped values in
 *       HDR storage — their edge detection is calibrated for [0,1] input
 *       and reads from RGBA16F still work via texture2D() returning float;</li>
 *   <li>downstream passes (TAA, RCAS, FXAA, Vignette) operate on values
 *       inside [0,1] and avoid clipping when the eventual blit-back to
 *       MC's RGBA8 main framebuffer happens;</li>
 *   <li>the upscaler's existing main-FB write path doesn't need to be
 *       HDR-aware — the pixel values it reads are already LDR by the
 *       time it samples them.</li>
 * </ul>
 *
 * <h3>Ping-pong via glCopyTexSubImage2D</h3>
 *
 * A fragment shader can't sample from the same color attachment it's
 * rendering to. Same approach as {@link RCASSharpenPass}: copy
 * sceneColorTex → owned source texture, then bind sceneFbo as draw and
 * sample from the copy. Format of the owned source texture matches the
 * scene (RGBA16F when HDR is on) so we don't lose dynamic range on the
 * copy itself.
 */
public final class HDRTonemapPass implements PostProcessPass {

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
        "uniform float u_exposure;\n" +
        "uniform int u_op;\n" +  // 0 aces, 1 reinhard, 2 uncharted2, 3 linear
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "// ACES filmic curve, approximate fitted form.\n" +
        "vec3 aces(vec3 x) {\n" +
        "    float a = 2.51;\n" +
        "    float b = 0.03;\n" +
        "    float c = 2.43;\n" +
        "    float d = 0.59;\n" +
        "    float e = 0.14;\n" +
        "    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);\n" +
        "}\n" +
        "\n" +
        "// Classic Reinhard — soft, no clipping by construction.\n" +
        "vec3 reinhard(vec3 x) {\n" +
        "    return x / (1.0 + x);\n" +
        "}\n" +
        "\n" +
        "// Uncharted 2 / John Hable curve. Punchier highlights than Reinhard.\n" +
        "vec3 uncharted2Partial(vec3 x) {\n" +
        "    float A = 0.15;\n" +
        "    float B = 0.50;\n" +
        "    float C = 0.10;\n" +
        "    float D = 0.20;\n" +
        "    float E = 0.02;\n" +
        "    float F = 0.30;\n" +
        "    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;\n" +
        "}\n" +
        "vec3 uncharted2(vec3 x) {\n" +
        "    float w = 11.2;\n" +
        "    vec3 mapped = uncharted2Partial(x * 2.0);\n" +
        "    vec3 whiteScale = vec3(1.0) / uncharted2Partial(vec3(w));\n" +
        "    return mapped * whiteScale;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec3 c = texture2D(u_srcTex, v_texCoord).rgb * u_exposure;\n" +
        "    vec3 mapped;\n" +
        "    if (u_op == 0) {\n" +
        "        mapped = aces(c);\n" +
        "    } else if (u_op == 1) {\n" +
        "        mapped = reinhard(c);\n" +
        "    } else if (u_op == 2) {\n" +
        "        mapped = uncharted2(c);\n" +
        "    } else {\n" +
        "        mapped = clamp(c, 0.0, 1.0);\n" +
        "    }\n" +
        "    gl_FragColor = vec4(mapped, 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private int sourceTex;
    private int sourceTexWidth;
    private int sourceTexHeight;
    private boolean sourceTexIsHDR;
    private boolean loggedFirstExecute;

    @Override
    public String id() { return "hdr_tonemap"; }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_hdr_tonemap", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: HDR tonemap shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: HDR tonemap shader compile failed; pass will no-op", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // Source texture reallocated lazily on dimension change in execute().
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (shaderFailed || shader == null) return;
        if (!ctx.bindingActive()) return;
        if (!LDOGConfig.enableHDRPipeline) return;

        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        if (!rtm.isReady()) return;

        int w = rtm.getScaledWidth();
        int h = rtm.getScaledHeight();
        if (w <= 0 || h <= 0) return;

        ensureSourceTexture(w, h, rtm.isHDR());
        if (sourceTex == 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        // Copy current scene-color contents into our source texture.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.sceneFbo());
        // glReadBuffer would be COLOR0 — vanilla default after we leave the
        // MRT entity-mask path. Don't override; if MRT was active, the
        // binding mixin restored single-attachment state on RETURN.
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

        // Draw target = scene FBO (writing tonemapped values back to scene).
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.sceneFbo());
        GlStateManager.viewport(0, 0, w, h);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);

        shader.bind();
        shader.setUniform1i("u_srcTex", 0);
        shader.setUniform1f("u_exposure", (float) LDOGConfig.hdrExposure);
        shader.setUniform1i("u_op", operatorId(LDOGConfig.hdrTonemap));

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
            LDOGMod.LOGGER.info("LDOG: HDR tonemap pass live ({}x{}, op={}, exposure={})",
                w, h, LDOGConfig.hdrTonemap, LDOGConfig.hdrExposure);
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
        // Always in the chain so the user can flip enableHDRPipeline live —
        // the inline gate in execute() makes this cheap when off.
        return true;
    }

    private void ensureSourceTexture(int w, int h, boolean wantHDR) {
        if (sourceTex != 0
            && sourceTexWidth == w
            && sourceTexHeight == h
            && sourceTexIsHDR == wantHDR) return;

        if (sourceTex != 0) {
            GL11.glDeleteTextures(sourceTex);
            sourceTex = 0;
        }

        sourceTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
        int internal = wantHDR ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
        int type = wantHDR ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0,
            GL11.GL_RGBA, type, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        sourceTexWidth = w;
        sourceTexHeight = h;
        sourceTexIsHDR = wantHDR;
        LDOGMod.LOGGER.info("LDOG: HDR tonemap source texture allocated at {}x{} ({})",
            w, h, wantHDR ? "RGBA16F" : "RGBA8");
    }

    private static int operatorId(String name) {
        if (name == null) return 0;
        switch (name.toLowerCase()) {
            case "aces":       return 0;
            case "reinhard":   return 1;
            case "uncharted2": return 2;
            case "linear":     return 3;
            default:           return 0;
        }
    }
}
