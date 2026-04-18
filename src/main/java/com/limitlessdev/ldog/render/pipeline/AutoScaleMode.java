package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.config.LDOGConfig;

/**
 * Operating mode for the auto-scale handler. Three states cycled by the
 * GUI button: OFF → NORMAL → AGGRESSIVE → OFF.
 *
 * <ul>
 *   <li><b>OFF</b>: handler skips all adjustments. User's manual scale
 *       value is fully respected.</li>
 *   <li><b>NORMAL</b>: 9a.9 baseline. Handler adjusts only
 *       {@link LDOGConfig#internalRenderScale} across the 5-tier ladder
 *       (1.00 → 0.85 → 0.75 → 0.67 → 0.50).</li>
 *   <li><b>AGGRESSIVE</b>: 9a.9 ext. Handler additionally manages
 *       upscaler algorithm + FXAA quality + FXAA on/off across a 7-tier
 *       extended ladder. At the bottom of the scale ladder the upscaler
 *       drops from FSR1-Quality → FSR1 → Bilinear and FXAA quality drops
 *       Ultra → High → Medium → Low → off, squeezing more frames out of
 *       low-end hardware before users see a hard floor.</li>
 * </ul>
 *
 * String-backed so config files survive enum rename / addition. Unknown
 * values fall back to OFF (safe default — never auto-changes user state).
 *
 * Naming pattern mirrors {@link UpscalerAlgorithm} and {@link FXAAQuality}
 * for consistency with the rest of the pipeline config surface.
 */
public enum AutoScaleMode {

    OFF        ("off",        "Off"),
    NORMAL     ("normal",     "Normal"),
    AGGRESSIVE ("aggressive", "Aggressive");

    private final String configKey;
    private final String displayName;

    AutoScaleMode(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }

    public static AutoScaleMode selected() {
        String key = LDOGConfig.autoScaleMode;
        if (key == null) return OFF;
        for (AutoScaleMode m : values()) {
            if (m.configKey.equalsIgnoreCase(key)) return m;
        }
        return OFF;
    }

    /** Cycle to the next mode in display order; wraps OFF → NORMAL → AGGRESSIVE → OFF. */
    public AutoScaleMode next() {
        AutoScaleMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
