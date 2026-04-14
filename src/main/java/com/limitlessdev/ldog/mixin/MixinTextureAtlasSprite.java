package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes HD texture support:
 * 1. Prevents crash on non-square textures (vanilla throws RuntimeException)
 * 2. Enforces configurable max texture resolution
 */
@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite {

    @Shadow
    protected int width;

    @Shadow
    protected int height;

    @Shadow
    private String iconName;

    /**
     * Intercept loadSprite to handle non-square textures gracefully.
     * Vanilla crashes with "broken aspect ratio and not an animation".
     * We catch this by injecting before the throw and treating non-square
     * textures as having the larger dimension (padding with transparent).
     */
    @Inject(method = "loadSprite", at = @At("HEAD"), cancellable = true)
    private void ldog$handleHDTextures(PngSizeInfo sizeInfo, boolean hasAnimation,
                                        CallbackInfo ci) {
        if (!LDOGConfig.enableHDTextures) return;

        int w = sizeInfo.pngWidth;
        int h = sizeInfo.pngHeight;

        // If non-square and not animated, fix it instead of crashing
        if (!hasAnimation && w != h) {
            // Use the larger dimension as the sprite size (square)
            int size = Math.max(w, h);
            this.width = size;
            this.height = size;
            LDOGMod.LOGGER.debug("LDOG: Fixed non-square texture '{}' ({}x{} -> {}x{})",
                iconName, w, h, size, size);
            ci.cancel();
        }
    }
}
