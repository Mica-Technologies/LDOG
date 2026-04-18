package com.limitlessdev.ldog.render.display;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.mixin.AccessorMinecraft;
import net.minecraft.client.Minecraft;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Runtime fullscreen toggle for the Phase 10 borderless-windowed mode.
 *
 * When the core plugin has set Window.undecorated=true at startup, the main
 * Display has no decorations for the entire session. "Fullscreen toggle" in
 * this mode doesn't enter exclusive fullscreen — it just resizes the
 * already-undecorated window to the desktop size and positions it at (0, 0).
 * Looks identical to fullscreen; costs a cheap resize instead of a GL
 * context switch. Toggling back restores the previous windowed bounds.
 *
 * The saved bounds are captured the first time we enter borderless mode in
 * a session, so the initial "game launched at 854x480" state is preserved
 * as the windowed restore target.
 */
public final class BorderlessFullscreenHandler {

    private static int savedWidth;
    private static int savedHeight;
    private static int savedX;
    private static int savedY;
    private static boolean hasSavedState;

    private BorderlessFullscreenHandler() {}

    /** True when the Display was created undecorated (core-plugin path). */
    public static boolean isActive() {
        return "true".equalsIgnoreCase(System.getProperty("org.lwjgl.opengl.Window.undecorated"));
    }

    public static void enterBorderless(Minecraft mc) throws LWJGLException {
        if (!hasSavedState) {
            savedWidth = Display.getWidth();
            savedHeight = Display.getHeight();
            savedX = Display.getX();
            savedY = Display.getY();
            hasSavedState = true;
        }

        DisplayMode desktop = Display.getDesktopDisplayMode();
        Display.setDisplayMode(desktop);
        Display.setResizable(false);
        Display.setLocation(0, 0);

        mc.displayWidth = desktop.getWidth();
        mc.displayHeight = desktop.getHeight();
        mc.gameSettings.fullScreen = true;
        ((AccessorMinecraft) (Object) mc).ldog$setFullscreen(true);
        ((AccessorMinecraft) (Object) mc).ldog$updateFramebufferSize();

        LDOGMod.LOGGER.info("LDOG: entered borderless fullscreen at {}x{}",
            desktop.getWidth(), desktop.getHeight());
    }

    public static void exitBorderless(Minecraft mc) throws LWJGLException {
        // Fall back to reasonable defaults if we never captured windowed state
        // (e.g. game was launched into fullscreen already and this is the first
        // toggle).
        int w = hasSavedState ? savedWidth : 854;
        int h = hasSavedState ? savedHeight : 480;

        Display.setDisplayMode(new DisplayMode(w, h));
        Display.setResizable(true);
        if (hasSavedState) {
            Display.setLocation(savedX, savedY);
        }

        mc.displayWidth = w;
        mc.displayHeight = h;
        mc.gameSettings.fullScreen = false;
        ((AccessorMinecraft) (Object) mc).ldog$setFullscreen(false);
        ((AccessorMinecraft) (Object) mc).ldog$updateFramebufferSize();

        LDOGMod.LOGGER.info("LDOG: exited borderless fullscreen back to {}x{}", w, h);
    }
}
