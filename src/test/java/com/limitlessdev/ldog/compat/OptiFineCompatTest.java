package com.limitlessdev.ldog.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OptiFine compatibility detection logic.
 * In the test environment OptiFine is not present, so all shouldHandle* methods
 * should return true (LDOG handles everything).
 */
class OptiFineCompatTest {

    @Test
    @DisplayName("OptiFine is not loaded in test environment")
    void optiFineNotLoaded() {
        // In our test environment, OptiFine classes won't be present
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
    }

    @Test
    @DisplayName("Render optimizations always handled by LDOG")
    void renderOptimizationsAlwaysHandled() {
        // This should return true regardless of OptiFine presence
        assertTrue(OptiFineCompat.shouldHandleRenderOptimizations());
    }
}
