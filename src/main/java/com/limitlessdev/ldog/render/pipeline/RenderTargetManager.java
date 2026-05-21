package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Owns the offscreen color/depth target used by the post-process pipeline,
 * plus a transient ping-pong color target for multi-pass filter chains.
 *
 * Phase 8a.2 scope: this class only manages lifecycle (create / resize /
 * dispose). It does not bind the scene target for world rendering on its
 * own — that's the job of a future mixin hook when a functional pass
 * (e.g. FSR1) needs scaled-resolution input. Allocating eagerly here lets
 * downstream passes hold stable handles without each reimplementing FBO
 * management.
 *
 * Color attachment is a texture (so passes can sample it); depth is a
 * renderbuffer (no need to sample it yet).
 */
public final class RenderTargetManager {

    public static final RenderTargetManager INSTANCE = new RenderTargetManager();

    private boolean unsupported;
    private boolean loggedSupport;

    private int baseWidth;
    private int baseHeight;
    private float scale = 1.0f;
    private int scaledWidth;
    private int scaledHeight;
    /** HDR pipeline mode — drives scene + ping color internal format. */
    private boolean hdr;

    // Scene (color + depth) target — receives scaled world rendering. Depth
    // is a TEXTURE (not a renderbuffer) since TAA's motion-vector path needs
    // to sample it in a fragment shader. Moved from RBO → texture in Phase
    // 9c.2 when depth sampling became a requirement.
    private int sceneFbo;
    private int sceneColorTex;
    private int sceneDepthTex;
    // Phase 9c.3-A: single-channel R8 attached to sceneFbo as COLOR_ATTACHMENT1.
    // Holds the "this pixel was drawn by an entity" reactive mask. Always
    // allocated so the FBO layout is stable — population is gated by the
    // pipeline binding mixin via glDrawBuffers + glColorMaski. TAA samples
    // it to drop history weight on entity silhouettes (kills moving-entity
    // ghosting without per-entity motion-vector emission).
    private int sceneReactiveMaskTex;

    // Ping-pong color-only target for multi-pass filters.
    private int pingFbo;
    private int pingColorTex;

    // Phase 9c.3-C: motion-vector target for entity reprojection. Half-res
    // RG16F (R = screen-space dx in [-1, +1] NDC units, G = dy). Allocated
    // alongside the scene target so dim changes reallocate atomically. Half
    // resolution because TAA bilinear-samples it — softness on the velocity
    // texture acts as a built-in smoothing pass and saves half the bandwidth.
    private int mvFbo;
    private int mvTex;
    private int mvWidth;
    private int mvHeight;

    private RenderTargetManager() {}

    public boolean isSupported() {
        if (unsupported) return false;
        boolean ok = GLContext.getCapabilities().OpenGL30;
        if (!ok && !loggedSupport) {
            loggedSupport = true;
            LDOGMod.LOGGER.warn("LDOG: Pipeline render targets unsupported — GL 3.0 not available");
            unsupported = true;
        }
        return ok;
    }

    public boolean isReady() {
        return sceneFbo != 0;
    }

    public int getSceneFbo() { return sceneFbo; }
    public int getSceneColorTexture() { return sceneColorTex; }
    public int getSceneDepthTexture() { return sceneDepthTex; }
    public int getSceneReactiveMaskTexture() { return sceneReactiveMaskTex; }
    public int getPingPongFbo() { return pingFbo; }
    public int getPingPongColorTexture() { return pingColorTex; }
    public int getMotionVectorFbo() { return mvFbo; }
    public int getMotionVectorTexture() { return mvTex; }
    public int getMotionVectorWidth() { return mvWidth; }
    public int getMotionVectorHeight() { return mvHeight; }
    public int getScaledWidth() { return scaledWidth; }
    public int getScaledHeight() { return scaledHeight; }
    public float getScale() { return scale; }
    public boolean isHDR() { return hdr; }

    /**
     * Ensure targets exist at the requested base dimensions and render scale.
     * Reallocates when any of those change. Returns false if GPU support is
     * missing or allocation fails — callers should treat the manager as
     * not-ready and skip any pass that would require it.
     */
    public boolean ensure(int baseW, int baseH, float requestedScale) {
        return ensure(baseW, baseH, requestedScale, false);
    }

    /**
     * HDR-aware ensure variant. When {@code requestedHDR} is true the scene
     * + ping color textures are allocated as GL_RGBA16F so intermediate
     * pipeline values can exceed [0,1]. Switching HDR mode forces a
     * reallocation since the internal format is set at glTexImage time.
     */
    public boolean ensure(int baseW, int baseH, float requestedScale, boolean requestedHDR) {
        if (!isSupported() || baseW <= 0 || baseH <= 0) return false;

        float clampedScale = clampScale(requestedScale);
        int newScaledW = Math.max(1, Math.round(baseW * clampedScale));
        int newScaledH = Math.max(1, Math.round(baseH * clampedScale));

        if (sceneFbo != 0
            && baseWidth == baseW
            && baseHeight == baseH
            && scaledWidth == newScaledW
            && scaledHeight == newScaledH
            && hdr == requestedHDR) {
            return true;
        }

        disposeTargets();

        baseWidth = baseW;
        baseHeight = baseH;
        scale = clampedScale;
        scaledWidth = newScaledW;
        scaledHeight = newScaledH;
        hdr = requestedHDR;

        if (!createSceneTarget(newScaledW, newScaledH)
            || !createPingPongTarget(newScaledW, newScaledH)
            || !createMotionVectorTarget(newScaledW / 2, newScaledH / 2)) {
            LDOGMod.LOGGER.error("LDOG: Pipeline target allocation failed; disabling manager for this session");
            disposeTargets();
            unsupported = true;
            return false;
        }

        LDOGMod.LOGGER.info(
            "LDOG: Pipeline render targets ready ({}x{} @ scale {} -> {}x{}, format={})",
            baseW, baseH, clampedScale, newScaledW, newScaledH, hdr ? "RGBA16F" : "RGBA8");
        return true;
    }

