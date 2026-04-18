package com.limitlessdev.ldog.compat;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;

import java.util.EnumMap;
import java.util.Map;

/**
 * Detects OptiFine at runtime and decides — per overlapping feature — whether
 * LDOG or OptiFine should handle it.
 *
 * <h3>Phase C4 — three-state per-feature mode</h3>
 *
 * Each {@link OFFeature} has its own {@link OFOverrideMode} config entry:
 * <ul>
 *   <li><b>AUTO</b> (default): legacy 1:1 behavior — defer to OF when loaded.</li>
 *   <li><b>LDOG_OVERRIDE</b>: try to disable OF's version via {@link OFConfigBridge};
 *       if successful, LDOG handles. If the disable write fails (field missing,
 *       OF version mismatch), falls back to OPTIFINE_OVERRIDE so we don't end
 *       up with both systems active.</li>
 *   <li><b>OPTIFINE_OVERRIDE</b>: explicitly defer to OF, even on edge cases
 *       where LDOG would otherwise take over.</li>
 * </ul>
 *
 * <h3>Override results are cached</h3>
 *
 * The actual disable-OF write happens once per session per feature (on the
 * first {@code shouldHandleX()} call after a config change). The cached
 * decision avoids repeated reflection calls in the per-frame hot path.
 * Cache resets whenever the user changes a mode in the GUI (handled by
 * {@link #invalidateCache()} called from the settings save path).
 */
public final class OptiFineCompat {

    private static boolean optiFineDetected = false;
    private static boolean detectionDone = false;

    /**
     * Cached "does LDOG handle this feature?" decision per feature. Built
     * lazily by {@link #shouldHandle(OFFeature)} so the first frame after
     * a mode change pays the reflection cost; subsequent frames are O(1).
     */
    private static final Map<OFFeature, Boolean> decisionCache = new EnumMap<>(OFFeature.class);

    private OptiFineCompat() {}

    /**
     * Checks whether OptiFine is loaded. Result is cached after first call.
     */
    public static boolean isOptiFineLoaded() {
        if (!detectionDone) {
            try {
                Class.forName("optifine.OptiFineForgeTweaker", false,
                    OptiFineCompat.class.getClassLoader());
                optiFineDetected = true;
                LDOGMod.LOGGER.info("OptiFine detected — LDOG will consult per-feature override modes");
            } catch (ClassNotFoundException e) {
                optiFineDetected = false;
                LDOGMod.LOGGER.info("OptiFine not detected — LDOG running all features");
            }
            detectionDone = true;
        }
        return optiFineDetected;
    }

    /**
     * Drop the per-feature decision cache. Call from the settings-save path
     * after the user changes any OF interop mode so the next frame's
     * {@link #shouldHandle(OFFeature)} re-evaluates against the new config.
     */
    public static void invalidateCache() {
        decisionCache.clear();
    }

    /**
     * Core decision: should LDOG handle this feature this session?
     *
     * <pre>
     *   OF not loaded     → true  (LDOG handles, no contention)
     *   AUTO              → false (defer to OF)
     *   OPTIFINE_OVERRIDE → false (defer to OF — explicit user choice)
     *   LDOG_OVERRIDE     → try to disable OF; success → true; failure → false
     * </pre>
     */
    public static boolean shouldHandle(OFFeature feature) {
        Boolean cached = decisionCache.get(feature);
        if (cached != null) return cached;

        boolean decision = computeDecision(feature);
        decisionCache.put(feature, decision);
        return decision;
    }

    private static boolean computeDecision(OFFeature feature) {
        if (!isOptiFineLoaded()) return true;

        OFOverrideMode mode = OFOverrideMode.fromConfigKey(modeFor(feature));
        switch (mode) {
            case OPTIFINE_OVERRIDE:
            case AUTO:
                return false;
            case LDOG_OVERRIDE: {
                boolean ok = OFConfigBridge.setOptiFineFeatureEnabled(feature, false);
                if (!ok) {
                    LDOGMod.LOGGER.warn(
                        "LDOG: LDOG_OVERRIDE for {} failed (field unmapped or write error) — " +
                        "falling back to OPTIFINE_OVERRIDE for this session",
                        feature.displayName());
                    return false;
                }
                LDOGMod.LOGGER.info(
                    "LDOG: LDOG_OVERRIDE active for {} — disabled OF's version, LDOG handles",
                    feature.displayName());
                return true;
            }
            default:
                return false;
        }
    }

    /** Map a feature to its config-stored mode string. */
    private static String modeFor(OFFeature feature) {
        switch (feature) {
            case CONNECTED_TEXTURES: return LDOGConfig.ofModeCTM;
            case EMISSIVE_TEXTURES:  return LDOGConfig.ofModeEmissive;
            case DYNAMIC_LIGHTS:     return LDOGConfig.ofModeDynamicLights;
            case CUSTOM_SKY:         return LDOGConfig.ofModeCustomSky;
            case HD_TEXTURES:        return LDOGConfig.ofModeHDTextures;
            case SMOOTH_FONT:        return LDOGConfig.ofModeSmoothFont;
            case SHADERS:            return LDOGConfig.ofModeShaders;
            default:                 return OFOverrideMode.AUTO.configKey();
        }
    }

    // ===== Legacy per-feature accessors (preserved for existing callers) =====
    // Each delegates to shouldHandle(OFFeature.X) so callers don't change.

    public static boolean shouldHandleCTM()             { return shouldHandle(OFFeature.CONNECTED_TEXTURES); }
    public static boolean shouldHandleEmissive()        { return shouldHandle(OFFeature.EMISSIVE_TEXTURES); }
    public static boolean shouldHandleDynamicLights()   { return shouldHandle(OFFeature.DYNAMIC_LIGHTS); }
    public static boolean shouldHandleShaders()         { return shouldHandle(OFFeature.SHADERS); }
    public static boolean shouldHandleCustomSky()       { return shouldHandle(OFFeature.CUSTOM_SKY); }
    public static boolean shouldHandleHDTextures()      { return shouldHandle(OFFeature.HD_TEXTURES); }
    public static boolean shouldHandleSmoothFont()      { return shouldHandle(OFFeature.SMOOTH_FONT); }

    /**
     * Render optimizations are unmanaged by C4 — they coexist with OF safely
     * (entity LOD, particle culling, etc. don't touch OF's render path). Kept
     * always-true; individual optimization mixins still check for specific
     * conflicts as needed.
     */
    public static boolean shouldHandleRenderOptimizations() {
        return true;
    }
}
