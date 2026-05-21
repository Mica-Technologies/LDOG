package com.limitlessdev.ldog.gui;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.compat.OptiFineCompat;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.dynamiclights.LightTemperaturePreset;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * LDOG settings GUI with a scrollable list of settings.
 * Uses GuiLDOGSettingsList (extends GuiListExtended) for the scrollable
 * content area, with a fixed Done button at the bottom.
 */
public class GuiLDOGSettings extends GuiScreen {

    private final GuiScreen parentScreen;
    private GuiLDOGSettingsList settingsList;
    /** Tooltip lines registered per button id. Rendered on hover in drawScreen. */
    private final Map<Integer, String[]> buttonTooltips = new HashMap<>();

    private static final int BTN_DONE = 200;
    private static final int BTN_RENDER_OPTS = 10;
    private static final int BTN_ENTITY_DIST = 11;
    private static final int BTN_TE_DIST = 12;
    private static final int BTN_PARTICLE_CULL = 13;
    // Per-type particle toggles (5 categories)
    private static final int BTN_PARTICLE_FIREWORK  = 500;
    private static final int BTN_PARTICLE_PORTAL    = 501;
    private static final int BTN_PARTICLE_POTION    = 502;
    private static final int BTN_PARTICLE_WATER     = 503;
    private static final int BTN_PARTICLE_DRIPPING  = 504;
    // Vignette post-process
    private static final int BTN_VIGNETTE_ENABLE    = 510;
    private static final int BTN_VIGNETTE_INTENSITY = 511;
    // Atmosphere section
    private static final int BTN_CLOUD_HEIGHT       = 520;
    private static final int BTN_FOG_DISTANCE       = 521;
    private static final int BTN_SUN_SIZE           = 522;
    private static final int BTN_MOON_SIZE          = 523;
    private static final int BTN_WEATHER_RENDER     = 524;
    private static final int BTN_WEATHER_DENSITY    = 525;
    private static final int BTN_BIOME_BLEND        = 526;
    // Comfort / Cinematic toggles
    private static final int BTN_NO_DAMAGE_TILT     = 540;
    private static final int BTN_NO_HURT_VIGNETTE   = 541;
    private static final int BTN_HIDE_HAND          = 542;
    private static final int BTN_FULLBRIGHT         = 543;
    private static final int BTN_HIDE_CROSSHAIR     = 544;
    private static final int BTN_HIDE_HOTBAR        = 545;
    private static final int BTN_HIDE_EXP           = 546;
    private static final int BTN_HIDE_JUMP          = 547;
    private static final int BTN_HIDE_TOOLTIP       = 548;
    private static final int BTN_NO_PORTAL_OVERLAY  = 549;
    // Info HUD overlays (top-right)
    private static final int BTN_HUD_COORDS         = 560;
    private static final int BTN_HUD_FACING         = 561;
    private static final int BTN_HUD_TIME           = 562;
    private static final int BTN_HUD_BIOME          = 563;
    private static final int BTN_HUD_LIGHT          = 564;
    private static final int BTN_HUD_PING           = 565;
    private static final int BTN_HUD_DAY            = 566;
    private static final int BTN_HUD_CPS            = 567;

    private static final int BTN_ADV_TOOLTIPS       = 568;
    private static final int BTN_NO_NAUSEA          = 569;

    private static final int BTN_HIDE_ARMOR         = 570;
    private static final int BTN_HIDE_HUNGER        = 571;
    private static final int BTN_HIDE_AIR           = 572;
    private static final int BTN_HIDE_BOSS          = 573;
    private static final int BTN_ENTITY_LOD = 14;
    private static final int BTN_FPS_REDUCER = 20;
    private static final int BTN_UNFOCUSED_FPS = 21;
    private static final int BTN_AFK_TIMEOUT = 22;
    private static final int BTN_AFK_FPS = 23;
    private static final int BTN_CLEAR_WATER = 30;
    private static final int BTN_WATER_OPACITY = 31;
    private static final int BTN_WATER_TINT = 32;
    private static final int BTN_WATER_RED = 33;
    private static final int BTN_WATER_GREEN = 34;
    private static final int BTN_WATER_BLUE = 35;
    private static final int BTN_WATER_PRESET = 36;

    // Water presets: {name, opacity, red, green, blue}
    private static final Object[][] WATER_PRESETS = {
        {"Clear",          0.2, 0.8, 0.9, 1.0},
        {"Tropical",       0.3, 0.4, 0.9, 1.0},
        {"Default",        0.4, 0.7, 0.85, 0.85},
        {"Arctic",         0.3, 0.7, 0.8, 1.0},
        {"Murky",          1.2, 0.7, 0.65, 0.5},
        {"Swamp",          2.0, 0.4, 0.6, 0.3},
        {"Muddy",          5.0, 0.8, 0.5, 0.3},
        {"Vanilla",        1.0, 1.0, 1.0, 1.0},
    };
    private int currentPresetIndex = -1; // -1 = custom (no preset matched)
    private boolean waterSettingsChanged = false; // triggers chunk rebuild on close
    private static final int BTN_CTM = 40;
    private static final int BTN_EMISSIVE = 41;
    private static final int BTN_DYNAMIC_LIGHTS = 42;
    private static final int BTN_DYN_LIGHT_INTERVAL = 46;
    private static final int BTN_CUSTOM_SKY = 43;
    private static final int BTN_HD_TEXTURES = 44;
    private static final int BTN_SHADERS = 45;
    private static final int BTN_SHADER_PACK = 610;
    private static final int BTN_SHADER_RESCAN = 611;
    private static final int BTN_LIGHT_TEMP = 50;
    private static final int BTN_LIGHT_TEMP_PRESET = 51;
    private static final int BTN_BLOCK_LIGHT_R = 52;
    private static final int BTN_BLOCK_LIGHT_G = 53;
    private static final int BTN_BLOCK_LIGHT_B = 54;
    private static final int BTN_SKY_LIGHT_R = 55;
    private static final int BTN_SKY_LIGHT_G = 56;
    private static final int BTN_SKY_LIGHT_B = 57;
    private static final int BTN_BRIGHTNESS_BOOST = 58;
    private static final int BTN_NIGHT_DARKNESS = 59;
    private static final int BTN_HDR = 60;
    private static final int BTN_BETTER_GRASS = 70;
    private static final int BTN_BETTER_SNOW = 71;
    private static final int BTN_PERF_OVERLAY = 72;
    private static final int BTN_NATURAL_TEXTURES = 73;
    private static final int BTN_CUSTOM_COLORS = 74;
    private static final int BTN_RANDOM_MOBS = 75;
    private static final int BTN_ANISOTROPIC = 80;
    private static final int BTN_ANISOTROPIC_LEVEL = 81;
    private static final int BTN_MSAA = 82;
    private static final int BTN_MSAA_SAMPLES = 83;
    private static final int BTN_FXAA = 84;
    private static final int BTN_EXT_BORDER = 85;
    private static final int BTN_SMOOTH_FONT = 90;
    private static final int BTN_HD_FONT = 91;
    private static final int BTN_AA_FONT = 92;
    private static final int BTN_FONT_WIDTHS = 93;
    private static final int BTN_TTF_FONT = 94;
    private static final int BTN_TTF_FAMILY = 95;
    private static final int BTN_TTF_SIZE = 96;
    private static final int BTN_FONT_SHADOW = 97;
    private static final int BTN_TTF_BOLD     = 98;
    private static final int BTN_TTF_ITALIC   = 99;
    private static final int BTN_TTF_SUBPIXEL = 580;
    private static final int BTN_PIPELINE = 100;
    private static final int BTN_PIPELINE_SCALE = 101;
    private static final int BTN_PIPELINE_UPSCALER = 102;
    private static final int BTN_FSR1_SHARPNESS = 103;
    private static final int BTN_UPSCALER_PRESET = 104;
    private static final int BTN_RCAS_ENABLE = 105;
    private static final int BTN_RCAS_STRENGTH = 106;
    private static final int BTN_LDOG_PRESET = 300;
    private static final int BTN_FXAA_QUALITY = 107;
    private static final int BTN_TAA_ENABLE = 108;
    private static final int BTN_TAA_WEIGHT = 109;
    private static final int BTN_TAA_REACTIVE_MASK = 113;
    private static final int BTN_TAA_ENTITY_MV     = 114;
    private static final int BTN_HDR_PIPELINE      = 600;
    private static final int BTN_HDR_TONEMAP       = 601;
    private static final int BTN_HDR_EXPOSURE      = 602;
    private static final int BTN_BLOOM_ENABLE      = 603;
    private static final int BTN_BLOOM_THRESHOLD   = 604;
    private static final int BTN_BLOOM_INTENSITY   = 605;
    private static final int BTN_AUTO_SCALE = 112;
    // Phase C4 OF interop modes — 7 features × 1 button each.
    // 400+ range to stay clear of BTN_DONE=200 and BTN_LDOG_PRESET=300.
    private static final int BTN_OF_MODE_CTM             = 400;
    private static final int BTN_OF_MODE_EMISSIVE        = 401;
    private static final int BTN_OF_MODE_DYNAMIC_LIGHTS  = 402;
    private static final int BTN_OF_MODE_CUSTOM_SKY      = 403;
    private static final int BTN_OF_MODE_HD_TEXTURES     = 404;
    private static final int BTN_OF_MODE_SMOOTH_FONT     = 405;
    private static final int BTN_OF_MODE_SHADERS         = 406;
    private static final int BTN_BORDERLESS_FULLSCREEN = 110;
    private static final int BTN_BLOCK_FSO = 111;

