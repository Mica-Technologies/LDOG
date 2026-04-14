package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.Display;

/**
 * Reduces frame rate when the game window is unfocused or the player is AFK.
 * Replaces the standalone FPS Reducer mod.
 * Uses Forge InputEvents for AFK tracking (no mixin needed).
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class FpsReducerHandler {

    private static long lastInputTimeMs = System.currentTimeMillis();
    private static boolean wasAfk = false;

    /**
     * Called externally to reset the AFK timer.
     */
    public static void onUserInput() {
        lastInputTimeMs = System.currentTimeMillis();
        if (wasAfk) {
            wasAfk = false;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        onUserInput();
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseInputEvent event) {
        onUserInput();
    }

    /**
     * Returns whether the FPS should currently be limited, and to what value.
     * Returns 0 if no limiting should occur.
     */
    public static int getTargetFpsLimit() {
        if (!LDOGConfig.enableFpsReducer) return 0;

        // Window unfocused takes priority
        if (!Display.isActive()) {
            return LDOGConfig.unfocusedFpsLimit;
        }

        // AFK detection
        if (LDOGConfig.afkTimeoutSeconds > 0) {
            long idleMs = System.currentTimeMillis() - lastInputTimeMs;
            if (idleMs >= LDOGConfig.afkTimeoutSeconds * 1000L) {
                wasAfk = true;
                return LDOGConfig.afkFpsLimit;
            }
        }

        return 0;
    }

    /**
     * Returns true if the player is currently considered AFK.
     */
    public static boolean isAfk() {
        return wasAfk;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!LDOGConfig.enableFpsReducer) return;

        int targetFps = getTargetFpsLimit();
        if (targetFps > 0) {
            Minecraft mc = Minecraft.getMinecraft();
            int currentLimit = mc.gameSettings.limitFramerate;
            if (currentLimit == 0 || currentLimit > targetFps) {
                Display.sync(targetFps);
            }
        }
    }
}
