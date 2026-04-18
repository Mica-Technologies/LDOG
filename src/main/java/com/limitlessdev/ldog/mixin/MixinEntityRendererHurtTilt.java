package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the camera-roll animation that vanilla applies for a few ticks
 * after the player takes damage. Common request for accessibility (motion
 * sickness) and for cinematic recordings where the tilt is distracting.
 *
 * Cancellation here is purely visual — gameplay damage is unaffected, the
 * hit sound + hurt animation on the player model still play normally.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererHurtTilt {

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void ldog$skipHurtTilt(float partialTicks, CallbackInfo ci) {
        if (LDOGConfig.disableDamageTilt) ci.cancel();
    }
}
