package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.texture.ExtendedBorderHandler;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Extended-border mipmap support: when active, shift each sprite's origin
 * inward by {@code border} pixels so that {@code initSprite} records the
 * position of the sprite's inner (non-halo) region on the atlas.
 *
 * <p>Combined with {@link MixinStitcherHolder} (which inflates slot dims
 * by {@code 2 * border}), this yields: slot top-left at halo start, inner
 * region starting at {@code slot + border}, another halo past the inner
 * region — exactly the geometry the upload step needs.
 */
@Mixin(Stitcher.class)
public abstract class MixinStitcher {

    @Redirect(
        method = "getStichSlots",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;initSprite(IIIIZ)V"))
    private void ldog$shiftOriginForBorder(TextureAtlasSprite sprite,
                                           int atlasWidth, int atlasHeight,
                                           int originX, int originY, boolean rotated) {
        if (ExtendedBorderHandler.isActive()) {
            int border = ExtendedBorderHandler.getBorderSize();
            sprite.initSprite(atlasWidth, atlasHeight, originX + border, originY + border, rotated);
        } else {
            sprite.initSprite(atlasWidth, atlasHeight, originX, originY, rotated);
        }
    }
}
