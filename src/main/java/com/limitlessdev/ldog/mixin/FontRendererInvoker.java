package com.limitlessdev.ldog.mixin;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes FontRenderer internals so {@link com.limitlessdev.ldog.render.font.SmoothFontHandler}
 * can substitute the bound texture at render time and apply width overrides
 * directly to the sprite's width table after reload.
 */
@Mixin(FontRenderer.class)
public interface FontRendererInvoker {

    // Forge-added method — no SRG mapping, so skip remap lookup.
    @Invoker(value = "bindTexture", remap = false)
    void ldog$invokeBindTexture(ResourceLocation location);

    @Accessor("charWidth")
    int[] ldog$getCharWidth();
}
