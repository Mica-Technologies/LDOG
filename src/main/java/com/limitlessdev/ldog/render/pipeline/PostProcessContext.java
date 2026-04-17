package com.limitlessdev.ldog.render.pipeline;

/**
 * Frame-local context passed to post-process stages.
 *
 * Carries both the vanilla main-framebuffer info (what GUI/HUD will draw to)
 * and the pipeline's scaled scene-target info (what the world was rendered
 * into when binding was active). Passes that produce the final upscaled
 * image should read from the scene target and write to the main framebuffer.
 */
public final class PostProcessContext {

    private final int mainFbo;
    private final int mainWidth;
    private final int mainHeight;
    private final int sceneFbo;
    private final int sceneColorTex;
    private final int sceneWidth;
    private final int sceneHeight;
    private final boolean bindingActive;
    private final int pass;
    private final float partialTicks;

    public PostProcessContext(
        int mainFbo, int mainWidth, int mainHeight,
        int sceneFbo, int sceneColorTex, int sceneWidth, int sceneHeight,
        boolean bindingActive, int pass, float partialTicks) {
        this.mainFbo = mainFbo;
        this.mainWidth = mainWidth;
        this.mainHeight = mainHeight;
        this.sceneFbo = sceneFbo;
        this.sceneColorTex = sceneColorTex;
        this.sceneWidth = sceneWidth;
        this.sceneHeight = sceneHeight;
        this.bindingActive = bindingActive;
        this.pass = pass;
        this.partialTicks = partialTicks;
    }

    public int mainFbo() { return mainFbo; }
    public int mainWidth() { return mainWidth; }
    public int mainHeight() { return mainHeight; }
    public int sceneFbo() { return sceneFbo; }
    public int sceneColorTexture() { return sceneColorTex; }
    public int sceneWidth() { return sceneWidth; }
    public int sceneHeight() { return sceneHeight; }

    /**
     * True when the mixin successfully redirected world rendering into the
     * scene target this frame. Passes that expect fresh scene-target pixels
     * (the bilinear blit, a future FSR1 EASU pass) must skip when this is
     * false, otherwise they'd composite stale data onto the main framebuffer.
     */
    public boolean bindingActive() { return bindingActive; }

    public int pass() { return pass; }
    public float partialTicks() { return partialTicks; }

    /**
     * Width/height of the stage this pass should write for. Default is main
     * framebuffer dimensions — passes that need to composite onto the final
     * display output read these. Passes operating inside the scene target
     * (early filters before upscale) should use sceneWidth/sceneHeight.
     */
    public int width() { return mainWidth; }
    public int height() { return mainHeight; }
}
