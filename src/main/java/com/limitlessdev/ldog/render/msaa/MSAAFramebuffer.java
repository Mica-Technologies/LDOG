package com.limitlessdev.ldog.render.msaa;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Manages an auxiliary multisampled FBO used to add MSAA to world geometry.
 *
 * Flow: MixinEntityRenderer binds this FBO at the start of renderWorldPass,
 * the world is rendered into multisampled color + depth attachments, then the
 * color attachment is blit-resolved into Minecraft's main framebuffer at the
 * end of the pass. Subsequent GUI / HUD rendering targets the main FBO as
 * normal — only world geometry gets MSAA, which is the only place it matters.
 */
public final class MSAAFramebuffer {

    private static int fbo;
    private static int colorRbo;
    private static int depthRbo;
    private static int width;
    private static int height;
    private static int samples;
    private static boolean unsupported;
    private static boolean loggedSupport;

    private MSAAFramebuffer() {}

    public static boolean isSupported() {
        if (unsupported) return false;
        boolean ok = GLContext.getCapabilities().OpenGL30
            || (GLContext.getCapabilities().GL_EXT_framebuffer_multisample
                && GLContext.getCapabilities().GL_EXT_framebuffer_blit);
        if (!ok && !loggedSupport) {
            loggedSupport = true;
            LDOGMod.LOGGER.warn("LDOG: MSAA unsupported — missing GL 3.0 or EXT_framebuffer_multisample+blit");
            unsupported = true;
        }
        return ok;
    }

    /**
     * Ensure the FBO exists with the requested dimensions and sample count.
     * Re-creates when any of those change (e.g. window resize, settings change).
     */
    public static boolean ensure(int w, int h) {
        if (!isSupported() || w <= 0 || h <= 0) return false;

        int maxSamples = GL11.glGetInteger(GL30.GL_MAX_SAMPLES);
        int wanted = Math.max(2, Math.min(LDOGConfig.msaaSamples, maxSamples));

        if (fbo != 0 && width == w && height == h && samples == wanted) return true;

        dispose();
        width = w;
        height = h;
        samples = wanted;

        fbo = GL30.glGenFramebuffers();
        colorRbo = GL30.glGenRenderbuffers();
        depthRbo = GL30.glGenRenderbuffers();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, colorRbo);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, samples, GL11.GL_RGBA8, w, h);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_RENDERBUFFER, colorRbo);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRbo);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, samples, GL30.GL_DEPTH24_STENCIL8, w, h);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
            GL30.GL_RENDERBUFFER, depthRbo);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: MSAA FBO incomplete (status=0x{}), disabling",
                Integer.toHexString(status));
            dispose();
            unsupported = true;
            return false;
        }

        LDOGMod.LOGGER.info("LDOG: MSAA FBO ready {}x{} samples={} (GPU max={})",
            w, h, samples, maxSamples);
        return true;
    }

    public static void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
    }

    /**
     * Blit-resolve multisampled color into the target FBO, then leave the
     * target FBO bound for subsequent drawing. Depth is intentionally NOT
     * blitted: the formats differ (GL_DEPTH24_STENCIL8 here vs MC's
     * GL_DEPTH_COMPONENT) and driver behavior for mismatched-format depth
     * resolve is implementation-defined. MC's GUI pass doesn't read the
     * world-pass depth so dropping it is safe.
     */
    public static void resolveTo(int targetFbo) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
    }

    public static void dispose() {
        if (depthRbo != 0) { GL30.glDeleteRenderbuffers(depthRbo); depthRbo = 0; }
        if (colorRbo != 0) { GL30.glDeleteRenderbuffers(colorRbo); colorRbo = 0; }
        if (fbo != 0) { GL30.glDeleteFramebuffers(fbo); fbo = 0; }
        width = height = samples = 0;
    }
}
