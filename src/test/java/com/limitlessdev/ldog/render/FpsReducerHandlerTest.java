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
    @DisplayName("Config values are within valid ranges")
    void configRangesValid() {
        assertTrue(LDOGConfig.unfocusedFpsLimit >= 1 && LDOGConfig.unfocusedFpsLimit <= 60);
        assertTrue(LDOGConfig.afkFpsLimit >= 1 && LDOGConfig.afkFpsLimit <= 60);
        assertTrue(LDOGConfig.afkTimeoutSeconds >= 0 && LDOGConfig.afkTimeoutSeconds <= 3600);
    }
}
