package com.limitlessdev.ldog.config;

import com.limitlessdev.ldog.render.pipeline.UpscalerPreset;

/**
 * Global LDOG config presets — whole-feature-set bundles that a user can
 * pick with one click instead of toggling 20+ options individually.
 *
 * Unlike {@link UpscalerPreset} (which auto-flips to CUSTOM whenever any
 * of its three underlying fields is edited), LDOGPreset does NOT auto-flip.
 * The stored preset name reflects the last explicit pick; users are free
 * to tweak individual settings without the label reverting. This is
 * appropriate for a coarser-grained control — users expect to pick a
 * preset and then make minor personal adjustments on top.
 *
 * What's NOT in any preset (intentionally left for per-user choice):
 *   - Font settings (family, AA mode, TTF source — all user-specific).
 *   - Water tint colors, light-temperature colors (aesthetic choices).
 *   - FPS-reducer thresholds (personal comfort).
 *   - Performance overlay toggle (UI preference).
 *   - Borderless fullscreen + Block-FS-Optim (platform-specific).
 */
public enum LDOGPreset {

    CUSTOM     ("custom",      "Custom"),
    VANILLA    ("vanilla",     "Vanilla"),      // LDOG features disabled; closest to vanilla MC look
    PERFORMANCE("performance", "Performance"),  // visuals off, perf optimizations max, upscaling on
    DEFAULT    ("default",     "Default"),      // LDOG's opinionated first-install defaults
    FANCY      ("fancy",       "Fancy"),        // all visuals on, MSAA 4x + FXAA, Quality upscale
    ULTRA      ("ultra",       "Ultra");        // everything max: MSAA 8x, Ultra upscale, RCAS on

    private final String configKey;
    private final String displayName;

    LDOGPreset(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }

    public void apply() {
        if (this == CUSTOM) return;
        switch (this) {
            case VANILLA:     applyVanilla();     break;
            case PERFORMANCE: applyPerformance(); break;
            case DEFAULT:     applyDefault();     break;
            case FANCY:       applyFancy();       break;
            case ULTRA:       applyUltra();       break;
            default: break;
        }
        LDOGConfig.globalPreset = configKey;
    }

    public static LDOGPreset selected() {
        String key = LDOGConfig.globalPreset;
        if (key == null) return CUSTOM;
        for (LDOGPreset p : values()) {
            if (p.configKey.equalsIgnoreCase(key)) return p;
        }
        return CUSTOM;
    }

    // ---- Preset implementations ----

    /** Strips LDOG visual features. Keeps perf optimizations (they match vanilla visuals). */
    private static void applyVanilla() {
        LDOGConfig.enableConnectedTextures = false;
        LDOGConfig.enableEmissiveTextures = false;
        LDOGConfig.enableDynamicLights = false;
        LDOGConfig.enableCustomSky = false;
        LDOGConfig.enableHDTextures = false;
        LDOGConfig.enableClearWater = false;
        LDOGConfig.enableWaterTint = false;
        LDOGConfig.betterGrass = "off";
        LDOGConfig.enableBetterSnow = false;
        LDOGConfig.enableNaturalTextures = false;
        LDOGConfig.enableCustomColors = false;
        LDOGConfig.enableRandomEntityTextures = false;
        LDOGConfig.enableLightTemperature = false;
        LDOGConfig.enableHDR = false;
        LDOGConfig.enableAnisotropicFiltering = false;
        LDOGConfig.enableExtendedBorderMipmaps = false;
        LDOGConfig.enableMSAA = false;
        LDOGConfig.enableFXAA = false;
        LDOGConfig.enablePostProcessPipeline = false;
        LDOGConfig.enableRcasSharpen = false;
        // Perf optimizations: leave chunk-render opts ON (invisible perf win),
        // but disable the LOD/culling tweaks that subtly change behavior.
        LDOGConfig.enableRenderOptimizations = true;
        LDOGConfig.enableParticleCulling = false;
        LDOGConfig.enableEntityLOD = false;
    }

