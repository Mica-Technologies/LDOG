package com.limitlessdev.ldog.render.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AutoScaleHandler}'s pure decision logic — the ladder snap,
 * the FPS-vs-target step rule, and the aggressive-tier scoring.
 *
 * <p>The handler's tick entry point can't run here: it reads
 * {@code Minecraft.getDebugFPS()}, {@code Display.getDesktopDisplayMode()} and
 * the FPS-reducer / MSAA guards, none of which exist in a plain JVM. Those
 * guards are straight-line early-returns; everything downstream of them is
 * exercised below through the package-private pure helpers.
 */
class AutoScaleHandlerTest {

    @Nested
    @DisplayName("findLadderIdx — snap a continuous scale to the nearest tier")
    class FindLadderIdx {

        @Test
        @DisplayName("Exact ladder values map to their own index")
        void exactValues() {
            for (int i = 0; i < AutoScaleHandler.simpleLadderSize(); i++) {
                assertEquals(i, AutoScaleHandler.findLadderIdx(AutoScaleHandler.simpleLadderScale(i)),
                    "tier " + i + " should snap to itself");
            }
        }

        @Test
        @DisplayName("Native scale (1.00) is tier 0, the top of the ladder")
        void nativeIsTop() {
            assertEquals(0, AutoScaleHandler.findLadderIdx(1.00));
            assertEquals(1.00, AutoScaleHandler.simpleLadderScale(0), 1e-9);
        }

        @Test
        @DisplayName("Ladder is ordered high quality -> low quality")
        void ladderIsDescending() {
            for (int i = 1; i < AutoScaleHandler.simpleLadderSize(); i++) {
                assertTrue(AutoScaleHandler.simpleLadderScale(i) < AutoScaleHandler.simpleLadderScale(i - 1),
                    "tier " + i + " should be a lower scale than tier " + (i - 1));
            }
        }

        @Test
        @DisplayName("In-between values snap to the nearest tier")
        void nearestWins() {
            // Ladder is {1.00, 0.85, 0.75, 0.67, 0.50}.
            assertEquals(0, AutoScaleHandler.findLadderIdx(0.99));
            assertEquals(1, AutoScaleHandler.findLadderIdx(0.86));
            assertEquals(1, AutoScaleHandler.findLadderIdx(0.81));
            assertEquals(2, AutoScaleHandler.findLadderIdx(0.76));
            assertEquals(3, AutoScaleHandler.findLadderIdx(0.66));
            assertEquals(4, AutoScaleHandler.findLadderIdx(0.51));
        }

        @Test
        @DisplayName("Out-of-range values clamp to the nearest end of the ladder")
        void outOfRangeClamps() {
            assertEquals(0, AutoScaleHandler.findLadderIdx(4.00));
            assertEquals(AutoScaleHandler.simpleLadderSize() - 1, AutoScaleHandler.findLadderIdx(0.01));
            assertEquals(AutoScaleHandler.simpleLadderSize() - 1, AutoScaleHandler.findLadderIdx(-1.0));
        }

        @Test
        @DisplayName("A near-midpoint scale resolves to the higher-quality tier")
        void midpointPrefersHigherQuality() {
            // 0.80 sits between 0.85 and 0.75. The scan uses a strict <, so the
            // first (higher-quality) tier wins any tie — and in IEEE-754 the
            // 0.85 side is in fact fractionally closer, so this is stable.
            assertEquals(1, AutoScaleHandler.findLadderIdx(0.80));
        }
    }

    @Nested
    @DisplayName("decideStep — FPS thresholds and dead zone")
    class DecideStep {

        private static final int LEN = 5;

        @Test
        @DisplayName("FPS well below target steps one tier DOWN")
        void belowTargetStepsDown() {
            // 0.90 threshold at target 60 => below 54 downshifts.
            assertEquals(2, AutoScaleHandler.decideStep(30, 60, 1, LEN));
            assertEquals("DOWN", AutoScaleHandler.describeStep(1, 2));
        }

        @Test
        @DisplayName("FPS well above target steps one tier UP")
        void aboveTargetStepsUp() {
            // 1.10 threshold at target 60 => above 66 upshifts.
            assertEquals(1, AutoScaleHandler.decideStep(120, 60, 2, LEN));
            assertEquals("UP", AutoScaleHandler.describeStep(2, 1));
        }

        @Test
        @DisplayName("FPS inside the dead zone HOLDs")
        void deadZoneHolds() {
            // 54 .. 66 exclusive is the hold band at target 60.
            assertEquals(2, AutoScaleHandler.decideStep(60, 60, 2, LEN));
            assertEquals(2, AutoScaleHandler.decideStep(55, 60, 2, LEN));
            assertEquals(2, AutoScaleHandler.decideStep(65, 60, 2, LEN));
            assertEquals("HOLD", AutoScaleHandler.describeStep(2, 2));
        }

