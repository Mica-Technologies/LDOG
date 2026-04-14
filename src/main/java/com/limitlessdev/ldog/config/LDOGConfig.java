package com.limitlessdev.ldog.config;

import com.limitlessdev.ldog.Tags;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Tags.MODID)
public class LDOGConfig {

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

    @Config.Comment("Enable chunk rendering optimizations. Runs even alongside OptiFine.")
    public static boolean enableRenderOptimizations = true;

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
