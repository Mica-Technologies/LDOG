package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.compat.OptiFineCompat;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-object draw-call dispatcher for an active shader pack's {@code gbuffers_*}
 * programs, plus the multi-render-target (MRT) G-buffer the pack writes into.
 * Mixins wrapping each MC draw type call {@link #begin} before the draw and
 * {@link #end} after; in between, the matching pack program is bound, the
 * program's {@code DRAWBUFFERS} output mapping is applied, and the standard
 * uniform set is fed.
 *
 * <h3>How it works on MC 1.12.2's legacy pipeline</h3>
 *
 * <p>Vanilla 1.12.2 renders the world fixed-function (no GLSL program bound).
 * Both immediate-mode draws (clouds, weather, hand) and chunk VBO draws
 * (terrain) feed the built-in {@code gl_Vertex}/{@code gl_Color}/
 * {@code gl_MultiTexCoord}/{@code gl_ModelViewMatrix} state, so a
 * {@code #version 120} gbuffer shader using {@code ftransform()} transforms +
 * textures correctly with no custom vertex format. The block atlas stays on
 * unit 0 and the lightmap on unit 1, so {@code texture}/{@code lightmap}
 * resolve to live data.
 *
 * <h3>MRT G-buffer</h3>
 *
 * <p>When the post-process pipeline is active (so a scene FBO exists), the
 * dispatcher builds a G-buffer FBO that <em>reuses</em> the pipeline's scene
 * colour texture as {@code colortex0} and its depth texture, and adds aux
 * {@code colortex1..N} attachments. Gbuffer programs writing
 * {@code gl_FragData[i]} land in {@code colortex[drawBuffers[i]]}; the composite
 * chain then samples real normal / material / extra-channel data instead of the
 * 1×1 black it used to read. The world's {@code colortex0} is the same texture
 * the rest of the LDOG pipeline (FSR / TAA / composite) already consumes, so
 * nothing downstream changes.
 *
 * <h3>Remaining gap</h3>
 *
 * <ul>
 *   <li>Custom vertex attributes ({@code mc_Entity}, {@code at_tangent}) absent
 *       → block-id / parallax effects degrade to zero.</li>
 *   <li>Aux attachments cap at {@code colortex1..7} ({@link #MAX_AUX}); a pack
 *       asking for more gets GL_NONE in those DRAWBUFFERS slots (the write is
 *       discarded, but the surviving slots keep their gl_FragData index).</li>
 *   <li>Shadow pass wired separately (see {@link ShadowMapManager}).</li>
 * </ul>
 *
 * <p>Gated behind {@link LDOGConfig#enableShaderGbuffers} (opt-in, default off).
 */
public final class ShaderPackGbufferManager {

    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    /** Hard cap on aux colortex attachments (colortex1..7 alongside colortex0). */
    private static final int MAX_AUX = 7;

    /** Saved {@code GL_CURRENT_PROGRAM} ids to restore on {@link #end}. */
    private static final Deque<Integer> PROGRAM_STACK = new ArrayDeque<>();

    /**
     * The per-frame uniform snapshot shared by EVERY stage of the pack —
     * gbuffer draws, the shadow program, and the composite chain. One instance
     * is deliberate: separate instances drift apart on {@code frameCounter} /
     * {@code frameTimeCounter} (each advances on its own snapshot) and, worse,
     * interpolate {@code cameraPosition} at different partialTicks than the
     * {@code gbufferModelView} the same frame was rendered with — an
     * oscillating translation mismatch that reads as in-motion shimmer.
     */
    private static final ShaderPackUniforms UNIFORMS = new ShaderPackUniforms();
    private static boolean snapshottedThisFrame;

    /** 1x1 black texture for unused samplers (normals/specular/shadow). */
    private static int blackTex;

    private static boolean loggedFirstBind;

    // --- MRT G-buffer state ---
    private static int gbufferFbo;
    private static final int[] aux = new int[MAX_AUX + 1]; // aux[1..N]; aux[0] unused (colortex0 = scene)
    private static int auxCount;
    private static int lastSceneColor, lastSceneDepth, lastW, lastH, lastAuxCount;
    /** Reusable scratch for glDrawBuffers. */
    private static final IntBuffer DRAW_BUF = BufferUtils.createIntBuffer(MAX_AUX + 1);
    private static boolean gbufferBound;

    private ShaderPackGbufferManager() {}

    /** True when the dispatcher should attempt to take over draws at all. */
    public static boolean isActive() {
        if (!LDOGConfig.enableShaders || !LDOGConfig.enableShaderGbuffers) return false;
        // Never drive a gbuffer program while OptiFine owns the shader pipeline —
        // two systems rebinding GL programs around the same draws is a guaranteed
        // corrupt frame. Cached O(1) lookup, so it is safe on this per-draw path.
        if (!OptiFineCompat.shouldHandleShaders()) return false;
        ShaderPackRuntime rt = ShaderPackManager.INSTANCE.getRuntime();
        return rt != null && rt.hasGbuffers();
    }

    /**
     * True when the full MRT deferred path can run: dispatcher active, the
     * post-process pipeline is on, and its scene target is allocated (so we have
     * a colortex0 + depth to wrap).
     */
    public static boolean isDeferredActive() {
        return isActive()
            && LDOGConfig.enablePostProcessPipeline
            && RenderTargetManager.INSTANCE.isReady();
    }

    /** Number of aux colortex attachments currently allocated (colortex1..N). */
    public static int auxColortexCount() { return auxCount; }

    /** The G-buffer FBO (colortex0=scene + aux1..N attached), or 0 if not built. */
    public static int gbufferFbo() { return gbufferFbo; }

    /** Texture handle for colortexI: scene colour for 0, aux for 1..N, else 0. */
    public static int colortex(int i) {
        if (i == 0) return RenderTargetManager.INSTANCE.getSceneColorTexture();
        if (i >= 1 && i <= auxCount) return aux[i];
        return 0;
    }

    /**
     * Reset the per-frame uniform snapshot latch. Called at renderSky HEAD
     * (first world draw of the frame).
     */
    public static void beginFrame() {
        // Baseline drain: clear any error left by vanilla / other LDOG features
        // BEFORE our world+pipeline stages, so a later stage's probe only sees
        // errors it actually produced. Anything reported here originates upstream.
        com.limitlessdev.ldog.render.pipeline.PipelineGlProbe.drain("frame-start (upstream)");
        snapshottedThisFrame = false;
        ShadowMapManager.beginFrame();
        ShaderPackUniforms.clearWorldMatrices();
    }

    /** Texture unit reserved for the shadow depth map (above the composite's 0..8). */
    private static final int SHADOW_UNIT = 9;

    /**
     * Bind the G-buffer FBO for the world render, clear its aux attachments, and
     * leave {@code glDrawBuffers} at colortex0 so vanilla's clear + any
     * un-dispatched draws only touch the scene colour. Returns the FBO handle,
     * or 0 when MRT couldn't be set up (caller then binds the plain scene FBO).
     * Called from the pipeline bind mixin in place of binding the scene FBO.
     */
    public static int beginWorldGBuffer() {
        int fbo = ensureGBuffer();
        if (fbo == 0) { gbufferBound = false; return 0; }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        gbufferBound = true;
        // Clear aux buffers to zero (vanilla's upcoming clear only covers the
        // draw-buffer set, which we narrow to colortex0 next).
        if (auxCount > 0) {
            setDrawBuffers(rangeAux());
            // MUST go through GlStateManager: it caches the clear colour and
            // skips redundant glClearColor calls. A raw GL11.glClearColor here
            // desyncs that cache, so vanilla's very next
            // GlStateManager.clearColor(fog...) sees "already set" and no-ops —
            // leaving the world cleared to OUR transparent black instead of the
            // fog colour.
            GlStateManager.clearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        setDrawBuffers(SINGLE0);
        com.limitlessdev.ldog.render.pipeline.PipelineGlProbe.drain("gbuffer:beginWorld");
        return fbo;
    }

    /** Whether the G-buffer FBO is the active world-render target this frame. */
    public static boolean isGBufferBound() { return gbufferBound; }

    /** Mark the G-buffer unbound (called at world-pass RETURN). */
    public static void endWorldGBuffer() {
        com.limitlessdev.ldog.render.pipeline.PipelineGlProbe.drain("gbuffer-world");
        gbufferBound = false;
    }

    /**
     * Bind the pack program for {@code category} and feed its uniforms. When the
     * G-buffer is bound, also apply the program's {@code DRAWBUFFERS} mapping so
     * its {@code gl_FragData[i]} outputs route to the right colortex. No-op (and
     * pushes nothing) when inactive or the pack ships none of the category's
     * programs, keeping the paired {@link #end} balanced.
     */
    public static void begin(GbufferProgram category) {
        if (!isActive()) return;
        // Don't recurse into the gbuffer path while the shadow map's terrain
        // replay is running — it renders depth-only with its own matrices.
        if (ShadowMapManager.isShadowPass()) return;
        ShaderPackRuntime rt = ShaderPackManager.INSTANCE.getRuntime();
        if (rt == null) return;
        ShaderPackRuntime.Stage stage = rt.resolveGbuffer(category);
        if (stage == null || stage.program == null) return;

        ensureWorldFrameSnapshot();
        ensureBlackTex();

        int prev = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        PROGRAM_STACK.push(prev);

        stage.program.bind();
        feedSamplers(stage.program);
        UNIFORMS.feedTo(stage.program);
        ShadowMapManager.feed(stage.program, SHADOW_UNIT);

        // Route MRT outputs for this program.
        if (gbufferBound) setDrawBuffers(mapDrawBuffers(stage.drawBuffers));

        // Localize errors from this category's program bind + uniform feed +
        // draw-buffer routing (the subsequent vanilla draw, if it errors, still
        // surfaces at the gbuffer-world boundary). Logged once per category.
        com.limitlessdev.ldog.render.pipeline.PipelineGlProbe.drain("gbuffer:bind:" + category);

        if (!loggedFirstBind) {
            loggedFirstBind = true;
            LDOGMod.LOGGER.info(
                "LDOG: Shader gbuffer dispatch ACTIVE — first bind {} ({}), MRT={} (colortex0..{})",
                category, rt.packName(), gbufferBound, auxCount);
        }
    }

    /**
     * Bind + feed the standard uniform set to the pack's shadow program for the
     * shadow-map render pass (called by {@link ShadowMapManager}). Same samplers
     * + per-frame uniforms a gbuffer draw gets (so waving/animation matches), but
     * the caller supplies the shadow matrices itself. Does NOT touch draw buffers
     * or the program stack — the shadow pass manages its own GL state.
     */
    public static void feedShadowProgram(ShaderProgram program) {
        ensureWorldFrameSnapshot();
        ensureBlackTex();
        program.bind();
        feedSamplers(program);
        UNIFORMS.feedTo(program);
        // feedSamplers points shadow/shadowtex0/1 at the 1x1 RGBA black texture.
        // A shadow.fsh that declares them sampler2DShadow would then sample a
        // non-compare, non-depth texture — GL_INVALID_OPERATION on every draw of
        // the shadow pass. Re-point them at the compare-mode dummy DEPTH texture
        // instead. Deliberately the DUMMY and not the real map: the real shadow
        // map is the render target of the pass we're feeding, so sampling it
        // would be a framebuffer feedback loop.
        ShadowMapManager.feedDummyShadowSamplers(program, SHADOW_UNIT);
    }

    /** Restore the program + colortex0-only draw buffer after a {@link #begin}. */
    public static void end() {
        if (PROGRAM_STACK.isEmpty()) return;
        int prev = PROGRAM_STACK.pop();
        GL20.glUseProgram(prev);
        if (gbufferBound) setDrawBuffers(SINGLE0);
    }

    public static void dispose() {
        if (blackTex != 0) { GL11.glDeleteTextures(blackTex); blackTex = 0; }
        disposeGBuffer();
        PROGRAM_STACK.clear();
        snapshottedThisFrame = false;
        loggedFirstBind = false;
        gbufferBound = false;
    }

    // --- MRT allocation ---

    /**
     * Build or rebuild the G-buffer FBO so it wraps the current scene colour +
     * depth textures with {@code auxCount} aux attachments. Returns the FBO, or
     * 0 if the scene target isn't ready.
     */
    private static int ensureGBuffer() {
        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        if (!rtm.isReady()) return 0;
        int sceneColor = rtm.getSceneColorTexture();
        int sceneDepth = rtm.getSceneDepthTexture();
        int w = rtm.getScaledWidth();
        int h = rtm.getScaledHeight();

        ShaderPackRuntime rt = ShaderPackManager.INSTANCE.getRuntime();
        int wantAux = rt == null ? 0 : Math.min(MAX_AUX, rt.maxColortex());

        if (gbufferFbo != 0 && sceneColor == lastSceneColor && sceneDepth == lastSceneDepth
            && w == lastW && h == lastH && wantAux == lastAuxCount) {
            return gbufferFbo;
        }

        disposeGBuffer();
        auxCount = wantAux;

        gbufferFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, gbufferFbo);
        // colortex0 = the pipeline's scene colour (shared — no extra VRAM, and
        // downstream passes read it unchanged).
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, sceneColor, 0);
        for (int i = 1; i <= auxCount; i++) {
            // Honour the pack's declared colortexI/gauxI format (HDR, 16-bit,
            // RGB10_A2, ...) so normal/material/temporal buffers keep precision
            // and HDR aux buffers don't clip — defaults to RGBA8 when undeclared.
            int fmt = rt == null ? ShaderColortexFormats.DEFAULT_FORMAT : rt.colortexFormat(i);
            aux[i] = allocAux(w, h, fmt);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i,
                GL11.GL_TEXTURE_2D, aux[i], 0);
        }
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
            GL11.GL_TEXTURE_2D, sceneDepth, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: G-buffer FBO incomplete (status=0x{}, aux={})",
                Integer.toHexString(status), auxCount);
            disposeGBuffer();
            return 0;
        }

        lastSceneColor = sceneColor; lastSceneDepth = sceneDepth;
        lastW = w; lastH = h; lastAuxCount = auxCount;
        LDOGMod.LOGGER.info("LDOG: G-buffer ready — colortex0 (scene) + {} aux at {}x{}", auxCount, w, h);
        return gbufferFbo;
    }

    private static int allocAux(int w, int h, int internalFormat) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0,
            ShaderColortexFormats.uploadFormat(internalFormat),
            ShaderColortexFormats.uploadType(internalFormat), (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static void disposeGBuffer() {
        for (int i = 1; i <= MAX_AUX; i++) {
            if (aux[i] != 0) { GL11.glDeleteTextures(aux[i]); aux[i] = 0; }
        }
        if (gbufferFbo != 0) { GL30.glDeleteFramebuffers(gbufferFbo); gbufferFbo = 0; }
        auxCount = 0;
        lastSceneColor = lastSceneDepth = lastW = lastH = lastAuxCount = 0;
    }

    // --- draw-buffer helpers ---

    private static final int[] SINGLE0 = {0};

    /** colortex indices 1..auxCount (the aux attachments) for the clear pass. */
    private static int[] rangeAux() {
        int[] r = new int[auxCount];
        for (int i = 0; i < auxCount; i++) r[i] = i + 1;
        return r;
    }

    /** Marks a DRAWBUFFERS slot we can't back with a real attachment. */
    private static final int SLOT_NONE = -1;

    /**
     * Mask out any target index we didn't allocate — WITHOUT changing the
     * position of the surviving entries.
     *
     * <p>The array index is the {@code gl_FragData[i]} slot, so removing an
     * entry shifts every later output into the wrong colortex. With
     * {@code DRAWBUFFERS:08367} and only colortex0..7 allocated, dropping the
     * unallocatable {@code 8} would route gl_FragData[1]→colortex3,
     * [2]→colortex6, [3]→colortex7 — cross-contaminating three buffers a pack
     * like BSL then reads as normals/material data. GL_NONE holds the slot
     * open instead: the write is discarded and the rest stay aligned.
     */
    private static int[] mapDrawBuffers(int[] requested) {
        int[] masked = null;
        for (int i = 0; i < requested.length; i++) {
            int idx = requested[i];
            if (idx >= 0 && idx <= auxCount) continue;
            if (masked == null) masked = requested.clone();
            masked[i] = SLOT_NONE;
        }
        return masked == null ? requested : masked;
    }

    /**
     * Set glDrawBuffers to the given colortex indices (as COLOR_ATTACHMENT0+i);
     * {@link #SLOT_NONE} entries become GL_NONE placeholders.
     */
    private static void setDrawBuffers(int[] indices) {
        DRAW_BUF.clear();
        for (int idx : indices) {
            if (!DRAW_BUF.hasRemaining()) break;  // more outputs than attachments
            DRAW_BUF.put(idx == SLOT_NONE ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0 + idx);
        }
        DRAW_BUF.flip();
        GL20.glDrawBuffers(DRAW_BUF);
    }

    /**
     * The frame's shared uniform snapshot. Feed it to any pack program —
     * gbuffer, shadow, deferred, composite or final — so every stage of the
     * frame sees identical values. Take the snapshot first via
     * {@link #ensureFrameSnapshot(float)}.
     */
    public static ShaderPackUniforms uniforms() { return UNIFORMS; }

    /**
     * Take this frame's uniform snapshot if no stage has taken it yet, using
     * the REAL partialTicks of the frame being rendered. Idempotent within a
     * frame — the latch resets at {@link #beginFrame()} (and at
     * {@link #releaseFrameSnapshot()} for packs whose only consumer is the
     * composite chain).
     */
    public static void ensureFrameSnapshot(float partialTicks) {
        if (snapshottedThisFrame) return;
        Minecraft mc = Minecraft.getMinecraft();
        UNIFORMS.rotatePrev();
        UNIFORMS.snapshot(mc.displayWidth, mc.displayHeight, partialTicks);
        snapshottedThisFrame = true;
    }

    /**
     * Release the snapshot latch so the next frame re-samples. Called at the
     * end of the composite chain, which is the last consumer in a frame — a
     * safety net for frames where {@link #beginFrame()} never fired (renderSky
     * is skipped below 4 chunks of render distance).
     */
    public static void releaseFrameSnapshot() {
        snapshottedThisFrame = false;
    }

    private static void ensureWorldFrameSnapshot() {
        if (snapshottedThisFrame) return;
        // We're mid-world-render here (first gbuffer bind) — capture the camera
        // matrices so the later composite pass feeds them as gbufferModelView/
        // Projection instead of MC's GUI ortho matrix.
        ShaderPackUniforms.captureWorldMatrices();
        ensureFrameSnapshot(Minecraft.getMinecraft().getRenderPartialTicks());
    }

    private static void feedSamplers(ShaderProgram program) {
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blackTex);
        // noisetex on unit 3 — a real tiled noise texture (water/cloud/dither).
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderNoiseTexture.get());
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        program.setUniform1i("texture", 0);
        program.setUniform1i("tex", 0);
        program.setUniform1i("gtexture", 0);
        program.setUniform1i("gcolor", 0);
        program.setUniform1i("colortex0", 0);
        program.setUniform1i("lightmap", 1);
        program.setUniform1i("normals", 2);
        program.setUniform1i("specular", 2);
        program.setUniform1i("shadow", 2);
        program.setUniform1i("shadowtex0", 2);
        program.setUniform1i("shadowtex1", 2);
        program.setUniform1i("shadowcolor0", 2);
        program.setUniform1i("shadowcolor1", 2);
        program.setUniform1i("noisetex", 3);
        program.setUniform1i("noiseTextureResolution", ShaderNoiseTexture.RESOLUTION);
    }

    private static void ensureBlackTex() {
        if (blackTex != 0) return;
        blackTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blackTex);
        java.nio.ByteBuffer zero = BufferUtils.createByteBuffer(4);
        zero.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zero);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
