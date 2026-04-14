package com.limitlessdev.ldog.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LDOGConfig default values and value ranges.
 * These verify that config defaults are sane and within documented bounds.
 */
class LDOGConfigTest {

    @Test
    @DisplayName("Entity render distance default is 64 blocks")
    void entityRenderDistanceDefault() {
        assertEquals(64, LDOGConfig.entityRenderDistance);
    }

    @Test
    @DisplayName("Tile entity render distance default is 64 blocks")
    void tileEntityRenderDistanceDefault() {
        assertEquals(64, LDOGConfig.tileEntityRenderDistance);
    }

    @Test
    @DisplayName("FPS reducer is enabled by default")
    void fpsReducerEnabledByDefault() {
        assertTrue(LDOGConfig.enableFpsReducer);
    }

    @Test
    @DisplayName("Unfocused FPS default is 5")
    void unfocusedFpsDefault() {
        assertEquals(5, LDOGConfig.unfocusedFpsLimit);
    }

    @Test
    @DisplayName("AFK timeout default is 300 seconds (5 minutes)")
    void afkTimeoutDefault() {
        assertEquals(300, LDOGConfig.afkTimeoutSeconds);
    }

    @Test
    @DisplayName("AFK FPS default is 15")
    void afkFpsDefault() {
        assertEquals(15, LDOGConfig.afkFpsLimit);
    }

    @Test
    @DisplayName("Particle culling is enabled by default")
    void particleCullingEnabledByDefault() {
        assertTrue(LDOGConfig.enableParticleCulling);
    }

    @Test
    @DisplayName("Clear water is enabled by default")
    void clearWaterEnabledByDefault() {
        assertTrue(LDOGConfig.enableClearWater);
    }

    @Test
    @DisplayName("Water opacity default is 0.4 (40%)")
    void waterOpacityDefault() {
        assertEquals(0.4, LDOGConfig.waterOpacity, 0.001);
    }

    @Test
    @DisplayName("Render optimizations enabled by default")
    void renderOptimizationsDefault() {
        assertTrue(LDOGConfig.enableRenderOptimizations);
    }

    @Test
    @DisplayName("Shaders disabled by default (stretch goal)")
    void shadersDisabledByDefault() {
        assertFalse(LDOGConfig.enableShaders);
    }

    @Test
    @DisplayName("Future features are enabled by default (ready for when implemented)")
    void futureFeatureDefaults() {
        assertTrue(LDOGConfig.enableConnectedTextures);
        assertTrue(LDOGConfig.enableEmissiveTextures);
        assertTrue(LDOGConfig.enableDynamicLights);
        assertTrue(LDOGConfig.enableCustomSky);
        assertTrue(LDOGConfig.enableHDTextures);
    }
}
