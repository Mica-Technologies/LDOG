package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
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
        if (LDOGConfig.hideHand) ci.cancel();
    }
}
