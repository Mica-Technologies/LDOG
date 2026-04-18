package com.limitlessdev.ldog.render.display;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
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

    /**
     * Startup path. Called before Display.create() while MC's
     * setInitialDisplayMode is running — the Display isn't created yet, and
     * MC's framebufferMc doesn't exist. We can't touch mc.displayWidth/Height
     * or updateFramebufferSize here; MC's own post-setFullscreen code reads
     * Display.getDisplayMode() afterward and sets those fields, then creates
     * the framebuffer with the correct dimensions on the next line.
     *
     * LDOGConfig may not yet be synced from disk at this point (FML preInit
     * hasn't run), so blockFullscreenOptimizations reads as the class default
     * (true). On first launch after the user toggles the config off, they'll
     * get dodge behavior at startup and their strict-fullscreen preference
     * only on F11 toggle afterward — acceptable edge case.
     */
    public static void setupAtStartup() throws LWJGLException {
        DisplayMode desktop = Display.getDesktopDisplayMode();
        int w = desktop.getWidth();
        int h = LDOGConfig.blockFullscreenOptimizations
            ? desktop.getHeight() - 1
            : desktop.getHeight();

        Display.setResizable(false);
        Display.setLocation(0, 0);
        Display.setDisplayMode(new DisplayMode(w, h));

        LDOGMod.LOGGER.info("LDOG: startup borderless prep — {}x{} (Display not yet created)", w, h);
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
        int w = desktop.getWidth();
        // Windows 10/11's Fullscreen Optimizations feature detects a borderless
        // window sized EXACTLY to the monitor at (0,0) and auto-transitions the
        // DWM into an optimized borderless-fullscreen compositor path. The
        // transition briefly flashes the desktop.
        //
        // Config toggle: when blockFullscreenOptimizations is ON, size the
        // window 1px shorter than the desktop so Windows doesn't trigger the
        // detection (no flicker, but taskbar stays visible in that 1px gap).
        // When OFF, match the desktop exactly — cleaner look (taskbar auto-
        // hides via DWM), at the cost of the transition flash on each toggle.
        int h = LDOGConfig.blockFullscreenOptimizations
            ? desktop.getHeight() - 1
            : desktop.getHeight();

        long t0 = System.nanoTime();
        Display.setResizable(false);
        long t1 = System.nanoTime();
        Display.setLocation(0, 0);
        long t2 = System.nanoTime();
        Display.setDisplayMode(new DisplayMode(w, h));
        long t3 = System.nanoTime();

        mc.gameSettings.fullScreen = true;
        ((AccessorMinecraft) (Object) mc).ldog$setFullscreen(true);
        // mc.resize(w, h) rather than direct dim-assignment + updateFramebufferSize:
        // resize() also invokes currentScreen.onResize, which is what lets an
        // already-displayed GuiScreen (the main menu on startup, or any open
        // settings screen during an F11) relayout itself for the new
        // dimensions. Without this the framebuffer is correct but the GUI
        // stays stuck at the pre-resize size, producing the 'menu in corner
        // with black bars' bug the user saw at launch with fullScreen=true.
        mc.resize(w, h);

        LDOGMod.LOGGER.info(
            "LDOG: entered borderless fullscreen at {}x{} (setResizable={}ms, setLocation={}ms, setDisplayMode={}ms)",
            w, h,
            (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
    }

    public static void exitBorderless(Minecraft mc) throws LWJGLException {
        // Fall back to reasonable defaults if we never captured windowed state
        // (e.g. game was launched into fullscreen already and this is the first
        // toggle).
        int w = hasSavedState ? savedWidth : 854;
        int h = hasSavedState ? savedHeight : 480;

        long t0 = System.nanoTime();
        Display.setResizable(true);
        long t1 = System.nanoTime();
        if (hasSavedState) {
            Display.setLocation(savedX, savedY);
        }
        long t2 = System.nanoTime();
        Display.setDisplayMode(new DisplayMode(w, h));
        long t3 = System.nanoTime();

        mc.gameSettings.fullScreen = false;
        ((AccessorMinecraft) (Object) mc).ldog$setFullscreen(false);
        // See enterBorderless comment — mc.resize relayouts currentScreen.
        mc.resize(w, h);

        LDOGMod.LOGGER.info(
            "LDOG: exited borderless fullscreen back to {}x{} (setResizable={}ms, setLocation={}ms, setDisplayMode={}ms)",
            w, h,
            (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
    }
}