    public void dispose() {
        disposeTargets();
        baseWidth = baseHeight = scaledWidth = scaledHeight = 0;
        scale = 1.0f;
    }

    private boolean createSceneTarget(int w, int h) {
        sceneFbo = GL30.glGenFramebuffers();
        sceneColorTex = GL11.glGenTextures();
        sceneDepthTex = GL11.glGenTextures();
        sceneReactiveMaskTex = GL11.glGenTextures();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneColorTex);
        // HDR uses RGBA16F (float linear, 16 bits per channel) so intermediate
        // pipeline values can exceed [0,1] for bloom/tonemap. LDR uses RGBA8
        // for memory + bandwidth savings.
        int colorInternal = hdr ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
        int colorType = hdr ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, colorInternal, w, h, 0,
            GL11.GL_RGBA, colorType, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Depth-stencil as a TEXTURE (not a renderbuffer) so Phase 9c.2 TAA
        // can sample it for motion-vector reprojection. GL_NEAREST filter —
        // interpolating depth values creates non-existent surfaces.
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepthTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8, w, h, 0,
            GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Phase 9c.3-A reactive-mask attachment. R8 single channel — fixed-
        // function fragment writes replicate gl_FragColor to all bound color
        // attachments, so the R component of any entity-fragment colour ends
        // up in this texture. We only care whether it's nonzero (entity drew
        // here) — the actual value is irrelevant. GL_LINEAR so upsampling in
        // TAA softens silhouette edges (catches motion-blur fringes).
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneReactiveMaskTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, w, h, 0,
            GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, sceneColorTex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
            GL11.GL_TEXTURE_2D, sceneReactiveMaskTex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
            GL11.GL_TEXTURE_2D, sceneDepthTex, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: Scene FBO incomplete (status=0x{})", Integer.toHexString(status));
            return false;
        }
        return true;
    }

    private boolean createPingPongTarget(int w, int h) {
        pingFbo = GL30.glGenFramebuffers();
        pingColorTex = GL11.glGenTextures();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pingColorTex);
        int pingInternal = hdr ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
        int pingType = hdr ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, pingInternal, w, h, 0,
            GL11.GL_RGBA, pingType, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, pingColorTex, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: Ping-pong FBO incomplete (status=0x{})", Integer.toHexString(status));
            return false;
        }
        return true;
    }

    private boolean createMotionVectorTarget(int w, int h) {
        // Half-res floor — clamp so 1px scenes don't try to allocate 0.
        w = Math.max(1, w);
        h = Math.max(1, h);
        mvFbo = GL30.glGenFramebuffers();
        mvTex = GL11.glGenTextures();
        mvWidth = w;
        mvHeight = h;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mvTex);
        // RG16F: two-channel float for screen-space velocity (NDC dx, dy).
        // Range roughly [-1, +1] for normal entity motion; clamp not needed
        // since RG16F is float — values can exceed [-1,1] if an entity
        // teleports, but TAA disocclusion handling will catch that anyway.
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG16F, w, h, 0,
            GL30.GL_RG, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mvFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, mvTex, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: MV FBO incomplete (status=0x{})", Integer.toHexString(status));
            return false;
        }
        return true;
    }

    private void disposeTargets() {
        if (sceneReactiveMaskTex != 0) { GL11.glDeleteTextures(sceneReactiveMaskTex); sceneReactiveMaskTex = 0; }
        if (sceneDepthTex != 0) { GL11.glDeleteTextures(sceneDepthTex); sceneDepthTex = 0; }
        if (sceneColorTex != 0) { GL11.glDeleteTextures(sceneColorTex); sceneColorTex = 0; }
        if (sceneFbo != 0)      { GL30.glDeleteFramebuffers(sceneFbo);  sceneFbo = 0; }
        if (pingColorTex != 0)  { GL11.glDeleteTextures(pingColorTex);  pingColorTex = 0; }
        if (pingFbo != 0)       { GL30.glDeleteFramebuffers(pingFbo);   pingFbo = 0; }
        if (mvTex != 0)         { GL11.glDeleteTextures(mvTex);         mvTex = 0; }
        if (mvFbo != 0)         { GL30.glDeleteFramebuffers(mvFbo);     mvFbo = 0; }
        mvWidth = mvHeight = 0;
    }

    private static float clampScale(float requested) {
        if (Float.isNaN(requested)) return 1.0f;
        if (requested < 0.5f) return 0.5f;
        if (requested > 1.0f) return 1.0f;
        return requested;
    }
}