    /** Max perf: visuals off, optimizations + upscaling to scale 0.5. */
    private static void applyPerformance() {
        LDOGConfig.enableConnectedTextures = false;
        LDOGConfig.enableEmissiveTextures = false;
        LDOGConfig.enableDynamicLights = false;
        LDOGConfig.enableCustomSky = false;
        LDOGConfig.enableHDTextures = false;
        LDOGConfig.enableClearWater = false;
        LDOGConfig.enableWaterTint = false;
        LDOGConfig.betterGrass = "off";
        LDOGConfig.enableBetterSnow = false;
        LDOGConfig.enableNaturalTextures = false;
        LDOGConfig.enableCustomColors = false;
        LDOGConfig.enableRandomEntityTextures = false;
        LDOGConfig.enableLightTemperature = false;
        LDOGConfig.enableHDR = false;
        LDOGConfig.enableAnisotropicFiltering = false;
        LDOGConfig.enableExtendedBorderMipmaps = false;
        LDOGConfig.enableMSAA = false;
        LDOGConfig.enableFXAA = false;
        LDOGConfig.enableRenderOptimizations = true;
        LDOGConfig.enableParticleCulling = true;
        LDOGConfig.enableEntityLOD = true;
        LDOGConfig.entityRenderDistance = 48;
        LDOGConfig.tileEntityRenderDistance = 48;
        LDOGConfig.enablePostProcessPipeline = true;
        UpscalerPreset.PERFORMANCE.apply();
        LDOGConfig.enableRcasSharpen = false;
    }

    /** LDOG's opinionated defaults as of first install. */
    private static void applyDefault() {
        LDOGConfig.enableConnectedTextures = true;
        LDOGConfig.enableEmissiveTextures = true;
        LDOGConfig.enableDynamicLights = true;
        LDOGConfig.enableCustomSky = true;
        LDOGConfig.enableHDTextures = true;
        LDOGConfig.enableClearWater = true;
        LDOGConfig.enableWaterTint = false;
        LDOGConfig.betterGrass = "fancy";
        LDOGConfig.enableBetterSnow = true;
        LDOGConfig.enableNaturalTextures = true;
        LDOGConfig.enableCustomColors = true;
        LDOGConfig.enableRandomEntityTextures = true;
        LDOGConfig.enableLightTemperature = false;
        LDOGConfig.enableHDR = true;
        LDOGConfig.enableAnisotropicFiltering = true;
        LDOGConfig.anisotropicLevel = 8;
        LDOGConfig.enableExtendedBorderMipmaps = true;
        LDOGConfig.enableMSAA = false;
        LDOGConfig.msaaSamples = 4;
        LDOGConfig.enableFXAA = false;
        LDOGConfig.enablePostProcessPipeline = false;
        LDOGConfig.enableRcasSharpen = false;
        LDOGConfig.enableRenderOptimizations = true;
        LDOGConfig.enableParticleCulling = true;
        LDOGConfig.enableEntityLOD = true;
        LDOGConfig.entityRenderDistance = 64;
        LDOGConfig.tileEntityRenderDistance = 64;
    }

    /**
     * All visuals on, upscaling-focused AA (FXAA + FSR1-Quality + RCAS).
     *
     * MSAA is explicitly OFF here even though "fancy" might suggest
     * maximum AA — MSAA and the post-process pipeline conflict. MSAA wraps
     * renderWorldPass with its own multisampled FBO swap, so
     * PostProcessPipeline.hasConflictingFeatureOn() makes the binding hook
     * yield when MSAA is on, effectively disabling the upscaler. Since the
     * Fancy preset's whole point is the upscaling + sharpening look, we
     * commit to that path here and leave pure-MSAA users on Custom.
     */
    private static void applyFancy() {
        applyDefault();
        LDOGConfig.enableMSAA = false;
        LDOGConfig.enableFXAA = true;
        LDOGConfig.enablePostProcessPipeline = true;
        UpscalerPreset.QUALITY.apply();
        LDOGConfig.enableRcasSharpen = true;
        LDOGConfig.rcasSharpness = 0.3;
    }

    /** Everything upscaling-oriented: AF 16x, FXAA, Ultra upscale, RCAS. MSAA off (see Fancy). */
    private static void applyUltra() {
        applyDefault();
        LDOGConfig.enableMSAA = false;
        LDOGConfig.enableFXAA = true;
        LDOGConfig.anisotropicLevel = 16;
        LDOGConfig.enablePostProcessPipeline = true;
        UpscalerPreset.ULTRA.apply();
        LDOGConfig.enableRcasSharpen = true;
        LDOGConfig.rcasSharpness = 0.4;
    }
}
