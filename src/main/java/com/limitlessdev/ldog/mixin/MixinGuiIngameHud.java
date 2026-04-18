package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD element hide toggles. Each method here corresponds to a single
 * {@code GuiIngame} render pass that's cleanly isolated (we cancel at
 * HEAD and the rest of the HUD continues unaffected).
 *
 * Note: armor / hunger / air bars are deliberately NOT here — vanilla
 * draws them all inside one {@code renderPlayerStats} method so canceling
 * at HEAD would hide them collectively (or none). Splitting those would
 * need profiler-section ordinal injection which is brittle. Skipped for
 * v1; user can use F1 to hide the entire HUD instead.
 *
 * Portal distortion override is here too (also a GuiIngame method): kills
 * the swirly purple overlay while keeping gameplay portal counter intact.
 */
@Mixin(GuiIngame.class)
public abstract class MixinGuiIngameHud {

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void ldog$hideHotbar(ScaledResolution sr, float partialTicks, CallbackInfo ci) {
        if (LDOGConfig.hideHotbar) ci.cancel();
    }

    @Inject(method = "renderExpBar", at = @At("HEAD"), cancellable = true)
    private void ldog$hideExpBar(ScaledResolution sr, int x, CallbackInfo ci) {
        if (LDOGConfig.hideExperienceBar) ci.cancel();
    }

    @Inject(method = "renderHorseJumpBar", at = @At("HEAD"), cancellable = true)
    private void ldog$hideHorseJump(ScaledResolution sr, int x, CallbackInfo ci) {
        if (LDOGConfig.hideHorseJumpBar) ci.cancel();
    }

    @Inject(method = "renderSelectedItem", at = @At("HEAD"), cancellable = true)
    private void ldog$hideItemTooltip(ScaledResolution sr, CallbackInfo ci) {
        if (LDOGConfig.hideHeldItemTooltip) ci.cancel();
    }

    @Inject(method = "renderPortal", at = @At("HEAD"), cancellable = true)
    private void ldog$disablePortalOverlay(float timeInPortal, ScaledResolution sr, CallbackInfo ci) {
        if (LDOGConfig.disablePortalOverlay) ci.cancel();
    }
}
