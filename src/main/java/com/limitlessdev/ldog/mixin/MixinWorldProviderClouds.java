package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cloud altitude override. Vanilla cloud Y comes from the dimension type
 * (Overworld = 128, Nether = no clouds, etc.). When the user sets a positive
 * override, we return it; -1 means "leave vanilla alone".
 *
 * Targets WorldProvider so the override applies to every dimension that
 * renders clouds, not just the Overworld. Mods that subclass WorldProvider
 * (custom dimensions) inherit the override automatically.
 */
@Mixin(WorldProvider.class)
public abstract class MixinWorldProviderClouds {

    @Inject(method = "getCloudHeight", at = @At("HEAD"), cancellable = true)
    private void ldog$overrideCloudHeight(CallbackInfoReturnable<Float> cir) {
        int override = LDOGConfig.cloudHeightOverride;
        if (override >= 0) {
            cir.setReturnValue((float) override);
        }
    }
}
