package com.limitlessdev.ldog.render.pipeline;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * Re-converges {@link GlStateManager}'s state cache with the real GL state.
 *
 * <h3>Why this is needed</h3>
 *
 * <p>{@code GlStateManager} caches the enable flags and the clear colour, and
 * SKIPS the GL call when the requested value already matches its cache. Our
 * passes wrap their work in {@code glPushAttrib}/{@code glPopAttrib} while
 * flipping those very states through {@code GlStateManager}. {@code glPopAttrib}
 * restores the real GL state but knows nothing about the cache, so afterwards
 * the two disagree — and the first thing vanilla does is issue a
 * {@code GlStateManager.disableDepth()} (or {@code clearColor(fog...)}) that is
 * silently dropped as "already set" while the driver is in the opposite state.
 * The visible result is a per-frame flicker whose cause moves around depending
 * on which pass ran last.
 *
 * <p>Rather than hand-tracking the pre-push values (GlStateManager's fields are
 * private, so they can't be read back), we ask GL what is actually true after
 * the pop and push that answer back through GlStateManager. When the cache
 * already agrees the call is a no-op; when it doesn't, GlStateManager issues one
 * redundant GL call and lands back in sync. Cost is a handful of cheap queries
 * per pass per frame.
 *
 * <p>Call {@link #afterPopAttrib()} immediately after every
 * {@code GL11.glPopAttrib()} in a pass that used GlStateManager inside the
 * push/pop region.
 */
public final class GlStateSync {

    private static final FloatBuffer CLEAR_COLOR_BUF = BufferUtils.createFloatBuffer(16);

    private GlStateSync() {}

    /** Re-sync every GlStateManager-tracked state our passes touch. */
    public static void afterPopAttrib() {
        syncEnableStates();
        syncClearColor();
    }

    /** Depth test / face culling / blending / alpha test enable flags. */
    public static void syncEnableStates() {
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();

        if (GL11.glIsEnabled(GL11.GL_CULL_FACE)) GlStateManager.enableCull();
        else GlStateManager.disableCull();

        if (GL11.glIsEnabled(GL11.GL_BLEND)) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();

        if (GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
    }

    /** The cached clear colour (GL_COLOR_BUFFER_BIT restores it on pop). */
    public static void syncClearColor() {
        CLEAR_COLOR_BUF.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, CLEAR_COLOR_BUF);
        GlStateManager.clearColor(
            CLEAR_COLOR_BUF.get(0), CLEAR_COLOR_BUF.get(1),
            CLEAR_COLOR_BUF.get(2), CLEAR_COLOR_BUF.get(3));
    }
}
