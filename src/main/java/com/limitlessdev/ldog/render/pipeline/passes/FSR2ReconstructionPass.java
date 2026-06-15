package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.CameraState;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Phase 9c.4 — LDOG-original FSR2-style temporal reconstruction.
 *
 * <p>This pass replaces the upscaler→TAA chain when {@code upscalerAlgorithm}
 * is {@code FSR2}. In one fragment-shader pass, for each native-resolution
 * output pixel it:
 *
 * <ol>
 *   <li><b>Reprojects history</b> — uses per-entity MV (9c.3-C) where present,
 *       falls back to camera-only depth-based MV (9c.2) otherwise. Disocclusion
 *       is detected when the reprojected UV exits the screen.</li>
 *   <li><b>Lanczos-3 source sampling</b> — gathers source texels from the
 *       scaled scene at sub-pixel offsets driven by the current jitter cycle
 *       (9c.1), weights by sinc·sinc, accumulates. This is what gives FSR2
 *       its sharper output vs FSR1's spatial-only kernel.</li>
 *   <li><b>Neighborhood color clamping</b> — clamps the reprojected history
 *       to the 3×3 source-pixel color box (anti-ghost guard).</li>
 *   <li><b>Reactive weighting</b> — pixels flagged by the reactive mask
 *       (alpha-tested geometry, modded TESRs that bypass the MV stamp path)
 *       drop history weight toward zero, accepting per-frame instability over
 *       persistent smear.</li>
 *   <li><b>Output</b> — blend Lanczos-reconstructed current + clamped
 *       reprojected history, write to main FB at native resolution.</li>
 * </ol>
 *
 * <p>LDOG-original throughout. The algorithm is informed by AMD's public
 * FSR2 specification (Lanczos source kernel + jitter accumulation + history
 * clamping is the published recipe), but no code was copied. Quality is not
 * production FSR2 — the published spec has a deeper kernel + locked-down
 * disocclusion + thin-feature reconstruction we don't replicate. Trade-off:
 * dramatically simpler implementation that hits the same architectural
 * footprint as the existing pipeline.
 */
