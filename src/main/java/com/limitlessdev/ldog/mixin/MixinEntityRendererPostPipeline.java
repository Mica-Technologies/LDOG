package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPipeline;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 8c: redirect world rendering into the pipeline's scaled scene target,
 * then hand control to PostProcessPipeline at RETURN so the pass chain
 * (currently just BilinearBlitPass) produces the final upscaled image on the
 * main framebuffer before UI draws.
 *
 * Three hooks:
 *   HEAD   — save the main FBO handle, bind the scene target.
 *   INVOKE — redirect the vanilla GlStateManager.viewport(displayW, displayH)
 *            call near the top of renderWorldPass so world rasterizes at the
 *            scaled target's dimensions. Without this the viewport extends
 *            past the scene FBO and rendering is clipped or undefined.
 *   RETURN — build a PostProcessContext with main + scene state and call
 *            PostProcessPipeline.onFrame. The pass chain is responsible for
 *            writing final pixels to the main framebuffer. After passes run,
 *            re-bind main FB and reset viewport so GUI/HUD renders at native.
 *
 * The vanilla GlStateManager.clear(0x4080) call at the top of renderWorldPass
 * runs AFTER our HEAD bind, so it clears the scene target for us — no manual
 * clear needed here.
 *
 * Activation guards (all must hold):
 *   - LDOGConfig.enablePostProcessPipeline
 *   - PostProcessPipeline.hasConflictingFeatureOn() is false (MSAA yields)
 *   - pass == 2 — this is the non-anaglyph world pass, i.e. the call MC
 *     makes when gameSettings.anaglyph is off (the common case). Pass 0 is
 *     the anaglyph red-eye, pass 1 the green+blue eye; both composite via
 *     colormask into the same framebuffer and our scene-target blit-back
 *     would clobber the interleaving, so anaglyph users are not served by
 *     the pipeline in 8c.
 *   - RenderTargetManager.ensure() succeeds and isReady()
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
        if (pass != 2) return;

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

        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        int mainFbo = ldog$pipelineActive ? ldog$savedFbo : fb.framebufferObject;
        int mainW = ldog$pipelineActive ? ldog$savedMainWidth : fb.framebufferTextureWidth;
        int mainH = ldog$pipelineActive ? ldog$savedMainHeight : fb.framebufferTextureHeight;

        PostProcessContext ctx = new PostProcessContext(
            mainFbo, mainW, mainH,
            rtm.getSceneFbo(), rtm.getSceneColorTexture(),
            rtm.getScaledWidth(), rtm.getScaledHeight(),
            ldog$pipelineActive, pass, partialTicks);

        PostProcessPipeline.INSTANCE.onFrame(ctx);

        if (ldog$pipelineActive) {
            // Defensive restore — BilinearBlitPass already leaves main FB
            // bound, but a future pass might not. Keep GUI/HUD draw state
            // predictable regardless of what ran in the chain.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
            GlStateManager.viewport(0, 0, mainW, mainH);
            ldog$pipelineActive = false;
        }
    }
}
