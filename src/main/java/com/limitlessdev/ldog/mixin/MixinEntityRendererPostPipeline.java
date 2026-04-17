package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessPipeline;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 8c: redirect world rendering into the pipeline's scaled scene target,
 * then blit back to the main framebuffer at native resolution before UI draws.
 *
 * Wraps EntityRenderer.renderWorldPass with three hooks:
 *   HEAD   — save the main FBO handle, bind the scene target.
 *   INVOKE — redirect the vanilla GlStateManager.viewport(displayW, displayH)
 *            call near the top of renderWorldPass so the world rasterizes at
 *            the scaled target's dimensions instead of overshooting past it.
 *   RETURN — run the post-process pass chain against the scene target, blit
 *            it back to main FB with GL_LINEAR (a plain bilinear upscale in
 *            8c — FSR1 EASU/RCAS lands in Phase 9a), then explicitly rebind
 *            main FB and reset the viewport so GUI/HUD renders at native.
 *
 * Only activates when all of:
 *   - LDOGConfig.enablePostProcessPipeline
 *   - PostProcessPipeline.hasConflictingFeatureOn() is false (MSAA owns the
 *     FBO swap when enabled, so the pipeline yields)
 *   - pass == 0 (anaglyph passes 1/2 would composite incorrectly through
 *     a full-blit overwrite)
 *   - RenderTargetManager.isReady() after ensure() succeeds
 *
 * The vanilla GlStateManager.clear(0x4080) call at the top of renderWorldPass
 * runs AFTER our HEAD bind, so it clears the scene target for us — no manual
 * clear needed here.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererPostPipeline {

    @Unique private int ldog$savedFbo = 0;
    @Unique private int ldog$savedMainWidth = 0;
    @Unique private int ldog$savedMainHeight = 0;
    @Unique private boolean ldog$pipelineActive = false;

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void ldog$pipelineBind(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ldog$pipelineActive = false;

        if (!LDOGConfig.enablePostProcessPipeline) return;
        if (PostProcessPipeline.hasConflictingFeatureOn()) return;
        if (pass != 0) return;

        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null) return;

        int mainW = fb.framebufferTextureWidth;
        int mainH = fb.framebufferTextureHeight;
        if (mainW <= 0 || mainH <= 0) return;

        float scale = (float) LDOGConfig.internalRenderScale;
        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        if (!rtm.ensure(mainW, mainH, scale) || !rtm.isReady()) return;

        ldog$savedFbo = fb.framebufferObject;
        ldog$savedMainWidth = mainW;
        ldog$savedMainHeight = mainH;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, rtm.getSceneFbo());
        ldog$pipelineActive = true;
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;viewport(IIII)V",
            ordinal = 0))
    private void ldog$pipelineViewport(int x, int y, int w, int h) {
        if (ldog$pipelineActive) {
            RenderTargetManager rtm = RenderTargetManager.INSTANCE;
            GlStateManager.viewport(0, 0, rtm.getScaledWidth(), rtm.getScaledHeight());
        } else {
            GlStateManager.viewport(x, y, w, h);
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("RETURN"))
    private void ldog$pipelineResolve(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!LDOGConfig.enablePostProcessPipeline) return;

        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null) return;

        if (!ldog$pipelineActive) {
            // Pipeline is enabled but yielded (MSAA on, anaglyph pass, or
            // RTM unavailable). Still tick the pass chain against main FB
            // dimensions so any future pass that wants to composite onto
            // the main framebuffer can run.
            PostProcessPipeline.INSTANCE.onWorldPassRendered(
                fb.framebufferTextureWidth, fb.framebufferTextureHeight,
                pass, partialTicks);
            return;
        }

        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        int scaledW = rtm.getScaledWidth();
        int scaledH = rtm.getScaledHeight();

        // Passes run against the scene target at scaled dimensions.
        PostProcessPipeline.INSTANCE.onWorldPassRendered(scaledW, scaledH, pass, partialTicks);

        // Blit scene target (scaled) -> main FB (native) with GL_LINEAR for
        // cheap bilinear upscaling. FSR1 EASU+RCAS in Phase 9a will replace
        // this with a quality upscaler reading from the scene color texture.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, rtm.getSceneFbo());
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ldog$savedFbo);
        GL30.glBlitFramebuffer(
            0, 0, scaledW, scaledH,
            0, 0, ldog$savedMainWidth, ldog$savedMainHeight,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        // Restore main FB + native viewport so GUI/HUD draws correctly.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ldog$savedFbo);
        GlStateManager.viewport(0, 0, ldog$savedMainWidth, ldog$savedMainHeight);

        ldog$pipelineActive = false;
    }
}
