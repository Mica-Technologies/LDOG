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

    @Config.Comment("Update interval for dynamic lights. 0 = Smooth (every frame, best quality), 1 = every tick, 2+ = every N ticks (better performance).")
    @Config.RangeInt(min = 0, max = 20)
    public static int dynamicLightsUpdateInterval = 1;

    @Config.Comment("Enable lighting customization (color temperature, brightness, night darkness, HDR).")
    public static boolean enableLightTemperature = false;

    @Config.Comment("Block light (torches/lamps) red multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double blockLightRed = 1.25;

    @Config.Comment("Block light green multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double blockLightGreen = 1.0;

    @Config.Comment("Block light blue multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double blockLightBlue = 0.7;

    @Config.Comment("Sky light (sun/moon) red multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double skyLightRed = 0.8;

    @Config.Comment("Sky light green multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double skyLightGreen = 0.88;

    @Config.Comment("Sky light blue multiplier.")
    @Config.RangeDouble(min = 0.5, max = 1.5)
    public static double skyLightBlue = 1.25;

    @Config.Comment("Overall brightness boost. -1.0 = very dark, 0.0 = vanilla, 1.0 = very bright.")
    @Config.RangeDouble(min = -1.0, max = 1.0)
    public static double lightBrightnessBoost = 0.0;

    @Config.Comment("Night darkness multiplier. 1.0 = vanilla, >1.0 = darker nights, 100.0 = pitch black at night.")
    @Config.RangeDouble(min = 0.5, max = 100.0)
    public static double nightDarkness = 1.0;

    @Config.Comment("Enable pseudo-HDR tonemapping (ACES filmic curve for cinematic contrast).")
    public static boolean enableHDR = true;

    @Config.Comment({
        "Better Grass rendering mode.",
        "off = vanilla, fast = always show grass top on sides, fancy = only when neighbor below is also grass."
    })
    public static String betterGrass = "fancy";

    @Config.Comment("Enable Better Snow (snow-textured sides on blocks with snow layers on top).")
    public static boolean enableBetterSnow = true;

    @Config.Comment("Enable natural textures (random rotation/flip of block textures to reduce visual repetition).")
    public static boolean enableNaturalTextures = true;

    @Config.Comment("Enable custom colors from resource packs (custom grass/foliage colormaps, redstone wire colors, etc.).")
    public static boolean enableCustomColors = true;

    @Config.Comment("Show performance metrics overlay in the upper-left corner (FPS, frame time, memory, culling stats).")
    public static boolean enablePerformanceOverlay = false;

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

    @Config.Comment({
        "Enable entity LOD (Level of Detail).",
        "Distant entities render less frequently to save GPU work.",
        "64-128 blocks: every other frame. 128+: every 4th frame."
    })
    public static boolean enableEntityLOD = true;

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

    @Config.Comment("Water opacity multiplier. 0.0 = fully transparent, 1.0 = vanilla opacity, >1.0 = murkier than vanilla (quadratic scale).")
    @Config.RangeDouble(min = 0.0, max = 10.0)
    public static double waterOpacity = 0.4;

    @Config.Comment({
        "Enable custom water color tinting.",
        "When enabled, the RGB values below are multiplied with the biome water color."
    })
    public static boolean enableWaterTint = false;

    @Config.Comment("Water red channel multiplier. 1.0 = unchanged, lower = less red.")
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double waterTintRed = 0.7;

    @Config.Comment("Water green channel multiplier. 1.0 = unchanged, higher = more green.")
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double waterTintGreen = 0.85;

    @Config.Comment("Water blue channel multiplier. 1.0 = unchanged, lower = less saturated blue.")
    @Config.RangeDouble(min = 0.0, max = 2.0)
    public static double waterTintBlue = 0.85;

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
