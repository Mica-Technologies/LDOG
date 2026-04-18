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
 * Direction-biased FSR1 EASU variant. LDOG-original implementation inspired
 * by the AMD FSR1 EASU public spec; no code copied from the reference.
 *
 * Algorithm:
 *   1. Sample a 3x3 neighborhood around each output pixel.
 *   2. Compute a Sobel gradient on luma to estimate the local edge direction.
 *   3. Sample 4 additional taps along the PERPENDICULAR of the gradient —
 *      i.e., along the detected edge — at sub-texel offsets.
 *   4. Blend the bilinear center with the anisotropic-edge average by edge
 *      magnitude. Strong edges lean heavily on the directional samples;
 *      flat regions keep the clean bilinear center.
 *   5. Apply contrast-adaptive sharpening on the blended result using the
 *      5-tap cross of the original neighborhood.
 *
 * Compared to the basic FSR1 (unsharp-mask only):
 *   - Diagonal geometry (stair treads, fence diagonals, leaf edges) gets
 *     visibly crisper because the sample kernel actually orients itself
 *     along the edge rather than operating on a fixed cross.
 *   - Cost is ~13 texture fetches per pixel vs 5 for basic FSR1 — still
 *     cheap on modern GPUs, negligible cost at any scale.
 *   - Sharpness slider (u_sharpness) still tunes the post-sharpen strength.
 */
public final class FSR1QualityPass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
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
        "\n" +
        "    // Full 3x3 neighborhood.\n" +
        "    vec3 c   = texture2D(u_sceneTex, v_texCoord).rgb;\n" +
        "    vec3 tl  = texture2D(u_sceneTex, v_texCoord + vec2(-off.x, -off.y)).rgb;\n" +
        "    vec3 t   = texture2D(u_sceneTex, v_texCoord + vec2(0.0,   -off.y)).rgb;\n" +
        "    vec3 tr  = texture2D(u_sceneTex, v_texCoord + vec2( off.x, -off.y)).rgb;\n" +
        "    vec3 l   = texture2D(u_sceneTex, v_texCoord + vec2(-off.x,  0.0)).rgb;\n" +
        "    vec3 r   = texture2D(u_sceneTex, v_texCoord + vec2( off.x,  0.0)).rgb;\n" +
        "    vec3 bl  = texture2D(u_sceneTex, v_texCoord + vec2(-off.x,  off.y)).rgb;\n" +
        "    vec3 b   = texture2D(u_sceneTex, v_texCoord + vec2(0.0,    off.y)).rgb;\n" +
        "    vec3 br  = texture2D(u_sceneTex, v_texCoord + vec2( off.x,  off.y)).rgb;\n" +
        "\n" +
        "    // Luma for Sobel.\n" +
        "    float ltl = luma(tl), lt = luma(t), ltr = luma(tr);\n" +
        "    float ll  = luma(l),  lc = luma(c), lr  = luma(r);\n" +
        "    float lbl = luma(bl), lb = luma(b), lbr = luma(br);\n" +
        "\n" +
        "    // Sobel gradient — standard 3x3 kernel weights.\n" +
        "    float gx = (ltr + 2.0 * lr + lbr) - (ltl + 2.0 * ll + lbl);\n" +
        "    float gy = (lbl + 2.0 * lb + lbr) - (ltl + 2.0 * lt + ltr);\n" +
        "    float gMag = sqrt(gx * gx + gy * gy);\n" +
        "\n" +
        "    // Perpendicular to the gradient is the edge direction itself.\n" +
        "    // Guard against div-by-zero on flat regions.\n" +
        "    vec2 edgeDir = gMag > 0.001 ? vec2(-gy, gx) / gMag : vec2(0.0);\n" +
        "\n" +
        "    // Anisotropic samples along the edge. Sub-texel step so the\n" +
        "    // directional average has finer resolution than the 3x3 taps.\n" +
        "    vec2 stp = edgeDir * off * 0.75;\n" +
        "    vec3 e1 = texture2D(u_sceneTex, v_texCoord + stp).rgb;\n" +
        "    vec3 e2 = texture2D(u_sceneTex, v_texCoord - stp).rgb;\n" +
        "    vec3 e3 = texture2D(u_sceneTex, v_texCoord + stp * 1.6).rgb;\n" +
        "    vec3 e4 = texture2D(u_sceneTex, v_texCoord - stp * 1.6).rgb;\n" +
        "    vec3 edgeAvg = (e1 + e2) * 0.3 + (e3 + e4) * 0.2;\n" +
        "\n" +
        "    // Blend bilinear center with directional average by edge magnitude.\n" +
        "    // Clamp below 1.0 so strong edges never fully replace the center.\n" +
        "    float edgeStr = clamp(gMag * 0.5, 0.0, 0.85);\n" +
        "    vec3 directional = mix(c, edgeAvg, edgeStr);\n" +
        "\n" +
        "    // Contrast-adaptive sharpen on the blended result.\n" +
        "    float lMin = min(min(min(min(ltl, lt), min(ltr, ll)),\n" +
        "                         min(min(lr, lbl), min(lb, lbr))), lc);\n" +
        "    float lMax = max(max(max(max(ltl, lt), max(ltr, ll)),\n" +
        "                         max(max(lr, lbl), max(lb, lbr))), lc);\n" +
        "    float contrast = lMax - lMin;\n" +
        "    float k = clamp(contrast * u_sharpness * 0.5, 0.0, u_sharpness * 0.5);\n" +
        "    vec3 sharpened = directional * (1.0 + 4.0 * k) - (t + b + l + r) * k;\n" +
        "\n" +
        "    gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private boolean loggedFirstExecute;

    @Override
    public String id() {
        return "fsr1_quality";
    }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_fsr1_quality", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: FSR1-Quality upscaler shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: FSR1-Quality shader compile failed; pass will fall back to bilinear blit", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // No per-resize state.
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
            LDOGMod.LOGGER.info("LDOG: FSR1-Quality pass live — {}x{} -> {}x{}",
                ctx.sceneWidth(), ctx.sceneHeight(), ctx.mainWidth(), ctx.mainHeight());
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
        GlStateManager.viewport(0, 0, ctx.mainWidth(), ctx.mainHeight());

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ctx.sceneColorTexture());

        shader.bind();
        shader.setUniform1i("u_sceneTex", 0);
        shader.setUniform2f("u_invSceneDim", 1.0f / ctx.sceneWidth(), 1.0f / ctx.sceneHeight());
        shader.setUniform1f("u_sharpness", (float) LDOGConfig.fsr1Sharpness);

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
        return UpscalerAlgorithm.selected() == UpscalerAlgorithm.FSR1_QUALITY;
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
