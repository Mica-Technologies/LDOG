package com.limitlessdev.ldog.render.pipeline;

/**
 * Lightweight counters to help validate pipeline wiring before functional passes land.
 */
public final class PipelineDebugStats {

    private static int activePasses;
    private static int lastWidth;
    private static int lastHeight;
    private static long lastFrameNanos;

    private PipelineDebugStats() {}

    public static void update(int activeCount, int width, int height, long nanos) {
        activePasses = activeCount;
        lastWidth = width;
        lastHeight = height;
        lastFrameNanos = nanos;
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
}

