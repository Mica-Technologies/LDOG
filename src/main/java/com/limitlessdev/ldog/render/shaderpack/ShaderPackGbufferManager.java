package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.Minecraft;
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
 *   <li>Composite stages can READ all colortex but only WRITE colortex0 (the
 *       existing single-target ping-pong). Multi-write composite chains aren't
 *       driven yet.</li>
 *   <li>Custom vertex attributes ({@code mc_Entity}, {@code at_tangent}) absent
 *       → block-id / parallax effects degrade to zero.</li>
 *   <li>Shadow pass wired separately (see shadow target hookup).</li>
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
            GL11.glClearColor(0f, 0f, 0f, 0f);
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

        ensureFrameSnapshot();
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

    /** Drop any target index that exceeds what we actually allocated. */
    private static int[] mapDrawBuffers(int[] requested) {
        int n = 0;
        for (int idx : requested) if (idx <= auxCount) n++;
        if (n == requested.length) return requested;
        int[] r = new int[Math.max(1, n)];
        if (n == 0) { r[0] = 0; return r; }
        int j = 0;
        for (int idx : requested) if (idx <= auxCount) r[j++] = idx;
        return r;
    }

    /** Set glDrawBuffers to the given colortex indices (as COLOR_ATTACHMENT0+i). */
    private static void setDrawBuffers(int[] indices) {
        DRAW_BUF.clear();
        for (int idx : indices) DRAW_BUF.put(GL30.GL_COLOR_ATTACHMENT0 + idx);
        DRAW_BUF.flip();
        GL20.glDrawBuffers(DRAW_BUF);
    }

    private static void ensureFrameSnapshot() {
        if (snapshottedThisFrame) return;
        Minecraft mc = Minecraft.getMinecraft();
        // We're mid-world-render here (first gbuffer bind) — capture the camera
        // matrices so the later composite pass feeds them as gbufferModelView/
        // Projection instead of MC's GUI ortho matrix.
        ShaderPackUniforms.captureWorldMatrices();
        UNIFORMS.rotatePrev();
        UNIFORMS.snapshot(mc.displayWidth, mc.displayHeight, mc.getRenderPartialTicks());
        snapshottedThisFrame = true;
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
