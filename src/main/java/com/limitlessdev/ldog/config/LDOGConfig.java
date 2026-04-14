package com.limitlessdev.ldog.config;

import com.limitlessdev.ldog.Tags;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Tags.MODID)
public class LDOGConfig {

    // ---- Future Features (not yet implemented) ----

    @Config.Comment({
        "Enable connected textures (CTM).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableConnectedTextures = true;

    @Config.Comment({
        "Enable emissive texture rendering.",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableEmissiveTextures = true;

    @Config.Comment({
        "Enable dynamic lights (light from held items).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableDynamicLights = true;

    @Config.Comment({
        "Enable shader support (stretch goal).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableShaders = false;

    @Config.Comment({
        "Enable custom sky rendering.",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableCustomSky = true;

    @Config.Comment({
        "Enable HD texture support (textures larger than 16x16).",
        "Auto-disabled when OptiFine is detected."
    })
    public static boolean enableHDTextures = true;

    // ---- Performance: Rendering Optimizations ----

    @Config.Comment("Enable chunk rendering optimizations. Runs even alongside OptiFine.")
    public static boolean enableRenderOptimizations = true;

    @Config.Comment("Maximum distance (in blocks) at which entities are rendered. 0 = use vanilla behavior.")
    @Config.RangeInt(min = 0, max = 512)
    public static int entityRenderDistance = 64;

    @Config.Comment("Maximum distance (in blocks) at which tile entity special renderers (TESRs) run. 0 = use vanilla behavior.")
    @Config.RangeInt(min = 0, max = 512)
    public static int tileEntityRenderDistance = 64;

    @Config.Comment("Enable frustum culling for particles (skip rendering off-screen particles).")
    public static boolean enableParticleCulling = true;

    // ---- Performance: FPS Management ----

    @Config.Comment("Enable FPS reduction when the game window is unfocused or player is AFK.")
    public static boolean enableFpsReducer = true;

    @Config.Comment("Maximum FPS when the game window is unfocused.")
    @Config.RangeInt(min = 1, max = 60)
    public static int unfocusedFpsLimit = 5;

    @Config.Comment("Seconds of inactivity before AFK mode activates. 0 = disable AFK detection.")
    @Config.RangeInt(min = 0, max = 3600)
    public static int afkTimeoutSeconds = 300;

    @Config.Comment("Maximum FPS during AFK mode.")
    @Config.RangeInt(min = 1, max = 60)
    public static int afkFpsLimit = 15;

    // ---- Visual: Water ----

    @Config.Comment("Enable clear water rendering (removes murky underwater overlay).")
    public static boolean enableClearWater = true;

    @Config.Comment("Water opacity multiplier. 0.0 = fully transparent, 1.0 = vanilla opacity.")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public static double waterOpacity = 0.4;

    @Mod.EventBusSubscriber(modid = Tags.MODID)
    private static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(Tags.MODID)) {
                ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
