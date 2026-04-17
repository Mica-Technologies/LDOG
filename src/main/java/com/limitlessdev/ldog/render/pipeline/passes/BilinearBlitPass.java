package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Default final-stage upscale: blit the scaled scene target to the main
 * framebuffer at native resolution using GL_LINEAR (bilinear filtering).
 *
 * This is the Phase 8c/9a.1 baseline. Phase 9a.2 will replace this with an
 * FSR1-style EASU pass that reads the scene color texture through a shader
 * for higher quality at the same perf cost. Until then, this pass proves
 * the plumbing end-to-end.
 *
 * Skips when binding was not active this frame (e.g. MSAA owns the FBO
 * swap, or an anaglyph non-zero pass). Blitting stale scene-target data
 * onto the main framebuffer would composite a one-frame-old world image.
 */
public final class BilinearBlitPass implements PostProcessPass {

    @Override
    public String id() {
        return "bilinear_blit";
    }

    @Override
    public void init(int width, int height) {
        // Stateless — nothing to allocate.
    }

    @Override
    public void resize(int width, int height) {
        // Stateless — nothing to resize.
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (!ctx.bindingActive()) return;

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.sceneFbo());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
        GL30.glBlitFramebuffer(
            0, 0, ctx.sceneWidth(), ctx.sceneHeight(),
            0, 0, ctx.mainWidth(), ctx.mainHeight(),
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        // Leave main framebuffer bound so any subsequent pass or the mixin's
        // viewport restore has a consistent state.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
    }

    @Override
    public void dispose() {
        // Stateless — nothing to free.
    }

    @Override
    public boolean isEnabled() {
        return UpscalerAlgorithm.selected() == UpscalerAlgorithm.BILINEAR;
    }
}
