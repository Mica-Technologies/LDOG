package com.limitlessdev.ldog.render.sky;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CustomSkyLayer#computeAlpha} — the piecewise sky-layer fade
 * evaluated on the 24000-tick circle. The interesting cases are the ones that
 * wrap past midnight, which a linear tick comparison gets wrong.
 */
class CustomSkyLayerTest {

    private static final float EPS = 1.0e-4f;

    private static float alpha(int time, int startIn, int endIn, int startOut, int endOut) {
        return CustomSkyLayer.computeAlpha(time, startIn, endIn, startOut, endOut);
    }

    @Test
    @DisplayName("Ordered schedule fades in, holds, then fades out")
    void orderedSchedule() {
        // in 12000-13000, hold, out 22000-23000
        assertEquals(0.0f, alpha(12000, 12000, 13000, 22000, 23000), EPS);
        assertEquals(0.5f, alpha(12500, 12000, 13000, 22000, 23000), EPS);
        assertEquals(1.0f, alpha(13000, 12000, 13000, 22000, 23000), EPS);
        assertEquals(1.0f, alpha(20000, 12000, 13000, 22000, 23000), EPS);
        assertEquals(1.0f, alpha(22000, 12000, 13000, 22000, 23000), EPS);
        assertEquals(0.5f, alpha(22500, 12000, 13000, 22000, 23000), EPS);
        assertEquals(0.0f, alpha(23000, 12000, 13000, 22000, 23000), EPS);
        assertEquals(0.0f, alpha(500, 12000, 13000, 22000, 23000), EPS);
    }

    @Test
    @DisplayName("Fade-out wrapping past midnight stays lit through the night")
    void fadeOutAfterMidnight() {
        // in 21000-22000, out 2000-3000: the whole visible window crosses 24000
        assertEquals(0.0f, alpha(21000, 21000, 22000, 2000, 3000), EPS);
        assertEquals(0.5f, alpha(21500, 21000, 22000, 2000, 3000), EPS);
        assertEquals(1.0f, alpha(22000, 21000, 22000, 2000, 3000), EPS);
        assertEquals(1.0f, alpha(23500, 21000, 22000, 2000, 3000), EPS);
        // Midnight rollover: previously read as "before startFadeIn" -> 0.
        assertEquals(1.0f, alpha(0, 21000, 22000, 2000, 3000), EPS);
        assertEquals(1.0f, alpha(1000, 21000, 22000, 2000, 3000), EPS);
        assertEquals(1.0f, alpha(2000, 21000, 22000, 2000, 3000), EPS);
        assertEquals(0.5f, alpha(2500, 21000, 22000, 2000, 3000), EPS);
        assertEquals(0.0f, alpha(3000, 21000, 22000, 2000, 3000), EPS);
        assertEquals(0.0f, alpha(12000, 21000, 22000, 2000, 3000), EPS);
    }

    @Test
    @DisplayName("Fade-in itself wrapping past midnight")
    void fadeInAcrossMidnight() {
        // in 23000-1000, out 5000-6000
        assertEquals(0.0f, alpha(23000, 23000, 1000, 5000, 6000), EPS);
        assertEquals(0.25f, alpha(23500, 23000, 1000, 5000, 6000), EPS);
        assertEquals(0.5f, alpha(0, 23000, 1000, 5000, 6000), EPS);
        assertEquals(0.75f, alpha(500, 23000, 1000, 5000, 6000), EPS);
        assertEquals(1.0f, alpha(1000, 23000, 1000, 5000, 6000), EPS);
        // Previously vanished the moment the wrapped fade-in completed.
        assertEquals(1.0f, alpha(2000, 23000, 1000, 5000, 6000), EPS);
        assertEquals(1.0f, alpha(5000, 23000, 1000, 5000, 6000), EPS);
        assertEquals(0.5f, alpha(5500, 23000, 1000, 5000, 6000), EPS);
        assertEquals(0.0f, alpha(6000, 23000, 1000, 5000, 6000), EPS);
        assertEquals(0.0f, alpha(20000, 23000, 1000, 5000, 6000), EPS);
    }

    @Test
    @DisplayName("Degenerate keyframes do not divide by zero")
    void degenerate() {
        // Instant fade in and instant fade out.
        assertEquals(1.0f, alpha(6000, 6000, 6000, 18000, 18000), EPS);
        assertEquals(1.0f, alpha(12000, 6000, 6000, 18000, 18000), EPS);
        assertEquals(0.0f, alpha(18000, 6000, 6000, 18000, 18000), EPS);
        assertEquals(0.0f, alpha(0, 6000, 6000, 18000, 18000), EPS);

        // All four identical: never visible, and must not blow up.
        assertEquals(0.0f, alpha(0, 0, 0, 0, 0), EPS);
        assertEquals(0.0f, alpha(9000, 0, 0, 0, 0), EPS);
    }

    @Test
    @DisplayName("Times outside 0-24000 normalize onto the circle")
    void outOfRangeTimes() {
        assertEquals(1.0f, alpha(24000 + 15000, 12000, 13000, 22000, 23000), EPS);
        assertEquals(1.0f, alpha(15000, 12000 + 24000, 13000 + 24000, 22000, 23000), EPS);
    }
}
