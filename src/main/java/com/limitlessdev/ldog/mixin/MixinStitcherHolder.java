package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.texture.ExtendedBorderHandler;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * When extended-border mipmaps are active, inflate each Holder's reported
 * sprite dimensions by {@code 2 * border} so the packer leaves halo space
 * between sprites. Only the Holder's internal view is inflated; the sprite's
 * own {@code width}/{@code height} fields stay untouched so external callers
 * (UV math, CTM, model baking) continue to see the sprite's inner size.
 */
@Mixin(Stitcher.Holder.class)
public abstract class MixinStitcherHolder {

    @Redirect(
        method = "<init>(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;getIconWidth()I"))
    private int ldog$inflateWidth(TextureAtlasSprite sprite) {
        int w = sprite.getIconWidth();
        return ExtendedBorderHandler.isActive() ? w + 2 * ExtendedBorderHandler.getBorderSize() : w;
    }

    @Redirect(
        method = "<init>(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;getIconHeight()I"))
    private int ldog$inflateHeight(TextureAtlasSprite sprite) {
        int h = sprite.getIconHeight();
        return ExtendedBorderHandler.isActive() ? h + 2 * ExtendedBorderHandler.getBorderSize() : h;
    }
}
