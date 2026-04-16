package com.limitlessdev.ldog.render.dynamiclights;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined light color temperature presets. Each preset defines RGB
 * multipliers applied to the lightmap, shifting all lighting toward
 * a specific color temperature.
 *
 * Multipliers are centered on 1.0 (unchanged). Values > 1.0 boost
 * a channel, < 1.0 reduce it.
 */
public enum LightTemperaturePreset {

    // Realistic temperatures
    NEUTRAL      ("neutral",      1.00f, 1.00f, 1.00f),
    WARM_WHITE   ("warm_white",   1.08f, 1.02f, 0.90f),
    CANDLELIGHT  ("candlelight",  1.15f, 0.95f, 0.75f),
    SUNLIGHT     ("sunlight",     1.05f, 1.03f, 0.95f),
    FLUORESCENT  ("fluorescent",  0.95f, 1.05f, 1.10f),
    MOONLIGHT    ("moonlight",    0.88f, 0.92f, 1.12f),
    OVERCAST     ("overcast",     0.95f, 0.97f, 1.05f),

    // Fun / creative
    PURPLE_HAZE  ("purple_haze",  1.10f, 0.85f, 1.15f),
    NEON_BLUE    ("neon_blue",    0.80f, 0.95f, 1.20f),
    RED_ALERT    ("red_alert",    1.20f, 0.80f, 0.80f);

    private static final Map<String, LightTemperaturePreset> BY_NAME = new HashMap<>();

    static {
        for (LightTemperaturePreset preset : values()) {
            BY_NAME.put(preset.configName, preset);
        }
    }

    public final String configName;
    public final float red;
    public final float green;
    public final float blue;

    LightTemperaturePreset(String configName, float red, float green, float blue) {
        this.configName = configName;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public static LightTemperaturePreset fromConfig(String name) {
        LightTemperaturePreset preset = BY_NAME.get(name.toLowerCase().trim());
        return preset != null ? preset : NEUTRAL;
    }
}