    private static final int[] ANISOTROPIC_VALUES = {2, 4, 8, 16};
    private static final int[] MSAA_VALUES = {2, 4, 8};
    private static final double[] PIPELINE_SCALE_VALUES = {1.0, 0.85, 0.75, 0.5};
    private static final double[] FSR1_SHARPNESS_VALUES = {0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0};
    private static final double[] RCAS_STRENGTH_VALUES = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.75, 1.0};
    private static final double[] VIGNETTE_INTENSITY_VALUES = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
    private static final int[] CLOUD_HEIGHT_VALUES = {-1, 64, 96, 128, 160, 192, 224, 255};
    private static final double[] FOG_DISTANCE_VALUES = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};
    private static final double[] SIZE_MULT_VALUES = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};
    private static final double[] EXPOSURE_VALUES = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};
    private static final double[] BLOOM_THRESHOLD_VALUES = {0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};
    private static final double[] BLOOM_INTENSITY_VALUES = {0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};
    private static final double[] WEATHER_DENSITY_VALUES = {0.1, 0.25, 0.5, 0.75, 1.0};
    private static final int[] BIOME_BLEND_VALUES = {1, 2, 3};
    private static final double[] TAA_WEIGHT_VALUES = {0.0, 0.5, 0.7, 0.8, 0.85, 0.9, 0.92, 0.95};
    private static final String[] FONT_AA_MODES = {"off", "bilinear", "trilinear"};
    private static final int[] TTF_SIZES = {16, 20, 24, 28, 32, 40, 48};

    private static final String[] BETTER_GRASS_MODES = {"off", "fast", "fancy"};

    private static final int[] ENTITY_DIST_VALUES = {0, 32, 48, 64, 96, 128, 192, 256, 512};
    private static final int[] TE_DIST_VALUES = {0, 16, 32, 48, 64, 96, 128, 256};
    private static final int[] UNFOCUSED_FPS_VALUES = {1, 2, 5, 10, 15, 30, 60};
    private static final int[] AFK_TIMEOUT_VALUES = {0, 60, 120, 300, 600, 900, 1800, 3600};
    private static final int[] AFK_FPS_VALUES = {1, 2, 5, 10, 15, 30, 60};
    private static final double[] WATER_OPACITY_VALUES = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.2, 1.5, 2.0, 3.0, 5.0, 7.0, 10.0};
    private static final double[] TINT_VALUES = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0};
    private static final int[] DYN_LIGHT_INTERVAL_VALUES = {0, 1, 2, 5, 10, 20};
    private static final double[] LIGHT_TINT_VALUES = {0.5, 0.6, 0.7, 0.8, 0.85, 0.9, 0.95, 1.0, 1.05, 1.1, 1.15, 1.2, 1.25, 1.3, 1.35, 1.4, 1.5};
    private static final double[] BRIGHTNESS_VALUES = {-1.0, -0.7, -0.5, -0.3, -0.2, -0.1, 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.7, 1.0};
    private static final double[] NIGHT_DARK_VALUES = {0.5, 0.7, 0.8, 1.0, 1.2, 1.5, 1.8, 2.0, 2.5, 3.0, 5.0, 10.0, 100.0};
    private int currentLightPresetIndex = -1;
    private static final String[] LIGHT_TEMP_PRESETS = {
        "neutral", "warm_torches", "cinematic", "candlelight", "moonlit",
        "dark_nights", "horror", "bright_caves", "vivid",
        "fluorescent", "purple_haze", "neon_blue", "red_alert"
    };

    public GuiLDOGSettings(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int w = 150;
        int h = 20;

        // Create scrollable list (area between title and Done button)
        settingsList = new GuiLDOGSettingsList(this.mc, this.width, this.height, 28, this.height - 32);
        buttonTooltips.clear();
        registerTooltips();

        // -- Global Preset --
        settingsList.addHeaderRow("LDOG Preset");
        settingsList.addButtonRow(
            new GuiButton(BTN_LDOG_PRESET, 0, 0, w, h,
                ldogPresetLabel(com.limitlessdev.ldog.config.LDOGPreset.selected())),
            null);

        // -- Performance --
        settingsList.addHeaderRow("Performance");
        settingsList.addButtonRow(
            new GuiButton(BTN_RENDER_OPTS, 0, 0, w, h,
                toggleLabel("Render Opts", LDOGConfig.enableRenderOptimizations)),
            new GuiButton(BTN_PARTICLE_CULL, 0, 0, w, h,
                toggleLabel("Particle Culling", LDOGConfig.enableParticleCulling)));
        // Per-type particle filter — kills specific particle categories at spawn.
        settingsList.addButtonRow(
            new GuiButton(BTN_PARTICLE_FIREWORK, 0, 0, w, h,
                toggleLabel("Firework Particles", LDOGConfig.enableParticleFirework)),
            new GuiButton(BTN_PARTICLE_PORTAL, 0, 0, w, h,
                toggleLabel("Portal Particles", LDOGConfig.enableParticlePortal)));
        settingsList.addButtonRow(
            new GuiButton(BTN_PARTICLE_POTION, 0, 0, w, h,
                toggleLabel("Potion Particles", LDOGConfig.enableParticlePotion)),
            new GuiButton(BTN_PARTICLE_WATER, 0, 0, w, h,
                toggleLabel("Water Particles", LDOGConfig.enableParticleWater)));
        settingsList.addButtonRow(
            new GuiButton(BTN_PARTICLE_DRIPPING, 0, 0, w, h,
                toggleLabel("Dripping Particles", LDOGConfig.enableParticleDripping)),
            null);
        settingsList.addButtonRow(
            new GuiButton(BTN_ENTITY_DIST, 0, 0, w, h,
                distLabel("Entity Dist", LDOGConfig.entityRenderDistance)),
            new GuiButton(BTN_TE_DIST, 0, 0, w, h,
                distLabel("TileEntity Dist", LDOGConfig.tileEntityRenderDistance)));
        settingsList.addButtonRow(
            new GuiButton(BTN_ENTITY_LOD, 0, 0, w, h,
                toggleLabel("Entity LOD", LDOGConfig.enableEntityLOD)),
            new GuiButton(BTN_PERF_OVERLAY, 0, 0, w, h,
                toggleLabel("Perf Overlay", LDOGConfig.enablePerformanceOverlay)));

        // -- FPS Management --
        settingsList.addHeaderRow("FPS Management");
        settingsList.addButtonRow(
            new GuiButton(BTN_FPS_REDUCER, 0, 0, w, h,
                toggleLabel("FPS Reducer", LDOGConfig.enableFpsReducer)),
            new GuiButton(BTN_UNFOCUSED_FPS, 0, 0, w, h,
                valLabel("Unfocused FPS", LDOGConfig.unfocusedFpsLimit)));
        settingsList.addButtonRow(
            new GuiButton(BTN_AFK_TIMEOUT, 0, 0, w, h,
                afkTimeoutLabel(LDOGConfig.afkTimeoutSeconds)),
            new GuiButton(BTN_AFK_FPS, 0, 0, w, h,
                valLabel("AFK FPS", LDOGConfig.afkFpsLimit)));

        // -- Anti-aliasing / Filtering --
        // AF can show faint block-edge bleed at distance (atlas sampling across tile borders
        // at high mip levels — fixed by extended-border mipmaps, tracked as Phase 7c).
        // MSAA can show faint rasterization edge lines on distant chunk seams.
        settingsList.addHeaderRow("Anti-aliasing (Experimental)");
        settingsList.addButtonRow(
            new GuiButton(BTN_ANISOTROPIC, 0, 0, w, h,
                toggleLabel("Anisotropic", LDOGConfig.enableAnisotropicFiltering)),
            new GuiButton(BTN_ANISOTROPIC_LEVEL, 0, 0, w, h,
                afLabel(LDOGConfig.anisotropicLevel)));
        settingsList.addButtonRow(
            new GuiButton(BTN_EXT_BORDER, 0, 0, w, h,
                toggleLabel("Ext Border Mips", LDOGConfig.enableExtendedBorderMipmaps)),
            null);
        settingsList.addButtonRow(
            new GuiButton(BTN_MSAA, 0, 0, w, h,
                toggleLabel("MSAA", LDOGConfig.enableMSAA)),
            new GuiButton(BTN_MSAA_SAMPLES, 0, 0, w, h,
                msaaLabel(LDOGConfig.msaaSamples)));
        settingsList.addButtonRow(
            new GuiButton(BTN_FXAA, 0, 0, w, h,
                toggleLabel("FXAA", LDOGConfig.enableFXAA)),
            new GuiButton(BTN_FXAA_QUALITY, 0, 0, w, h,
                fxaaQualityLabel(com.limitlessdev.ldog.render.pipeline.FXAAQuality.selected())));
        settingsList.addButtonRow(
            new GuiButton(BTN_TAA_ENABLE, 0, 0, w, h,
                toggleLabel("TAA", LDOGConfig.enableTAA)),
            new GuiButton(BTN_TAA_WEIGHT, 0, 0, w, h,
                taaWeightLabel(LDOGConfig.taaHistoryWeight)));
        settingsList.addButtonRow(
            new GuiButton(BTN_TAA_REACTIVE_MASK, 0, 0, w, h,
                toggleLabel("Entity Reactive Mask", LDOGConfig.enableEntityReactiveMask)),
            new GuiButton(BTN_TAA_ENTITY_MV, 0, 0, w, h,
                toggleLabel("Entity MV", LDOGConfig.enableEntityMotionVectors)));

        // -- Post-Process Pipeline (Experimental) --
        // Master switch for the modern render pipeline — gates upscaling,
        // tunable FXAA, TAA, RCAS, vignette, HDR + Bloom.
        settingsList.addHeaderRow("Post-Process (Experimental)");
        settingsList.addButtonRow(
            new GuiButton(BTN_PIPELINE, 0, 0, w, h,
                toggleLabel("Post Pipeline", LDOGConfig.enablePostProcessPipeline)),
            new GuiButton(BTN_UPSCALER_PRESET, 0, 0, w, h,
                upscalerPresetLabel(com.limitlessdev.ldog.render.pipeline.UpscalerPreset.selected())));
        settingsList.addButtonRow(
            new GuiButton(BTN_PIPELINE_SCALE, 0, 0, w, h,
                pipelineScaleLabel(LDOGConfig.internalRenderScale)),
            new GuiButton(BTN_PIPELINE_UPSCALER, 0, 0, w, h,
                upscalerLabel(com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm.selected())));
        settingsList.addButtonRow(
            new GuiButton(BTN_FSR1_SHARPNESS, 0, 0, w, h,
                fsr1SharpnessLabel(LDOGConfig.fsr1Sharpness)),
            new GuiButton(BTN_AUTO_SCALE, 0, 0, w, h,
                autoScaleLabel(com.limitlessdev.ldog.render.pipeline.AutoScaleMode.selected())));
        settingsList.addButtonRow(
            new GuiButton(BTN_RCAS_ENABLE, 0, 0, w, h,
                toggleLabel("RCAS Sharpen", LDOGConfig.enableRcasSharpen)),
            new GuiButton(BTN_RCAS_STRENGTH, 0, 0, w, h,
                rcasStrengthLabel(LDOGConfig.rcasSharpness)));
        settingsList.addButtonRow(
            new GuiButton(BTN_VIGNETTE_ENABLE, 0, 0, w, h,
                toggleLabel("Vignette", LDOGConfig.enableVignette)),
            new GuiButton(BTN_VIGNETTE_INTENSITY, 0, 0, w, h,
                vignetteIntensityLabel(LDOGConfig.vignetteIntensity)));

        // -- HDR + Bloom (Phase 8 stretch, Experimental) --
        settingsList.addHeaderRow("HDR + Bloom (Experimental)");
        settingsList.addButtonRow(
            new GuiButton(BTN_HDR_PIPELINE, 0, 0, w, h,
                toggleLabel("HDR Pipeline", LDOGConfig.enableHDRPipeline)),
            new GuiButton(BTN_HDR_TONEMAP, 0, 0, w, h,
                tonemapLabel(LDOGConfig.hdrTonemap)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HDR_EXPOSURE, 0, 0, w, h,
                multLabel("Exposure", LDOGConfig.hdrExposure)),
            new GuiButton(BTN_BLOOM_ENABLE, 0, 0, w, h,
                toggleLabel("Bloom", LDOGConfig.enableBloom)));
        settingsList.addButtonRow(
            new GuiButton(BTN_BLOOM_THRESHOLD, 0, 0, w, h,
                multLabel("Bloom Thresh", LDOGConfig.bloomThreshold)),
            new GuiButton(BTN_BLOOM_INTENSITY, 0, 0, w, h,
                multLabel("Bloom Strength", LDOGConfig.bloomIntensity)));

        // -- Atmosphere (clouds / fog / sky / weather / biomes) --
        settingsList.addHeaderRow("Atmosphere");
        settingsList.addButtonRow(
            new GuiButton(BTN_CLOUD_HEIGHT, 0, 0, w, h,
                cloudHeightLabel(LDOGConfig.cloudHeightOverride)),
            new GuiButton(BTN_FOG_DISTANCE, 0, 0, w, h,
                multLabel("Fog Dist", LDOGConfig.fogDistanceMultiplier)));
        settingsList.addButtonRow(
            new GuiButton(BTN_SUN_SIZE, 0, 0, w, h,
                multLabel("Sun Size", LDOGConfig.sunSizeMultiplier)),
            new GuiButton(BTN_MOON_SIZE, 0, 0, w, h,
                multLabel("Moon Size", LDOGConfig.moonSizeMultiplier)));
        settingsList.addButtonRow(
            new GuiButton(BTN_WEATHER_RENDER, 0, 0, w, h,
                toggleLabel("Weather Render", LDOGConfig.enableWeatherRender)),
            new GuiButton(BTN_WEATHER_DENSITY, 0, 0, w, h,
                multLabel("Weather Density", LDOGConfig.weatherDensity)));
        settingsList.addButtonRow(
            new GuiButton(BTN_BIOME_BLEND, 0, 0, w, h,
                biomeBlendLabel(LDOGConfig.biomeBlendRadius)),
            null);

        // -- Display (window mode) --
        // Session-scoped toggles — set once and (mostly) forget.
        settingsList.addHeaderRow("Display");
        settingsList.addButtonRow(
            new GuiButton(BTN_BORDERLESS_FULLSCREEN, 0, 0, w, h,
                toggleLabel("Borderless Windowed", LDOGConfig.borderlessFullscreen)),
            new GuiButton(BTN_BLOCK_FSO, 0, 0, w, h,
                toggleLabel("Block FS Optim", LDOGConfig.blockFullscreenOptimizations)));

        // -- Comfort / Cinematic --
        settingsList.addHeaderRow("Comfort / Cinematic");
        settingsList.addButtonRow(
            new GuiButton(BTN_NO_DAMAGE_TILT, 0, 0, w, h,
                toggleLabel("No Damage Tilt", LDOGConfig.disableDamageTilt)),
            new GuiButton(BTN_NO_HURT_VIGNETTE, 0, 0, w, h,
                toggleLabel("No Hurt Vignette", LDOGConfig.disableHurtVignette)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HIDE_HAND, 0, 0, w, h,
                toggleLabel("Hide Hand", LDOGConfig.hideHand)),
            new GuiButton(BTN_HIDE_CROSSHAIR, 0, 0, w, h,
                toggleLabel("Hide Crosshair", LDOGConfig.hideCrosshair)));
        settingsList.addButtonRow(
            new GuiButton(BTN_FULLBRIGHT, 0, 0, w, h,
                toggleLabel("Fullbright", LDOGConfig.enableFullbright)),
            new GuiButton(BTN_NO_PORTAL_OVERLAY, 0, 0, w, h,
                toggleLabel("No Portal Distort", LDOGConfig.disablePortalOverlay)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HIDE_HOTBAR, 0, 0, w, h,
                toggleLabel("Hide Hotbar", LDOGConfig.hideHotbar)),
            new GuiButton(BTN_HIDE_EXP, 0, 0, w, h,
                toggleLabel("Hide XP Bar", LDOGConfig.hideExperienceBar)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HIDE_JUMP, 0, 0, w, h,
                toggleLabel("Hide Jump Bar", LDOGConfig.hideHorseJumpBar)),
            new GuiButton(BTN_HIDE_TOOLTIP, 0, 0, w, h,
                toggleLabel("Hide Item Tooltip", LDOGConfig.hideHeldItemTooltip)));

        // -- Info HUD (LDOG-rendered top-right overlay) --
        settingsList.addHeaderRow("Info HUD");
        settingsList.addButtonRow(
            new GuiButton(BTN_HUD_COORDS, 0, 0, w, h,
                toggleLabel("Coords", LDOGConfig.showCoordsHud)),
            new GuiButton(BTN_HUD_FACING, 0, 0, w, h,
                toggleLabel("Facing", LDOGConfig.showFacingHud)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HUD_TIME, 0, 0, w, h,
                toggleLabel("Time", LDOGConfig.showTimeHud)),
            new GuiButton(BTN_HUD_BIOME, 0, 0, w, h,
                toggleLabel("Biome", LDOGConfig.showBiomeHud)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HUD_LIGHT, 0, 0, w, h,
                toggleLabel("Light Level", LDOGConfig.showLightLevelHud)),
            new GuiButton(BTN_HUD_PING, 0, 0, w, h,
                toggleLabel("Ping", LDOGConfig.showPingHud)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HUD_DAY, 0, 0, w, h,
                toggleLabel("Day Counter", LDOGConfig.showDayHud)),
            new GuiButton(BTN_HUD_CPS, 0, 0, w, h,
                toggleLabel("CPS", LDOGConfig.showCpsHud)));

        // -- Hide HUD Elements + Quality of Life --
        // Player UX cluster, lives next to Comfort / Cinematic and Info HUD.
        settingsList.addHeaderRow("Hide HUD Elements");
        settingsList.addButtonRow(
            new GuiButton(BTN_HIDE_ARMOR, 0, 0, w, h,
                toggleLabel("Hide Armor Bar", LDOGConfig.hideArmorBar)),
            new GuiButton(BTN_HIDE_HUNGER, 0, 0, w, h,
                toggleLabel("Hide Hunger Bar", LDOGConfig.hideHungerBar)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HIDE_AIR, 0, 0, w, h,
                toggleLabel("Hide Air Bar", LDOGConfig.hideAirBar)),
            new GuiButton(BTN_HIDE_BOSS, 0, 0, w, h,
                toggleLabel("Hide Boss Health", LDOGConfig.hideBossHealthBars)));
        settingsList.addButtonRow(
            new GuiButton(BTN_ADV_TOOLTIPS, 0, 0, w, h,
                toggleLabel("Adv. Tooltips Always", LDOGConfig.advancedTooltipsAlways)),
            new GuiButton(BTN_NO_NAUSEA, 0, 0, w, h,
                toggleLabel("No Nausea Distort", LDOGConfig.disableNauseaDistortion)));

        // -- Font Rendering --
        // Drop-in replacement for the Smooth Font mod. Swaps in HD ascii.png from
        // optifine/mcpatcher resource-pack paths and applies GL_LINEAR filtering
        // for antialiased glyphs. Auto-disabled when OptiFine is detected.
        settingsList.addHeaderRow("Font Rendering");
        settingsList.addButtonRow(
            new GuiButton(BTN_SMOOTH_FONT, 0, 0, w, h,
                toggleLabel("Smooth Font", LDOGConfig.enableSmoothFont)),
            new GuiButton(BTN_HD_FONT, 0, 0, w, h,
                toggleLabel("HD Font Texture", LDOGConfig.useHDFontTexture)));
        settingsList.addButtonRow(
            new GuiButton(BTN_AA_FONT, 0, 0, w, h,
                fontAALabel(LDOGConfig.fontAntialiasing)),
            new GuiButton(BTN_FONT_WIDTHS, 0, 0, w, h,
                toggleLabel("Pack Widths", LDOGConfig.useFontPropertyWidths)));
        settingsList.addButtonRow(
            new GuiButton(BTN_TTF_FONT, 0, 0, w, h,
                toggleLabel("TTF Font", LDOGConfig.useTTFFont)),
            new GuiButton(BTN_TTF_SIZE, 0, 0, w, h,
                valLabel("TTF Size", LDOGConfig.ttfFontSize)));
        settingsList.addButtonRow(
            new GuiButton(BTN_TTF_FAMILY, 0, 0, w, h,
                fontFamilyLabel(LDOGConfig.ttfFontFamily)),
            new GuiButton(BTN_FONT_SHADOW, 0, 0, w, h,
                toggleLabel("Drop Shadows", LDOGConfig.fontDropShadows)));
        settingsList.addButtonRow(
            new GuiButton(BTN_TTF_BOLD, 0, 0, w, h,
                toggleLabel("TTF Bold", LDOGConfig.ttfBold)),
            new GuiButton(BTN_TTF_ITALIC, 0, 0, w, h,
                toggleLabel("TTF Italic", LDOGConfig.ttfItalic)));
        settingsList.addButtonRow(
            new GuiButton(BTN_TTF_SUBPIXEL, 0, 0, w, h,
                toggleLabel("LCD Subpixel", LDOGConfig.ttfSubpixel)),
            null);

        // -- Visual --
        currentPresetIndex = detectCurrentPreset();
        settingsList.addHeaderRow("Visual");
        settingsList.addButtonRow(
            new GuiButton(BTN_CLEAR_WATER, 0, 0, w, h,
                toggleLabel("Clear Water", LDOGConfig.enableClearWater)),
            new GuiButton(BTN_WATER_PRESET, 0, 0, w, h,
                presetLabel()));
        settingsList.addButtonRow(
            new GuiButton(BTN_WATER_OPACITY, 0, 0, w, h,
                opacityLabel("Water Opacity", LDOGConfig.waterOpacity)),
            new GuiButton(BTN_WATER_TINT, 0, 0, w, h,
                toggleLabel("Water Tint", LDOGConfig.enableWaterTint)));
        settingsList.addButtonRow(
            new GuiButton(BTN_WATER_RED, 0, 0, w, h,
                tintLabel("Red", LDOGConfig.waterTintRed, "\u00a7c")),
            new GuiButton(BTN_WATER_GREEN, 0, 0, w, h,
                tintLabel("Green", LDOGConfig.waterTintGreen, "\u00a7a")));
        settingsList.addButtonRow(
            new GuiButton(BTN_WATER_BLUE, 0, 0, w, h,
                tintLabel("Blue", LDOGConfig.waterTintBlue, "\u00a79")),
            null);
        settingsList.addButtonRow(
            new GuiButton(BTN_BETTER_GRASS, 0, 0, w, h,
                betterGrassLabel(LDOGConfig.betterGrass)),
            new GuiButton(BTN_BETTER_SNOW, 0, 0, w, h,
                toggleLabel("Better Snow", LDOGConfig.enableBetterSnow)));
        settingsList.addButtonRow(
            new GuiButton(BTN_NATURAL_TEXTURES, 0, 0, w, h,
                toggleLabel("Natural Textures", LDOGConfig.enableNaturalTextures)),
            new GuiButton(BTN_CUSTOM_COLORS, 0, 0, w, h,
                toggleLabel("Custom Colors", LDOGConfig.enableCustomColors)));
        settingsList.addButtonRow(
            new GuiButton(BTN_RANDOM_MOBS, 0, 0, w, h,
                toggleLabel("Random Mobs", LDOGConfig.enableRandomEntityTextures)),
            null);

        // -- Lighting --
        settingsList.addHeaderRow("Dynamic Lights");
        settingsList.addButtonRow(
            makeFeatureButton(BTN_DYNAMIC_LIGHTS, w, h, "Dynamic Lights",
                LDOGConfig.enableDynamicLights, OptiFineCompat.shouldHandleDynamicLights()),
            new GuiButton(BTN_DYN_LIGHT_INTERVAL, 0, 0, w, h,
                dynLightIntervalLabel(LDOGConfig.dynamicLightsUpdateInterval)));

        settingsList.addHeaderRow("Light Customization");
        settingsList.addButtonRow(
            new GuiButton(BTN_LIGHT_TEMP, 0, 0, w, h,
                toggleLabel("Light Customization", LDOGConfig.enableLightTemperature)),
            new GuiButton(BTN_LIGHT_TEMP_PRESET, 0, 0, w, h,
                lightTempPresetLabel()));
        settingsList.addButtonRow(
            new GuiButton(BTN_BLOCK_LIGHT_R, 0, 0, w, h,
                tintLabel("Block Red", LDOGConfig.blockLightRed, "\u00a7c")),
            new GuiButton(BTN_BLOCK_LIGHT_G, 0, 0, w, h,
                tintLabel("Block Green", LDOGConfig.blockLightGreen, "\u00a7a")));
        settingsList.addButtonRow(
            new GuiButton(BTN_BLOCK_LIGHT_B, 0, 0, w, h,
                tintLabel("Block Blue", LDOGConfig.blockLightBlue, "\u00a79")),
            new GuiButton(BTN_SKY_LIGHT_R, 0, 0, w, h,
                tintLabel("Sky Red", LDOGConfig.skyLightRed, "\u00a7c")));
        settingsList.addButtonRow(
            new GuiButton(BTN_SKY_LIGHT_G, 0, 0, w, h,
                tintLabel("Sky Green", LDOGConfig.skyLightGreen, "\u00a7a")),
            new GuiButton(BTN_SKY_LIGHT_B, 0, 0, w, h,
                tintLabel("Sky Blue", LDOGConfig.skyLightBlue, "\u00a79")));
        settingsList.addButtonRow(
            new GuiButton(BTN_BRIGHTNESS_BOOST, 0, 0, w, h,
                brightnessLabel(LDOGConfig.lightBrightnessBoost)),
            new GuiButton(BTN_NIGHT_DARKNESS, 0, 0, w, h,
                nightDarknessLabel(LDOGConfig.nightDarkness)));
        settingsList.addButtonRow(
            new GuiButton(BTN_HDR, 0, 0, w, h,
                toggleLabel("HDR Tonemapping", LDOGConfig.enableHDR)),
            null);

        // -- Features --
        String featureNote = OptiFineCompat.isOptiFineLoaded()
            ? "Features (OptiFine handles these)"
            : "Features";
        settingsList.addHeaderRow(featureNote);
        settingsList.addButtonRow(
            makeFeatureButton(BTN_CTM, w, h, "Connected Textures",
                LDOGConfig.enableConnectedTextures, OptiFineCompat.shouldHandleCTM()),
            makeFeatureButton(BTN_EMISSIVE, w, h, "Emissive Textures",
                LDOGConfig.enableEmissiveTextures, OptiFineCompat.shouldHandleEmissive()));
        settingsList.addButtonRow(
            makeFeatureButton(BTN_CUSTOM_SKY, w, h, "Custom Sky",
                LDOGConfig.enableCustomSky, OptiFineCompat.shouldHandleCustomSky()),
            null);
        settingsList.addButtonRow(
            makeFeatureButton(BTN_HD_TEXTURES, w, h, "HD Textures",
                LDOGConfig.enableHDTextures, OptiFineCompat.shouldHandleHDTextures()),
            makeFeatureButton(BTN_SHADERS, w, h, "Shaders",
                LDOGConfig.enableShaders, OptiFineCompat.shouldHandleShaders()));
        // Shader pack picker — cycles through whatever lives in shaderpacks/.
        // Only shown when LDOG's shader path is on (the master toggle above);
        // otherwise the row is hidden so it doesn't suggest activation that
        // wouldn't take effect. Rescan button forces a directory rescan if
        // the user added a pack without restarting MC.
        if (LDOGConfig.enableShaders) {
            settingsList.addButtonRow(
                new GuiButton(BTN_SHADER_PACK, 0, 0, w, h,
                    "Pack: §a" + com.limitlessdev.ldog.render.shaderpack.ShaderPackManager
                        .INSTANCE.getActiveName()),
                new GuiButton(BTN_SHADER_RESCAN, 0, 0, w, h, "Rescan Packs"));
        }

        // -- OptiFine Interop (Phase C4) — only shown when OF is detected --
        if (OptiFineCompat.isOptiFineLoaded()) {
            settingsList.addHeaderRow("OptiFine Interop");
            settingsList.addButtonRow(
                ofInteropButton(BTN_OF_MODE_CTM, w, h,
                    com.limitlessdev.ldog.compat.OFFeature.CONNECTED_TEXTURES,
                    LDOGConfig.ofModeCTM),
                ofInteropButton(BTN_OF_MODE_EMISSIVE, w, h,
                    com.limitlessdev.ldog.compat.OFFeature.EMISSIVE_TEXTURES,
                    LDOGConfig.ofModeEmissive));
            settingsList.addButtonRow(
                ofInteropButton(BTN_OF_MODE_DYNAMIC_LIGHTS, w, h,
                    com.limitlessdev.ldog.compat.OFFeature.DYNAMIC_LIGHTS,
                    LDOGConfig.ofModeDynamicLights),
                ofInteropButton(BTN_OF_MODE_CUSTOM_SKY, w, h,
                    com.limitlessdev.ldog.compat.OFFeature.CUSTOM_SKY,
                    LDOGConfig.ofModeCustomSky));
            settingsList.addButtonRow(
                ofInteropButton(BTN_OF_MODE_HD_TEXTURES, w, h,
                    com.limitlessdev.ldog.compat.OFFeature.HD_TEXTURES,
                    LDOGConfig.ofModeHDTextures),
                ofInteropButton(BTN_OF_MODE_SMOOTH_FONT, w, h,
                    com.limitlessdev.ldog.compat.OFFeature.SMOOTH_FONT,
                    LDOGConfig.ofModeSmoothFont));
            settingsList.addButtonRow(
                ofInteropButton(BTN_OF_MODE_SHADERS, w, h,
                    com.limitlessdev.ldog.compat.OFFeature.SHADERS,
                    LDOGConfig.ofModeShaders),
                null);
        }

        // Done button (fixed at bottom, outside scrollable area)
        this.buttonList.add(new GuiButton(BTN_DONE,
            this.width / 2 - 100, this.height - 27, 200, h,
            I18n.format("gui.done")));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        settingsList.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        settingsList.mouseClicked(mouseX, mouseY, mouseButton);

        // Handle button clicks from the scrollable list
        handleListButtonClick(mouseX, mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        settingsList.mouseReleased(mouseX, mouseY, state);
    }

    private void handleListButtonClick(int mouseX, int mouseY) {
        // Find which button in the list was clicked and dispatch actionPerformed.
        // Returns on the first hit so a single click can only fire one button —
        // prior behavior iterated through ALL rows and dispatched every hit,
        // which could double-fire if stale coordinates on off-screen rows
        // happened to coincide with the click point.
        for (int i = 0; i < settingsList.getSize(); i++) {
            net.minecraft.client.gui.GuiListExtended.IGuiListEntry entry = settingsList.getListEntry(i);
            if (!(entry instanceof GuiLDOGSettingsList.ButtonRowEntry)) continue;
            GuiLDOGSettingsList.ButtonRowEntry row = (GuiLDOGSettingsList.ButtonRowEntry) entry;
            GuiButton left = row.getLeftButton();
            GuiButton right = row.getRightButton();
            if (left != null && left.mousePressed(this.mc, mouseX, mouseY)) {
                try { actionPerformed(left); } catch (IOException ignored) {}
                return;
            }
            if (right != null && right.mousePressed(this.mc, mouseX, mouseY)) {
                try { actionPerformed(right); } catch (IOException ignored) {}
                return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;

        switch (button.id) {
            case BTN_DONE:
                saveAndClose();
                break;
            case BTN_RENDER_OPTS:
                LDOGConfig.enableRenderOptimizations = !LDOGConfig.enableRenderOptimizations;
                button.displayString = toggleLabel("Render Opts", LDOGConfig.enableRenderOptimizations);
                break;
            case BTN_PARTICLE_CULL:
                LDOGConfig.enableParticleCulling = !LDOGConfig.enableParticleCulling;
                button.displayString = toggleLabel("Particle Culling", LDOGConfig.enableParticleCulling);
                break;
            case BTN_PARTICLE_FIREWORK:
                LDOGConfig.enableParticleFirework = !LDOGConfig.enableParticleFirework;
                button.displayString = toggleLabel("Firework Particles", LDOGConfig.enableParticleFirework);
                break;
            case BTN_PARTICLE_PORTAL:
                LDOGConfig.enableParticlePortal = !LDOGConfig.enableParticlePortal;
                button.displayString = toggleLabel("Portal Particles", LDOGConfig.enableParticlePortal);
                break;
            case BTN_PARTICLE_POTION:
                LDOGConfig.enableParticlePotion = !LDOGConfig.enableParticlePotion;
                button.displayString = toggleLabel("Potion Particles", LDOGConfig.enableParticlePotion);
                break;
            case BTN_PARTICLE_WATER:
                LDOGConfig.enableParticleWater = !LDOGConfig.enableParticleWater;
                button.displayString = toggleLabel("Water Particles", LDOGConfig.enableParticleWater);
                break;
            case BTN_PARTICLE_DRIPPING:
                LDOGConfig.enableParticleDripping = !LDOGConfig.enableParticleDripping;
                button.displayString = toggleLabel("Dripping Particles", LDOGConfig.enableParticleDripping);
                break;
            case BTN_VIGNETTE_ENABLE:
                LDOGConfig.enableVignette = !LDOGConfig.enableVignette;
                button.displayString = toggleLabel("Vignette", LDOGConfig.enableVignette);
                break;
            case BTN_VIGNETTE_INTENSITY:
                LDOGConfig.vignetteIntensity = cycleValue(VIGNETTE_INTENSITY_VALUES, LDOGConfig.vignetteIntensity);
                button.displayString = vignetteIntensityLabel(LDOGConfig.vignetteIntensity);
                break;
            case BTN_HDR_PIPELINE:
                LDOGConfig.enableHDRPipeline = !LDOGConfig.enableHDRPipeline;
                button.displayString = toggleLabel("HDR Pipeline", LDOGConfig.enableHDRPipeline);
                break;
            case BTN_HDR_TONEMAP: {
                String[] ops = {"aces", "reinhard", "uncharted2", "linear"};
                LDOGConfig.hdrTonemap = cycleStringValue(ops, LDOGConfig.hdrTonemap);
                button.displayString = tonemapLabel(LDOGConfig.hdrTonemap);
                break;
            }
            case BTN_HDR_EXPOSURE:
                LDOGConfig.hdrExposure = cycleValue(EXPOSURE_VALUES, LDOGConfig.hdrExposure);
                button.displayString = multLabel("Exposure", LDOGConfig.hdrExposure);
                break;
            case BTN_BLOOM_ENABLE:
                LDOGConfig.enableBloom = !LDOGConfig.enableBloom;
                button.displayString = toggleLabel("Bloom", LDOGConfig.enableBloom);
                break;
            case BTN_BLOOM_THRESHOLD:
                LDOGConfig.bloomThreshold = cycleValue(BLOOM_THRESHOLD_VALUES, LDOGConfig.bloomThreshold);
                button.displayString = multLabel("Bloom Thresh", LDOGConfig.bloomThreshold);
                break;
            case BTN_BLOOM_INTENSITY:
                LDOGConfig.bloomIntensity = cycleValue(BLOOM_INTENSITY_VALUES, LDOGConfig.bloomIntensity);
                button.displayString = multLabel("Bloom Strength", LDOGConfig.bloomIntensity);
                break;
            case BTN_CLOUD_HEIGHT:
                LDOGConfig.cloudHeightOverride = cycleValue(CLOUD_HEIGHT_VALUES, LDOGConfig.cloudHeightOverride);
                button.displayString = cloudHeightLabel(LDOGConfig.cloudHeightOverride);
                break;
            case BTN_FOG_DISTANCE:
                LDOGConfig.fogDistanceMultiplier = cycleValue(FOG_DISTANCE_VALUES, LDOGConfig.fogDistanceMultiplier);
                button.displayString = multLabel("Fog Dist", LDOGConfig.fogDistanceMultiplier);
                break;
            case BTN_SUN_SIZE:
                LDOGConfig.sunSizeMultiplier = cycleValue(SIZE_MULT_VALUES, LDOGConfig.sunSizeMultiplier);
                button.displayString = multLabel("Sun Size", LDOGConfig.sunSizeMultiplier);
                break;
            case BTN_MOON_SIZE:
                LDOGConfig.moonSizeMultiplier = cycleValue(SIZE_MULT_VALUES, LDOGConfig.moonSizeMultiplier);
                button.displayString = multLabel("Moon Size", LDOGConfig.moonSizeMultiplier);
                break;
            case BTN_WEATHER_RENDER:
                LDOGConfig.enableWeatherRender = !LDOGConfig.enableWeatherRender;
                button.displayString = toggleLabel("Weather Render", LDOGConfig.enableWeatherRender);
                break;
            case BTN_WEATHER_DENSITY:
                LDOGConfig.weatherDensity = cycleValue(WEATHER_DENSITY_VALUES, LDOGConfig.weatherDensity);
                button.displayString = multLabel("Weather Density", LDOGConfig.weatherDensity);
                break;
            case BTN_BIOME_BLEND:
                LDOGConfig.biomeBlendRadius = cycleValue(BIOME_BLEND_VALUES, LDOGConfig.biomeBlendRadius);
                button.displayString = biomeBlendLabel(LDOGConfig.biomeBlendRadius);
                // Biome color is cached per chunk render — need a chunk re-mesh
                // for the new radius to take effect. Easiest way: trigger a
                // resource reload, which invalidates chunk meshes.
                net.minecraft.client.Minecraft.getMinecraft().renderGlobal.loadRenderers();
                break;
            case BTN_NO_DAMAGE_TILT:
                LDOGConfig.disableDamageTilt = !LDOGConfig.disableDamageTilt;
                button.displayString = toggleLabel("No Damage Tilt", LDOGConfig.disableDamageTilt);
                break;
            case BTN_NO_HURT_VIGNETTE:
                LDOGConfig.disableHurtVignette = !LDOGConfig.disableHurtVignette;
                button.displayString = toggleLabel("No Hurt Vignette", LDOGConfig.disableHurtVignette);
                break;
            case BTN_HIDE_HAND:
                LDOGConfig.hideHand = !LDOGConfig.hideHand;
                button.displayString = toggleLabel("Hide Hand", LDOGConfig.hideHand);
                break;
            case BTN_FULLBRIGHT:
                LDOGConfig.enableFullbright = !LDOGConfig.enableFullbright;
                button.displayString = toggleLabel("Fullbright", LDOGConfig.enableFullbright);
                break;
            case BTN_HIDE_CROSSHAIR:
                LDOGConfig.hideCrosshair = !LDOGConfig.hideCrosshair;
                button.displayString = toggleLabel("Hide Crosshair", LDOGConfig.hideCrosshair);
                break;
            case BTN_HIDE_HOTBAR:
                LDOGConfig.hideHotbar = !LDOGConfig.hideHotbar;
                button.displayString = toggleLabel("Hide Hotbar", LDOGConfig.hideHotbar);
                break;
            case BTN_HIDE_EXP:
                LDOGConfig.hideExperienceBar = !LDOGConfig.hideExperienceBar;
                button.displayString = toggleLabel("Hide XP Bar", LDOGConfig.hideExperienceBar);
                break;
            case BTN_HIDE_JUMP:
                LDOGConfig.hideHorseJumpBar = !LDOGConfig.hideHorseJumpBar;
                button.displayString = toggleLabel("Hide Jump Bar", LDOGConfig.hideHorseJumpBar);
                break;
            case BTN_HIDE_TOOLTIP:
                LDOGConfig.hideHeldItemTooltip = !LDOGConfig.hideHeldItemTooltip;
                button.displayString = toggleLabel("Hide Item Tooltip", LDOGConfig.hideHeldItemTooltip);
                break;
            case BTN_NO_PORTAL_OVERLAY:
                LDOGConfig.disablePortalOverlay = !LDOGConfig.disablePortalOverlay;
                button.displayString = toggleLabel("No Portal Distort", LDOGConfig.disablePortalOverlay);
                break;
            case BTN_HUD_COORDS:
                LDOGConfig.showCoordsHud = !LDOGConfig.showCoordsHud;
                button.displayString = toggleLabel("Coords", LDOGConfig.showCoordsHud);
                break;
            case BTN_HUD_FACING:
                LDOGConfig.showFacingHud = !LDOGConfig.showFacingHud;
                button.displayString = toggleLabel("Facing", LDOGConfig.showFacingHud);
                break;
            case BTN_HUD_TIME:
                LDOGConfig.showTimeHud = !LDOGConfig.showTimeHud;
                button.displayString = toggleLabel("Time", LDOGConfig.showTimeHud);
                break;
            case BTN_HUD_BIOME:
                LDOGConfig.showBiomeHud = !LDOGConfig.showBiomeHud;
                button.displayString = toggleLabel("Biome", LDOGConfig.showBiomeHud);
                break;
            case BTN_HUD_LIGHT:
                LDOGConfig.showLightLevelHud = !LDOGConfig.showLightLevelHud;
                button.displayString = toggleLabel("Light Level", LDOGConfig.showLightLevelHud);
                break;
            case BTN_HUD_PING:
                LDOGConfig.showPingHud = !LDOGConfig.showPingHud;
                button.displayString = toggleLabel("Ping", LDOGConfig.showPingHud);
                break;
            case BTN_HUD_DAY:
                LDOGConfig.showDayHud = !LDOGConfig.showDayHud;
                button.displayString = toggleLabel("Day Counter", LDOGConfig.showDayHud);
                break;
            case BTN_HUD_CPS:
                LDOGConfig.showCpsHud = !LDOGConfig.showCpsHud;
                button.displayString = toggleLabel("CPS", LDOGConfig.showCpsHud);
                break;
            case BTN_ADV_TOOLTIPS:
                LDOGConfig.advancedTooltipsAlways = !LDOGConfig.advancedTooltipsAlways;
                button.displayString = toggleLabel("Adv. Tooltips Always", LDOGConfig.advancedTooltipsAlways);
                break;
            case BTN_NO_NAUSEA:
                LDOGConfig.disableNauseaDistortion = !LDOGConfig.disableNauseaDistortion;
                button.displayString = toggleLabel("No Nausea Distort", LDOGConfig.disableNauseaDistortion);
                break;
            case BTN_HIDE_ARMOR:
                LDOGConfig.hideArmorBar = !LDOGConfig.hideArmorBar;
                button.displayString = toggleLabel("Hide Armor Bar", LDOGConfig.hideArmorBar);
                break;
            case BTN_HIDE_HUNGER:
                LDOGConfig.hideHungerBar = !LDOGConfig.hideHungerBar;
                button.displayString = toggleLabel("Hide Hunger Bar", LDOGConfig.hideHungerBar);
                break;
            case BTN_HIDE_AIR:
                LDOGConfig.hideAirBar = !LDOGConfig.hideAirBar;
                button.displayString = toggleLabel("Hide Air Bar", LDOGConfig.hideAirBar);
                break;
            case BTN_HIDE_BOSS:
                LDOGConfig.hideBossHealthBars = !LDOGConfig.hideBossHealthBars;
                button.displayString = toggleLabel("Hide Boss Health", LDOGConfig.hideBossHealthBars);
                break;
            case BTN_ENTITY_DIST:
                LDOGConfig.entityRenderDistance = cycleValue(ENTITY_DIST_VALUES, LDOGConfig.entityRenderDistance);
                button.displayString = distLabel("Entity Dist", LDOGConfig.entityRenderDistance);
                break;
            case BTN_TE_DIST:
                LDOGConfig.tileEntityRenderDistance = cycleValue(TE_DIST_VALUES, LDOGConfig.tileEntityRenderDistance);
                button.displayString = distLabel("TileEntity Dist", LDOGConfig.tileEntityRenderDistance);
                break;
            case BTN_ENTITY_LOD:
                LDOGConfig.enableEntityLOD = !LDOGConfig.enableEntityLOD;
                button.displayString = toggleLabel("Entity LOD", LDOGConfig.enableEntityLOD);
                break;
            case BTN_FPS_REDUCER:
                LDOGConfig.enableFpsReducer = !LDOGConfig.enableFpsReducer;
                button.displayString = toggleLabel("FPS Reducer", LDOGConfig.enableFpsReducer);
                break;
            case BTN_UNFOCUSED_FPS:
                LDOGConfig.unfocusedFpsLimit = cycleValue(UNFOCUSED_FPS_VALUES, LDOGConfig.unfocusedFpsLimit);
                button.displayString = valLabel("Unfocused FPS", LDOGConfig.unfocusedFpsLimit);
                break;
            case BTN_AFK_TIMEOUT:
                LDOGConfig.afkTimeoutSeconds = cycleValue(AFK_TIMEOUT_VALUES, LDOGConfig.afkTimeoutSeconds);
                button.displayString = afkTimeoutLabel(LDOGConfig.afkTimeoutSeconds);
                break;
            case BTN_AFK_FPS:
                LDOGConfig.afkFpsLimit = cycleValue(AFK_FPS_VALUES, LDOGConfig.afkFpsLimit);
                button.displayString = valLabel("AFK FPS", LDOGConfig.afkFpsLimit);
                break;
            case BTN_CLEAR_WATER:
                LDOGConfig.enableClearWater = !LDOGConfig.enableClearWater;
                button.displayString = toggleLabel("Clear Water", LDOGConfig.enableClearWater);
                waterSettingsChanged = true;
                break;
            case BTN_WATER_OPACITY:
                LDOGConfig.waterOpacity = cycleValue(WATER_OPACITY_VALUES, LDOGConfig.waterOpacity);
                button.displayString = opacityLabel("Water Opacity", LDOGConfig.waterOpacity);
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_TINT:
                LDOGConfig.enableWaterTint = !LDOGConfig.enableWaterTint;
                button.displayString = toggleLabel("Water Tint", LDOGConfig.enableWaterTint);
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_RED:
                LDOGConfig.waterTintRed = cycleValue(TINT_VALUES, LDOGConfig.waterTintRed);
                button.displayString = tintLabel("Red", LDOGConfig.waterTintRed, "\u00a7c");
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_GREEN:
                LDOGConfig.waterTintGreen = cycleValue(TINT_VALUES, LDOGConfig.waterTintGreen);
                button.displayString = tintLabel("Green", LDOGConfig.waterTintGreen, "\u00a7a");
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_BLUE:
                LDOGConfig.waterTintBlue = cycleValue(TINT_VALUES, LDOGConfig.waterTintBlue);
                button.displayString = tintLabel("Blue", LDOGConfig.waterTintBlue, "\u00a79");
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_PRESET:
                applyNextPreset();
                refreshWaterButtons();
                waterSettingsChanged = true;
                break;
            case BTN_CTM:
                LDOGConfig.enableConnectedTextures = !LDOGConfig.enableConnectedTextures;
                button.displayString = featureLabel("Connected Textures", LDOGConfig.enableConnectedTextures, OptiFineCompat.shouldHandleCTM());
                break;
            case BTN_EMISSIVE:
                LDOGConfig.enableEmissiveTextures = !LDOGConfig.enableEmissiveTextures;
                button.displayString = featureLabel("Emissive Textures", LDOGConfig.enableEmissiveTextures, OptiFineCompat.shouldHandleEmissive());
                break;
            case BTN_DYNAMIC_LIGHTS:
                LDOGConfig.enableDynamicLights = !LDOGConfig.enableDynamicLights;
                button.displayString = featureLabel("Dynamic Lights", LDOGConfig.enableDynamicLights, OptiFineCompat.shouldHandleDynamicLights());
                break;
            case BTN_DYN_LIGHT_INTERVAL:
                LDOGConfig.dynamicLightsUpdateInterval = cycleValue(DYN_LIGHT_INTERVAL_VALUES, LDOGConfig.dynamicLightsUpdateInterval);
                button.displayString = dynLightIntervalLabel(LDOGConfig.dynamicLightsUpdateInterval);
                break;
            case BTN_LIGHT_TEMP:
                LDOGConfig.enableLightTemperature = !LDOGConfig.enableLightTemperature;
                button.displayString = toggleLabel("Light Customization", LDOGConfig.enableLightTemperature);
                break;
            case BTN_LIGHT_TEMP_PRESET:
                applyNextLightPreset();
                refreshLightButtons();
                break;
            case BTN_BLOCK_LIGHT_R:
                LDOGConfig.blockLightRed = cycleValue(LIGHT_TINT_VALUES, LDOGConfig.blockLightRed);
                button.displayString = tintLabel("Block Red", LDOGConfig.blockLightRed, "\u00a7c");
                autoEnableLightCustomization();
                break;
            case BTN_BLOCK_LIGHT_G:
                LDOGConfig.blockLightGreen = cycleValue(LIGHT_TINT_VALUES, LDOGConfig.blockLightGreen);
                button.displayString = tintLabel("Block Green", LDOGConfig.blockLightGreen, "\u00a7a");
                autoEnableLightCustomization();
                break;
            case BTN_BLOCK_LIGHT_B:
                LDOGConfig.blockLightBlue = cycleValue(LIGHT_TINT_VALUES, LDOGConfig.blockLightBlue);
                button.displayString = tintLabel("Block Blue", LDOGConfig.blockLightBlue, "\u00a79");
                autoEnableLightCustomization();
                break;
            case BTN_SKY_LIGHT_R:
                LDOGConfig.skyLightRed = cycleValue(LIGHT_TINT_VALUES, LDOGConfig.skyLightRed);
                button.displayString = tintLabel("Sky Red", LDOGConfig.skyLightRed, "\u00a7c");
                autoEnableLightCustomization();
                break;
            case BTN_SKY_LIGHT_G:
                LDOGConfig.skyLightGreen = cycleValue(LIGHT_TINT_VALUES, LDOGConfig.skyLightGreen);
                button.displayString = tintLabel("Sky Green", LDOGConfig.skyLightGreen, "\u00a7a");
                autoEnableLightCustomization();
                break;
            case BTN_SKY_LIGHT_B:
                LDOGConfig.skyLightBlue = cycleValue(LIGHT_TINT_VALUES, LDOGConfig.skyLightBlue);
                button.displayString = tintLabel("Sky Blue", LDOGConfig.skyLightBlue, "\u00a79");
                autoEnableLightCustomization();
                break;
            case BTN_BRIGHTNESS_BOOST:
                LDOGConfig.lightBrightnessBoost = cycleValue(BRIGHTNESS_VALUES, LDOGConfig.lightBrightnessBoost);
                button.displayString = brightnessLabel(LDOGConfig.lightBrightnessBoost);
                autoEnableLightCustomization();
                break;
            case BTN_NIGHT_DARKNESS:
                LDOGConfig.nightDarkness = cycleValue(NIGHT_DARK_VALUES, LDOGConfig.nightDarkness);
                button.displayString = nightDarknessLabel(LDOGConfig.nightDarkness);
                autoEnableLightCustomization();
                break;
            case BTN_HDR:
                LDOGConfig.enableHDR = !LDOGConfig.enableHDR;
                button.displayString = toggleLabel("HDR Tonemapping", LDOGConfig.enableHDR);
                autoEnableLightCustomization();
                break;
            case BTN_CUSTOM_SKY:
                LDOGConfig.enableCustomSky = !LDOGConfig.enableCustomSky;
                button.displayString = featureLabel("Custom Sky", LDOGConfig.enableCustomSky, OptiFineCompat.shouldHandleCustomSky());
                break;
            case BTN_HD_TEXTURES:
                LDOGConfig.enableHDTextures = !LDOGConfig.enableHDTextures;
                button.displayString = featureLabel("HD Textures", LDOGConfig.enableHDTextures, OptiFineCompat.shouldHandleHDTextures());
                break;
            case BTN_SHADERS:
                LDOGConfig.enableShaders = !LDOGConfig.enableShaders;
                button.displayString = featureLabel("Shaders", LDOGConfig.enableShaders, OptiFineCompat.shouldHandleShaders());
                com.limitlessdev.ldog.render.shaderpack.ShaderPackManager.INSTANCE.applyConfigSelection();
                break;
            case BTN_SHADER_PACK: {
                com.limitlessdev.ldog.render.shaderpack.ShaderPackManager mgr =
                    com.limitlessdev.ldog.render.shaderpack.ShaderPackManager.INSTANCE;
                java.util.List<String> names = mgr.getPackNames();
                LDOGConfig.shaderPackName = cycleStringValue(
                    names.toArray(new String[0]), LDOGConfig.shaderPackName);
                mgr.activate(LDOGConfig.shaderPackName);
                button.displayString = "Pack: §a" + mgr.getActiveName();
                break;
            }
            case BTN_SHADER_RESCAN:
                com.limitlessdev.ldog.render.shaderpack.ShaderPackManager.INSTANCE.rescan();
                button.displayString = "§aRescanned";
                break;
            case BTN_BETTER_GRASS:
                LDOGConfig.betterGrass = cycleStringValue(BETTER_GRASS_MODES, LDOGConfig.betterGrass);
                button.displayString = betterGrassLabel(LDOGConfig.betterGrass);
                break;
            case BTN_BETTER_SNOW:
                LDOGConfig.enableBetterSnow = !LDOGConfig.enableBetterSnow;
                button.displayString = toggleLabel("Better Snow", LDOGConfig.enableBetterSnow);
                break;
            case BTN_PERF_OVERLAY:
                LDOGConfig.enablePerformanceOverlay = !LDOGConfig.enablePerformanceOverlay;
                button.displayString = toggleLabel("Perf Overlay", LDOGConfig.enablePerformanceOverlay);
                break;
            case BTN_NATURAL_TEXTURES:
                LDOGConfig.enableNaturalTextures = !LDOGConfig.enableNaturalTextures;
                button.displayString = toggleLabel("Natural Textures", LDOGConfig.enableNaturalTextures);
                break;
            case BTN_CUSTOM_COLORS:
                LDOGConfig.enableCustomColors = !LDOGConfig.enableCustomColors;
                button.displayString = toggleLabel("Custom Colors", LDOGConfig.enableCustomColors);
                break;
            case BTN_RANDOM_MOBS:
                LDOGConfig.enableRandomEntityTextures = !LDOGConfig.enableRandomEntityTextures;
                button.displayString = toggleLabel("Random Mobs", LDOGConfig.enableRandomEntityTextures);
                break;
            case BTN_ANISOTROPIC:
                LDOGConfig.enableAnisotropicFiltering = !LDOGConfig.enableAnisotropicFiltering;
                button.displayString = toggleLabel("Anisotropic", LDOGConfig.enableAnisotropicFiltering);
                aaSettingsChanged = true;
                break;
            case BTN_ANISOTROPIC_LEVEL:
                LDOGConfig.anisotropicLevel = cycleValue(ANISOTROPIC_VALUES, LDOGConfig.anisotropicLevel);
                button.displayString = afLabel(LDOGConfig.anisotropicLevel);
                aaSettingsChanged = true;
                break;
            case BTN_MSAA:
                LDOGConfig.enableMSAA = !LDOGConfig.enableMSAA;
                button.displayString = toggleLabel("MSAA", LDOGConfig.enableMSAA);
                break;
            case BTN_MSAA_SAMPLES:
                LDOGConfig.msaaSamples = cycleValue(MSAA_VALUES, LDOGConfig.msaaSamples);
                button.displayString = msaaLabel(LDOGConfig.msaaSamples);
                break;
            case BTN_FXAA:
                LDOGConfig.enableFXAA = !LDOGConfig.enableFXAA;
                button.displayString = toggleLabel("FXAA", LDOGConfig.enableFXAA);
                fxaaSettingsChanged = true;
                break;
            case BTN_TAA_ENABLE:
                LDOGConfig.enableTAA = !LDOGConfig.enableTAA;
                button.displayString = toggleLabel("TAA", LDOGConfig.enableTAA);
                break;
            case BTN_AUTO_SCALE: {
                com.limitlessdev.ldog.render.pipeline.AutoScaleMode next =
                    com.limitlessdev.ldog.render.pipeline.AutoScaleMode.selected().next();
                LDOGConfig.autoScaleMode = next.configKey();
                button.displayString = autoScaleLabel(next);
                break;
            }
            // Phase C4 OF interop mode cycles. Each delegates to cycleOFMode
            // which: reads current mode from LDOGConfig, advances via .next(),
            // writes back, recomputes label. Cache invalidation happens on
            // the settings-screen save event in LDOGConfig.EventHandler so we
            // don't need to call OptiFineCompat.invalidateCache() per-click
            // (settings only commit on Done).
            case BTN_OF_MODE_CTM:
                LDOGConfig.ofModeCTM = cycleOFMode(LDOGConfig.ofModeCTM);
                button.displayString = ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature.CONNECTED_TEXTURES,
                    com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(LDOGConfig.ofModeCTM),
                    com.limitlessdev.ldog.compat.OFConfigBridge.canControl(com.limitlessdev.ldog.compat.OFFeature.CONNECTED_TEXTURES));
                break;
            case BTN_OF_MODE_EMISSIVE:
                LDOGConfig.ofModeEmissive = cycleOFMode(LDOGConfig.ofModeEmissive);
                button.displayString = ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature.EMISSIVE_TEXTURES,
                    com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(LDOGConfig.ofModeEmissive),
                    com.limitlessdev.ldog.compat.OFConfigBridge.canControl(com.limitlessdev.ldog.compat.OFFeature.EMISSIVE_TEXTURES));
                break;
            case BTN_OF_MODE_DYNAMIC_LIGHTS:
                LDOGConfig.ofModeDynamicLights = cycleOFMode(LDOGConfig.ofModeDynamicLights);
                button.displayString = ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature.DYNAMIC_LIGHTS,
                    com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(LDOGConfig.ofModeDynamicLights),
                    com.limitlessdev.ldog.compat.OFConfigBridge.canControl(com.limitlessdev.ldog.compat.OFFeature.DYNAMIC_LIGHTS));
                break;
            case BTN_OF_MODE_CUSTOM_SKY:
                LDOGConfig.ofModeCustomSky = cycleOFMode(LDOGConfig.ofModeCustomSky);
                button.displayString = ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature.CUSTOM_SKY,
                    com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(LDOGConfig.ofModeCustomSky),
                    com.limitlessdev.ldog.compat.OFConfigBridge.canControl(com.limitlessdev.ldog.compat.OFFeature.CUSTOM_SKY));
                break;
            case BTN_OF_MODE_HD_TEXTURES:
                LDOGConfig.ofModeHDTextures = cycleOFMode(LDOGConfig.ofModeHDTextures);
                button.displayString = ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature.HD_TEXTURES,
                    com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(LDOGConfig.ofModeHDTextures),
                    com.limitlessdev.ldog.compat.OFConfigBridge.canControl(com.limitlessdev.ldog.compat.OFFeature.HD_TEXTURES));
                break;
            case BTN_OF_MODE_SMOOTH_FONT:
                LDOGConfig.ofModeSmoothFont = cycleOFMode(LDOGConfig.ofModeSmoothFont);
                button.displayString = ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature.SMOOTH_FONT,
                    com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(LDOGConfig.ofModeSmoothFont),
                    com.limitlessdev.ldog.compat.OFConfigBridge.canControl(com.limitlessdev.ldog.compat.OFFeature.SMOOTH_FONT));
                break;
            case BTN_OF_MODE_SHADERS:
                LDOGConfig.ofModeShaders = cycleOFMode(LDOGConfig.ofModeShaders);
                button.displayString = ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature.SHADERS,
                    com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(LDOGConfig.ofModeShaders),
                    com.limitlessdev.ldog.compat.OFConfigBridge.canControl(com.limitlessdev.ldog.compat.OFFeature.SHADERS));
                break;
            case BTN_TAA_WEIGHT:
                LDOGConfig.taaHistoryWeight = cycleValue(TAA_WEIGHT_VALUES, LDOGConfig.taaHistoryWeight);
                button.displayString = taaWeightLabel(LDOGConfig.taaHistoryWeight);
                break;
            case BTN_TAA_REACTIVE_MASK:
                LDOGConfig.enableEntityReactiveMask = !LDOGConfig.enableEntityReactiveMask;
                button.displayString = toggleLabel("Entity Reactive Mask", LDOGConfig.enableEntityReactiveMask);
                break;
            case BTN_TAA_ENTITY_MV:
                LDOGConfig.enableEntityMotionVectors = !LDOGConfig.enableEntityMotionVectors;
                button.displayString = toggleLabel("Entity MV", LDOGConfig.enableEntityMotionVectors);
                break;
            case BTN_FXAA_QUALITY: {
                com.limitlessdev.ldog.render.pipeline.FXAAQuality current =
                    com.limitlessdev.ldog.render.pipeline.FXAAQuality.selected();
                com.limitlessdev.ldog.render.pipeline.FXAAQuality[] all =
                    com.limitlessdev.ldog.render.pipeline.FXAAQuality.values();
                com.limitlessdev.ldog.render.pipeline.FXAAQuality next =
                    all[(current.ordinal() + 1) % all.length];
                LDOGConfig.fxaaQuality = next.configKey();
                button.displayString = fxaaQualityLabel(next);
                // Only the pipeline FXAA honors the quality uniform; MC's
                // fixed shader doesn't. Live-adjustable without reload.
                break;
            }
            case BTN_PIPELINE:
                LDOGConfig.enablePostProcessPipeline = !LDOGConfig.enablePostProcessPipeline;
                button.displayString = toggleLabel("Post Pipeline", LDOGConfig.enablePostProcessPipeline);
                // Pipeline state gates which FXAA runs — MC's when off, LDOG's
                // when on. Force FXAAHandler.apply on Done so MC's shader is
                // unloaded/reloaded to match.
                fxaaSettingsChanged = true;
                break;
            case BTN_PIPELINE_SCALE:
                LDOGConfig.internalRenderScale = cycleValue(
                    PIPELINE_SCALE_VALUES, LDOGConfig.internalRenderScale);
                button.displayString = pipelineScaleLabel(LDOGConfig.internalRenderScale);
                com.limitlessdev.ldog.render.pipeline.UpscalerPreset.markCustom();
                refreshPresetButton();
                break;
            case BTN_PIPELINE_UPSCALER: {
                com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm current =
                    com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm.selected();
                com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm[] all =
                    com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm.values();
                com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm next =
                    all[(current.ordinal() + 1) % all.length];
                LDOGConfig.upscalerAlgorithm = next.configKey();
                button.displayString = upscalerLabel(next);
                com.limitlessdev.ldog.render.pipeline.UpscalerPreset.markCustom();
                refreshPresetButton();
                break;
            }
            case BTN_FSR1_SHARPNESS:
                LDOGConfig.fsr1Sharpness = cycleValue(FSR1_SHARPNESS_VALUES, LDOGConfig.fsr1Sharpness);
                button.displayString = fsr1SharpnessLabel(LDOGConfig.fsr1Sharpness);
                com.limitlessdev.ldog.render.pipeline.UpscalerPreset.markCustom();
                refreshPresetButton();
                break;
            case BTN_LDOG_PRESET: {
                com.limitlessdev.ldog.config.LDOGPreset current =
                    com.limitlessdev.ldog.config.LDOGPreset.selected();
                com.limitlessdev.ldog.config.LDOGPreset[] all =
                    com.limitlessdev.ldog.config.LDOGPreset.values();
                com.limitlessdev.ldog.config.LDOGPreset next =
                    all[(current.ordinal() + 1) % all.length];
                next.apply();

                // A preset can change AA / FXAA / Ext-Border / water toggles
                // all at once. Mark the settings-changed flags so saveAndClose
                // triggers the right reload paths on Done — extBorder triggers
                // the heavy refreshResources (which also covers AA changes),
                // fxaa triggers FXAAHandler.apply, water triggers chunk rebuild.
                extBorderSettingsChanged = true;
                fxaaSettingsChanged = true;
                waterSettingsChanged = true;

                // Rebuild the entire settings list so every affected button
                // picks up its new label. Simpler than chasing 20+ updates.
                this.initGui();
                return;
            }
            case BTN_RCAS_ENABLE:
                LDOGConfig.enableRcasSharpen = !LDOGConfig.enableRcasSharpen;
                button.displayString = toggleLabel("RCAS Sharpen", LDOGConfig.enableRcasSharpen);
                break;
            case BTN_RCAS_STRENGTH:
                LDOGConfig.rcasSharpness = cycleValue(RCAS_STRENGTH_VALUES, LDOGConfig.rcasSharpness);
                button.displayString = rcasStrengthLabel(LDOGConfig.rcasSharpness);
                break;
            case BTN_UPSCALER_PRESET: {
                com.limitlessdev.ldog.render.pipeline.UpscalerPreset current =
                    com.limitlessdev.ldog.render.pipeline.UpscalerPreset.selected();
                com.limitlessdev.ldog.render.pipeline.UpscalerPreset[] all =
                    com.limitlessdev.ldog.render.pipeline.UpscalerPreset.values();
                com.limitlessdev.ldog.render.pipeline.UpscalerPreset next =
                    all[(current.ordinal() + 1) % all.length];
                next.apply();
                button.displayString = upscalerPresetLabel(next);
                // Update the individual controls' labels to reflect the
                // preset's applied values. Preset=CUSTOM applies nothing,
                // so those labels stay as they were.
                refreshIndividualButtons();
                break;
            }
            case BTN_BORDERLESS_FULLSCREEN:
                LDOGConfig.borderlessFullscreen = !LDOGConfig.borderlessFullscreen;
                button.displayString = toggleLabel("Borderless Windowed", LDOGConfig.borderlessFullscreen);
                break;
            case BTN_BLOCK_FSO:
                LDOGConfig.blockFullscreenOptimizations = !LDOGConfig.blockFullscreenOptimizations;
                button.displayString = toggleLabel("Block FS Optim", LDOGConfig.blockFullscreenOptimizations);
                break;
            case BTN_EXT_BORDER:
                LDOGConfig.enableExtendedBorderMipmaps = !LDOGConfig.enableExtendedBorderMipmaps;
                button.displayString = toggleLabel("Ext Border Mips", LDOGConfig.enableExtendedBorderMipmaps);
                extBorderSettingsChanged = true;
                break;
            case BTN_SMOOTH_FONT:
                LDOGConfig.enableSmoothFont = !LDOGConfig.enableSmoothFont;
                button.displayString = toggleLabel("Smooth Font", LDOGConfig.enableSmoothFont);
                fontSettingsChanged = true;
                break;
            case BTN_HD_FONT:
                LDOGConfig.useHDFontTexture = !LDOGConfig.useHDFontTexture;
                button.displayString = toggleLabel("HD Font Texture", LDOGConfig.useHDFontTexture);
                fontSettingsChanged = true;
                break;
            case BTN_AA_FONT: {
                String prev = LDOGConfig.fontAntialiasing;
                LDOGConfig.fontAntialiasing = cycleStringValue(FONT_AA_MODES, prev);
                button.displayString = fontAALabel(LDOGConfig.fontAntialiasing);
                // A pure filter flip is cheap (two glTexParameteri calls) and
                // avoids a full resource reload; but if the change crosses the
                // mipmap boundary (trilinear <-> off/bilinear) we need a fresh
                // upload to build or drop the chain.
                com.limitlessdev.ldog.render.font.FontAAMode target =
                    com.limitlessdev.ldog.render.font.FontAAMode.parse(LDOGConfig.fontAntialiasing);
                if (com.limitlessdev.ldog.render.font.SmoothFontHandler.INSTANCE.needsReloadToSwitchTo(target)) {
                    fontSettingsChanged = true;
                } else {
                    fontFilterChanged = true;
                }
                break;
            }
            case BTN_FONT_WIDTHS:
                LDOGConfig.useFontPropertyWidths = !LDOGConfig.useFontPropertyWidths;
                button.displayString = toggleLabel("Pack Widths", LDOGConfig.useFontPropertyWidths);
                fontSettingsChanged = true;
                break;
            case BTN_TTF_FONT:
                LDOGConfig.useTTFFont = !LDOGConfig.useTTFFont;
                button.displayString = toggleLabel("TTF Font", LDOGConfig.useTTFFont);
                fontSettingsChanged = true;
                break;
            case BTN_TTF_FAMILY:
                LDOGConfig.ttfFontFamily = cycleStringValue(
                    com.limitlessdev.ldog.render.font.TTFFontCatalog.getAllFamilies(),
                    LDOGConfig.ttfFontFamily);
                button.displayString = fontFamilyLabel(LDOGConfig.ttfFontFamily);
                fontSettingsChanged = true;
                break;
            case BTN_FONT_SHADOW:
                LDOGConfig.fontDropShadows = !LDOGConfig.fontDropShadows;
                button.displayString = toggleLabel("Drop Shadows", LDOGConfig.fontDropShadows);
                // Flag-only flip — next frame's draw reads the new config value.
                // No filter refresh or resource reload needed.
                break;
            case BTN_TTF_SIZE:
                LDOGConfig.ttfFontSize = cycleValue(TTF_SIZES, LDOGConfig.ttfFontSize);
                // Keep cell size in sync: ~4/3 of the font size rounded up to 8px,
                // capped at the config max. Gives enough breathing room in the cell
                // for ascenders/descenders without wasting atlas area.
                int cell = Math.min(128, ((LDOGConfig.ttfFontSize * 4 / 3) + 7) & ~7);
                LDOGConfig.ttfCellSize = Math.max(LDOGConfig.ttfFontSize, cell);
                button.displayString = valLabel("TTF Size", LDOGConfig.ttfFontSize);
                fontSettingsChanged = true;
                break;
            case BTN_TTF_BOLD:
                LDOGConfig.ttfBold = !LDOGConfig.ttfBold;
                button.displayString = toggleLabel("TTF Bold", LDOGConfig.ttfBold);
                fontSettingsChanged = true;
                break;
            case BTN_TTF_ITALIC:
                LDOGConfig.ttfItalic = !LDOGConfig.ttfItalic;
                button.displayString = toggleLabel("TTF Italic", LDOGConfig.ttfItalic);
                fontSettingsChanged = true;
                break;
            case BTN_TTF_SUBPIXEL:
                LDOGConfig.ttfSubpixel = !LDOGConfig.ttfSubpixel;
                button.displayString = toggleLabel("LCD Subpixel", LDOGConfig.ttfSubpixel);
                fontSettingsChanged = true;
                break;
        }
    }

    private boolean aaSettingsChanged = false;
    private boolean fxaaSettingsChanged = false;
    private boolean extBorderSettingsChanged = false;
    /** True when the HD-font discovery inputs changed — needs a resource reload. */
    private boolean fontSettingsChanged = false;
    /** True when only the antialias filter flipped — cheap to apply live. */
    private boolean fontFilterChanged = false;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        settingsList.drawScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRenderer, "LDOG Settings", this.width / 2, 8, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawHoveredTooltip(mouseX, mouseY);
    }

    private void drawHoveredTooltip(int mouseX, int mouseY) {
        // Only show when the cursor is inside the list's visible area (above/below
        // the scroll region, buttons would be clipped but still "hovered" by coords).
        if (mouseY < settingsList.top || mouseY >= settingsList.bottom) return;
        for (Map.Entry<Integer, String[]> e : buttonTooltips.entrySet()) {
            GuiButton btn = settingsList.findButton(e.getKey());
            if (btn == null || !btn.visible) continue;
            if (mouseX >= btn.x && mouseY >= btn.y
                && mouseX < btn.x + btn.width && mouseY < btn.y + btn.height) {
                this.drawHoveringText(Arrays.asList(e.getValue()), mouseX, mouseY);
                return;
            }
        }
    }

    private void registerTooltip(int buttonId, String... lines) {
        buttonTooltips.put(buttonId, lines);
    }

    /** Called from {@link #initGui} to populate per-button hover tooltips. */
    private void registerTooltips() {
        registerTooltip(BTN_LDOG_PRESET,
            "\u00a7eLDOG Config Preset",
            "\u00a77One-click bundle for the whole mod. Overwrites ~20 config",
            "\u00a77toggles at once.",
            "",
            "\u00a7fVanilla:\u00a77 LDOG visual features OFF, closest to stock MC.",
            "\u00a7bPerformance:\u00a77 visuals OFF, perf opts MAX, FSR1 at 0.5.",
            "\u00a7aDefault:\u00a77 LDOG's opinionated first-install defaults.",
            "\u00a7eFancy:\u00a77 visuals ON, FXAA + FSR1-Quality upscale + RCAS.",
            "\u00a76Ultra:\u00a77 everything max — AF 16x, FSR1 Ultra upscale, RCAS.",
            "\u00a77Custom:\u00a77 your own mix.",
            "",
            "\u00a7cNote:\u00a77 MSAA is OFF in Fancy and Ultra because MSAA and the",
            "\u00a77post-process pipeline conflict — pipeline yields when MSAA",
            "\u00a77is on. Those presets commit to the upscaling look.",
            "",
            "\u00a7cNot included:\u00a77 fonts, tint colors, FPS limits, borderless",
            "\u00a77(all user-specific / platform-specific, preserved across changes).");
        registerTooltip(BTN_EXT_BORDER,
            "\u00a7eExtended Border Mipmaps",
            "\u00a77Fixes faint block-edge lines at distance when",
            "\u00a77Anisotropic Filtering is on. Pads every atlas sprite",
            "\u00a77with an edge-extended halo so AF can't bleed across",
            "\u00a77tile boundaries at high mip levels.",
            "",
            "\u00a7cImpact:\u00a77 grows the block atlas by ~3x for 16x packs.",
            "\u00a77May push HD packs past the GPU's max texture size.",
            "\u00a77Zero runtime cost once the atlas is built.",
            "",
            "\u00a7aRecommended:\u00a77 enable alongside AF.");
        registerTooltip(BTN_SMOOTH_FONT,
            "\u00a7eSmooth Font (master)",
            "\u00a77Drop-in replacement for the Smooth Font mod.",
            "\u00a77Turn off to use pure vanilla font rendering.",
            "\u00a77Auto-disabled when OptiFine is detected.");
        registerTooltip(BTN_HD_FONT,
            "\u00a7eHD Font Texture",
            "\u00a77Loads the HD ASCII font PNG from the resource pack",
            "\u00a77(optifine/font/ascii.png or mcpatcher/font/ascii.png)",
            "\u00a77instead of the vanilla 128x128 texture.",
            "",
            "\u00a77Ignored when TTF Font is on (TTF takes priority).");
        registerTooltip(BTN_TTF_FONT,
            "\u00a7eTTF Font",
            "\u00a77Rasterize glyphs from a system TrueType font at startup",
            "\u00a77using Java AWT, similar to Smooth Font's default mode.",
            "\u00a77Produces pixel-perfect antialiased glyphs at the chosen",
            "\u00a77cell size — crisper than any filter applied to a",
            "\u00a77pre-rasterized atlas.",
            "",
            "\u00a77Overrides HD Font Texture when enabled.");
        registerTooltip(BTN_TTF_FAMILY,
            "\u00a7eTTF Font Family",
            "\u00a77Cycles through available font families. Logical names",
            "\u00a77(SansSerif, Serif, Monospaced) always resolve;",
            "\u00a77OS-specific names fall back to the system default",
            "\u00a77if missing on your machine.",
            "",
            "\u00a7aBring your own:\u00a77 drop .ttf or .otf files into",
            "\u00a77\u00a7oconfig/ldog/fonts/\u00a7r\u00a77 to see them in this cycle.",
            "\u00a77Custom entries are highlighted in yellow. Files can be",
            "\u00a77added/removed while the game is running — F3+T to rescan.");
        registerTooltip(BTN_TTF_SIZE,
            "\u00a7eTTF Font Size",
            "\u00a77AWT point size used to rasterize. Cell size is",
            "\u00a77auto-sized 4/3 larger (rounded up to 8px) to leave",
            "\u00a77breathing room for ascenders/descenders.",
            "",
            "\u00a77Larger = sharper at high GUI scales, more atlas memory.");
        registerTooltip(BTN_FONT_SHADOW,
            "\u00a7eDrop Shadows",
            "\u00a77Render the 1-pixel offset dark copy behind text that",
            "\u00a77MC draws for most UI strings. Disable for a flatter,",
            "\u00a77cleaner look.",
            "",
            "\u00a77Flips live — no reload needed.");
        registerTooltip(BTN_AA_FONT,
            "\u00a7eFont Antialiasing",
            "\u00a7cOff:\u00a77 GL_NEAREST, blocky vanilla look.",
            "\u00a7eBilinear:\u00a77 GL_LINEAR. Smooth at 1:1 but aliased at",
            "\u00a77GUI scale since the HD atlas has no mip chain.",
            "\u00a7aTrilinear:\u00a77 LINEAR_MIPMAP_LINEAR + generated mipmaps.",
            "\u00a77Smoothest — the GPU picks a pre-downsampled level",
            "\u00a77that matches the screen footprint.",
            "",
            "\u00a77Bilinear<->Off flip live. Switching to/from Trilinear",
            "\u00a77triggers a resource reload to build/drop the mip chain.");
        registerTooltip(BTN_FONT_WIDTHS,
            "\u00a7ePack Widths",
            "\u00a77Honor per-glyph widths from the pack's",
            "\u00a77ascii.properties file (format: width.N=W).",
            "\u00a77Checks optifine/font, mcpatcher/font, then font/.");
        registerTooltip(BTN_UPSCALER_PRESET,
            "\u00a7eQuality Preset",
            "\u00a77One-click bundle of render scale + upscaler + sharpness.",
            "",
            "\u00a7bNative:\u00a77 scale 1.00, Bilinear, sharp 0.0 — baseline.",
            "\u00a7aUltra:\u00a77 scale 0.85, FSR1-Quality, sharp 0.75 — near-",
            "\u00a77native quality with a light perf win.",
            "\u00a7aQuality:\u00a77 scale 0.75, FSR1-Quality, sharp 1.00 — good",
            "\u00a77balance.",
            "\u00a7eBalanced:\u00a77 scale 0.67, FSR1, sharp 1.25 — noticeable",
            "\u00a77perf gain, accept softer edges.",
            "\u00a76Performance:\u00a77 scale 0.50, FSR1, sharp 1.50 — max perf,",
            "\u00a77visibly softer.",
            "\u00a77Custom:\u00a77 you're driving the individual controls.",
            "",
            "\u00a77Editing any individual control flips back to Custom.");
        registerTooltip(BTN_PIPELINE,
            "\u00a7ePost-Process Pipeline",
            "\u00a77Master switch for LDOG's render pipeline. Required by:",
            "\u00a77 \u2022 Render Scale below 1.0 + Upscalers (FSR1, FSR1-Quality, FSR2)",
            "\u00a77 \u2022 RCAS post-upscale sharpening",
            "\u00a77 \u2022 LDOG's pipeline FXAA (with quality levels)",
            "\u00a77 \u2022 Temporal AA + Entity Motion Vectors + Reactive Mask",
            "\u00a77 \u2022 Vignette, HDR + Bloom",
            "",
            "\u00a77When off, everything in this list is bypassed and the game",
            "\u00a77renders the vanilla way.",
            "",
            "\u00a7eHeads up:\u00a77 if MSAA is on, the pipeline yields the render",
            "\u00a77target to MSAA. The two can't both own world rendering at",
            "\u00a77once. FXAA composites cleanly either way.");
        registerTooltip(BTN_PIPELINE_SCALE,
            "\u00a7eInternal Render Scale",
            "\u00a771.0x = native resolution. Below 1.0 renders the world",
            "\u00a77smaller and upscales to native — a perf win at the cost",
            "\u00a77of image quality that depends on the chosen upscaler.",
            "",
            "\u00a77Only consumed when the Post Pipeline toggle is on.");
        registerTooltip(BTN_PIPELINE_UPSCALER,
            "\u00a7eUpscaler Algorithm",
            "\u00a77Selects the filter used to resolve the scaled scene",
            "\u00a77target back to native resolution.",
            "",
            "\u00a7aBilinear:\u00a77 plain GL_LINEAR blit. Cheapest, blurriest",
            "\u00a77at low scales. Good baseline.",
            "\u00a7aFSR1:\u00a77 edge-adaptive spatial upsampling. Sharper",
            "\u00a77edges than bilinear at the same render scale.",
            "\u00a7aFSR1-Quality:\u00a77 direction-biased EASU + sharpen \u2014 best",
            "\u00a77spatial-only option for diagonal geometry.",
            "\u00a7aFSR2:\u00a77 \u00a76TEMPORAL\u00a77 \u2014 Lanczos-3 source + jittered",
            "\u00a77projection + camera/entity MV reprojection + history",
            "\u00a77accumulation. Best quality at low scales when the",
            "\u00a77temporal inputs are clean. Replaces both upscaler + TAA.",
            "",
            "\u00a7eFSR2 needs:\u00a77 Post Pipeline ON, render scale < 1.0,",
            "\u00a77and (recommended) Entity MV ON for ghost-free entities.",
            "\u00a77Disocclusion artifacts on fast camera turns are expected.",
            "",
            "\u00a77Only applies when render scale is below 1.0.");
        registerTooltip(BTN_BLOCK_FSO,
            "\u00a7eBlock Windows Fullscreen Optimizations",
            "\u00a77Windows 10/11 auto-detects borderless windows sized to",
            "\u00a77the whole desktop and switches the DWM compositor into an",
            "\u00a77optimized path. That transition flashes the desktop briefly.",
            "",
            "\u00a7aON:\u00a77 window is 1 pixel shorter than the desktop — Windows",
            "\u00a77doesn't trigger detection. Flicker-free toggle, but the",
            "\u00a77taskbar remains visible at the bottom.",
            "\u00a7eOFF:\u00a77 window matches the desktop exactly. Taskbar auto-",
            "\u00a77hides (clean look), but you get the flash each toggle.",
            "",
            "\u00a77Only consumed when Borderless Windowed is on. Live-toggle.");
        registerTooltip(BTN_BORDERLESS_FULLSCREEN,
            "\u00a7eBorderless Windowed Fullscreen",
            "\u00a77Replaces exclusive fullscreen (F11) with an undecorated",
            "\u00a77window sized to the desktop. Benefits:",
            "\u00a77 - instant alt-tab, no display-mode flicker",
            "\u00a77 - external overlays (Discord, browser, etc.) work",
            "\u00a77 - cursor moves freely between monitors",
            "",
            "\u00a7cTrade-off:\u00a77 the game window has no decorations in",
            "\u00a77\u00a7owindowed mode\u00a7r\u00a77 either — no title bar, no resize grips.",
            "\u00a77Drag via Alt+drag on Windows.",
            "",
            "\u00a7cREQUIRES RESTART:\u00a77 LWJGL only reads the undecorated",
            "\u00a77flag at Display creation. Toggle this, click Done to save,",
            "\u00a77then relaunch the game for it to take effect.");
        registerTooltip(BTN_AUTO_SCALE,
            "\u00a7eAuto Scale (Dynamic Resolution Scaling)",
            "\u00a77Three-state cycle: Off / Normal / Aggressive.",
            "",
            "\u00a77\u00a77Off:\u00a77 manual Render Scale honored.",
            "\u00a7aNormal:\u00a77 only Render Scale auto-adjusts.",
            "\u00a77 5-tier ladder: 1.00 -> 0.85 -> 0.75 -> 0.67 -> 0.50.",
            "\u00a76Aggressive:\u00a77 also auto-manages Upscaler",
            "\u00a77 algorithm and FXAA quality + on/off across a 7-tier",
            "\u00a77 extended ladder. At 0.50x, FSR1-Quality drops to FSR1",
            "\u00a77 then Bilinear; FXAA Ultra -> High -> Medium -> Low -> off.",
            "",
            "\u00a77Decisions every 2s: drops a tier when FPS < 90% of target,",
            "\u00a77raises when FPS > 110%. Target = min(refresh, FPS limit).",
            "",
            "\u00a7cOverrides manual settings it manages.\u00a77 In Aggressive mode,",
            "\u00a77this includes Upscaler + FXAA — manual GUI changes are reset",
            "\u00a77on the next auto-tick.",
            "",
            "\u00a77Requires Post Pipeline ON.");
        registerTooltip(BTN_TAA_ENABLE,
            "\u00a7eTemporal Anti-Aliasing",
            "\u00a77Smooths edges by blending in detail from previous frames.",
            "\u00a77Static scenes look noticeably cleaner than FXAA alone, with",
            "\u00a77accumulated sub-pixel detail building up over time.",
            "",
            "\u00a77Camera motion is handled by reprojecting the previous frame",
            "\u00a77(no ghost trails from terrain). Moving entities are handled",
            "\u00a77by the Entity MV / Reactive Mask rows below.",
            "",
            "\u00a77Tune the blend strength with TAA Blend in the next row.",
            "",
            "\u00a77Needs Post Pipeline turned on.");
        registerTooltip(BTN_TAA_WEIGHT,
            "\u00a7eTAA History Blend Weight",
            "\u00a770.00 = no history (TAA off effectively).",
            "\u00a7a0.80:\u00a77 balanced, less ghost-prone.",
            "\u00a7e0.90:\u00a77 default, strong temporal smoothing.",
            "\u00a760.95:\u00a77 maximum smoothing, heavy ghosting on motion.",
            "",
            "\u00a77Live-adjustable. Lower values = less ghosting, less AA.");
        registerTooltip(BTN_TAA_ENTITY_MV,
            "\u00a7eEntity Motion Vectors",
            "\u00a77Tracks each entity's on-screen movement frame-to-frame so",
            "\u00a77TAA and FSR2 can reproject it correctly. Result: no ghost",
            "\u00a77trails AND the entity keeps its accumulated detail.",
            "",
            "\u00a77Higher visual quality than Reactive Mask, with a small",
            "\u00a77cost per visible entity each frame. Negligible until you",
            "\u00a77see dozens of mobs at once.",
            "",
            "\u00a77Approximate \u2014 works at bounding-box level rather than",
            "\u00a77per-pixel. Modded entities with custom render paths fall",
            "\u00a77back to Reactive Mask automatically.",
            "",
            "\u00a77Needs TAA + Post Pipeline on. Strongly recommended when",
            "\u00a77using FSR2 so moving entities don't ghost.");
        registerTooltip(BTN_TAA_REACTIVE_MASK,
            "\u00a7eEntity Reactive Mask",
            "\u00a77Drops TAA history weight on entity silhouettes so moving",
            "\u00a77mobs/items don't leave a smear trail. Per-frame entity",
            "\u00a77instability replaces persistent ghosting — visually less",
            "\u00a77objectionable than smear, but you'll see slight shimmer",
            "\u00a77on entity edges during motion.",
            "",
            "\u00a77Costs roughly 5-10% more GPU memory. Particles are",
            "\u00a77intentionally not covered.",
            "",
            "\u00a77Requires TAA + Post Pipeline ON.");
        registerTooltip(BTN_FXAA_QUALITY,
            "\u00a7eFXAA Quality Level",
            "\u00a77Controls search-step count and edge-detection threshold",
            "\u00a77of LDOG's pipeline FXAA.",
            "",
            "\u00a77Low:\u00a77 4 steps, fastest, coarsest.",
            "\u00a7aMedium:\u00a77 6 steps.",
            "\u00a7aHigh:\u00a77 8 steps (default).",
            "\u00a7eUltra:\u00a77 12 steps, refined.",
            "\u00a76Extreme:\u00a77 24 steps, maximum quality, most samples.",
            "",
            "\u00a77Requires Post Pipeline ON — when pipeline is off, MC's",
            "\u00a77fixed-quality FXAA runs instead and this setting is ignored.");
        registerTooltip(BTN_RCAS_ENABLE,
            "\u00a7eRCAS Post-Upscale Sharpen",
            "\u00a77Contrast-adaptive sharpening applied AFTER the upscaler,",
            "\u00a77at native resolution. Complements the upscaler's built-in",
            "\u00a77sharpen — not a replacement.",
            "",
            "\u00a7aWorks at scale 1.0 too:\u00a77 pure sharpening mode, no",
            "\u00a77upscaling. Useful for HD resource packs at native res.",
            "",
            "\u00a77Requires Post Pipeline to be ON.");
        registerTooltip(BTN_RCAS_STRENGTH,
            "\u00a7eRCAS Strength",
            "\u00a770.0 = no sharpening, 1.0 = maximum.",
            "",
            "\u00a7aSweet spot is 0.3 — 0.6.\u00a77 Above 0.8 you'll see halos",
            "\u00a77on high-contrast edges.",
            "",
            "\u00a77Live-adjustable.");
        registerTooltip(BTN_FSR1_SHARPNESS,
            "\u00a7eFSR1 Sharpness",
            "\u00a77Strength of FSR1's edge-enhancement kernel.",
            "",
            "\u00a770.00:\u00a77 no sharpening (matches bilinear).",
            "\u00a7a1.00:\u00a77 moderate — safe on any content.",
            "\u00a7e1.50:\u00a77 aggressive (default) — clear crispness win.",
            "\u00a762.00:\u00a77 very aggressive — may show halos on hard edges.",
            "",
            "\u00a77Live-adjustable. Only applies when Upscaler is FSR1.");

        // -- Phase C4 OF Interop tooltips --
        String[] interopBody = new String[] {
            "\u00a77Per-feature override of who handles this when OptiFine",
            "\u00a77is installed alongside LDOG.",
            "",
            "\u00a78Auto:\u00a77 legacy \u2014 defer to OptiFine. Safe default.",
            "\u00a7aLDOG:\u00a77 reflectively flip OF's matching GameSettings",
            "\u00a77field off, then run LDOG's impl. Falls back to OF",
            "\u00a77silently if the probe can't find the field.",
            "\u00a7eOptiFine:\u00a77 explicitly hand the feature to OF.",
            "",
            "\u00a7cOnly meaningful when OF is detected. No effect otherwise."
        };
        registerTooltip(BTN_OF_MODE_CTM,
            joinTooltip("\u00a7eOF Interop \u2014 Connected Textures", interopBody));
        registerTooltip(BTN_OF_MODE_EMISSIVE,
            joinTooltip("\u00a7eOF Interop \u2014 Emissive Textures", interopBody));
        registerTooltip(BTN_OF_MODE_DYNAMIC_LIGHTS,
            joinTooltip("\u00a7eOF Interop \u2014 Dynamic Lights", interopBody));
        registerTooltip(BTN_OF_MODE_CUSTOM_SKY,
            joinTooltip("\u00a7eOF Interop \u2014 Custom Sky", interopBody));
        registerTooltip(BTN_OF_MODE_HD_TEXTURES,
            joinTooltip("\u00a7eOF Interop \u2014 HD Textures", interopBody));
        registerTooltip(BTN_OF_MODE_SMOOTH_FONT,
            joinTooltip("\u00a7eOF Interop \u2014 Smooth Font", interopBody));
        registerTooltip(BTN_OF_MODE_SHADERS,
            "\u00a7eOF Interop \u2014 Shaders",
            "\u00a77Same Auto / LDOG / OptiFine semantics as the other rows.",
            "",
            "\u00a7cHeads up:\u00a77 LDOG's own shader pack support is still",
            "\u00a77experimental \u2014 it can discover packs but doesn't yet",
            "\u00a77run them. Setting this to LDOG will turn OF's shaders",
            "\u00a77off without anything to replace them. Leave on OptiFine",
            "\u00a77or Auto until LDOG's shader pack support is complete.");

        // ====================================================================
        // Performance section
        // ====================================================================
        registerTooltip(BTN_RENDER_OPTS,
            "\u00a7eRendering Optimizations",
            "\u00a77Master switch for LDOG's chunk-level render speedups:",
            "\u00a77skipping empty chunk sections, particle frustum culling,",
            "\u00a77and the entity / tile-entity distance limits below.",
            "",
            "\u00a7aTurn on by default \u2014 free FPS, no visual cost.");
        registerTooltip(BTN_PARTICLE_CULL,
            "\u00a7eParticle Culling",
            "\u00a77Skips rendering particles outside your view frustum",
            "\u00a77(behind the camera, off-screen). Helps a lot during",
            "\u00a77explosions, mob farms, redstone-dust areas.",
            "",
            "\u00a7aSafe to leave on. No visible change.");
        String[] particleBody = new String[] {
            "\u00a77Toggle off if a particle category is too noisy for you",
            "\u00a77or to claw back FPS in heavy effects. Cancels the",
            "\u00a77particles at spawn, so there's zero per-frame cost",
            "\u00a77when off.",
            "",
            "\u00a77Only filters vanilla particles \u2014 modded particles are",
            "\u00a77intentionally left alone."
        };
        registerTooltip(BTN_PARTICLE_FIREWORK,
            joinTooltip("\u00a7eFirework Particles", particleBody));
        registerTooltip(BTN_PARTICLE_PORTAL,
            joinTooltip("\u00a7ePortal Particles", particleBody));
        registerTooltip(BTN_PARTICLE_POTION,
            joinTooltip("\u00a7ePotion Particles", particleBody));
        registerTooltip(BTN_PARTICLE_WATER,
            joinTooltip("\u00a7eWater Particles", particleBody));
        registerTooltip(BTN_PARTICLE_DRIPPING,
            joinTooltip("\u00a7eDripping Particles", particleBody));
        registerTooltip(BTN_ENTITY_DIST,
            "\u00a7eEntity Render Distance",
            "\u00a77Maximum distance (in blocks) at which entities are drawn.",
            "\u00a77Entities farther than this get skipped entirely \u2014 saves",
            "\u00a77significant FPS in heavily-populated areas (farms, raids).",
            "",
            "\u00a77Vanilla doesn't have a separate cap for this; it just",
            "\u00a77ties everything to render distance. 64 blocks is a sweet",
            "\u00a77spot for most players. 0 = vanilla behavior.");
        registerTooltip(BTN_TE_DIST,
            "\u00a7eTile-Entity Render Distance",
            "\u00a77Maximum distance for tile-entity special renderers \u2014 the",
            "\u00a77custom render code for things like chests, beacons,",
            "\u00a77banners, modded multiblocks.",
            "",
            "\u00a77Modded TESRs can be expensive. Lower this to recover",
            "\u00a77FPS in tech / magic packs. 0 = vanilla.");
        registerTooltip(BTN_ENTITY_LOD,
            "\u00a7eEntity Level of Detail",
            "\u00a77Reduces how often distant entities re-render each frame.",
            "\u00a77Entities 64\u2013128 blocks away render every other frame,",
            "\u00a77past 128 blocks every fourth. Saves vertex transform",
            "\u00a77work in scenes with many entities.",
            "",
            "\u00a77Visible as mild \"stutter\" on distant mobs if too",
            "\u00a77aggressive \u2014 sweet spot for most players is just on.");
        registerTooltip(BTN_PERF_OVERLAY,
            "\u00a7ePerformance Overlay",
            "\u00a77Top-left HUD showing LDOG's per-frame stats: FPS,",
            "\u00a77frame time, memory, culling counts. Cheap to leave on.");

        // ====================================================================
        // FPS Management
        // ====================================================================
        registerTooltip(BTN_FPS_REDUCER,
            "\u00a7eFPS Reducer",
            "\u00a77Caps frame rate when the game is unfocused or you've",
            "\u00a77been AFK. Saves power and stops your fans spinning up",
            "\u00a77when you're not actually playing.",
            "",
            "\u00a7aLeave on. The caps below set the actual values.");
        registerTooltip(BTN_UNFOCUSED_FPS,
            "\u00a7eUnfocused FPS Limit",
            "\u00a77Maximum frame rate when the game window doesn't have",
            "\u00a77focus (you've alt-tabbed to a browser, etc.).",
            "",
            "\u00a7a5\u20131 FPS is fine \u2014 nothing's looking at the screen.");
        registerTooltip(BTN_AFK_TIMEOUT,
            "\u00a7eAFK Timeout",
            "\u00a77Seconds of no mouse/keyboard activity before the AFK",
            "\u00a77frame cap kicks in. 0 = AFK detection off.",
            "",
            "\u00a77Window-focus and AFK are separate \u2014 the AFK cap applies",
            "\u00a77even when the window has focus.");
        registerTooltip(BTN_AFK_FPS,
            "\u00a7eAFK FPS Limit",
            "\u00a77Maximum frame rate while the AFK timer has elapsed.",
            "\u00a7715\u201330 keeps the game responsive enough to react if",
            "\u00a77something happens.");

        // ====================================================================
        // AA / Texture filtering
        // ====================================================================
        registerTooltip(BTN_ANISOTROPIC,
            "\u00a7eAnisotropic Filtering",
            "\u00a77Sharpens textures viewed at glancing angles \u2014 the",
            "\u00a77classic 'tilted floor / distant block face' fuzziness",
            "\u00a77vanilla has. Free quality win on any GPU made after",
            "\u00a77~2008.",
            "",
            "\u00a7eRecommended:\u00a77 also turn on Extended Border Mipmaps,",
            "\u00a77which fixes a subtle block-edge bleed AF can introduce.");
        registerTooltip(BTN_ANISOTROPIC_LEVEL,
            "\u00a7eAnisotropic Level",
            "\u00a77How many extra texture samples to take along the angle",
            "\u00a77of maximum change. Higher = sharper. 16x is the typical",
            "\u00a77maximum supported by hardware.",
            "",
            "\u00a77Clamped to your GPU's reported max if you pick higher.");
        registerTooltip(BTN_MSAA,
            "\u00a7eMSAA \u2014 Multi-Sample Anti-Aliasing",
            "\u00a77Hardware AA that samples each pixel multiple times along",
            "\u00a77geometry edges. Very high quality on solid edges, but",
            "\u00a77doesn't touch alpha-test edges (leaves, fences, grass).",
            "",
            "\u00a7cHeads up:\u00a77 MSAA owns the world render target while it's",
            "\u00a77on \u2014 the Post-Process Pipeline yields to it, so things",
            "\u00a77like FSR upscaling and TAA won't run at the same time.",
            "\u00a77Pick MSAA OR the pipeline-driven AA, not both.");
        registerTooltip(BTN_MSAA_SAMPLES,
            "\u00a7eMSAA Samples",
            "\u00a77Number of samples per pixel: 2x is cheapest, 4x is the",
            "\u00a77sweet spot, 8x is overkill on most hardware.",
            "",
            "\u00a77Clamped to your GPU's max if higher.");
        registerTooltip(BTN_FXAA,
            "\u00a7eFXAA \u2014 Fast Approximate Anti-Aliasing",
            "\u00a77Post-process AA that smooths edges detected from the",
            "\u00a77final image. Cheaper than MSAA and works on alpha-test",
            "\u00a77edges (leaves, fences) that MSAA misses.",
            "",
            "\u00a77When the Post Pipeline is on, LDOG's tunable FXAA with",
            "\u00a77quality levels (see next row) is used. Otherwise MC's",
            "\u00a77built-in fixed-quality FXAA runs.");

        // ====================================================================
        // HDR + Bloom
        // ====================================================================
        registerTooltip(BTN_HDR_PIPELINE,
            "\u00a7eHDR Pipeline",
            "\u00a77Renders the world in High Dynamic Range internally:",
            "\u00a77bright pixels (sun, torches, lava) keep their full",
            "\u00a77luminance instead of being clipped at white. Required",
            "\u00a77for Bloom to look right, and improves tonemap-driven",
            "\u00a77contrast in general.",
            "",
            "\u00a77Costs roughly 2x video memory on the scene target.",
            "\u00a77Needs Post Pipeline turned on.",
            "",
            "\u00a78Note:\u00a77 this is a different feature from the 'HDR" +
                " Tonemap'",
            "\u00a77toggle in Light Customization \u2014 that one shifts the",
            "\u00a77block-light colour curve on the CPU. This is real",
            "\u00a77HDR-format framebuffer rendering on the GPU.");
        registerTooltip(BTN_HDR_TONEMAP,
            "\u00a7eTonemap Operator",
            "\u00a77Maps the HDR scene values back into displayable range:",
            "",
            "\u00a7aACES:\u00a77 cinematic, slight S-curve. Best default.",
            "\u00a7aReinhard:\u00a77 soft rolloff, no clipping, flatter look.",
            "\u00a7aUncharted 2:\u00a77 punchy highlights, John Hable's curve.",
            "\u00a77Linear:\u00a77 no tonemap, just clip. Useful for debugging.",
            "",
            "\u00a77Only consumed when HDR Pipeline is on.");
        registerTooltip(BTN_HDR_EXPOSURE,
            "\u00a7eExposure",
            "\u00a77Brightness multiplier applied before tonemapping.",
            "\u00a771.0 is neutral. Higher pushes more pixels into the",
            "\u00a77highlight roll-off (and into Bloom), lower darkens.",
            "",
            "\u00a77Only consumed when HDR Pipeline is on.");
        registerTooltip(BTN_BLOOM_ENABLE,
            "\u00a7eBloom",
            "\u00a77Soft glow around bright pixels \u2014 sun, torches, lava,",
            "\u00a77fire, glowstone, etc. Adds a noticeable cinematic feel",
            "\u00a77at the cost of one half-res blur pass per frame.",
            "",
            "\u00a7eRequires HDR Pipeline ON.\u00a77 Without HDR, the scene is",
            "\u00a77already clipped to white before the bloom shader sees",
            "\u00a77it \u2014 nothing to extract.");
        registerTooltip(BTN_BLOOM_THRESHOLD,
            "\u00a7eBloom Threshold",
            "\u00a77Brightness floor for a pixel to contribute to bloom.",
            "\u00a771.0 = only HDR values above LDR range bloom (clean,",
            "\u00a77focused glow). Lower bleeds mid-tones too \u2014 dreamier",
            "\u00a77look, can wash out detail.");
        registerTooltip(BTN_BLOOM_INTENSITY,
            "\u00a7eBloom Strength",
            "\u00a77How strongly the blurred bloom layer composites over",
            "\u00a77the scene.",
            "",
            "\u00a7a0.6\u20131.0 is the typical sweet spot.\u00a77 2.0+ gets",
            "\u00a77aggressively dreamy.");

        // ====================================================================
        // Atmosphere
        // ====================================================================
        registerTooltip(BTN_CLOUD_HEIGHT,
            "\u00a7eCloud Height",
            "\u00a77Overrides vanilla's per-dimension cloud altitude.",
            "\u00a77Useful for skybox builds (push clouds below your build",
            "\u00a77limit) or cinematic shots (lift them above).",
            "",
            "\u00a77\"Default\" leaves the dimension's own value alone.");
        registerTooltip(BTN_FOG_DISTANCE,
            "\u00a7eFog Distance",
            "\u00a77Multiplier on the start/end fog distances. Lower =",
            "\u00a77closer fog (more atmospheric, less view distance).",
            "\u00a77Higher = farther fog (more visibility, less mood).",
            "",
            "\u00a77Applied to both start and end so the gradient keeps",
            "\u00a77its shape.");
        registerTooltip(BTN_SUN_SIZE,
            "\u00a7eSun Size",
            "\u00a77Visual scale of the sun disc. 1.0 = vanilla. Cinematic",
            "\u00a77shots love 2.0x. No gameplay effect \u2014 just visuals.");
        registerTooltip(BTN_MOON_SIZE,
            "\u00a7eMoon Size",
            "\u00a77Visual scale of the moon disc. 1.0 = vanilla.");
        registerTooltip(BTN_WEATHER_RENDER,
            "\u00a7eRender Weather",
            "\u00a77Off skips the rain/snow visual particles entirely.",
            "\u00a77Gameplay weather still works (mobs get wet, fires get",
            "\u00a77put out, etc.) \u2014 only the visual effect is suppressed.",
            "",
            "\u00a7aBig FPS win in heavy storms on weaker hardware.");
        registerTooltip(BTN_WEATHER_DENSITY,
            "\u00a7eWeather Density",
            "\u00a77Multiplier on rain/snow particle density around the",
            "\u00a77player. 1.0 = vanilla. 0.5 = roughly a quarter of the",
            "\u00a77particles (density scales by the square of radius).",
            "",
            "\u00a77Lower for FPS, higher for cinematic.");
        registerTooltip(BTN_BIOME_BLEND,
            "\u00a7eBiome Blend",
            "\u00a77How smoothly grass / foliage / water colours blend",
            "\u00a77across biome borders.",
            "",
            "\u00a771:\u00a77 vanilla (3x3 sample average).",
            "\u00a7a2:\u00a77 25-block radius. Visibly smoother transitions.",
            "\u00a7e3:\u00a77 49-block radius. Very smooth, but adds noticeable",
            "\u00a77chunk-rebuild cost \u2014 only use on a strong CPU.");

        // ====================================================================
        // Comfort / Cinematic / QoL / Hide HUD
        // ====================================================================
        registerTooltip(BTN_NO_DAMAGE_TILT,
            "\u00a7eDisable Damage Tilt",
            "\u00a77Stops the camera from tilting when you take damage.");
        registerTooltip(BTN_NO_HURT_VIGNETTE,
            "\u00a7eDisable Hurt Vignette",
            "\u00a77Stops the red screen-edge flash when damaged.",
            "",
            "\u00a7cAlso disables the worldborder vignette \u2014 vanilla draws",
            "\u00a77both in the same code, can't separate them.");
        registerTooltip(BTN_HIDE_HAND,
            "\u00a7eHide Hand",
            "\u00a77Hides your held items / arm in first-person view.",
            "\u00a77Great for cinematic shots.");
        registerTooltip(BTN_FULLBRIGHT,
            "\u00a7eFullbright",
            "\u00a77Forces the lightmap to maximum brightness everywhere.",
            "\u00a77Caves and night-time look fully lit.",
            "",
            "\u00a77Overrides Light Customization when both are on.");
        registerTooltip(BTN_HIDE_CROSSHAIR,
            "\u00a7eHide Crosshair",
            "\u00a77Hides the crosshair / attack indicator. Useful for",
            "\u00a77screenshots and cinematic recordings.");
        registerTooltip(BTN_HIDE_HOTBAR,
            "\u00a7eHide Hotbar",
            "\u00a77Hides just the hotbar. F1 hides the whole HUD; this",
            "\u00a77lets you keep everything else.");
        registerTooltip(BTN_HIDE_EXP,
            "\u00a7eHide XP Bar",
            "\u00a77Hides the experience bar + level number.");
        registerTooltip(BTN_HIDE_JUMP,
            "\u00a7eHide Horse Jump Bar",
            "\u00a77Hides the jump-charge bar shown while riding a mount.");
        registerTooltip(BTN_HIDE_TOOLTIP,
            "\u00a7eHide Held Item Tooltip",
            "\u00a77Hides the floating item name that appears when you",
            "\u00a77switch hotbar slots.");
        registerTooltip(BTN_NO_PORTAL_OVERLAY,
            "\u00a7eNo Portal Distortion",
            "\u00a77Hides the purple swirl overlay while standing in a",
            "\u00a77nether portal.",
            "",
            "\u00a77Doesn't affect the gameplay portal timer \u2014 you'll",
            "\u00a77still travel to the nether after the usual delay.");
        registerTooltip(BTN_HIDE_ARMOR,
            "\u00a7eHide Armor Bar",
            "\u00a77Hides the armor row above the hotbar.");
        registerTooltip(BTN_HIDE_HUNGER,
            "\u00a7eHide Hunger Bar",
            "\u00a77Hides the food shanks above the hotbar.");
        registerTooltip(BTN_HIDE_AIR,
            "\u00a7eHide Air Bar",
            "\u00a77Hides the breath bubbles shown when underwater.");
        registerTooltip(BTN_HIDE_BOSS,
            "\u00a7eHide Boss Health",
            "\u00a77Hides boss health bars at the top of the screen",
            "\u00a77(Wither, Ender Dragon, modded bosses).");
        registerTooltip(BTN_ADV_TOOLTIPS,
            "\u00a7eAdvanced Item Tooltips Always",
            "\u00a77Keeps the F3+H breakdown (durability, NBT, lore IDs)",
            "\u00a77visible on item hover without having to hold F3+H.");
        registerTooltip(BTN_NO_NAUSEA,
            "\u00a7eDisable Nausea Distortion",
            "\u00a77Stops the screen from swirling when under the Nausea",
            "\u00a77effect or briefly when entering a portal.",
            "",
            "\u00a77Gameplay isn't affected \u2014 just the visual swirl.");
        registerTooltip(BTN_HUD_PING,
            "\u00a7eShow Ping",
            "\u00a77Adds your server ping to the Info HUD. Multiplayer",
            "\u00a77only \u2014 hides itself in singleplayer.");
        registerTooltip(BTN_HUD_DAY,
            "\u00a7eShow Day Counter",
            "\u00a77Adds the current in-game day number to the Info HUD.");
        registerTooltip(BTN_HUD_CPS,
            "\u00a7eShow CPS",
            "\u00a77Adds a clicks-per-second counter (left + right mouse)",
            "\u00a77to the Info HUD. Rolling 1-second window.");

        // ====================================================================
        // Visual / Water / Pack features / Lighting
        // ====================================================================
        registerTooltip(BTN_CLEAR_WATER,
            "\u00a7eClear Water",
            "\u00a77Removes vanilla's murky underwater overlay and lets you",
            "\u00a77tune water transparency and tint below.",
            "",
            "\u00a77Replaces the standalone Clear Water mod.");
        registerTooltip(BTN_WATER_OPACITY,
            "\u00a7eWater Opacity",
            "\u00a770.0 = fully transparent, 1.0 = vanilla murkiness,",
            "\u00a77higher = even murkier. Affects both the surface and",
            "\u00a77the underwater fog.");
        registerTooltip(BTN_WATER_TINT,
            "\u00a7eWater Tint",
            "\u00a77When on, multiplies the biome water colour by the",
            "\u00a77R/G/B sliders below. Lets you swing biome water toward",
            "\u00a77more blue / green / your preference.");
        registerTooltip(BTN_WATER_PRESET,
            "\u00a7eWater Preset",
            "\u00a77One-click colour bundle for clear water + tint. Cycles",
            "\u00a77through curated looks (default, tropical, swampy, etc.).",
            "\u00a77Editing any individual water control flips to Custom.");
        registerTooltip(BTN_BETTER_GRASS,
            "\u00a7eBetter Grass",
            "\u00a77Replaces the grass side texture with the top texture",
            "\u00a77on grass / mycelium blocks.",
            "",
            "\u00a77Off:\u00a77 vanilla side textures.",
            "\u00a77Fast:\u00a77 always show the top texture on sides.",
            "\u00a7aFancy:\u00a77 only when the neighbour below is also grass.");
        registerTooltip(BTN_BETTER_SNOW,
            "\u00a7eBetter Snow",
            "\u00a77When a snow layer sits on top of an opaque block,",
            "\u00a77renders snow-textured sides on the block beneath \u2014 the",
            "\u00a77snow looks like it's wrapping the block edges instead",
            "\u00a77of just sitting on top.");
        registerTooltip(BTN_NATURAL_TEXTURES,
            "\u00a7eNatural Textures",
            "\u00a77Randomly rotates / flips block textures so identical",
            "\u00a77adjacent blocks (dirt, sand, stone) don't form obvious",
            "\u00a77tile patterns.",
            "",
            "\u00a77Uses your resource pack's optifine/natural.properties",
            "\u00a77if one is present, falls back to sensible defaults.");
        registerTooltip(BTN_CUSTOM_COLORS,
            "\u00a7eCustom Colors",
            "\u00a77Reads grass/foliage colormaps and color.properties from",
            "\u00a77your resource pack \u2014 lets packs override the colours of",
            "\u00a77grass, leaves, redstone, water per biome, potions, dyes,",
            "\u00a77and more.");
        registerTooltip(BTN_RANDOM_MOBS,
            "\u00a7eRandom Entity Textures",
            "\u00a77Picks one of multiple texture variants per mob based",
            "\u00a77on the mob's UUID. Resource packs ship variants in",
            "\u00a77optifine/random/entity/ \u2014 try the Faithful or Affinity",
            "\u00a77HD packs for examples.");
        registerTooltip(BTN_DYNAMIC_LIGHTS,
            "\u00a7eDynamic Lights",
            "\u00a77Held torches, lava buckets, glowstone, etc. light up",
            "\u00a77the area around you and any other entity carrying them.",
            "\u00a77Dropped items light their surroundings too.",
            "",
            "\u00a77Replaces the standalone Dynamic Lights mod.");
        registerTooltip(BTN_DYN_LIGHT_INTERVAL,
            "\u00a7eDynamic Lights Update Interval",
            "\u00a77How often the dynamic-light scan runs.",
            "",
            "\u00a7aSmooth:\u00a77 every render frame \u2014 best quality, highest cost.",
            "\u00a77Fast:\u00a77 every game tick (20 Hz) \u2014 good balance.",
            "\u00a77N ticks:\u00a77 every N game ticks \u2014 cheap, mildly choppy.");
        registerTooltip(BTN_LIGHT_TEMP,
            "\u00a7eLight Customization",
            "\u00a77Master switch for LDOG's lightmap tweaks: warm/cool",
            "\u00a77light tints, brightness boost, night darkness, and",
            "\u00a77HDR tonemap on the lightmap itself.",
            "",
            "\u00a77This is CPU lightmap manipulation, different from the",
            "\u00a77HDR Pipeline framebuffer mode in the Post-Process",
            "\u00a77section.");
        registerTooltip(BTN_LIGHT_TEMP_PRESET,
            "\u00a7eLighting Preset",
            "\u00a77One-click bundle: cinematic, candlelight, moonlit,",
            "\u00a77dark nights, horror, neon, etc. Each preset sets the",
            "\u00a77tint, brightness, darkness, and tonemap values together.",
            "",
            "\u00a77Editing any individual control flips to Custom.");
        String[] tintBody = new String[] {
            "\u00a77Multiplier on the matching channel for the light source.",
            "\u00a771.0 = unchanged. Lower = less of that colour, higher = more.",
            "",
            "\u00a77Warm torches = high R, low G, low B.",
            "\u00a77Cool moonlight = low R, normal G, high B."
        };
        registerTooltip(BTN_BLOCK_LIGHT_R,
            joinTooltip("\u00a7eBlock Light \u2014 Red", tintBody));
        registerTooltip(BTN_BLOCK_LIGHT_G,
            joinTooltip("\u00a7eBlock Light \u2014 Green", tintBody));
        registerTooltip(BTN_BLOCK_LIGHT_B,
            joinTooltip("\u00a7eBlock Light \u2014 Blue", tintBody));
        registerTooltip(BTN_SKY_LIGHT_R,
            joinTooltip("\u00a7eSky Light \u2014 Red", tintBody));
        registerTooltip(BTN_SKY_LIGHT_G,
            joinTooltip("\u00a7eSky Light \u2014 Green", tintBody));
        registerTooltip(BTN_SKY_LIGHT_B,
            joinTooltip("\u00a7eSky Light \u2014 Blue", tintBody));
        registerTooltip(BTN_BRIGHTNESS_BOOST,
            "\u00a7eBrightness Boost",
            "\u00a77Additive shift to the whole lightmap.",
            "\u00a77-1.0 = pitch black, 0.0 = vanilla, 1.0 = washed out.",
            "",
            "\u00a77Apply before Night Darkness so night still gets dark.");
        registerTooltip(BTN_NIGHT_DARKNESS,
            "\u00a7eNight Darkness",
            "\u00a77Multiplier on the sky-light side of the lightmap.",
            "\u00a771.0 = vanilla, higher = darker nights. 100 = pitch black",
            "\u00a77at midnight (torches still work normally).",
            "",
            "\u00a77Better than vanilla's gamma slider \u2014 this can't be",
            "\u00a77counteracted by the user cranking gamma in MC settings.");
        registerTooltip(BTN_HDR,
            "\u00a7eHDR Lightmap Tonemap",
            "\u00a77Applies an ACES filmic curve to the lightmap before",
            "\u00a77it's uploaded. Punchier highlights, deeper shadows,",
            "\u00a77more cinematic feel.",
            "",
            "\u00a77This is the lightmap-only HDR (CPU). The HDR Pipeline",
            "\u00a77toggle in Post-Process is the full HDR-framebuffer",
            "\u00a77feature \u2014 they stack cleanly.");

        // ====================================================================
        // Pack-feature toggles (gated by OF detection in the GUI)
        // ====================================================================
        registerTooltip(BTN_CTM,
            "\u00a7eConnected Textures (CTM)",
            "\u00a77Glass panes, bookshelves, sandstone, and other CTM-",
            "\u00a77aware blocks visually connect across edges based on",
            "\u00a77your resource pack's connected-textures definitions.",
            "",
            "\u00a77Reads OptiFine and MCPatcher format CTM packs.",
            "\u00a77Auto-disabled when OptiFine is detected (use the OF",
            "\u00a77Interop section to override).");
        registerTooltip(BTN_EMISSIVE,
            "\u00a7eEmissive Textures",
            "\u00a77Glow overlays on blocks and items shipped by your",
            "\u00a77resource pack (e.g. emissive ores, redstone-lit metal).",
            "",
            "\u00a77Reads OptiFine and MCPatcher emissive textures",
            "\u00a77(_e.png suffix). Auto-disabled when OF is detected.");
        registerTooltip(BTN_CUSTOM_SKY,
            "\u00a7eCustom Sky",
            "\u00a77Resource-pack-driven skybox layers with time-based",
            "\u00a77fades \u2014 nebulas, stars, custom moons, etc.",
            "",
            "\u00a77Reads OptiFine and MCPatcher sky definitions. Auto-",
            "\u00a77disabled when OF is detected.");
        registerTooltip(BTN_HD_TEXTURES,
            "\u00a7eHD Textures",
            "\u00a77Removes vanilla's hard 16x16 atlas-sprite limit so HD",
            "\u00a77resource packs (32x, 64x, 128x, 256x+) load without",
            "\u00a77crashing or being downsampled.",
            "",
            "\u00a77Auto-disabled when OF is detected.");
        registerTooltip(BTN_SHADERS,
            "\u00a7eShaders",
            "\u00a77Master switch for LDOG's shader pack support. When on,",
            "\u00a77the Pack picker below appears \u2014 it scans",
            "\u00a77\u00a7ashaderpacks/\u00a77 for .zip or directory packs.",
            "",
            "\u00a7eExperimental:\u00a77 LDOG can discover and select packs",
            "\u00a77today, but doesn't yet run gbuffer / composite stages.",
            "\u00a77Activating a pack is log-only until that work lands.",
            "",
            "\u00a77Auto-disabled when OF is detected.");
        registerTooltip(BTN_SHADER_PACK,
            "\u00a7eShader Pack",
            "\u00a77Cycles through packs found in \u00a7ashaderpacks/\u00a77. Drop",
            "\u00a77OptiFine or Iris-format pack .zip files (or extracted",
            "\u00a77folders) into that directory to see them here.",
            "",
            "\u00a77\"(none)\" deactivates the active pack.");
        registerTooltip(BTN_SHADER_RESCAN,
            "\u00a7eRescan Shader Packs",
            "\u00a77Force a fresh scan of \u00a7ashaderpacks/\u00a77 \u2014 use after",
            "\u00a77dropping a new pack in without restarting MC.");

        // ====================================================================
        // Font polish (the rows added late)
        // ====================================================================
        registerTooltip(BTN_TTF_BOLD,
            "\u00a7eTTF Bold",
            "\u00a77Requests a bold face from the selected TTF font.",
            "\u00a77Only consumed when TTF Font is on.",
            "",
            "\u00a77Triggers a font reload on toggle.");
        registerTooltip(BTN_TTF_ITALIC,
            "\u00a7eTTF Italic",
            "\u00a77Requests an italic face from the selected TTF font.",
            "\u00a77Only consumed when TTF Font is on.");
        registerTooltip(BTN_TTF_SUBPIXEL,
            "\u00a7eLCD Subpixel Rendering",
            "\u00a77Uses the GPU's RGB subpixel layout to triple the",
            "\u00a77effective horizontal resolution of TTF glyphs.",
            "\u00a77Noticeably sharper edges on horizontal-RGB LCDs.",
            "",
            "\u00a7cCan cause colored fringing\u00a77 on rotated panels or",
            "\u00a77atypical subpixel layouts \u2014 turn off if you see",
            "\u00a77red/blue tinting at glyph edges.");
    }

    /** Helper for OF Interop tooltips: prepend a per-feature title to a shared body. */
    private static String[] joinTooltip(String title, String... body) {
        String[] out = new String[body.length + 1];
        out[0] = title;
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }

    private void saveAndClose() {
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        if (waterSettingsChanged && this.mc.renderGlobal != null) {
            // Water opacity/tint is baked into chunk vertex data — must rebuild.
            this.mc.renderGlobal.loadRenderers();
        }
        if (extBorderSettingsChanged || fontSettingsChanged) {
            // Packing or font-discovery changes — requires a full resource reload
            // so Stitcher/FontRenderer pick up the new inputs. Subsumes AF refresh
            // (TextureStitchEvent.Post re-runs) and font-filter refresh.
            this.mc.refreshResources();
        } else {
            if (aaSettingsChanged) {
                com.limitlessdev.ldog.texture.AnisotropicFilteringHandler.refreshMainAtlas();
            }
            if (fontFilterChanged) {
                com.limitlessdev.ldog.render.font.SmoothFontHandler.INSTANCE.refreshAntialiasFilter();
            }
        }
        if (fxaaSettingsChanged) {
            com.limitlessdev.ldog.render.fxaa.FXAAHandler.apply();
        }
        this.mc.displayGuiScreen(this.parentScreen);
    }

    // ---- Label helpers ----

    static String toggleLabel(String name, boolean value) {
        return name + ": " + (value ? "\u00a7aON" : "\u00a7cOFF");
    }

    static String distLabel(String name, int value) {
        return name + ": " + (value == 0 ? "\u00a77Vanilla" : "\u00a7a" + value);
    }

    static String valLabel(String name, int value) {
        return name + ": \u00a7a" + value;
    }

    static String opacityLabel(String name, double value) {
        return name + ": \u00a7a" + String.format("%.0f%%", value * 100);
    }

    static String afkTimeoutLabel(int seconds) {
        if (seconds == 0) return "AFK Timeout: \u00a7cDisabled";
        if (seconds < 60) return "AFK Timeout: \u00a7a" + seconds + "s";
        return "AFK Timeout: \u00a7a" + (seconds / 60) + "m";
    }

    static String tintLabel(String channel, double value, String colorCode) {
        return channel + ": " + colorCode + String.format("%.1f", value);
    }

    static String dynLightIntervalLabel(int value) {
        if (value == 0) return "Speed: \u00a7aSmooth (per frame)";
        if (value == 1) return "Speed: \u00a7aFast (every tick)";
        return "Speed: \u00a7e" + value + " ticks";
    }

    static String featureLabel(String name, boolean enabled, boolean ldogHandles) {
        if (!ldogHandles) return name + ": \u00a77OptiFine";
        return name + ": " + (enabled ? "\u00a7aON" : "\u00a7cOFF");
    }

    private static GuiButton makeFeatureButton(int id, int w, int h,
                                                String name, boolean enabled, boolean ldogHandles) {
        GuiButton btn = new GuiButton(id, 0, 0, w, h, featureLabel(name, enabled, ldogHandles));
        if (!ldogHandles) {
            btn.enabled = false;
        }
        return btn;
    }

    /**
     * Phase C4: builds an OF interop cycle button. Greyed when the bridge
     * couldn't resolve a write target for the feature (per-OF-version field
     * naming differences), so the user understands why LDOG_OVERRIDE wouldn't
     * stick if they tried to set it.
     */
    private static GuiButton ofInteropButton(int id, int w, int h,
                                              com.limitlessdev.ldog.compat.OFFeature feature,
                                              String currentMode) {
        com.limitlessdev.ldog.compat.OFOverrideMode mode =
            com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(currentMode);
        boolean controllable = com.limitlessdev.ldog.compat.OFConfigBridge.canControl(feature);
        String label = ofInteropLabel(feature, mode, controllable);
        GuiButton btn = new GuiButton(id, 0, 0, w, h, label);
        // Don't disable when uncontrollable — user can still pick auto/optifine,
        // just LDOG_OVERRIDE will fall back. Visual hint via the label colour.
        return btn;
    }

    static String ofInteropLabel(com.limitlessdev.ldog.compat.OFFeature feature,
                                  com.limitlessdev.ldog.compat.OFOverrideMode mode,
                                  boolean controllable) {
        String modeColour;
        switch (mode) {
            case AUTO:              modeColour = "\u00a77"; break;  // grey
            case LDOG_OVERRIDE:     modeColour = controllable ? "\u00a7a" : "\u00a7c"; break;  // green if works, red if can't control
            case OPTIFINE_OVERRIDE: modeColour = "\u00a7e"; break;  // yellow
            default:                modeColour = "\u00a77"; break;
        }
        return feature.displayName() + ": " + modeColour + mode.displayName();
    }

    /** Cycle an OF interop mode string: auto → ldog → optifine → auto. */
    static String cycleOFMode(String current) {
        return com.limitlessdev.ldog.compat.OFOverrideMode.fromConfigKey(current).next().configKey();
    }

    static int cycleValue(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    static double cycleValue(double[] values, double current) {
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.01) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    static String cycleStringValue(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    static String afLabel(int level) {
        return "AF Level: \u00a7a" + level + "x";
    }

    static String msaaLabel(int samples) {
        return "Samples: \u00a7a" + samples + "x";
    }

    static String pipelineScaleLabel(double scale) {
        String color = Math.abs(scale - 1.0) < 0.01 ? "\u00a77" : "\u00a7e";
        return "Render Scale: " + color + String.format("%.2fx", scale);
    }

    static String upscalerLabel(com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm alg) {
        return "Upscaler: \u00a7a" + alg.displayName();
    }

    static String autoScaleLabel(com.limitlessdev.ldog.render.pipeline.AutoScaleMode mode) {
        String color;
        switch (mode) {
            case OFF:        color = "\u00a77"; break;  // grey
            case NORMAL:     color = "\u00a7a"; break;  // green
            case AGGRESSIVE: color = "\u00a76"; break;  // gold — owns more settings, louder warning colour
            default:         color = "\u00a77"; break;
        }
        return "Auto Scale: " + color + mode.displayName();
    }

    static String ldogPresetLabel(com.limitlessdev.ldog.config.LDOGPreset preset) {
        String color;
        switch (preset) {
            case CUSTOM:      color = "\u00a77"; break;
            case VANILLA:     color = "\u00a7f"; break;
            case PERFORMANCE: color = "\u00a7b"; break;
            case DEFAULT:     color = "\u00a7a"; break;
            case FANCY:       color = "\u00a7e"; break;
            case ULTRA:       color = "\u00a76"; break;
            default:          color = "\u00a77"; break;
        }
        return "Preset: " + color + preset.displayName();
    }

    static String tonemapLabel(String op) {
        String pretty;
        switch (op == null ? "" : op.toLowerCase()) {
            case "aces":       pretty = "ACES";       break;
            case "reinhard":   pretty = "Reinhard";   break;
            case "uncharted2": pretty = "Uncharted2"; break;
            case "linear":     pretty = "Linear";     break;
            default:           pretty = op;           break;
        }
        return "Tonemap: §a" + pretty;
    }

    static String taaWeightLabel(double w) {
        String color;
        if (w < 0.01) color = "\u00a77";
        else if (w <= 0.8) color = "\u00a7a";
        else if (w <= 0.9) color = "\u00a7e";
        else color = "\u00a76";
        return "TAA Blend: " + color + String.format("%.2f", w);
    }

    static String fxaaQualityLabel(com.limitlessdev.ldog.render.pipeline.FXAAQuality q) {
        String color;
        switch (q) {
            case LOW:     color = "\u00a77"; break;
            case MEDIUM:  color = "\u00a7a"; break;
            case HIGH:    color = "\u00a7a"; break;
            case ULTRA:   color = "\u00a7e"; break;
            case EXTREME: color = "\u00a76"; break;
            default:      color = "\u00a77"; break;
        }
        return "FXAA Qual: " + color + q.displayName();
    }

    static String rcasStrengthLabel(double v) {
        String color;
        if (v < 0.01) color = "\u00a77";
        else if (v <= 0.4) color = "\u00a7a";
        else if (v <= 0.6) color = "\u00a7e";
        else color = "\u00a76";
        return "RCAS Str: " + color + String.format("%.2f", v);
    }

    static String vignetteIntensityLabel(double v) {
        String color;
        if (v <= 0.3) color = "\u00a7a";       // green — subtle
        else if (v <= 0.6) color = "\u00a7e";  // yellow — moderate
        else color = "\u00a76";                 // gold — heavy / cinematic
        return "Vignette Str: " + color + String.format("%.2f", v);
    }

    static String cloudHeightLabel(int y) {
        if (y < 0) return "Cloud Height: \u00a77Vanilla";
        return "Cloud Height: \u00a7e" + y;
    }

    /** Generic "Name: 1.00x" multiplier label, green at 1.0, yellow off-default. */
    static String multLabel(String name, double v) {
        String color = Math.abs(v - 1.0) < 0.01 ? "\u00a77" : "\u00a7e";
        return name + ": " + color + String.format("%.2fx", v);
    }

    static String biomeBlendLabel(int r) {
        String color;
        switch (r) {
            case 1:  color = "\u00a77"; break;  // grey — vanilla
            case 2:  color = "\u00a7a"; break;  // green — improvement
            case 3:  color = "\u00a76"; break;  // gold — heavy
            default: color = "\u00a77"; break;
        }
        int side = 2 * r + 1;
        return "Biome Blend: " + color + side + "x" + side;
    }

    static String upscalerPresetLabel(com.limitlessdev.ldog.render.pipeline.UpscalerPreset preset) {
        String color;
        switch (preset) {
            case CUSTOM:      color = "\u00a77"; break;
            case NATIVE:      color = "\u00a7b"; break;
            case ULTRA:       color = "\u00a7a"; break;
            case QUALITY:     color = "\u00a7a"; break;
            case BALANCED:    color = "\u00a7e"; break;
            case PERFORMANCE: color = "\u00a76"; break;
            default:          color = "\u00a77"; break;
        }
        return "Preset: " + color + preset.displayName();
    }

    /** After a preset is applied, refresh scale/upscaler/sharpness labels. */
    private void refreshIndividualButtons() {
        GuiButton scale = settingsList.findButton(BTN_PIPELINE_SCALE);
        if (scale != null) scale.displayString = pipelineScaleLabel(LDOGConfig.internalRenderScale);
        GuiButton upscaler = settingsList.findButton(BTN_PIPELINE_UPSCALER);
        if (upscaler != null) upscaler.displayString =
            upscalerLabel(com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm.selected());
        GuiButton sharp = settingsList.findButton(BTN_FSR1_SHARPNESS);
        if (sharp != null) sharp.displayString = fsr1SharpnessLabel(LDOGConfig.fsr1Sharpness);
    }

    /** After an individual control changes, refresh the preset label (usually to Custom). */
    private void refreshPresetButton() {
        GuiButton preset = settingsList.findButton(BTN_UPSCALER_PRESET);
        if (preset != null) preset.displayString =
            upscalerPresetLabel(com.limitlessdev.ldog.render.pipeline.UpscalerPreset.selected());
    }

    static String fsr1SharpnessLabel(double value) {
        String color;
        if (value < 0.01) color = "\u00a77";           // gray — effectively off
        else if (value <= 1.0) color = "\u00a7a";      // green — safe range
        else if (value <= 1.5) color = "\u00a7e";      // yellow — default/aggressive
        else color = "\u00a76";                         // gold — very aggressive
        return "Sharpness: " + color + String.format("%.2f", value);
    }

    static String betterGrassLabel(String mode) {
        switch (mode.toLowerCase()) {
            case "off":   return "Better Grass: \u00a7cOFF";
            case "fast":  return "Better Grass: \u00a7aFast";
            case "fancy": return "Better Grass: \u00a7aFancy";
            default:      return "Better Grass: \u00a77" + mode;
        }
    }

    static String fontAALabel(String mode) {
        switch (mode == null ? "" : mode.toLowerCase()) {
            case "off":       return "Font AA: \u00a7cOff";
            case "bilinear":  return "Font AA: \u00a7eBilinear";
            case "trilinear": return "Font AA: \u00a7aTrilinear";
            default:          return "Font AA: \u00a77" + mode;
        }
    }

    /** Highlights user-dropped fonts in yellow so custom vs built-in is visible. */
    static String fontFamilyLabel(String family) {
        boolean isCustom = com.limitlessdev.ldog.render.font.TTFFontCatalog.isCustomFamily(family);
        String color = isCustom ? "\u00a7e" : "\u00a7a";
        return "Family: " + color + family;
    }

    // ---- Water preset helpers ----

    private String presetLabel() {
        if (currentPresetIndex < 0) return "Preset: \u00a77Custom";
        return "Preset: \u00a7a" + WATER_PRESETS[currentPresetIndex][0];
    }

    private int detectCurrentPreset() {
        for (int i = 0; i < WATER_PRESETS.length; i++) {
            double opacity = (Double) WATER_PRESETS[i][1];
            double red     = (Double) WATER_PRESETS[i][2];
            double green   = (Double) WATER_PRESETS[i][3];
            double blue    = (Double) WATER_PRESETS[i][4];
            if (Math.abs(LDOGConfig.waterOpacity   - opacity) < 0.01 &&
                Math.abs(LDOGConfig.waterTintRed   - red)    < 0.01 &&
                Math.abs(LDOGConfig.waterTintGreen - green)  < 0.01 &&
                Math.abs(LDOGConfig.waterTintBlue  - blue)   < 0.01) {
                return i;
            }
        }
        return -1;
    }

    private void applyNextPreset() {
        currentPresetIndex = (currentPresetIndex + 1) % WATER_PRESETS.length;
        Object[] preset = WATER_PRESETS[currentPresetIndex];
        LDOGConfig.waterOpacity   = (Double) preset[1];
        LDOGConfig.waterTintRed   = (Double) preset[2];
        LDOGConfig.waterTintGreen = (Double) preset[3];
        LDOGConfig.waterTintBlue  = (Double) preset[4];
        // All presets except Vanilla enable clear water + tint
        boolean isVanilla = "Vanilla".equals(preset[0]);
        LDOGConfig.enableClearWater = !isVanilla;
        LDOGConfig.enableWaterTint  = !isVanilla;
    }

    private void refreshWaterButtons() {
        for (int i = 0; i < settingsList.getSize(); i++) {
            net.minecraft.client.gui.GuiListExtended.IGuiListEntry entry = settingsList.getListEntry(i);
            if (!(entry instanceof GuiLDOGSettingsList.ButtonRowEntry)) continue;
            GuiLDOGSettingsList.ButtonRowEntry row = (GuiLDOGSettingsList.ButtonRowEntry) entry;
            refreshButton(row.getLeftButton());
            refreshButton(row.getRightButton());
        }
    }

    /**
     * Auto-enable light customization when any individual option is changed,
     * and refresh the toggle button + mark preset as custom.
     */
    private void autoEnableLightCustomization() {
        currentLightPresetIndex = -1;
        if (!LDOGConfig.enableLightTemperature) {
            LDOGConfig.enableLightTemperature = true;
            refreshLightButtons();
        }
    }

    // ---- Light preset helpers ----

    private String lightTempPresetLabel() {
        if (currentLightPresetIndex < 0) return "Preset: \u00a77Custom";
        String name = LIGHT_TEMP_PRESETS[currentLightPresetIndex];
        String display = name.substring(0, 1).toUpperCase() + name.substring(1).replace('_', ' ');
        return "Preset: \u00a7a" + display;
    }

    static String brightnessLabel(double value) {
        if (value == 0) return "Brightness: \u00a77Default";
        if (value > 0) return "Brightness: \u00a7a+" + String.format("%.1f", value);
        return "Brightness: \u00a7c" + String.format("%.1f", value);
    }

    static String nightDarknessLabel(double value) {
        if (Math.abs(value - 1.0) < 0.01) return "Night Dark: \u00a77Vanilla";
        if (value < 1.0) return "Night Dark: \u00a7a" + String.format("%.1f", value) + "x (brighter)";
        if (value >= 100) return "Night Dark: \u00a74Pitch Black";
        if (value >= 5) return "Night Dark: \u00a7c" + String.format("%.0f", value) + "x (extreme)";
        return "Night Dark: \u00a7c" + String.format("%.1f", value) + "x";
    }

    private void applyNextLightPreset() {
        currentLightPresetIndex = (currentLightPresetIndex + 1) % LIGHT_TEMP_PRESETS.length;
        LightTemperaturePreset preset = LightTemperaturePreset.fromConfig(
            LIGHT_TEMP_PRESETS[currentLightPresetIndex]);
        LDOGConfig.blockLightRed = preset.blockR;
        LDOGConfig.blockLightGreen = preset.blockG;
        LDOGConfig.blockLightBlue = preset.blockB;
        LDOGConfig.skyLightRed = preset.skyR;
        LDOGConfig.skyLightGreen = preset.skyG;
        LDOGConfig.skyLightBlue = preset.skyB;
        LDOGConfig.lightBrightnessBoost = preset.brightnessBoost;
        LDOGConfig.nightDarkness = preset.nightDarkness;
        LDOGConfig.enableHDR = preset.hdr;
        LDOGConfig.enableLightTemperature = true;
    }

    private void refreshLightButtons() {
        for (int i = 0; i < settingsList.getSize(); i++) {
            net.minecraft.client.gui.GuiListExtended.IGuiListEntry entry = settingsList.getListEntry(i);
            if (!(entry instanceof GuiLDOGSettingsList.ButtonRowEntry)) continue;
            GuiLDOGSettingsList.ButtonRowEntry row = (GuiLDOGSettingsList.ButtonRowEntry) entry;
            refreshLightButton(row.getLeftButton());
            refreshLightButton(row.getRightButton());
        }
    }

    private void refreshLightButton(GuiButton btn) {
        if (btn == null) return;
        switch (btn.id) {
            case BTN_LIGHT_TEMP:
                btn.displayString = toggleLabel("Light Customization", LDOGConfig.enableLightTemperature); break;
            case BTN_LIGHT_TEMP_PRESET:
                btn.displayString = lightTempPresetLabel(); break;
            case BTN_BLOCK_LIGHT_R:
                btn.displayString = tintLabel("Block Red", LDOGConfig.blockLightRed, "\u00a7c"); break;
            case BTN_BLOCK_LIGHT_G:
                btn.displayString = tintLabel("Block Green", LDOGConfig.blockLightGreen, "\u00a7a"); break;
            case BTN_BLOCK_LIGHT_B:
                btn.displayString = tintLabel("Block Blue", LDOGConfig.blockLightBlue, "\u00a79"); break;
            case BTN_SKY_LIGHT_R:
                btn.displayString = tintLabel("Sky Red", LDOGConfig.skyLightRed, "\u00a7c"); break;
            case BTN_SKY_LIGHT_G:
                btn.displayString = tintLabel("Sky Green", LDOGConfig.skyLightGreen, "\u00a7a"); break;
            case BTN_SKY_LIGHT_B:
                btn.displayString = tintLabel("Sky Blue", LDOGConfig.skyLightBlue, "\u00a79"); break;
            case BTN_BRIGHTNESS_BOOST:
                btn.displayString = brightnessLabel(LDOGConfig.lightBrightnessBoost); break;
            case BTN_NIGHT_DARKNESS:
                btn.displayString = nightDarknessLabel(LDOGConfig.nightDarkness); break;
            case BTN_HDR:
                btn.displayString = toggleLabel("HDR Tonemapping", LDOGConfig.enableHDR); break;
        }
    }

    private void refreshButton(GuiButton btn) {
        if (btn == null) return;
        switch (btn.id) {
            case BTN_CLEAR_WATER:
                btn.displayString = toggleLabel("Clear Water", LDOGConfig.enableClearWater); break;
            case BTN_WATER_PRESET:
                btn.displayString = presetLabel(); break;
            case BTN_WATER_OPACITY:
                btn.displayString = opacityLabel("Water Opacity", LDOGConfig.waterOpacity); break;
            case BTN_WATER_TINT:
                btn.displayString = toggleLabel("Water Tint", LDOGConfig.enableWaterTint); break;
            case BTN_WATER_RED:
                btn.displayString = tintLabel("Red", LDOGConfig.waterTintRed, "\u00a7c"); break;
            case BTN_WATER_GREEN:
                btn.displayString = tintLabel("Green", LDOGConfig.waterTintGreen, "\u00a7a"); break;
            case BTN_WATER_BLUE:
                btn.displayString = tintLabel("Blue", LDOGConfig.waterTintBlue, "\u00a79"); break;
        }
    }
}
