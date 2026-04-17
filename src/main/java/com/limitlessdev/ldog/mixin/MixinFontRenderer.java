package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.font.SmoothFontHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
        // Only swap for the main Minecraft FontRenderer. Forge's SplashFontRenderer
        // (and potentially other mod subclasses) overrides bindTexture with a
        // private texture pool that throws on unknown ResourceLocations, and runs
        // on a dedicated thread with its own GL context — binding our HD location
        // there would crash and running our filter refresh would hit the wrong
        // context. Pass-through for any subclass.
        if (self.getClass() != FontRenderer.class) {
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
