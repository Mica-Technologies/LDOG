package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the screen-darkening vignette overlay (worldborder + hurt flash).
 *
 * Vanilla {@code renderVignette} draws both effects in one method via a
 * {@code GlStateManager.color(0, f, f, 1)} multiplicative blend — we cancel
 * the whole method when the toggle is on. The trade-off: users who only
 * want hurt-flash off but keep worldborder vignette aren't served. Could
 * be split via @Redirect on the color call later if requested.
 */
@Mixin(GuiIngame.class)
public abstract class MixinGuiIngameVignette {

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void ldog$skipVignette(float lightLevel, ScaledResolution scaledRes, CallbackInfo ci) {
        if (LDOGConfig.disableHurtVignette) ci.cancel();
    }
}
