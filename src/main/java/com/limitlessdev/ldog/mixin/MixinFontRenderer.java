package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.font.SmoothFontHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
        FontRendererInvoker invoker = (FontRendererInvoker) self;
        // Only swap for the main Minecraft FontRenderer.
        //
        // 1. Forge's SplashFontRenderer (and potentially other mod subclasses)
        //    overrides bindTexture with a private texture pool that throws on
        //    unknown ResourceLocations, and runs on a dedicated thread with its
        //    own GL context — binding our HD location there would crash and
        //    running our filter refresh would hit the wrong context.
        // 2. Minecraft#standardGalacticFontRenderer is an *exact* FontRenderer
        //    instance, so the class check alone let the HD/TTF font replace the
        //    enchanting table's Standard Galactic glyphs. It is distinguished by
        //    its texture (textures/font/ascii_sga.png), which the redirected
        //    bindTexture call hands us as `vanillaFont`.
        if (self.getClass() != FontRenderer.class
                || !"textures/font/ascii.png".equals(vanillaFont.getPath())
                || !"minecraft".equals(vanillaFont.getNamespace())) {
            invoker.ldog$invokeBindTexture(vanillaFont);
            return;
        }
        SmoothFontHandler handler = SmoothFontHandler.INSTANCE;
        if (handler.hasCustomFont()) {
            invoker.ldog$invokeBindTexture(handler.getCustomFontLocation());
        } else {
            invoker.ldog$invokeBindTexture(vanillaFont);
        }
        handler.onFirstBindAfterReload();
    }

    /**
     * Halves the offset of bold text's second pass while an HD or TTF font is
     * active. Vanilla draws bold by rendering each glyph twice, one whole unit
     * apart, which thickens an 8-pixel bitmap font by exactly one of its
     * pixels. An HD glyph's strokes are much thinner than a unit, so the two
     * copies separate and read as a ghost image. OptiFine does the same for
     * its custom fonts.
     *
     * <p>The local is {@code f1} in {@code renderStringAtPos}, the first float
     * stored there: 1 for the default font, already 0.5 for the Unicode font.
     */
    @ModifyVariable(
        method = "renderStringAtPos",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0)
    private float ldog$hdBoldOffset(float offset) {
        if (offset != 1.0F || ((Object) this).getClass() != FontRenderer.class) return offset;
        return SmoothFontHandler.INSTANCE.hasCustomFont() ? 0.5F : offset;
    }

    /**
     * The extra advance a bold glyph gets ({@code ++f} after the second pass)
     * follows the same halving, so bold HD text is not spaced a whole unit
     * wider per letter than the offset that now thickens it. It is the second
     * {@code 1.0F} constant in {@code renderStringAtPos}; the first is the
     * {@code f1} ternary handled above.
     */
    @ModifyConstant(
        method = "renderStringAtPos",
        constant = @Constant(floatValue = 1.0F, ordinal = 1))
    private float ldog$hdBoldAdvance(float advance) {
        if (((Object) this).getClass() != FontRenderer.class) return advance;
        return SmoothFontHandler.INSTANCE.hasCustomFont() ? 0.5F : advance;
    }

    /**
     * Forces the {@code dropShadow} argument of the main drawString overload
     * to {@code false} when the user disables font drop shadows. This is the
     * funnel for all text rendering — drawStringWithShadow calls this with
     * {@code true}, and renderString reads the same boolean — so a single
     * modify-variable here catches every caller without needing to patch
     * every draw site.
     */
    @ModifyVariable(
        method = "drawString(Ljava/lang/String;FFIZ)I",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0)
    private boolean ldog$maybeSuppressShadow(boolean dropShadow) {
        return LDOGConfig.fontDropShadows ? dropShadow : false;
    }
}
