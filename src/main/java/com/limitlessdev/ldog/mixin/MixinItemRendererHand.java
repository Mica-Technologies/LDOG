package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.shaderpack.GbufferProgram;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the held item / hand in first-person view. Useful for cinematic
 * shots and screenshots. Targets the public single-arg entry point of
 * renderItemInFirstPerson — cancelling here suppresses both hands (main
 * + off) since the per-hand internal overload runs after this entry.
 *
 * Third-person view is unaffected — held items still render on the player
 * model.
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemRendererHand {

    @Inject(method = "renderItemInFirstPerson(F)V", at = @At("HEAD"), cancellable = true)
    private void ldog$hideHand(float partialTicks, CallbackInfo ci) {
        if (LDOGConfig.hideHand) {
            ci.cancel();
            return;
        }
        // Bind the pack's hand gbuffer program around the first-person draw.
        // After the cancel so begin/end stay balanced when the hand is hidden.
        ShaderPackGbufferManager.begin(GbufferProgram.HAND);
    }

    @Inject(method = "renderItemInFirstPerson(F)V", at = @At("RETURN"))
    private void ldog$handGbufferEnd(float partialTicks, CallbackInfo ci) {
        ShaderPackGbufferManager.end();
    }
}
