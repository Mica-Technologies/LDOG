package com.limitlessdev.ldog.compat;

import com.limitlessdev.ldog.LDOGMod;

/**
 * Detects OptiFine at runtime and tracks which features should be deferred to it.
 * When OptiFine is present, LDOG disables its own implementations of overlapping
 * features to avoid conflicts, letting OptiFine handle those while LDOG handles
 * features it does better or that OptiFine doesn't cover.
 */
public final class OptiFineCompat {

    private static boolean optiFineDetected = false;
    private static boolean detectionDone = false;

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
                LDOGMod.LOGGER.info("OptiFine detected -- LDOG will disable overlapping features");
            } catch (ClassNotFoundException e) {
                optiFineDetected = false;
                LDOGMod.LOGGER.info("OptiFine not detected -- LDOG running all features");
            }
            detectionDone = true;
        }
        return optiFineDetected;
    }

    /**
     * Returns true if LDOG should handle connected textures.
     * Defers to OptiFine when present, since OptiFine's CTM is tightly coupled
     * to its renderer and would conflict with a second CTM implementation.
     */
    public static boolean shouldHandleCTM() {
        return !isOptiFineLoaded();
    }

    /**
     * Returns true if LDOG should handle emissive textures.
     * Defers to OptiFine when present.
     */
    public static boolean shouldHandleEmissive() {
        return !isOptiFineLoaded();
    }

    /**
     * Returns true if LDOG should handle dynamic lights.
     * Defers to OptiFine when present.
     */
    public static boolean shouldHandleDynamicLights() {
        return !isOptiFineLoaded();
    }

    /**
     * Returns true if LDOG should handle shaders.
     * Defers to OptiFine when present (OptiFine's shader pipeline is deeply integrated).
     */
    public static boolean shouldHandleShaders() {
        return !isOptiFineLoaded();
    }

    /**
     * Returns true if LDOG should handle custom sky rendering.
     * Defers to OptiFine when present.
     */
    public static boolean shouldHandleCustomSky() {
        return !isOptiFineLoaded();
    }

    /**
     * Returns true if LDOG should handle HD textures.
     * Defers to OptiFine when present.
     */
    public static boolean shouldHandleHDTextures() {
        return !isOptiFineLoaded();
    }

    /**
     * Returns true if LDOG should handle render optimizations.
     * This is the one area where LDOG may run ALONGSIDE OptiFine,
     * since non-conflicting optimizations can coexist. Individual
     * optimization mixins should check for specific conflicts.
     */
    public static boolean shouldHandleRenderOptimizations() {
        return true;
    }
}
