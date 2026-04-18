package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
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
 * MVP limitations (can be addressed later):
 *   - Doesn't touch upscaler algorithm or sharpness — only scale. If
 *     user wants adaptive upscaler quality, that's a future extension.
 *   - Uses MC's rolling-1s FPS counter; could track a longer window for
 *     more stability at the cost of slower response.
 *   - When user has auto-scale on and also cycles the Render Scale
 *     button manually, the next auto-tick will override. GUI tooltip
 *     calls this out.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class AutoScaleHandler {

    /** Discrete render-scale tiers, ordered high → low. */
    private static final double[] LADDER = {1.00, 0.85, 0.75, 0.67, 0.50};

    /** Ticks between adjustments (20 tps * 2 = 40 ticks). */
    private static final int TICK_INTERVAL = 40;

    /** FPS headroom thresholds. Step down below 0.9x target, up above 1.1x. */
    private static final float DOWNSHIFT_THRESHOLD = 0.90f;
    private static final float UPSHIFT_THRESHOLD = 1.10f;

    /** Fallback target when refresh rate query fails. */
    private static final int FALLBACK_TARGET_FPS = 60;

    private static int tickCounter = 0;
    private static boolean firstTickLogged = false;

    private AutoScaleHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // One-shot: prove the @EventBusSubscriber wiring landed and we're being invoked.
        // Fires regardless of the feature toggle so a "no log at all" symptom can
        // distinguish "handler never registered" from "handler registered but disabled".
        if (!firstTickLogged) {
            firstTickLogged = true;
            LDOGMod.LOGGER.info(
                "LDOG: AutoScale handler registered and ticking (enableAutoScale={}, enablePostProcessPipeline={})",
                LDOGConfig.enableAutoScale, LDOGConfig.enablePostProcessPipeline);
        }

        if (!LDOGConfig.enableAutoScale) return;
        if (!LDOGConfig.enablePostProcessPipeline) return;

        tickCounter++;
        if (tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) return; // only adjust while a world is loaded

        int fps = Minecraft.getDebugFPS();
        int target = computeTargetFPS(mc);
        if (target <= 0) return;

        int currentIdx = findLadderIdx(LDOGConfig.internalRenderScale);
        int newIdx = currentIdx;
        String decision;

        if (fps < target * DOWNSHIFT_THRESHOLD && currentIdx < LADDER.length - 1) {
            newIdx = currentIdx + 1;
            decision = "DOWN";
        } else if (fps > target * UPSHIFT_THRESHOLD && currentIdx > 0) {
            newIdx = currentIdx - 1;
            decision = "UP";
        } else {
            // No change: either inside the dead zone, or already at a ladder boundary.
            decision = "HOLD";
        }

        if (newIdx != currentIdx) {
            LDOGConfig.internalRenderScale = LADDER[newIdx];
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

    /** Snap a continuous scale to the nearest ladder index. */
    private static int findLadderIdx(double scale) {
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
