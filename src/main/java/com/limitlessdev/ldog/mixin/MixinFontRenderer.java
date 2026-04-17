package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.font.SmoothFontHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the {@code bindTexture(locationFontTexture)} call inside
 * {@code renderDefaultChar} to our handler, which decides whether to bind the
 * HD swap-in or the vanilla texture. Unicode page rendering is deliberately
 * untouched.
 *
 * <p>Width overrides are applied separately, directly from
 * {@link SmoothFontHandler#onResourceManagerReload} via the
 * {@link FontRendererInvoker} accessor — mixin-based {@code @Inject(TAIL)} on
 * {@code readFontTexture} would run before our handler's reload listener (MC
 * registers FontRenderer first, so it fires first), leaving us one reload
 * behind.
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {

    // FontRenderer.bindTexture is Forge-added (no SRG mapping) so the @At target
    // uses remap = false — dev and prod both see the same method name.
    @Redirect(
        method = "renderDefaultChar",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            remap = false))
    private void ldog$bindActiveFontTexture(FontRenderer self, ResourceLocation vanillaFont) {
        SmoothFontHandler handler = SmoothFontHandler.INSTANCE;
        FontRendererInvoker invoker = (FontRendererInvoker) self;
        if (handler.hasHDFont()) {
            invoker.ldog$invokeBindTexture(handler.getHDFontLocation());
            handler.onFirstBindAfterReload();
        } else {
            invoker.ldog$invokeBindTexture(vanillaFont);
            handler.onFirstBindAfterReload();
        }
    }
}
