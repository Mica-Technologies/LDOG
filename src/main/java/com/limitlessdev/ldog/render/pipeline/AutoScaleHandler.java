package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.FpsReducerHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Auto-scale (target-FPS dynamic resolution scaling) — Phase 9a.9.
 *
 * Every {@link #TICK_INTERVAL} client ticks, compares current FPS against
 * a target derived from display refresh and MC's FPS limit (whichever is
 * lower). Steps {@link LDOGConfig#internalRenderScale} down the {@link
 * #LADDER} when FPS is significantly below target, or back up when FPS
 * has enough headroom.
 *
 * Tuning choices:
 *   - 2-second decision interval keeps adjustments from flickering the
 *     image every frame.
 *   - 90% / 110% thresholds create a dead zone around the target so
 *     scale doesn't bounce between tiers when FPS sits right at target.
 *   - Steps ONE tier per interval. Taking multiple tiers at once would
 *     make the visual jump jarring.
 *
 * Three operating modes selected via {@link AutoScaleMode} (cycled by GUI):
 *
 *   <b>OFF</b>: handler skips all adjustments. User's manual scale honored.
 *
 *   <b>NORMAL</b>: only {@link LDOGConfig#internalRenderScale} is adjusted.
 *   5 tiers from 1.00 → 0.50. Original 9a.9 contract.
 *
 *   <b>AGGRESSIVE (9a.9 ext)</b>: also manages upscaler algorithm + FXAA quality
 *   + FXAA on/off across a 7-tier extended ladder. At the bottom of the scale
 *   ladder downgrades FSR1-Quality → FSR1 → Bilinear and FXAA quality Ultra →
 *   High → Medium → Low → off, then disables FXAA. Handler OWNS those settings
 *   while in this mode — manual GUI changes are overwritten on the next tick.
 *   Exception: a user-selected FSR2 is never downgraded (see
 *   {@link #applyAggressiveTier}).
 *
 * Skipped entirely when the measured FPS isn't a quality signal:
 *   - {@link FpsReducerHandler} is actively capping (window unfocused / AFK).
 *     Its cap makes FPS look catastrophic, and the handler would walk quality
 *     down to the floor while the user is alt-tabbed — then walk it back up
 *     one 2-second step at a time once they return.
 *   - MSAA owns the world-pass framebuffer
 *     ({@link PostProcessPipeline#hasConflictingFeatureOn()}), so the pipeline
 *     yields and none of the settings we'd tune are in the frame at all.
 *
 * Limitations:
 *   - Uses MC's rolling-1s FPS counter; could track a longer window for
 *     more stability at the cost of slower response.
 *   - Manual GUI changes to managed settings (scale, upscaler, FXAA) are
 *     overwritten by the next auto-tick. GUI tooltips call this out.
 *   - Aggressive mode does not currently touch RCAS, anisotropic, or MSAA;
 *     those are user choices left untouched.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class AutoScaleHandler {

    /** Discrete render-scale tiers, ordered high → low. Used in simple mode. */
    private static final double[] LADDER = {1.00, 0.85, 0.75, 0.67, 0.50};

    /**
     * Aggressive-mode ladder. Each tier bundles scale + upscaler + FXAA
     * settings. Ordered best-quality → worst-quality. The first 4 tiers
     * primarily change scale + upscaler quality; the last 3 retain scale=0.50
     * and progressively cut FXAA cost, then downgrade to bilinear upscale.
     *
     * Rationale for ordering:
     *   - FSR1_QUALITY → FSR1 transition at tier 4: FSR1_QUALITY adds ~8
     *     extra texture fetches per pixel; dropping to plain FSR1 is a
     *     meaningful perf win at the same render scale.
     *   - FXAA Medium → Low at tier 5: shaves search-step count from 6 to 4
     *     while keeping AA on for visual continuity.
     *   - FXAA off + Bilinear at tier 6: last-ditch fallback for users with
     *     hardware that can't sustain target FPS even at 0.50x. Image quality
     *     here is intentionally poor — the goal is to keep the game playable.
     */
    private static final AggressiveTier[] AGGRESSIVE_LADDER = {
        new AggressiveTier(1.00, "fsr1_quality", "ultra",  true,  "Native+Q+UltraFXAA"),
        new AggressiveTier(0.85, "fsr1_quality", "high",   true,  "0.85+Q+HighFXAA"),
        new AggressiveTier(0.75, "fsr1_quality", "high",   true,  "0.75+Q+HighFXAA"),
        new AggressiveTier(0.67, "fsr1_quality", "medium", true,  "0.67+Q+MedFXAA"),
        new AggressiveTier(0.50, "fsr1",         "medium", true,  "0.50+FSR1+MedFXAA"),
        new AggressiveTier(0.50, "fsr1",         "low",    true,  "0.50+FSR1+LowFXAA"),
        new AggressiveTier(0.50, "bilinear",     "low",    false, "0.50+Bilinear+NoFXAA"),
    };

    /** Ticks between adjustments (20 tps * 2 = 40 ticks). */
    private static final int TICK_INTERVAL = 40;

    /** FPS headroom thresholds. Step down below 0.9x target, up above 1.1x. */
    private static final float DOWNSHIFT_THRESHOLD = 0.90f;
    private static final float UPSHIFT_THRESHOLD = 1.10f;

    /** Fallback target when refresh rate query fails. */
    private static final int FALLBACK_TARGET_FPS = 60;

    private static int tickCounter = 0;
    private static boolean firstTickLogged = false;

    /**
     * Aggressive-mode ladder index. -1 = not yet snapped; we re-snap from
     * current settings on first aggressive-mode tick or after the user
     * cycles modes (so a user-edited setting between mode changes is honored
     * as the new starting point).
     */
    private static int currentAggressiveIdx = -1;

    /** Tracks the previous tick's mode to detect mode transitions. */
    private static AutoScaleMode prevMode = AutoScaleMode.OFF;

    private AutoScaleHandler() {}

    /**
     * Bundle of settings applied together as one ladder step. Final + immutable
     * because the static AGGRESSIVE_LADDER array is shared state read every tick.
     */
    private static final class AggressiveTier {
        final double scale;
        final String upscalerKey;
        final String fxaaKey;
        final boolean fxaaEnabled;
        final String label;

        AggressiveTier(double scale, String upscalerKey, String fxaaKey,
                       boolean fxaaEnabled, String label) {
            this.scale = scale;
            this.upscalerKey = upscalerKey;
            this.fxaaKey = fxaaKey;
            this.fxaaEnabled = fxaaEnabled;
            this.label = label;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // One-shot: prove the @EventBusSubscriber wiring landed and we're being invoked.
        // Fires regardless of the feature toggle so a "no log at all" symptom can
        // distinguish "handler never registered" from "handler registered but disabled".
        if (!firstTickLogged) {
            firstTickLogged = true;
            LDOGMod.LOGGER.info(
                "LDOG: AutoScale handler registered and ticking (autoScaleMode={}, enablePostProcessPipeline={})",
                LDOGConfig.autoScaleMode, LDOGConfig.enablePostProcessPipeline);
        }

        AutoScaleMode mode = AutoScaleMode.selected();
        if (mode == AutoScaleMode.OFF) return;
        if (!LDOGConfig.enablePostProcessPipeline) return;
        // MSAA owns the world-pass FBO; the pipeline yields to it, so none of
        // the settings this handler tunes affect the frame. Adjusting them
        // against MSAA's frame rate would just churn config.
        if (PostProcessPipeline.hasConflictingFeatureOn()) return;

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) return; // only adjust while a world is loaded

        // FPS is being deliberately held down (unfocused window / AFK), so it
        // says nothing about rendering cost. Hold the current quality — don't
        // read the cap as "this GPU can't keep up".
        if (FpsReducerHandler.getTargetFpsLimit() > 0) {
            LDOGMod.LOGGER.debug("LDOG: AutoScale HOLD — FPS reducer is capping this frame");
            return;
        }

        int fps = Minecraft.getDebugFPS();
        int target = computeTargetFPS(mc);
        if (target <= 0) return;

        // Detect mode transitions: when cycling modes, drop the cached
        // aggressive index so the next tick re-snaps from whatever settings
        // the user has now (in case they edited in the GUI between cycles).
        if (mode != prevMode) {
            currentAggressiveIdx = -1;
            prevMode = mode;
        }

        if (mode == AutoScaleMode.AGGRESSIVE) {
            tickAggressive(fps, target);
        } else {
            tickSimple(fps, target);
        }
    }

    /** Original 9a.9 logic — adjust internalRenderScale only. */
    private static void tickSimple(int fps, int target) {
        int currentIdx = findLadderIdx(LDOGConfig.internalRenderScale);
        int newIdx = decideStep(fps, target, currentIdx, LADDER.length);
        String decision = describeStep(currentIdx, newIdx);

        if (newIdx != currentIdx) {
            LDOGConfig.internalRenderScale = LADDER[newIdx];
            markPresetCustom();
            LDOGMod.LOGGER.info(
                "LDOG: AutoScale {} to {} (fps={}, target={})",
                decision,
                String.format("%.2fx", LDOGConfig.internalRenderScale),
                fps, target);
        } else {
            // Per-decision visibility for diagnosing "no log output" complaints
            // when FPS sits in the dead zone or is pinned at a ladder boundary.
            // DEBUG level so it doesn't spam normal logs; user enables log4j
            // DEBUG (or sets `-Dlog4j.configurationFile=...`) to see it.
            LDOGMod.LOGGER.debug(
                "LDOG: AutoScale {} at {} (fps={}, target={})",
                decision,
                String.format("%.2fx", LDOGConfig.internalRenderScale),
                fps, target);
        }
    }

    /**
     * 9a.9 ext — aggressive mode. Adjusts scale + upscaler + FXAA quality + FXAA
     * enabled together via the {@link #AGGRESSIVE_LADDER}. Same FPS thresholds
     * and step-one-tier-at-a-time behavior as simple mode.
     */
    private static void tickAggressive(int fps, int target) {
        if (currentAggressiveIdx < 0) {
            currentAggressiveIdx = snapToAggressiveTier();
            LDOGMod.LOGGER.info(
                "LDOG: AutoScale (aggressive) snapped to tier {} ({})",
                currentAggressiveIdx, AGGRESSIVE_LADDER[currentAggressiveIdx].label);
        }

        int newIdx = decideStep(fps, target, currentAggressiveIdx, AGGRESSIVE_LADDER.length);
        String decision = describeStep(currentAggressiveIdx, newIdx);

        if (newIdx != currentAggressiveIdx) {
            applyAggressiveTier(AGGRESSIVE_LADDER[newIdx]);
            currentAggressiveIdx = newIdx;
            LDOGMod.LOGGER.info(
                "LDOG: AutoScale (aggressive) {} to tier {} ({}, fps={}, target={})",
                decision, newIdx, AGGRESSIVE_LADDER[newIdx].label, fps, target);
        } else {
            LDOGMod.LOGGER.debug(
                "LDOG: AutoScale (aggressive) {} at tier {} ({}, fps={}, target={})",
                decision, currentAggressiveIdx, AGGRESSIVE_LADDER[currentAggressiveIdx].label,
                fps, target);
        }
    }

    /**
     * Apply an aggressive tier.
     *
     * <p>FSR2 is preserved when the user selected it: the ladder only names
     * spatial upscalers, so writing {@code t.upscalerKey} would silently
     * demote a deliberate FSR2 choice to FSR1/bilinear — a change of image
     * character (temporal reconstruction gone) that no FPS reading justifies,
     * and one the user can't undo because the next tick would overwrite it
     * again. Scale and FXAA still move: those are the tier's actual perf
     * levers, and FSR2 reconstructs from whatever scale it is handed.
     */
    private static void applyAggressiveTier(AggressiveTier t) {
        LDOGConfig.internalRenderScale = t.scale;
        if (UpscalerAlgorithm.selected() != UpscalerAlgorithm.FSR2) {
            LDOGConfig.upscalerAlgorithm = t.upscalerKey;
        }
        LDOGConfig.fxaaQuality = t.fxaaKey;
        LDOGConfig.enableFXAA = t.fxaaEnabled;
        markPresetCustom();
    }

    /**
     * The auto-tuned values no longer match whatever named preset the GUI is
     * displaying — flip it to Custom, exactly as a manual edit in the GUI does.
     * Without this the settings screen keeps claiming e.g. "Quality" while the
     * handler has moved scale/upscaler/FXAA somewhere else entirely.
     */
    private static void markPresetCustom() {
        if (UpscalerPreset.selected() != UpscalerPreset.CUSTOM) {
            UpscalerPreset.markCustom();
        }
    }

    /**
     * The FPS-vs-target threshold decision, shared by both modes: step one tier
     * down below {@link #DOWNSHIFT_THRESHOLD}x target, one tier up above
     * {@link #UPSHIFT_THRESHOLD}x, hold in the dead zone between. Clamped at
     * both ends of the ladder.
     *
     * <p>Pure function of its arguments (no config, no Minecraft) so the
     * decision rule is unit-testable on its own.
     *
     * @return the ladder index to move to, equal to {@code currentIdx} on HOLD
     */
    static int decideStep(int fps, int target, int currentIdx, int ladderLength) {
        if (fps < target * DOWNSHIFT_THRESHOLD && currentIdx < ladderLength - 1) {
            return currentIdx + 1;
        }
        if (fps > target * UPSHIFT_THRESHOLD && currentIdx > 0) {
            return currentIdx - 1;
        }
        return currentIdx;
    }

    /** Log label for a {@link #decideStep} result. */
    static String describeStep(int currentIdx, int newIdx) {
        if (newIdx > currentIdx) return "DOWN";
        if (newIdx < currentIdx) return "UP";
        return "HOLD";
    }

    /**
     * Find the AGGRESSIVE_LADDER tier that best matches the current settings.
     * Score is a weighted distance: scale-delta dominates, with penalties for
     * mismatched upscaler / FXAA toggle / FXAA quality. The non-zero weights
     * for the categorical fields are deliberately small so two tiers with the
     * same scale but different upscaler aren't equally matched — the weight
     * order encodes "scale matters most, then upscaler, then FXAA on/off,
     * then FXAA quality."
     *
     * <p>With FSR2 selected no tier's upscaler matches, so that term becomes a
     * constant offset and the snap falls back to scale + FXAA — which is the
     * intent, since {@link #applyAggressiveTier} leaves FSR2 in place.
     */
    private static int snapToAggressiveTier() {
        return snapToAggressiveTier(LDOGConfig.internalRenderScale, LDOGConfig.upscalerAlgorithm,
            LDOGConfig.enableFXAA, LDOGConfig.fxaaQuality);
    }

    /**
     * Pure form of {@link #snapToAggressiveTier()} — takes the four settings it
     * scores against explicitly instead of reading {@link LDOGConfig}, so the
     * scoring rule is unit-testable without touching global state.
     */
    static int snapToAggressiveTier(double scale, String upscalerAlgorithm,
                                    boolean fxaaEnabled, String fxaaQuality) {
        int best = 0;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < AGGRESSIVE_LADDER.length; i++) {
            AggressiveTier t = AGGRESSIVE_LADDER[i];
            double score = Math.abs(t.scale - scale);
            if (!t.upscalerKey.equalsIgnoreCase(upscalerAlgorithm)) score += 0.4;
            if (t.fxaaEnabled != fxaaEnabled) score += 0.2;
            if (!t.fxaaKey.equalsIgnoreCase(fxaaQuality)) score += 0.1;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    // ===== Read-only ladder accessors (package-private, for unit tests) =====

    /** Number of tiers in the simple-mode scale ladder. */
    static int simpleLadderSize() { return LADDER.length; }

    /** Render scale of simple-mode ladder tier {@code idx}. */
    static double simpleLadderScale(int idx) { return LADDER[idx]; }

    /** Number of tiers in the aggressive-mode ladder. */
    static int aggressiveLadderSize() { return AGGRESSIVE_LADDER.length; }

    /** Render scale of aggressive-mode ladder tier {@code idx}. */
    static double aggressiveLadderScale(int idx) { return AGGRESSIVE_LADDER[idx].scale; }

    /** Upscaler config key of aggressive-mode ladder tier {@code idx}. */
    static String aggressiveLadderUpscaler(int idx) { return AGGRESSIVE_LADDER[idx].upscalerKey; }

    /** min(display refresh, MC's limitFramerate). Falls back to 60 on query failure. */
    private static int computeTargetFPS(Minecraft mc) {
        int refresh = FALLBACK_TARGET_FPS;
        try {
            DisplayMode desktop = Display.getDesktopDisplayMode();
            if (desktop != null && desktop.getFrequency() > 0) {
                refresh = desktop.getFrequency();
            }
        } catch (Exception ignored) {
            // Keep fallback.
        }

        int limit = mc.gameSettings.limitFramerate;
        // MC's "unlimited" slider position sets limit to 260+; treat as no limit.
        if (limit <= 0 || limit >= 260) return refresh;
        return Math.min(refresh, limit);
    }

    /** Snap a continuous scale to the nearest ladder index. Package-private for unit tests. */
    static int findLadderIdx(double scale) {
        int best = 0;
        double bestDelta = Math.abs(LADDER[0] - scale);
        for (int i = 1; i < LADDER.length; i++) {
            double d = Math.abs(LADDER[i] - scale);
            if (d < bestDelta) {
                bestDelta = d;
                best = i;
            }
        }
        return best;
    }
}
