package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.UpscalerAlgorithm;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * FSR1-style Edge Adaptive Spatial Upsampling pass.
 *
 * Phase 9a.2.1 placeholder: ships the upscaler-selector plumbing so the
 * config and GUI can target "fsr1", but the actual EASU fragment shader
 * lands in 9a.2.3. Until then, this pass falls back to the same bilinear
 * glBlitFramebuffer that BilinearBlitPass uses, so selecting FSR1 produces
 * a working (if not yet sharper) image instead of a black screen.
 *
 * The one-shot INFO log on first execute makes the fallback visible without
 * spamming the console every frame.
 */
public final class FSR1EASUPass implements PostProcessPass {

    private boolean loggedFallback;

    @Override
    public String id() {
        return "fsr1_easu";
    }

    @Override
    public void init(int width, int height) {
        // 9a.2.3 will allocate the shader program + VAO here.
    }

    @Override
    public void resize(int width, int height) {
        // 9a.2.3 will update any resolution-dependent uniforms here.
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (!ctx.bindingActive()) return;

        if (!loggedFallback) {
            loggedFallback = true;
            LDOGMod.LOGGER.info(
                "LDOG: FSR1 upscaler selected but shader not yet implemented (Phase 9a.2.3) — falling back to bilinear blit for now");
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.sceneFbo());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
        GL30.glBlitFramebuffer(
            0, 0, ctx.sceneWidth(), ctx.sceneHeight(),
            0, 0, ctx.mainWidth(), ctx.mainHeight(),
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
    }

    @Override
    public void dispose() {
        // 9a.2.3 will release the shader program + VAO here.
    }

    @Override
    public boolean isEnabled() {
        return UpscalerAlgorithm.selected() == UpscalerAlgorithm.FSR1;
    }
}
