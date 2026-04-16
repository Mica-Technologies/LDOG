package com.limitlessdev.ldog.render.dynamiclights;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined lighting presets. Each preset configures:
 * - Block light RGB tint (torch/lamp color)
 * - Sky light RGB tint (sun/moon color)
 * - Brightness boost (-1 to +1)
 * - Night darkness multiplier (1.0 = vanilla, >1 = darker nights, <1 = brighter)
 * - HDR tonemapping flag
 *
 * All values applied to the lightmap texture once per frame — zero per-block cost.
 */
public enum LightTemperaturePreset {

    NEUTRAL       ("neutral",
        1.00f, 1.00f, 1.00f,   // block light: neutral
        1.00f, 1.00f, 1.00f,   // sky light: neutral
        0.0f, 1.0f, false),

    WARM_TORCHES  ("warm_torches",
        1.25f, 1.00f, 0.70f,   // block light: warm amber
        1.00f, 1.00f, 1.00f,   // sky light: neutral
        0.0f, 1.0f, false),

    CINEMATIC     ("cinematic",
        1.25f, 1.00f, 0.70f,   // block light: warm amber
        0.80f, 0.88f, 1.25f,   // sky light: cool blue
        0.0f, 1.0f, true),

    CANDLELIGHT   ("candlelight",
        1.35f, 0.90f, 0.55f,   // block light: deep amber
        0.90f, 0.92f, 1.05f,   // sky light: slightly cool
        0.0f, 1.0f, false),

    MOONLIT       ("moonlit",
        1.10f, 1.00f, 0.85f,   // block light: slightly warm
        0.70f, 0.80f, 1.35f,   // sky light: strong cool blue
        0.0f, 1.0f, false),

    DARK_NIGHTS   ("dark_nights",
        1.10f, 1.00f, 0.90f,   // block light: slightly warm
        0.85f, 0.90f, 1.10f,   // sky light: slightly cool
        0.0f, 1.8f, false),    // nights 80% darker

    HORROR        ("horror",
        1.15f, 0.85f, 0.70f,   // block light: sickly warm
        0.70f, 0.75f, 0.85f,   // sky light: desaturated
        -0.3f, 2.0f, false),   // darker overall, very dark nights

    BRIGHT_CAVES  ("bright_caves",
        1.05f, 1.02f, 0.95f,   // block light: slightly warm
        1.00f, 1.00f, 1.00f,   // sky light: neutral
        0.4f, 1.0f, false),    // brightness boost

    VIVID         ("vivid",
        1.20f, 0.95f, 0.70f,   // block light: warm
        0.85f, 0.95f, 1.20f,   // sky light: cool
        0.1f, 1.0f, true),     // slight brightness + HDR

    FLUORESCENT   ("fluorescent",
        0.85f, 1.05f, 1.25f,   // block light: cool white/blue
        1.00f, 1.00f, 1.00f,   // sky light: neutral
        0.0f, 1.0f, false),

    PURPLE_HAZE   ("purple_haze",
        1.20f, 0.70f, 1.30f,   // block light: purple
        0.90f, 0.75f, 1.20f,   // sky light: purple-ish
        0.0f, 1.0f, false),

    NEON_BLUE     ("neon_blue",
        0.65f, 0.90f, 1.40f,   // block light: blue
        0.75f, 0.90f, 1.30f,   // sky light: blue
        0.0f, 1.0f, false),

    RED_ALERT     ("red_alert",
        1.40f, 0.60f, 0.55f,   // block light: red
        1.20f, 0.70f, 0.70f,   // sky light: reddish
        -0.1f, 1.3f, false);

    private static final Map<String, LightTemperaturePreset> BY_NAME = new HashMap<>();

    static {
        for (LightTemperaturePreset preset : values()) {
            BY_NAME.put(preset.configName, preset);
        }
    }

    public final String configName;

    // Block light (torches, lamps) RGB multipliers
    public final float blockR, blockG, blockB;
    // Sky light (sun, moon) RGB multipliers
    public final float skyR, skyG, skyB;
    // Overall brightness boost (-1.0 to +1.0)
    public final float brightnessBoost;
    // Night darkness multiplier (1.0 = vanilla, >1 = darker, <1 = brighter nights)
    public final float nightDarkness;
    // Apply pseudo-HDR tonemapping
    public final boolean hdr;

    LightTemperaturePreset(String configName,
                            float blockR, float blockG, float blockB,
                            float skyR, float skyG, float skyB,
                            float brightnessBoost, float nightDarkness, boolean hdr) {
        this.configName = configName;
        this.blockR = blockR; this.blockG = blockG; this.blockB = blockB;
        this.skyR = skyR; this.skyG = skyG; this.skyB = skyB;
        this.brightnessBoost = brightnessBoost;
        this.nightDarkness = nightDarkness;
        this.hdr = hdr;
    }

    public static LightTemperaturePreset fromConfig(String name) {
        LightTemperaturePreset preset = BY_NAME.get(name.toLowerCase().trim());
        return preset != null ? preset : NEUTRAL;
    }
}
