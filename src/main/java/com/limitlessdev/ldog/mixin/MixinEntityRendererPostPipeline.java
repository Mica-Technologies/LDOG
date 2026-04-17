package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 8a scaffold hook.
 *
 * For PR-8a.1 this only drives pipeline lifecycle on renderWorldPass return,
 * intentionally without changing draw output.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererPostPipeline {

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("RETURN"))
    private void ldog$postPipelineTick(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (!LDOGConfig.enablePostProcessPipeline) return;

        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null) return;

        PostProcessPipeline.INSTANCE.onWorldPassRendered(
            fb.framebufferTextureWidth,
            fb.framebufferTextureHeight,
            pass,
            partialTicks
        );
    }
}

