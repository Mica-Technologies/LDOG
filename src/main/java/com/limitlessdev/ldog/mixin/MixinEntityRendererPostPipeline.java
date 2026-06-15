package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.EntityReactiveMaskState;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPipeline;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;

import java.nio.IntBuffer;
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
    @Unique private boolean ldog$reactiveMaskActive = false;

    // Phase 9c.3-A: cached glDrawBuffers args. [COLOR0, COLOR1] when the
    // reactive-mask attachment is being populated, [COLOR0] otherwise.
    @Unique private static final IntBuffer LDOG_DRAW_BUF_MRT = ldog$mkBuf(
        GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1);
    @Unique private static final IntBuffer LDOG_DRAW_BUF_SINGLE = ldog$mkBuf(
        GL30.GL_COLOR_ATTACHMENT0);

    @Unique
    private static IntBuffer ldog$mkBuf(int... attachments) {
        IntBuffer buf = BufferUtils.createIntBuffer(attachments.length);
        for (int a : attachments) buf.put(a);
        buf.flip();
        return buf;
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void ldog$pipelineBind(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ldog$pipelineActive = false;
        ldog$reactiveMaskActive = false;

        if (!LDOGConfig.enablePostProcessPipeline) return;
        if (PostProcessPipeline.hasConflictingFeatureOn()) return;
        if (pass != 2) return;

        // Phase 9c.3-C: reset the per-frame entity-render queue at the start
        // of every world pass so MixinRenderManagerEntityMV's captures populate
        // a fresh list. The EntityMotionVectorPass drains it later in the
        // pipeline tick.
        com.limitlessdev.ldog.render.pipeline.EntityRenderStateCache.get().beginFrame();

        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null) return;

        int mainW = fb.framebufferTextureWidth;
        int mainH = fb.framebufferTextureHeight;
        if (mainW <= 0 || mainH <= 0) return;

        float scale = (float) LDOGConfig.internalRenderScale;
        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        // Pass HDR flag so this mixin stays in sync with PostProcessPipeline.
        // Earlier the 3-arg overload defaulted HDR=false here — each frame the
        // pipeline's ensure(true) and this ensure(false) fought, reallocating
        // targets every frame and producing a black screen with HDR enabled.
        if (!rtm.ensure(mainW, mainH, scale, LDOGConfig.enableHDRPipeline) || !rtm.isReady()) return;

        ldog$savedFbo = fb.framebufferObject;
        ldog$savedMainWidth = mainW;
        ldog$savedMainHeight = mainH;

        // Shader-pack deferred path: when a pack's gbuffers are driving the
        // world, render into the MRT G-buffer (colortex0 = this same scene
        // colour texture + aux colortex1..N) instead of the plain scene FBO.
        // Reactive masking is mutually exclusive with the deferred path.
        boolean deferred = false;
        if (ShaderPackGbufferManager.isDeferredActive()) {
            int gfbo = ShaderPackGbufferManager.beginWorldGBuffer();
            if (gfbo != 0) deferred = true;
        }
        if (!deferred) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, rtm.getSceneFbo());
        }

        // Phase 9c.3-A: when entity reactive masking is active alongside TAA,
        // expand drawBuffers to populate sceneFbo's color1 attachment too.
        // glColorMaski(1, F,F,F,F) here means non-entity scene draws (sky,
        // chunks, weather) write only to color0; the mask stays at the
        // glClear value (0) until MixinRenderGlobal flips colorMaski(1) on
        // around the entity loop. The vanilla GlStateManager.clear that
        // runs after this HEAD inject clears BOTH attached colour buffers
        // because both are listed in drawBuffers — no manual mask clear.
        if (!deferred && LDOGConfig.enableTAA && LDOGConfig.enableEntityReactiveMask) {
            LDOG_DRAW_BUF_MRT.position(0);
            GL20.glDrawBuffers(LDOG_DRAW_BUF_MRT);
            GL30.glColorMaski(1, false, false, false, false);
            ldog$reactiveMaskActive = true;
        }
        EntityReactiveMaskState.setActive(ldog$reactiveMaskActive);

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

        // World draw done — the G-buffer is no longer the active draw target.
        ShaderPackGbufferManager.endWorldGBuffer();

        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null) return;

        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        int mainFbo = ldog$pipelineActive ? ldog$savedFbo : fb.framebufferObject;
        int mainW = ldog$pipelineActive ? ldog$savedMainWidth : fb.framebufferTextureWidth;
        int mainH = ldog$pipelineActive ? ldog$savedMainHeight : fb.framebufferTextureHeight;

        // Phase 9c.3-A: restore single-attachment drawBuffers + open colorMaski
        // BEFORE the pipeline runs. Pipeline passes write through their own
        // FBOs (main FB or pingFbo), neither of which has color1 attached, so
        // the MRT layout would be invalid for them. Restoring colorMaski(1, T)
        // also keeps GL state predictable for any code that runs after.
        if (ldog$reactiveMaskActive) {
            LDOG_DRAW_BUF_SINGLE.position(0);
            GL20.glDrawBuffers(LDOG_DRAW_BUF_SINGLE);
            GL30.glColorMaski(1, true, true, true, true);
        }

        PostProcessContext ctx = new PostProcessContext(
            mainFbo, mainW, mainH,
            rtm.getSceneFbo(), rtm.getSceneColorTexture(),
            rtm.getScaledWidth(), rtm.getScaledHeight(),
            ldog$pipelineActive, ldog$reactiveMaskActive, pass, partialTicks);

        PostProcessPipeline.INSTANCE.onFrame(ctx);

        // Force the final framebuffer opaque when a shader pack is driving the
        // image. Pack gbuffer/composite/final stages routinely write alpha < 1
        // (they only care about RGB), and in windowed/borderless mode the OS
        // composites a sub-1 alpha framebuffer as a see-through window — the
        // "whole game goes transparent" symptom. Clear just the alpha to 1
        // without touching RGB. Cheap; skipped entirely when no pack is active.
        boolean ldog$packActive = ShaderPackGbufferManager.isActive()
            || com.limitlessdev.ldog.render.shaderpack.ShaderPackManager.INSTANCE.getRuntime() != null;
        if (ldog$pipelineActive && ldog$packActive) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
            GlStateManager.colorMask(false, false, false, true);
            GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GlStateManager.clear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
            GlStateManager.colorMask(true, true, true, true);
        }

        if (ldog$pipelineActive) {
            // Defensive restore — BilinearBlitPass already leaves main FB
            // bound, but a future pass might not. Keep GUI/HUD draw state
            // predictable regardless of what ran in the chain.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
            GlStateManager.viewport(0, 0, mainW, mainH);
            ldog$pipelineActive = false;
        }
        ldog$reactiveMaskActive = false;
        EntityReactiveMaskState.setActive(false);
    }
}
