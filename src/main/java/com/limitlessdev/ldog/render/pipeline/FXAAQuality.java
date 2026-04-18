package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.config.LDOGConfig;

/**
 * FXAA quality preset. Controls the two main FXAA knobs:
 *
 *   - {@link #searchSteps()}: how far along a detected edge the shader traces
 *     to find endpoints. More steps = better sub-pixel edge position estimation
 *     = smoother diagonals and curves. Cost scales roughly linearly.
 *
 *   - {@link #edgeThreshold()}: minimum local luma contrast required to treat
 *     a pixel as on an edge. Lower threshold catches more subtle edges at the
 *     cost of occasionally blurring near-flat gradients.
 *
 * Values chosen to roughly match NVIDIA's canonical FXAA 3.11 quality bands
 * (LOW / MEDIUM / HIGH / ULTRA / EXTREME). LDOG-original mapping — not a port.
 */
public enum FXAAQuality {

    LOW     ("low",     "Low",      4,  0.200f),
    MEDIUM  ("medium",  "Medium",   6,  0.125f),
    HIGH    ("high",    "High",     8,  0.100f),
    ULTRA   ("ultra",   "Ultra",    12, 0.080f),
    EXTREME ("extreme", "Extreme",  24, 0.063f);

    private final String configKey;
    private final String displayName;
    private final int searchSteps;
    private final float edgeThreshold;

    FXAAQuality(String configKey, String displayName, int searchSteps, float edgeThreshold) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.searchSteps = searchSteps;
        this.edgeThreshold = edgeThreshold;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }
    public int searchSteps() { return searchSteps; }
    public float edgeThreshold() { return edgeThreshold; }

    public static FXAAQuality selected() {
        String key = LDOGConfig.fxaaQuality;
        if (key == null) return HIGH;
        for (FXAAQuality q : values()) {
            if (q.configKey.equalsIgnoreCase(key)) return q;
        }
        return HIGH;
    }
}
