package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.config.LDOGConfig;

/**
 * Quality preset bundle — a single-click way to set render scale +
 * upscaler + sharpness together without tuning each individually.
 *
 * {@link #CUSTOM} is the "user is driving the individual controls" state.
 * When any of the three underlying fields is edited directly, the preset
 * should flip back to CUSTOM so the GUI truthfully reflects that it no
 * longer corresponds to any named bundle.
 */
public enum UpscalerPreset {

    CUSTOM     ("custom",      "Custom",      0.0, null,                          0.0),
    NATIVE     ("native",      "Native",      1.00, UpscalerAlgorithm.BILINEAR,   0.00),
    ULTRA      ("ultra",       "Ultra",       0.85, UpscalerAlgorithm.FSR1_QUALITY, 0.75),
    QUALITY    ("quality",     "Quality",     0.75, UpscalerAlgorithm.FSR1_QUALITY, 1.00),
    BALANCED   ("balanced",    "Balanced",    0.67, UpscalerAlgorithm.FSR1,       1.25),
    PERFORMANCE("performance", "Performance", 0.50, UpscalerAlgorithm.FSR1,       1.50);

    private final String configKey;
    private final String displayName;
    private final double scale;
    private final UpscalerAlgorithm algorithm;
    private final double sharpness;

    UpscalerPreset(String configKey, String displayName,
                   double scale, UpscalerAlgorithm algorithm, double sharpness) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.scale = scale;
        this.algorithm = algorithm;
        this.sharpness = sharpness;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }
    public double scale() { return scale; }
    public UpscalerAlgorithm algorithm() { return algorithm; }
    public double sharpness() { return sharpness; }

    /** Write this preset's bundled values into LDOGConfig. No-op for CUSTOM. */
    public void apply() {
        if (this == CUSTOM) return;
        LDOGConfig.internalRenderScale = scale;
        LDOGConfig.upscalerAlgorithm = algorithm.configKey();
        LDOGConfig.fsr1Sharpness = sharpness;
        LDOGConfig.upscalerPreset = configKey;
    }

    public static UpscalerPreset selected() {
        String key = LDOGConfig.upscalerPreset;
        if (key == null) return CUSTOM;
        for (UpscalerPreset p : values()) {
            if (p.configKey.equalsIgnoreCase(key)) return p;
        }
        return CUSTOM;
    }

    /**
     * Flip the saved preset to CUSTOM. Called when the user touches any of
     * the individual controls (scale, upscaler, sharpness) so the GUI label
     * doesn't keep claiming a preset that no longer matches reality.
     */
    public static void markCustom() {
        LDOGConfig.upscalerPreset = CUSTOM.configKey;
    }
}
