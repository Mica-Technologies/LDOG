package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.font.SmoothFontHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wires {@link SmoothFontHandler} into vanilla font rendering:
 * <ul>
 *   <li>The {@code bindTexture(locationFontTexture)} call inside
 *       {@code renderDefaultChar} is redirected to our handler, which decides
 *       whether to bind the HD swap-in or the vanilla texture. Unicode page
 *       rendering is deliberately untouched — those live in separate pages and
 *       are out of scope for this phase.</li>
 *   <li>After {@code readFontTexture} computes char widths by scanning the PNG,
 *       an injection overwrites any entries the resource pack provides in
 *       {@code ascii.properties}.</li>
 * </ul>
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {

    @Shadow
    protected final int[] charWidth = new int[256];

    // FontRenderer.bindTexture is Forge-added (no SRG mapping) so the @At target
    // uses remap = false — dev and prod both see the same method name.
    @Redirect(
        method = "renderDefaultChar",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            remap = false))
    private void ldog$bindActiveFontTexture(FontRenderer self, ResourceLocation vanillaFont) {
        SmoothFontHandler handler = SmoothFontHandler.INSTANCE;
        handler.noteFirstBindIfNeeded();
        FontRendererInvoker invoker = (FontRendererInvoker) self;
        if (handler.hasHDFont()) {
            invoker.ldog$invokeBindTexture(handler.getHDFontLocation());
        } else {
            invoker.ldog$invokeBindTexture(vanillaFont);
        }
    }

    @Inject(method = "readFontTexture", at = @At("RETURN"))
    private void ldog$applyWidthOverrides(CallbackInfo ci) {
        SmoothFontHandler handler = SmoothFontHandler.INSTANCE;
        for (int i = 0; i < this.charWidth.length; i++) {
            int override = handler.getWidthOverride(i);
            if (override >= 0) {
                this.charWidth[i] = override;
            }
        }
    }
}
