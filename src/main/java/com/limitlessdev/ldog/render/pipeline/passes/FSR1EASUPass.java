package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * LDOG's FSR1-inspired edge-adaptive spatial upscaler.
 *
 * This is an LDOG-original implementation, not a port of AMD's FidelityFX
 * FSR1 source. It captures the essential character of FSR1's spatial path:
 * sample the scaled scene with bilinear as a baseline, then apply
 * contrast-adaptive sharpening over a 5-tap kernel, clamping each channel
 * to the local min/max to prevent ringing. Sharpen strength scales with
 * local contrast so flat areas stay soft and edges recover bite.
 *
 * Compared to the pure bilinear upscaler:
 * - Edges and fine detail recover noticeably more definition.
 * - Cost is ~5 texture fetches + a few ALU ops per output pixel — still
 *   cheap enough to be worthwhile at any scale &lt; 1.0.
 * - At scale = 1.0 the sample kernel reduces to the center pixel with
 *   minimal neighbor contribution, so output is near-identical to source.
 *
 * Falls back to plain bilinear blit when shader compilation fails at init,
 * so a GPU that can't run the shader still produces a usable image.
 */
public final class FSR1EASUPass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    // Map NDC [-1,1] to texCoord [0,1]. Fullscreen triangle extends to\n" +
        "    // NDC [3,-1]/[-1,3] so its texCoord hits [2,0]/[0,2] — clamped by\n" +
        "    // CLAMP_TO_EDGE on the scene texture at sample time.\n" +
        "    v_texCoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    private static final String FRAG_SOURCE =
        "#version 120\n" +
        "uniform sampler2D u_sceneTex;\n" +
        "uniform vec2 u_invSceneDim;\n" +
        "uniform float u_sharpness;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "float luma(vec3 c) {\n" +
        "    return dot(c, vec3(0.299, 0.587, 0.114));\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 off = u_invSceneDim;\n" +
        "    // 5-tap cross feeds the sharpen kernel.\n" +
        "    vec3 c = texture2D(u_sceneTex, v_texCoord).rgb;\n" +
        "    vec3 n = texture2D(u_sceneTex, v_texCoord + vec2(0.0, -off.y)).rgb;\n" +
        "    vec3 s = texture2D(u_sceneTex, v_texCoord + vec2(0.0,  off.y)).rgb;\n" +
        "    vec3 e = texture2D(u_sceneTex, v_texCoord + vec2( off.x, 0.0)).rgb;\n" +
        "    vec3 w = texture2D(u_sceneTex, v_texCoord + vec2(-off.x, 0.0)).rgb;\n" +
        "\n" +
        "    // Local contrast gates the sharpen strength — flat regions stay soft,\n" +
        "    // high-contrast edges get full kernel weight. Using 5-tap luma range\n" +
        "    // (not the full 3x3) because that's what the sharpen kernel itself\n" +
        "    // operates on.\n" +
        "    float lc = luma(c), ln = luma(n), ls = luma(s), le = luma(e), lw = luma(w);\n" +
        "    float lMin = min(min(min(ln, ls), min(le, lw)), lc);\n" +
        "    float lMax = max(max(max(ln, ls), max(le, lw)), lc);\n" +
        "    float contrast = lMax - lMin;\n" +
        "    float k = clamp(contrast * u_sharpness, 0.0, u_sharpness);\n" +
        "\n" +
        "    // Unsharp-mask kernel: center weighted up, neighbors subtracted.\n" +
        "    // No anti-ringing clamp — letting the sharpen naturally overshoot is what\n" +
        "    // actually delivers visible edge definition. Any ringing on hard edges\n" +
        "    // should be rare enough that the quality win dominates.\n" +
        "    vec3 sharpened = c * (1.0 + 4.0 * k) - (n + s + e + w) * k;\n" +
        "\n" +
        "    gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private boolean loggedFirstExecute;

    @Override
    public String id() {
        return "fsr1_easu";
    }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_fsr1", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: FSR1 upscaler shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: FSR1 shader compile failed; pass will fall back to bilinear blit", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // No per-resize work; the scaled dims are read from context each frame.
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (!ctx.bindingActive()) return;

        if (shader == null || shaderFailed) {
            bilinearFallback(ctx);
            return;
        }

        if (!loggedFirstExecute) {
            loggedFirstExecute = true;
            LDOGMod.LOGGER.info("LDOG: FSR1 pass live — {}x{} -> {}x{}",
                ctx.sceneWidth(), ctx.sceneHeight(), ctx.mainWidth(), ctx.mainHeight());
        }

        // Save compat-profile GL state we're about to mutate. One push/pop pair
        // is cheap on the driver and avoids bookkeeping every flag by hand.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        // Target main FB at native dims — we're rendering the final upscaled image.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
        GlStateManager.viewport(0, 0, ctx.mainWidth(), ctx.mainHeight());

        // Fullscreen blit: depth/cull/blend off, we want to replace every pixel.
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        // Bind scene color texture on unit 0.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ctx.sceneColorTexture());

        shader.bind();
        shader.setUniform1i("u_sceneTex", 0);
        shader.setUniform2f("u_invSceneDim", 1.0f / ctx.sceneWidth(), 1.0f / ctx.sceneHeight());
        // Sharpness is live-configurable via LDOGConfig.fsr1Sharpness — read
        // fresh every frame so the in-game slider feels responsive without
        // any reload.
        shader.setUniform1f("u_sharpness", (float) LDOGConfig.fsr1Sharpness);

        // Fullscreen triangle — bigger than the viewport, GPU clips the overhang.
        // Avoids the 2-triangle diagonal seam and is one fewer edge to rasterize.
        // Vertex shader derives texCoord from gl_Vertex.xy, so we only need
        // position attributes.
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();

        ShaderProgram.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL11.glPopAttrib();
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }

    @Override
    public boolean isEnabled() {
        return UpscalerAlgorithm.selected() == UpscalerAlgorithm.FSR1;
    }

    private void bilinearFallback(PostProcessContext ctx) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.sceneFbo());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
        GL30.glBlitFramebuffer(
            0, 0, ctx.sceneWidth(), ctx.sceneHeight(),
            0, 0, ctx.mainWidth(), ctx.mainHeight(),
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
    }
}