public final class FSR2ReconstructionPass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    v_texCoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    /**
     * Fragment kernel. Reads:
     *   - u_sceneColor  (scaled, RGBA8 or RGBA16F)
     *   - u_sceneDepth  (scaled, depth texture)
     *   - u_history     (native-res previous output)
     *   - u_entityMV    (half-res screen-space velocity)
     *   - u_reactiveMask (scaled, R-channel entity flag)
     * Matrix uniforms are the same as TAA's 9c.2 reprojection path.
     */
    private static final String FRAG_SOURCE =
        "#version 120\n" +
        "uniform sampler2D u_sceneColor;\n" +
        "uniform sampler2D u_sceneDepth;\n" +
        "uniform sampler2D u_history;\n" +
        "uniform sampler2D u_entityMV;\n" +
        "uniform sampler2D u_reactiveMask;\n" +
        "uniform vec2 u_invScaledDim;\n" +   // 1/sceneWidth, 1/sceneHeight
        "uniform vec2 u_invMainDim;\n" +     // 1/mainWidth, 1/mainHeight
        "uniform float u_scaleX;\n" +        // scaledW / mainW (== render scale)
        "uniform float u_scaleY;\n" +
        "uniform float u_historyWeight;\n" +
        "uniform float u_sharpness;\n" +
        "uniform mat4 u_invCurViewProj;\n" +
        "uniform mat4 u_prevViewProj;\n" +
        "uniform bool u_useEntityMV;\n" +
        "uniform bool u_useReactiveMask;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "// LDOG-original Lanczos-3 weight. Public formula sinc(x)*sinc(x/3).\n" +
        "float lanczos3(float x) {\n" +
        "    if (x < 1e-5 && x > -1e-5) return 1.0;\n" +
        "    if (x >= 3.0 || x <= -3.0) return 0.0;\n" +
        "    float pix = 3.14159265 * x;\n" +
        "    return (sin(pix) * sin(pix / 3.0)) / (pix * pix / 3.0);\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    // 1. Native UV -> scaled UV (just the same v_texCoord since UV is\n" +
        "    //    [0,1] in both spaces, GL_LINEAR sampling handles the rest).\n" +
        "    vec2 sUV = v_texCoord;\n" +
        "\n" +
        "    // 2. Lanczos-3 source kernel: 5x5 taps around the projected\n" +
        "    //    sub-pixel location in scaled space. Sub-pixel offset within\n" +
        "    //    the source texel is what drives temporal detail accumulation\n" +
        "    //    across the jitter cycle.\n" +
        "    vec2 sceneTexel = sUV / u_invScaledDim;\n" +
        "    vec2 frac = sceneTexel - floor(sceneTexel) - 0.5;\n" +
        "    vec3 srcAcc = vec3(0.0);\n" +
        "    float weightAcc = 0.0;\n" +
        "    vec3 minC = vec3( 1e6);\n" +
        "    vec3 maxC = vec3(-1e6);\n" +
        "    for (int y = -2; y <= 2; y++) {\n" +
        "        for (int x = -2; x <= 2; x++) {\n" +
        "            vec2 tapUV = sUV + vec2(float(x), float(y)) * u_invScaledDim;\n" +
        "            vec3 c = texture2D(u_sceneColor, tapUV).rgb;\n" +
        "            float wx = lanczos3(float(x) - frac.x);\n" +
        "            float wy = lanczos3(float(y) - frac.y);\n" +
        "            float w = wx * wy;\n" +
        "            srcAcc += c * w;\n" +
        "            weightAcc += w;\n" +
        "            // 3x3 inner box for history clamping (anti-ghost).\n" +
        "            if (x >= -1 && x <= 1 && y >= -1 && y <= 1) {\n" +
        "                minC = min(minC, c);\n" +
        "                maxC = max(maxC, c);\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    vec3 cur = srcAcc / max(weightAcc, 1e-5);\n" +
        "\n" +
        "    // 3. Reproject history.\n" +
        "    vec2 entityVel = vec2(0.0);\n" +
        "    bool hasEntityMV = false;\n" +
        "    if (u_useEntityMV) {\n" +
        "        entityVel = texture2D(u_entityMV, v_texCoord).rg;\n" +
        "        hasEntityMV = abs(entityVel.x) > 0.0005 || abs(entityVel.y) > 0.0005;\n" +
        "    }\n" +
        "    vec2 histUV = v_texCoord;\n" +
        "    if (hasEntityMV) {\n" +
        "        histUV = v_texCoord - entityVel;\n" +
        "    } else {\n" +
        "        // Camera MV: depth -> world -> prev clip -> prev UV.\n" +
        "        float depth = texture2D(u_sceneDepth, sUV).r;\n" +
        "        vec4 ndc = vec4(v_texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);\n" +
        "        vec4 worldW = u_invCurViewProj * ndc;\n" +
        "        if (abs(worldW.w) > 1e-6) {\n" +
        "            vec3 world = worldW.xyz / worldW.w;\n" +
        "            vec4 prevClip = u_prevViewProj * vec4(world, 1.0);\n" +
        "            if (prevClip.w > 0.0) {\n" +
        "                vec2 prevNdc = prevClip.xy / prevClip.w;\n" +
        "                histUV = prevNdc * 0.5 + 0.5;\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // 4. Disocclusion: bail to current if reprojected UV exited screen.\n" +
        "    if (histUV.x < 0.0 || histUV.x > 1.0 || histUV.y < 0.0 || histUV.y > 1.0) {\n" +
        "        // Apply RCAS-style sharpen anyway so disoccluded pixels match.\n" +
        "        gl_FragColor = vec4(cur, 1.0);\n" +
        "        return;\n" +
        "    }\n" +
        "    vec3 hist = texture2D(u_history, histUV).rgb;\n" +
        "    vec3 clampedHist = clamp(hist, minC, maxC);\n" +
        "\n" +
        "    // 5. Reactive weighting.\n" +
        "    float weight = u_historyWeight;\n" +
        "    if (u_useReactiveMask && !hasEntityMV) {\n" +
        "        float reactive = clamp(texture2D(u_reactiveMask, sUV).r * 4.0, 0.0, 1.0);\n" +
        "        weight = mix(weight, 0.0, reactive);\n" +
        "    }\n" +
        "\n" +
        "    // 6. Blend + light sharpen (the integrated FSR2 'sharpen against\n" +
        "    //    Lanczos accumulator' step — keeps reconstructed edges crisp).\n" +
        "    vec3 blended = mix(cur, clampedHist, weight);\n" +
        "    vec3 sharpened = blended + (cur - blended) * u_sharpness;\n" +
        "    gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private int historyTex;
    private int historyWidth;
    private int historyHeight;
    private boolean hasHistory;
    private boolean loggedFirstExecute;

    private static final FloatBuffer MAT_BUF_INV = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MAT_BUF_PREV = BufferUtils.createFloatBuffer(16);

    @Override public String id() { return "fsr2_reconstruction"; }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_fsr2", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: FSR2 reconstruction shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: FSR2 shader compile failed; pass will no-op", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        hasHistory = false; // dim change invalidates accumulated history
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (shaderFailed || shader == null) return;
        if (!ctx.bindingActive()) return;
        if (UpscalerAlgorithm.selected() != UpscalerAlgorithm.FSR2) {
            hasHistory = false;
            return;
        }

        int mainW = ctx.mainWidth();
        int mainH = ctx.mainHeight();
        int sceneW = ctx.sceneWidth();
        int sceneH = ctx.sceneHeight();
        if (mainW <= 0 || mainH <= 0 || sceneW <= 0 || sceneH <= 0) return;
        // Without camera matrices we can't reproject — but we must STILL resolve
        // the scene to the main FB, or the screen is left unresolved (black world
        // + ghosted UI). Fall back to a plain blit instead of returning. (The
        // jitter mixin captures CameraState whenever FSR2 is selected, so this is
        // a safety net, e.g. the very first frame.)
        if (!CameraState.isReady()) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.sceneFbo());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
            GL30.glBlitFramebuffer(0, 0, sceneW, sceneH, 0, 0, mainW, mainH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
            return;
        }

        ensureHistory(mainW, mainH);
        if (historyTex == 0) return;

        // First active frame: seed history from a single-pass bilinear blit of
        // the scene so the next frame has something coherent to clamp against.
        if (!hasHistory) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.sceneFbo());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
            GL30.glBlitFramebuffer(0, 0, sceneW, sceneH, 0, 0, mainW, mainH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            // Copy seeded main FB into history for next frame.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, mainW, mainH);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            hasHistory = true;
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        RenderTargetManager rtm = RenderTargetManager.INSTANCE;

        // Bind texture units.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, rtm.getSceneColorTexture());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, rtm.getSceneDepthTexture());
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);

        boolean useEntityMV = LDOGConfig.enableEntityMotionVectors
            && rtm.getMotionVectorTexture() != 0;
        if (useEntityMV) {
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, rtm.getMotionVectorTexture());
        }
        boolean useMask = ctx.reactiveMaskActive() && rtm.getSceneReactiveMaskTexture() != 0;
        if (useMask) {
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, rtm.getSceneReactiveMaskTexture());
        }

        // Draw target = main FB at native res.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
        GlStateManager.viewport(0, 0, mainW, mainH);
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        shader.bind();
        shader.setUniform1i("u_sceneColor", 0);
        shader.setUniform1i("u_sceneDepth", 1);
        shader.setUniform1i("u_history", 2);
        shader.setUniform1i("u_entityMV", 3);
        shader.setUniform1i("u_reactiveMask", 4);
        shader.setUniform2f("u_invScaledDim", 1.0f / sceneW, 1.0f / sceneH);
        shader.setUniform2f("u_invMainDim", 1.0f / mainW, 1.0f / mainH);
        shader.setUniform1f("u_scaleX", (float) sceneW / mainW);
        shader.setUniform1f("u_scaleY", (float) sceneH / mainH);
        shader.setUniform1f("u_historyWeight", (float) LDOGConfig.taaHistoryWeight);
        // Sharpness slider doubles as the integrated FSR2 sharpen.
        shader.setUniform1f("u_sharpness", (float) Math.min(1.0, LDOGConfig.fsr1Sharpness * 0.25));
        shader.setUniform1i("u_useEntityMV", useEntityMV ? 1 : 0);
        shader.setUniform1i("u_useReactiveMask", useMask ? 1 : 0);

        CameraState.writeCurInvViewProj(MAT_BUF_INV);
        CameraState.writePrevViewProj(MAT_BUF_PREV);
        shader.setUniformMatrix4("u_invCurViewProj", MAT_BUF_INV);
        shader.setUniformMatrix4("u_prevViewProj", MAT_BUF_PREV);

        // Fullscreen triangle that overdraws the screen rectangle.
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();

        ShaderProgram.unbind();

        // Copy main FB result into history for next frame's reprojection.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, mainW, mainH);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Unbind extra units.
        if (useMask) {
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        if (useEntityMV) {
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL11.glPopAttrib();

        if (!loggedFirstExecute) {
            loggedFirstExecute = true;
            LDOGMod.LOGGER.info(
                "LDOG: FSR2 reconstruction live ({}x{} scaled -> {}x{} native, scale {})",
                sceneW, sceneH, mainW, mainH, (float) sceneW / mainW);
        }
    }

    private void ensureHistory(int w, int h) {
        if (historyTex != 0 && historyWidth == w && historyHeight == h) return;
        if (historyTex != 0) {
            GL11.glDeleteTextures(historyTex);
            historyTex = 0;
            hasHistory = false;
        }
        historyTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
        // Match main FB's RGBA8 format. HDR mode is upstream of the blit-back
        // here — history holds tonemapped LDR output.
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        historyWidth = w;
        historyHeight = h;
    }

    @Override
    public void dispose() {
        if (shader != null) { shader.dispose(); shader = null; }
        if (historyTex != 0) {
            GL11.glDeleteTextures(historyTex);
            historyTex = 0;
        }
        historyWidth = historyHeight = 0;
        hasHistory = false;
    }

    @Override public boolean isEnabled() { return true; }
}
