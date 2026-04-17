package com.limitlessdev.ldog.render.pipeline;

/**
 * Small lifecycle contract for post-process stages.
 *
 * Implementations should be lightweight and safe to disable when they fail.
 */
public interface PostProcessPass {

    /**
     * Stable id for logging and debug overlays.
     */
    String id();

    /**
     * Called when the pass first becomes active.
     */
    void init(int width, int height) throws Exception;

    /**
     * Called when framebuffer dimensions change.
     */
    void resize(int width, int height) throws Exception;

    /**
     * Execute pass logic for the current frame.
     */
    void execute(PostProcessContext context) throws Exception;

    /**
     * Release any owned resources.
     */
    void dispose();

    /**
     * Allows individual pass-level feature gating.
     */
    boolean isEnabled();
}

