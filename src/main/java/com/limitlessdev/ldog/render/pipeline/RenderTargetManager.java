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

    // Scene (color + depth) target — receives scaled world rendering. Depth
    // is a TEXTURE (not a renderbuffer) since TAA's motion-vector path needs
    // to sample it in a fragment shader. Moved from RBO → texture in Phase
    // 9c.2 when depth sampling became a requirement.
    private int sceneFbo;
    private int sceneColorTex;
    private int sceneDepthTex;

    // Ping-pong color-only target for multi-pass filters.
    private int pingFbo;
    private int pingColorTex;

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
    public int getPingPongFbo() { return pingFbo; }
    public int getPingPongColorTexture() { return pingColorTex; }
    public int getScaledWidth() { return scaledWidth; }
    public int getScaledHeight() { return scaledHeight; }
    public float getScale() { return scale; }

    /**
     * Ensure targets exist at the requested base dimensions and render scale.
     * Reallocates when any of those change. Returns false if GPU support is
     * missing or allocation fails — callers should treat the manager as
     * not-ready and skip any pass that would require it.
     */
    public boolean ensure(int baseW, int baseH, float requestedScale) {
        if (!isSupported() || baseW <= 0 || baseH <= 0) return false;

        float clampedScale = clampScale(requestedScale);
        int newScaledW = Math.max(1, Math.round(baseW * clampedScale));
        int newScaledH = Math.max(1, Math.round(baseH * clampedScale));

        if (sceneFbo != 0
            && baseWidth == baseW
            && baseHeight == baseH
            && scaledWidth == newScaledW
            && scaledHeight == newScaledH) {
            return true;
        }

        disposeTargets();

        baseWidth = baseW;
        baseHeight = baseH;
        scale = clampedScale;
        scaledWidth = newScaledW;
        scaledHeight = newScaledH;

        if (!createSceneTarget(newScaledW, newScaledH)
            || !createPingPongTarget(newScaledW, newScaledH)) {
            LDOGMod.LOGGER.error("LDOG: Pipeline target allocation failed; disabling manager for this session");
            disposeTargets();
            unsupported = true;
            return false;
        }

        LDOGMod.LOGGER.info("LDOG: Pipeline render targets ready ({}x{} @ scale {} -> {}x{})",
            baseW, baseH, clampedScale, newScaledW, newScaledH);
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

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneColorTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
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

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, sceneColorTex, 0);
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
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
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

    private void disposeTargets() {
        if (sceneDepthTex != 0) { GL11.glDeleteTextures(sceneDepthTex); sceneDepthTex = 0; }
        if (sceneColorTex != 0) { GL11.glDeleteTextures(sceneColorTex); sceneColorTex = 0; }
        if (sceneFbo != 0)      { GL30.glDeleteFramebuffers(sceneFbo);  sceneFbo = 0; }
        if (pingColorTex != 0)  { GL11.glDeleteTextures(pingColorTex);  pingColorTex = 0; }
        if (pingFbo != 0)       { GL30.glDeleteFramebuffers(pingFbo);   pingFbo = 0; }
    }

    private static float clampScale(float requested) {
        if (Float.isNaN(requested)) return 1.0f;
        if (requested < 0.5f) return 0.5f;
        if (requested > 1.0f) return 1.0f;
        return requested;
    }
}
