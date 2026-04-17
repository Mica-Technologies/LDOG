package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PipelineDebugStats;
import com.limitlessdev.ldog.render.pipeline.PostProcessPipeline;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a compact performance metrics HUD in the upper-left corner.
 *
 * Displays FPS, frame time (ms), memory usage, and LDOG culling stats.
 * Frame time is tracked as a rolling average over the last 60 frames
 * for stable readability.
 *
 * Hidden when F3 is visible (to avoid overlapping vanilla debug screen)
 * and when disabled via config.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class PerformanceOverlayRenderer {

    private static final int MAX_SAMPLES = 60;
    private static final long[] frameTimes = new long[MAX_SAMPLES];
    private static int frameIndex = 0;
    private static int frameCount = 0;
    private static long lastFrameNano = 0;

    // Colors
    private static final int COLOR_HEADER = 0x55FFFF;  // cyan
    private static final int COLOR_GOOD = 0x55FF55;     // green
    private static final int COLOR_WARN = 0xFFFF55;     // yellow
    private static final int COLOR_BAD = 0xFF5555;       // red
    private static final int COLOR_LABEL = 0xAAAAAA;     // gray
    private static final int BG_COLOR = 0x90000000;      // semi-transparent black

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!LDOGConfig.enablePerformanceOverlay) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) return;

        trackFrameTime();

        FontRenderer font = mc.fontRenderer;
        int lineHeight = font.FONT_HEIGHT + 1;
        int x = 4;
        int y = 4;
        int maxWidth = 0;

        // Collect lines with their colors
        String[] lines = buildLines();
        int[] colors = buildColors(lines);

        // Compute background width
        for (String line : lines) {
            int w = font.getStringWidth(line);
            if (w > maxWidth) maxWidth = w;
        }

        // Draw background
        Gui.drawRect(x - 2, y - 2,
            x + maxWidth + 4, y + lines.length * lineHeight + 2, BG_COLOR);

        // Draw lines
        for (int i = 0; i < lines.length; i++) {
            font.drawStringWithShadow(lines[i], x, y + i * lineHeight, colors[i]);
        }
    }

    private static void trackFrameTime() {
        long now = System.nanoTime();
        if (lastFrameNano != 0) {
            frameTimes[frameIndex] = now - lastFrameNano;
            frameIndex = (frameIndex + 1) % MAX_SAMPLES;
            if (frameCount < MAX_SAMPLES) frameCount++;
        }
        lastFrameNano = now;
    }

    private static double getAverageFrameTimeMs() {
        if (frameCount == 0) return 0;
        long sum = 0;
        for (int i = 0; i < frameCount; i++) {
            sum += frameTimes[i];
        }
        return (sum / (double) frameCount) / 1_000_000.0;
    }

    private static double getMaxFrameTimeMs() {
        if (frameCount == 0) return 0;
        long max = 0;
        for (int i = 0; i < frameCount; i++) {
            if (frameTimes[i] > max) max = frameTimes[i];
        }
        return max / 1_000_000.0;
    }

    private static String[] buildLines() {
        int fps = Minecraft.getDebugFPS();
        double avgMs = getAverageFrameTimeMs();
        double maxMs = getMaxFrameTimeMs();

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);

        int culled = LDOGStats.entitiesCulledByDistance + LDOGStats.entitiesSkippedByLOD;
        int pCulled = LDOGStats.particlesCulled;
        int teCulled = LDOGStats.tileEntitiesCulled;

        List<String> lines = new ArrayList<>(5);
        lines.add("LDOG Performance");
        lines.add(String.format("%d FPS  %.1f ms avg  %.1f ms max", fps, avgMs, maxMs));
        lines.add(String.format("Mem: %d / %d MB", usedMB, maxMB));
        lines.add(String.format("Culled: %dE %dP %dTE", culled, pCulled, teCulled));

        if (LDOGConfig.enablePostProcessPipeline) {
            lines.add(buildPipelineLine());
        }

        return lines.toArray(new String[0]);
    }

    private static int[] buildColors(String[] lines) {
        int fps = Minecraft.getDebugFPS();

        int fpsColor;
        if (fps >= 60) fpsColor = COLOR_GOOD;
        else if (fps >= 30) fpsColor = COLOR_WARN;
        else fpsColor = COLOR_BAD;

        List<Integer> colors = new ArrayList<>(5);
        colors.add(COLOR_HEADER);
        colors.add(fpsColor);
        colors.add(COLOR_LABEL);
        colors.add(COLOR_LABEL);

        if (LDOGConfig.enablePostProcessPipeline) {
            colors.add(pipelineLineColor());
        }

        int[] out = new int[colors.size()];
        for (int i = 0; i < out.length; i++) out[i] = colors.get(i);
        return out;
    }

    private static String buildPipelineLine() {
        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        if (PipelineDebugStats.bindingActive()) {
            double ms = PipelineDebugStats.lastFrameNanos() / 1_000_000.0;
            return String.format("Pipeline: ACTIVE [%s]  %dx%d @ %.2fx  %dp %.2fms",
                UpscalerAlgorithm.selected().displayName(),
                rtm.getScaledWidth(), rtm.getScaledHeight(), rtm.getScale(),
                PipelineDebugStats.activePasses(), ms);
        }
        String reason = pipelineYieldReason();
        return "Pipeline: " + reason;
    }

    private static int pipelineLineColor() {
        if (PipelineDebugStats.bindingActive()) return COLOR_GOOD;
        if (PostProcessPipeline.hasConflictingFeatureOn()) return COLOR_WARN;
        if (Minecraft.getMinecraft().gameSettings.anaglyph) return COLOR_WARN;
        if (!RenderTargetManager.INSTANCE.isReady()) return COLOR_BAD;
        return COLOR_BAD;
    }

    private static String pipelineYieldReason() {
        if (PostProcessPipeline.hasConflictingFeatureOn()) return "YIELD (msaa)";
        if (Minecraft.getMinecraft().gameSettings.anaglyph) return "YIELD (anaglyph)";
        if (!RenderTargetManager.INSTANCE.isReady()) return "UNAVAIL (no GL3)";
        return "INACTIVE";
    }
}