        @Test
        @DisplayName("Threshold boundaries are exclusive — exactly 0.9x / 1.1x HOLDs")
        void boundariesHold() {
            assertEquals(2, AutoScaleHandler.decideStep(54, 60, 2, LEN), "54 == 0.9 * 60");
            assertEquals(2, AutoScaleHandler.decideStep(66, 60, 2, LEN), "66 == 1.1 * 60");
            // One frame past each boundary does move.
            assertEquals(3, AutoScaleHandler.decideStep(53, 60, 2, LEN));
            assertEquals(1, AutoScaleHandler.decideStep(67, 60, 2, LEN));
        }

        @Test
        @DisplayName("Never steps past the bottom of the ladder")
        void clampsAtBottom() {
            assertEquals(LEN - 1, AutoScaleHandler.decideStep(1, 60, LEN - 1, LEN));
            assertEquals("HOLD", AutoScaleHandler.describeStep(LEN - 1, LEN - 1));
        }

        @Test
        @DisplayName("Never steps past the top of the ladder")
        void clampsAtTop() {
            assertEquals(0, AutoScaleHandler.decideStep(500, 60, 0, LEN));
        }

        @Test
        @DisplayName("Steps at most one tier per decision, however bad the FPS")
        void oneTierPerDecision() {
            assertEquals(1, AutoScaleHandler.decideStep(1, 240, 0, LEN));
            assertEquals(0, AutoScaleHandler.decideStep(2000, 30, 1, LEN));
        }

        @Test
        @DisplayName("Works against the aggressive ladder's length too")
        void aggressiveLength() {
            int len = AutoScaleHandler.aggressiveLadderSize();
            assertEquals(7, len, "aggressive ladder is documented as 7 tiers");
            assertEquals(len - 1, AutoScaleHandler.decideStep(5, 60, len - 1, len));
            assertEquals(len - 2, AutoScaleHandler.decideStep(200, 60, len - 1, len));
        }
    }

    @Nested
    @DisplayName("snapToAggressiveTier — score current settings against the ladder")
    class SnapToAggressiveTier {

        @Test
        @DisplayName("An exact tier match snaps to that tier")
        void exactMatch() {
            assertEquals(0, AutoScaleHandler.snapToAggressiveTier(1.00, "fsr1_quality", true, "ultra"));
            assertEquals(1, AutoScaleHandler.snapToAggressiveTier(0.85, "fsr1_quality", true, "high"));
            assertEquals(3, AutoScaleHandler.snapToAggressiveTier(0.67, "fsr1_quality", true, "medium"));
            assertEquals(6, AutoScaleHandler.snapToAggressiveTier(0.50, "bilinear", false, "low"));
        }

        @Test
        @DisplayName("Upscaler key matching is case-insensitive")
        void caseInsensitive() {
            assertEquals(0, AutoScaleHandler.snapToAggressiveTier(1.00, "FSR1_QUALITY", true, "ULTRA"));
        }

        @Test
        @DisplayName("Scale dominates the score over the categorical penalties")
        void scaleDominates() {
            // Wrong upscaler + wrong FXAA quality (0.4 + 0.1 penalty) still can't
            // pull the snap away from the tier whose scale actually matches.
            int idx = AutoScaleHandler.snapToAggressiveTier(0.85, "bilinear", true, "low");
            assertEquals(0.85, AutoScaleHandler.aggressiveLadderScale(idx), 1e-9);
        }

        @Test
        @DisplayName("FSR2 matches no tier, so the snap falls back to scale + FXAA")
        void fsr2FallsBackToScale() {
            // Every tier takes the same +0.4 upscaler penalty, which cancels out.
            int idx = AutoScaleHandler.snapToAggressiveTier(0.67, "fsr2", true, "medium");
            assertEquals(3, idx);
            assertNotEquals("fsr2", AutoScaleHandler.aggressiveLadderUpscaler(idx),
                "no ladder tier names FSR2 — applyAggressiveTier is what preserves it");
        }

        @Test
        @DisplayName("Never returns an out-of-range tier for absurd settings")
        void alwaysInRange() {
            int idx = AutoScaleHandler.snapToAggressiveTier(99.0, "nonsense", false, "nonsense");
            assertTrue(idx >= 0 && idx < AutoScaleHandler.aggressiveLadderSize());
            idx = AutoScaleHandler.snapToAggressiveTier(-5.0, "", true, "");
            assertTrue(idx >= 0 && idx < AutoScaleHandler.aggressiveLadderSize());
        }

        @Test
        @DisplayName("Aggressive ladder never increases scale as tiers get worse")
        void aggressiveLadderIsMonotonic() {
            for (int i = 1; i < AutoScaleHandler.aggressiveLadderSize(); i++) {
                assertTrue(AutoScaleHandler.aggressiveLadderScale(i)
                        <= AutoScaleHandler.aggressiveLadderScale(i - 1),
                    "tier " + i + " should not raise the render scale");
            }
        }
    }
}
