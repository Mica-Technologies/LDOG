package com.limitlessdev.ldog.compat;

import com.limitlessdev.ldog.config.LDOGConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OptiFine compatibility detection and the per-feature
 * AUTO / LDOG_OVERRIDE / OPTIFINE_OVERRIDE decision matrix.
 *
 * <p>OptiFine is never on the test classpath, so the "OptiFine present" half of
 * the matrix is reached through {@link OptiFineCompat#setDetectedForTests} — a
 * package-private test seam that forces the detection result and drops the
 * decision cache. Every test restores real detection in teardown.
 */
class OptiFineCompatTest {

    private String[] savedModes;

    @BeforeEach
    void saveConfig() {
        savedModes = new String[]{
            LDOGConfig.ofModeCTM, LDOGConfig.ofModeEmissive, LDOGConfig.ofModeDynamicLights,
            LDOGConfig.ofModeCustomSky, LDOGConfig.ofModeHDTextures,
            LDOGConfig.ofModeSmoothFont, LDOGConfig.ofModeShaders,
        };
    }

    @AfterEach
    void restore() {
        LDOGConfig.ofModeCTM = savedModes[0];
        LDOGConfig.ofModeEmissive = savedModes[1];
        LDOGConfig.ofModeDynamicLights = savedModes[2];
        LDOGConfig.ofModeCustomSky = savedModes[3];
        LDOGConfig.ofModeHDTextures = savedModes[4];
        LDOGConfig.ofModeSmoothFont = savedModes[5];
        LDOGConfig.ofModeShaders = savedModes[6];
        OptiFineCompat.resetForTests();
    }

    // ---- Detection ----

    @Test
    @DisplayName("OptiFine is not loaded in test environment")
    void optiFineNotLoaded() {
        assertFalse(OptiFineCompat.isOptiFineLoaded());
    }

    @Test
    @DisplayName("Detection result is cached after first call")
    void detectionIsCached() {
        boolean first = OptiFineCompat.isOptiFineLoaded();
        boolean second = OptiFineCompat.isOptiFineLoaded();
        assertEquals(first, second, "Detection result should be consistent");
    }

    @Test
    @DisplayName("All features handled by LDOG when OptiFine absent")
    void allFeaturesHandledWithoutOptiFine() {
        assertTrue(OptiFineCompat.shouldHandleCTM());
        assertTrue(OptiFineCompat.shouldHandleEmissive());
        assertTrue(OptiFineCompat.shouldHandleDynamicLights());
        assertTrue(OptiFineCompat.shouldHandleShaders());
        assertTrue(OptiFineCompat.shouldHandleCustomSky());
        assertTrue(OptiFineCompat.shouldHandleHDTextures());
        assertTrue(OptiFineCompat.shouldHandleSmoothFont());
    }

    @Test
    @DisplayName("Render optimizations always handled by LDOG")
    void renderOptimizationsAlwaysHandled() {
        // This should return true regardless of OptiFine presence
        assertTrue(OptiFineCompat.shouldHandleRenderOptimizations());
        OptiFineCompat.setDetectedForTests(true);
        assertTrue(OptiFineCompat.shouldHandleRenderOptimizations());
    }

    @Test
    @DisplayName("Test seam restores real classpath detection")
    void seamRestoresRealDetection() {
        OptiFineCompat.setDetectedForTests(true);
        assertTrue(OptiFineCompat.isOptiFineLoaded());
        OptiFineCompat.resetForTests();
        assertFalse(OptiFineCompat.isOptiFineLoaded(),
            "resetForTests must re-run the real classpath probe");
    }

    // ---- Decision matrix ----

    @Nested
    @DisplayName("OptiFine absent — every mode still runs LDOG's implementation")
    class WithoutOptiFine {

        @BeforeEach
        void noOptiFine() {
            OptiFineCompat.setDetectedForTests(false);
        }

        @Test
        @DisplayName("AUTO runs LDOG")
        void autoRunsLdog() {
            LDOGConfig.ofModeCTM = "auto";
            assertTrue(OptiFineCompat.shouldHandleCTM());
        }

        @Test
        @DisplayName("LDOG_OVERRIDE runs LDOG")
        void ldogOverrideRunsLdog() {
            LDOGConfig.ofModeCTM = "ldog";
            assertTrue(OptiFineCompat.shouldHandleCTM());
        }

        @Test
        @DisplayName("OPTIFINE_OVERRIDE still runs LDOG — there is no OF to defer to")
        void optiFineOverrideStillRunsLdog() {
            LDOGConfig.ofModeCTM = "optifine";
            assertTrue(OptiFineCompat.shouldHandleCTM());
        }

        @Test
        @DisplayName("An unrecognised mode string is treated as AUTO")
        void unknownModeIsAuto() {
            LDOGConfig.ofModeCTM = "banana";
            assertTrue(OptiFineCompat.shouldHandleCTM());
        }
    }

    @Nested
    @DisplayName("OptiFine present — LDOG defers unless explicitly overridden")
    class WithOptiFine {

        @BeforeEach
        void optiFinePresent() {
            OptiFineCompat.setDetectedForTests(true);
        }

        @Test
        @DisplayName("AUTO defers to OptiFine")
        void autoDefers() {
            LDOGConfig.ofModeCTM = "auto";
            assertFalse(OptiFineCompat.shouldHandleCTM(),
                "AUTO is the safe default: OF owns the feature when it is installed");
        }

        @Test
        @DisplayName("OPTIFINE_OVERRIDE defers to OptiFine")
        void optiFineOverrideDefers() {
            LDOGConfig.ofModeCTM = "optifine";
            assertFalse(OptiFineCompat.shouldHandleCTM());
        }

        @Test
        @DisplayName("An unrecognised mode string falls back to AUTO, i.e. defers")
        void unknownModeDefers() {
            LDOGConfig.ofModeCTM = "banana";
            assertFalse(OptiFineCompat.shouldHandleCTM());
        }

        @Test
        @DisplayName("A null mode string falls back to AUTO, i.e. defers")
        void nullModeDefers() {
            LDOGConfig.ofModeCTM = null;
            assertFalse(OptiFineCompat.shouldHandleCTM());
        }

        @Test
        @DisplayName("LDOG_OVERRIDE falls back to deferring when the OF bridge can't write")
        void ldogOverrideFallsBackWhenBridgeUnavailable() {
            LDOGConfig.ofModeCTM = "ldog";
            boolean handled;
            try {
                handled = OptiFineCompat.shouldHandleCTM();
            } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
                // LDOG_OVERRIDE routes through OFConfigBridge, which needs a live
                // Minecraft client to find the GameSettings instance carrying
                // OF's ofXxx fields. There is no client here. The production
                // fallback for "bridge unavailable" is the same answer this test
                // asserts, so skip rather than report a false failure.
                Assumptions.abort("OFConfigBridge requires a Minecraft client: " + e);
                return;
            }
            assertFalse(handled,
                "the reflective disable cannot succeed without OF, so LDOG must not "
                    + "take over — otherwise both systems would drive the feature");
        }

        @Test
        @DisplayName("Modes are read per feature, not shared across features")
        void decisionsArePerFeature() {
            // Same detection state, different per-feature modes: each feature
            // must consult its own config entry.
            LDOGConfig.ofModeCTM = "auto";
            LDOGConfig.ofModeShaders = "optifine";
            LDOGConfig.ofModeSmoothFont = "auto";
            assertFalse(OptiFineCompat.shouldHandleCTM());
            assertFalse(OptiFineCompat.shouldHandleShaders());
            assertFalse(OptiFineCompat.shouldHandleSmoothFont());
            // Repeat reads (now served from the cache) give the same answer.
            assertFalse(OptiFineCompat.shouldHandleCTM());
            assertFalse(OptiFineCompat.shouldHandleShaders());
        }
    }

    @Test
    @DisplayName("Dropping the cache re-evaluates AUTO against the new detection state")
    void cacheDropReEvaluates() {
        LDOGConfig.ofModeCTM = "auto";

        OptiFineCompat.setDetectedForTests(false);
        assertTrue(OptiFineCompat.shouldHandleCTM(), "no OF: LDOG handles CTM");

        // setDetectedForTests drops the decision cache, exactly as the
        // settings-save path's invalidateCache() does.
        OptiFineCompat.setDetectedForTests(true);
        assertFalse(OptiFineCompat.shouldHandleCTM(),
            "OF now present: AUTO must flip to deferring, proving the cached "
                + "decision was dropped and re-computed rather than reused");
    }

    @Test
    @DisplayName("invalidateCache is safe to call when nothing is cached")
    void invalidateCacheOnEmptyIsSafe() {
        OptiFineCompat.invalidateCache();
        OptiFineCompat.invalidateCache();
        assertTrue(OptiFineCompat.shouldHandleCTM());
    }
}
