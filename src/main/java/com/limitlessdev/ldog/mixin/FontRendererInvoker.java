package com.limitlessdev.ldog.mixin;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes FontRenderer's protected {@code bindTexture(ResourceLocation)} so
 * MixinFontRenderer's @Redirect can invoke it on behalf of vanilla rendering
 * with a substituted ResourceLocation (our HD font swap-in).
 */
@Mixin(FontRenderer.class)
public interface FontRendererInvoker {

    // Forge-added method — no SRG mapping, so skip remap lookup.
    @Invoker(value = "bindTexture", remap = false)
    void ldog$invokeBindTexture(ResourceLocation location);
}
