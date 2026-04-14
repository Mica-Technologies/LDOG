package com.limitlessdev.ldog.render;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LDOGStats tracking and reset behavior.
 */
class LDOGStatsTest {

    @BeforeEach
    void reset() {
        LDOGStats.resetFrame();
    }

    @Test
    @DisplayName("All stats are zero after reset")
    void allZeroAfterReset() {
        assertEquals(0, LDOGStats.entitiesCulledByDistance);
        assertEquals(0, LDOGStats.entitiesSkippedByLOD);
        assertEquals(0, LDOGStats.entitiesRendered);
        assertEquals(0, LDOGStats.particlesCulled);
        assertEquals(0, LDOGStats.particlesRendered);
        assertEquals(0, LDOGStats.tileEntitiesCulled);
    }

    @Test
    @DisplayName("Stats accumulate within a frame")
    void statsAccumulate() {
        LDOGStats.entitiesRendered = 10;
        LDOGStats.entitiesCulledByDistance = 5;
        LDOGStats.entitiesSkippedByLOD = 3;
        LDOGStats.particlesRendered = 100;
        LDOGStats.particlesCulled = 20;
        LDOGStats.tileEntitiesCulled = 2;

        assertEquals(10, LDOGStats.entitiesRendered);
        assertEquals(5, LDOGStats.entitiesCulledByDistance);
        assertEquals(3, LDOGStats.entitiesSkippedByLOD);
        assertEquals(100, LDOGStats.particlesRendered);
        assertEquals(20, LDOGStats.particlesCulled);
        assertEquals(2, LDOGStats.tileEntitiesCulled);
    }

    @Test
    @DisplayName("Reset clears all accumulated stats")
    void resetClearsAll() {
        LDOGStats.entitiesRendered = 50;
        LDOGStats.particlesCulled = 30;
        LDOGStats.tileEntitiesCulled = 10;

        LDOGStats.resetFrame();

        assertEquals(0, LDOGStats.entitiesRendered);
        assertEquals(0, LDOGStats.particlesCulled);
        assertEquals(0, LDOGStats.tileEntitiesCulled);
    }
}
