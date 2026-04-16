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
    WARM_WHITE   ("warm_white",   1.15f, 1.03f, 0.80f),
    CANDLELIGHT  ("candlelight",  1.30f, 0.90f, 0.55f),
    SUNLIGHT     ("sunlight",     1.10f, 1.05f, 0.88f),
    FLUORESCENT  ("fluorescent",  0.85f, 1.05f, 1.25f),
    MOONLIGHT    ("moonlight",    0.75f, 0.85f, 1.30f),
    OVERCAST     ("overcast",     0.90f, 0.93f, 1.10f),

    // Fun / creative
    PURPLE_HAZE  ("purple_haze",  1.20f, 0.70f, 1.30f),
    NEON_BLUE    ("neon_blue",    0.65f, 0.90f, 1.40f),
    RED_ALERT    ("red_alert",    1.40f, 0.65f, 0.65f);

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
