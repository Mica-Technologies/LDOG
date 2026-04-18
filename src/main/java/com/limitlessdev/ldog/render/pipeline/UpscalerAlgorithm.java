package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.config.LDOGConfig;

/**
 * Registry of available final-stage upscalers for the post-process pipeline.
 *
 * Kept as a string-backed enum-like so config files survive the addition of
 * new algorithms (unknown values fall back to BILINEAR). Add new entries
 * here as they ship; each upscaler pass reads {@link #selected()} to decide
 * whether it is the active one this frame.
 */
public enum UpscalerAlgorithm {

    /** GL_LINEAR blit via glBlitFramebuffer. Cheap, blurry at low scales. */
    BILINEAR("bilinear", "Bilinear"),

    /** FSR1-style edge-adaptive spatial upsampling. Sharper than bilinear. */
    FSR1("fsr1", "FSR1"),

    /**
     * FSR1 with direction-biased EASU sampling: Sobel edge detection +
     * anisotropic taps along the detected edge, plus contrast-adaptive
     * sharpen on top. Noticeably crisper on diagonal geometry compared to
     * the plain FSR1 unsharp-mask, at the cost of ~8 extra texture fetches
     * per pixel. Still cheap enough to enable at any render scale.
     */
    FSR1_QUALITY("fsr1_quality", "FSR1-Quality");

    private final String configKey;
    private final String displayName;

    UpscalerAlgorithm(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }

    /**
     * Resolve the currently selected algorithm from config. Unknown values
     * fall back to {@link #BILINEAR} so a typo or a removed algorithm never
     * leaves the pipeline with no upscaler active.
     */
    public static UpscalerAlgorithm selected() {
        String key = LDOGConfig.upscalerAlgorithm;
        if (key == null) return BILINEAR;
        for (UpscalerAlgorithm a : values()) {
            if (a.configKey.equalsIgnoreCase(key)) return a;
        }
        return BILINEAR;
    }
}
