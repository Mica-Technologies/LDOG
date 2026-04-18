package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.CameraState;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Phase 9c.1 — jittered-projection temporal anti-aliasing.
 *
 * Each frame:
 *   1. Copy current main-FB contents into {@code currentTex}.
 *   2. Sample {@code currentTex} + {@code historyTex} in shader.
 *   3. Clamp {@code historyTex} colour to the 3×3 neighborhood of
 *      {@code currentTex} (standard TAA anti-ghosting trick).
 *   4. Blend {@code taaHistoryWeight * clampedHistory + (1-weight) * current}.
 *   5. Write to main FB.
 *   6. Copy main FB result to {@code historyTex} for next frame.
 *
 * Companion mixin {@code MixinEntityRendererJitter} applies sub-pixel
 * jitter to the projection matrix each frame so consecutive samples hit
 * different pixel centers. Without jitter, TAA just smears without
 * accumulating sub-pixel detail.
 *
 * Ordering in the pipeline: AFTER the upscaler, BEFORE RCAS and FXAA.
 * Temporal blend operates on the upscaled output; RCAS sharpens the
 * blended result; FXAA smooths final edges.
 *
 * Known limitation of this MVP stage: no motion vectors yet — camera
 * motion produces visible ghosting. Neighborhood clamping mitigates but
 * doesn't eliminate. Phase 9c.2 adds camera MV to fix.
 */
