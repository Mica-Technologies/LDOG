package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.display.BorderlessFullscreenHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's exclusive-fullscreen toggle with LDOG's borderless
 * resize-to-desktop behavior when the borderless mode is active (i.e. the
 * core plugin set Window.undecorated=true at startup).
 *
 * When borderless mode is NOT active (default / config off), this mixin is
 * a no-op and vanilla runs as usual.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftBorderless {

    /**
     * Startup fullscreen interception. MC's setInitialDisplayMode is called
     * BEFORE Display.create() and calls Display.setFullscreen(true) directly
     * (not via toggleFullscreen) when gameSettings.fullScreen is on at launch.
     * Without this redirect, users with fullscreen already saved would enter
     * true exclusive fullscreen at startup — our toggleFullscreen mixin only
     * intercepts later F11 presses. That's why the last test showed a
     * badly-sized main menu right after load: MC went into exclusive
     * fullscreen, resized displayWidth/Height, but the main-menu GuiScreen
     * didn't re-layout until the user navigated away and back.
     *
     * When borderless mode is active, redirect the setFullscreen call to our
     * startup-setup path instead. MC's subsequent code reads
     * Display.getDisplayMode() and updates displayWidth/Height itself, so
     * the framebuffer that gets created on the next line has the right
     * dimensions.
     */
    @Redirect(
        method = "setInitialDisplayMode",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setFullscreen(Z)V"))
    private void ldog$startupFullscreen(boolean fullscreen) throws LWJGLException {
        if (fullscreen && BorderlessFullscreenHandler.isActive()) {
            BorderlessFullscreenHandler.setupAtStartup();
        } else {
            Display.setFullscreen(fullscreen);
        }
    }

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void ldog$borderlessToggle(CallbackInfo ci) {
        if (!BorderlessFullscreenHandler.isActive()) return;

        Minecraft mc = (Minecraft) (Object) this;
        boolean currentlyFullscreen = ((AccessorMinecraft) (Object) mc).ldog$getFullscreen();
        boolean goingToFullscreen = !currentlyFullscreen;

        try {
            if (goingToFullscreen) {
                BorderlessFullscreenHandler.enterBorderless(mc);
            } else {
                BorderlessFullscreenHandler.exitBorderless(mc);
            }
            Display.setVSyncEnabled(mc.gameSettings.enableVsync);
            ci.cancel();
        } catch (LWJGLException e) {
            LDOGMod.LOGGER.error("LDOG: borderless toggle failed; falling back to vanilla fullscreen", e);
            // Don't cancel — let vanilla's toggle logic run as a fallback.
        }
    }
}
