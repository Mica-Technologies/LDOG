package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Phase Tier A: zero out the portal/nausea-time interpolation that drives the
 * camera distortion in {@code EntityRenderer.renderWorldPass}. The vanilla
 * gameplay portal counter (which sends the player to the nether) lives on
 * {@code EntityPlayer.timeInPortal} and ticks independently of this read —
 * suppressing only the local interpolation result is purely cosmetic.
 *
 * Targets the local {@code float f1} that vanilla computes from
 * {@code prevTimeInPortal + (timeInPortal - prevTimeInPortal) * partialTicks}.
 * The subsequent {@code if (f1 > 0.0F)} branch then never enters, skipping
 * the rotate+scale shake that produces the nausea distortion.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererNausea {

    @ModifyVariable(method = "renderWorldPass(IFJ)V",
        ordinal = 0,
        at = @At(value = "STORE"),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;applyBobbing(F)V"),
            to   = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;orientCamera(F)V")))
    private float ldog$suppressNausea(float f1) {
        return LDOGConfig.disableNauseaDistortion ? 0.0F : f1;
    }
}
