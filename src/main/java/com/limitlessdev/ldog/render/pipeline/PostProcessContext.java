package com.limitlessdev.ldog.render.pipeline;

/**
 * Frame-local context passed to post-process stages.
 */
public final class PostProcessContext {

    private final int width;
    private final int height;
    private final int pass;
    private final float partialTicks;

    public PostProcessContext(int width, int height, int pass, float partialTicks) {
        this.width = width;
        this.height = height;
        this.pass = pass;
        this.partialTicks = partialTicks;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pass() {
        return pass;
    }

    public float partialTicks() {
        return partialTicks;
    }
}

