package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the crosshair / attack indicator. Targets {@code renderAttackIndicator}
 * which is the wider entry point — covers both the simple crosshair and
 * the attack-charge animation that vanilla draws when a sword is charging.
 *
 * Cinematic / screenshot use case. Players who want a hidden HUD can press
 * F1; this lets them keep the rest of the HUD while losing only the crosshair.
 */
@Mixin(GuiIngame.class)
public abstract class MixinGuiIngameCrosshair {

    @Inject(method = "renderAttackIndicator", at = @At("HEAD"), cancellable = true)
    private void ldog$hideCrosshair(float partialTicks, ScaledResolution scaledRes, CallbackInfo ci) {
        if (LDOGConfig.hideCrosshair) ci.cancel();
    }
}
