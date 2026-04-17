package com.limitlessdev.ldog.render.pipeline;

/**
 * Lightweight counters to help validate pipeline wiring before functional passes land.
 */
public final class PipelineDebugStats {

    private static int activePasses;
    private static int lastWidth;
    private static int lastHeight;
    private static long lastFrameNanos;

    private static boolean targetReady;
    private static float targetScale;
    private static int targetScaledWidth;
    private static int targetScaledHeight;
    private static boolean bindingActive;

    private PipelineDebugStats() {}

    public static void update(int activeCount, int width, int height, long nanos) {
        activePasses = activeCount;
        lastWidth = width;
        lastHeight = height;
        lastFrameNanos = nanos;
    }

    public static void updateTargets(boolean ready, float scale, int scaledW, int scaledH) {
        targetReady = ready;
        targetScale = scale;
        targetScaledWidth = scaledW;
        targetScaledHeight = scaledH;
    }

    public static void updateBinding(boolean active) {
        bindingActive = active;
    }

    public static int activePasses() {
        return activePasses;
    }

    public static int lastWidth() {
        return lastWidth;
    }

    public static int lastHeight() {
        return lastHeight;
    }

    public static long lastFrameNanos() {
        return lastFrameNanos;
    }

    public static boolean targetReady() {
        return targetReady;
    }

    public static float targetScale() {
        return targetScale;
    }

    public static int targetScaledWidth() {
        return targetScaledWidth;
    }

    public static int targetScaledHeight() {
        return targetScaledHeight;
    }

    public static boolean bindingActive() {
        return bindingActive;
    }
}

