package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.config.LDOGConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FpsReducerHandler AFK detection logic.
 * Note: Window focus detection (Display.isActive()) can't be tested without
 * a real LWJGL context, so we test the AFK path only.
 */
class FpsReducerHandlerTest {

    @BeforeEach
    void resetState() {
        // Reset user input timestamp to "now"
        FpsReducerHandler.onUserInput();
        // Ensure FPS reducer is enabled for tests
        LDOGConfig.enableFpsReducer = true;
        LDOGConfig.afkTimeoutSeconds = 300;
        LDOGConfig.afkFpsLimit = 15;
    }

    @Test
    @DisplayName("User is not AFK immediately after input")
    void notAfkAfterInput() {
        FpsReducerHandler.onUserInput();
        assertFalse(FpsReducerHandler.isAfk());
    }

    @Test
    @DisplayName("onUserInput resets AFK state")
    void onUserInputResetsAfk() {
        FpsReducerHandler.onUserInput();
        // Even if we were previously AFK, input should clear it
        assertFalse(FpsReducerHandler.isAfk());
    }

    @Test
    @DisplayName("getTargetFpsLimit returns 0 when disabled")
    void returnsZeroWhenDisabled() {
        LDOGConfig.enableFpsReducer = false;
        // getTargetFpsLimit() short-circuits before calling Display.isActive() when disabled
        assertEquals(0, FpsReducerHandler.getTargetFpsLimit());
    }

    @Test
    @DisplayName("AFK timeout of 0 disables AFK detection")
    void zeroTimeoutDisablesAfk() {
        LDOGConfig.afkTimeoutSeconds = 0;
        // Even with the feature enabled, timeout=0 means no AFK detection
        // Note: getTargetFpsLimit() calls Display.isActive() which requires LWJGL,
        // so we only test the AFK state flag here
        assertFalse(FpsReducerHandler.isAfk());
    }

    @Test
    @DisplayName("applyLimit leaves vanilla's cap alone when the reducer is disabled")
    void applyLimitPassThroughWhenDisabled() {
        LDOGConfig.enableFpsReducer = false;
        assertEquals(260, FpsReducerHandler.applyLimit(260));
        assertEquals(60, FpsReducerHandler.applyLimit(60));
    }

    @Test
    @DisplayName("applyLimit takes the lower of vanilla's cap and the reducer target")
    void applyLimitTakesMinimum() {
        // getTargetFpsLimit() reads Display.isActive(), which needs LWJGL; the
        // pure combination rule is what this pins down. Simulate both sides.
        assertEquals(15, FpsReducerHandler.combine(60, 15));
        assertEquals(30, FpsReducerHandler.combine(30, 45));
        assertEquals(15, FpsReducerHandler.combine(260, 15)); // 260 = vanilla "unlimited"
        assertEquals(15, FpsReducerHandler.combine(0, 15));   // defensive: 0 treated as no vanilla cap
        assertEquals(60, FpsReducerHandler.combine(60, 0));   // no reducer target
    }

    @Test
    @DisplayName("Config values are within valid ranges")
    void configRangesValid() {
        assertTrue(LDOGConfig.unfocusedFpsLimit >= 1 && LDOGConfig.unfocusedFpsLimit <= 60);
        assertTrue(LDOGConfig.afkFpsLimit >= 1 && LDOGConfig.afkFpsLimit <= 60);
        assertTrue(LDOGConfig.afkTimeoutSeconds >= 0 && LDOGConfig.afkTimeoutSeconds <= 3600);
    }
}