public final class TAAAccumulatePass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    v_texCoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    private static final String FRAG_SOURCE =
        "#version 120\n" +
        "uniform sampler2D u_current;\n" +
        "uniform sampler2D u_history;\n" +
        "uniform sampler2D u_sceneDepth;\n" +
        "uniform vec2 u_invMainDim;\n" +
        "uniform float u_historyWeight;\n" +
        "uniform mat4 u_invCurViewProj;\n" +
        "uniform mat4 u_prevViewProj;\n" +
        "uniform bool u_useMotionVectors;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 off = u_invMainDim;\n" +
        "    vec3 cur = texture2D(u_current, v_texCoord).rgb;\n" +
        "\n" +
        "    // Phase 9c.2: reproject history via depth + camera matrix delta so\n" +
        "    // camera motion doesn't ghost. Fall back to direct history read\n" +
        "    // when MV data isn't available (pipeline off or first frame).\n" +
        "    vec2 histUV = v_texCoord;\n" +
        "    if (u_useMotionVectors) {\n" +
        "        float depth = texture2D(u_sceneDepth, v_texCoord).r;\n" +
        "        // Reconstruct world-space position from NDC + current inverse VP.\n" +
        "        vec4 ndc = vec4(v_texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);\n" +
        "        vec4 worldW = u_invCurViewProj * ndc;\n" +
        "        if (abs(worldW.w) > 1e-6) {\n" +
        "            vec3 world = worldW.xyz / worldW.w;\n" +
        "            // Re-project into previous-frame clip space.\n" +
        "            vec4 prevClip = u_prevViewProj * vec4(world, 1.0);\n" +
        "            if (prevClip.w > 0.0) {\n" +
        "                vec2 prevNdc = prevClip.xy / prevClip.w;\n" +
        "                histUV = prevNdc * 0.5 + 0.5;\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // Disocclusion: reprojected UV outside screen means the pixel\n" +
        "    // became visible this frame (no valid history). Use current only.\n" +
        "    if (histUV.x < 0.0 || histUV.x > 1.0 || histUV.y < 0.0 || histUV.y > 1.0) {\n" +
        "        gl_FragColor = vec4(cur, 1.0);\n" +
        "        return;\n" +
        "    }\n" +
        "\n" +
        "    vec3 hist = texture2D(u_history, histUV).rgb;\n" +
        "\n" +
        "    // 3x3 neighborhood of CURRENT for anti-ghost clamping.\n" +
        "    vec3 n  = texture2D(u_current, v_texCoord + vec2(0.0,   -off.y)).rgb;\n" +
        "    vec3 s  = texture2D(u_current, v_texCoord + vec2(0.0,    off.y)).rgb;\n" +
        "    vec3 e  = texture2D(u_current, v_texCoord + vec2( off.x, 0.0)).rgb;\n" +
        "    vec3 w  = texture2D(u_current, v_texCoord + vec2(-off.x, 0.0)).rgb;\n" +
        "    vec3 nw = texture2D(u_current, v_texCoord + vec2(-off.x,-off.y)).rgb;\n" +
        "    vec3 ne = texture2D(u_current, v_texCoord + vec2( off.x,-off.y)).rgb;\n" +
        "    vec3 sw = texture2D(u_current, v_texCoord + vec2(-off.x, off.y)).rgb;\n" +
        "    vec3 se = texture2D(u_current, v_texCoord + vec2( off.x, off.y)).rgb;\n" +
        "\n" +
        "    vec3 minC = min(min(min(min(cur, n), min(s, e)), min(min(w, nw), min(ne, sw))), se);\n" +
        "    vec3 maxC = max(max(max(max(cur, n), max(s, e)), max(max(w, nw), max(ne, sw))), se);\n" +
        "\n" +
        "    // Clamp reprojected history to current's local range. Entities that\n" +
        "    // moved (not reflected in MV) can't drag stale colour into the blend.\n" +
        "    vec3 clampedHist = clamp(hist, minC, maxC);\n" +
        "\n" +
        "    vec3 blended = mix(cur, clampedHist, u_historyWeight);\n" +
        "    gl_FragColor = vec4(blended, 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private int currentTex;
    private int historyTex;
    private int texWidth;
    private int texHeight;
    private boolean hasHistory;
    private boolean loggedFirstExecute;
    private boolean loggedFirstMV;

    private static final FloatBuffer MAT_BUF_INV = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MAT_BUF_PREV = BufferUtils.createFloatBuffer(16);

    @Override
    public String id() {
        return "taa_accumulate";
    }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_taa", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: TAA shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: TAA shader compile failed; pass will no-op", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // Textures reallocated lazily on dim change in execute. History reset
        // on dim change too — stale blend between two different sizes is wrong.
        hasHistory = false;
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (shaderFailed || shader == null) return;
        if (!LDOGConfig.enableTAA) {
            // If TAA was just disabled, drop history so a re-enable starts fresh.
            hasHistory = false;
            return;
        }

        int w = ctx.mainWidth();
        int h = ctx.mainHeight();
        if (w <= 0 || h <= 0) return;

        ensureTextures(w, h);
        if (currentTex == 0 || historyTex == 0) return;

        // First active frame: seed the history buffer from current main-FB and
        // skip the blend. Prevents blending against random garbage in an
        // uninitialized texture, and prevents a flash on enable.
        if (!hasHistory) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            hasHistory = true;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        // Capture current main-FB as the "current" sample source.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

        // Bind main FB as draw target.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
        GlStateManager.viewport(0, 0, w, h);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);

        // Phase 9c.2: bind scene depth on unit 2 + supply camera matrices for MV
        // reprojection. Only enabled when the pipeline is actually binding the
        // scene target (scene depth is valid) and CameraState has captured two
        // frames. Falls back to 9c.1 direct-read behavior otherwise.
        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        boolean useMV = ctx.bindingActive() && rtm.isReady() && CameraState.isReady();
        if (useMV) {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, rtm.getSceneDepthTexture());
        }

        shader.bind();
        shader.setUniform1i("u_current", 0);
        shader.setUniform1i("u_history", 1);
        shader.setUniform1i("u_sceneDepth", 2);
        shader.setUniform2f("u_invMainDim", 1.0f / w, 1.0f / h);
        shader.setUniform1f("u_historyWeight", (float) LDOGConfig.taaHistoryWeight);
        shader.setUniform1i("u_useMotionVectors", useMV ? 1 : 0);

        if (useMV) {
            CameraState.writeCurInvViewProj(MAT_BUF_INV);
            CameraState.writePrevViewProj(MAT_BUF_PREV);
            shader.setUniformMatrix4("u_invCurViewProj", MAT_BUF_INV);
            shader.setUniformMatrix4("u_prevViewProj", MAT_BUF_PREV);
            if (!loggedFirstMV) {
                loggedFirstMV = true;
                LDOGMod.LOGGER.info("LDOG: TAA motion-vector reprojection ACTIVE (9c.2)");
            }
        }

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();

        ShaderProgram.unbind();

        // Unbind extra units, leave unit 0 selected.
        if (useMV) {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Copy blended main-FB output into history for next frame.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL11.glPopAttrib();

        if (!loggedFirstExecute) {
            loggedFirstExecute = true;
            LDOGMod.LOGGER.info("LDOG: TAA pass live at {}x{}", w, h);
        }
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (currentTex != 0) { GL11.glDeleteTextures(currentTex); currentTex = 0; }
        if (historyTex != 0) { GL11.glDeleteTextures(historyTex); historyTex = 0; }
        texWidth = texHeight = 0;
        hasHistory = false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private void ensureTextures(int w, int h) {
        if (currentTex != 0 && historyTex != 0 && texWidth == w && texHeight == h) return;

        if (currentTex != 0) { GL11.glDeleteTextures(currentTex); currentTex = 0; }
        if (historyTex != 0) { GL11.glDeleteTextures(historyTex); historyTex = 0; }
        hasHistory = false;

        currentTex = allocateTex(w, h);
        historyTex = allocateTex(w, h);
        texWidth = w;
        texHeight = h;
        LDOGMod.LOGGER.info("LDOG: TAA current+history textures allocated at {}x{}", w, h);
    }

    private static int allocateTex(int w, int h) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return id;
    }
}
