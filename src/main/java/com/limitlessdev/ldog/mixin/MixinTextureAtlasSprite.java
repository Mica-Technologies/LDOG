package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.compat.OptiFineCompat;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.texture.SquarePadding;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * HD texture support: lets a non-square, non-animated sprite stitch instead
 * of crashing vanilla's {@code loadSprite} ("broken aspect ratio and not an
 * animation").
 *
 * <p>Two halves, and both are required. {@code loadSprite} only records the
 * sprite's dimensions, so declaring the sprite square there is not enough:
 * {@code loadSpriteFrames} then reads the PNG and stores a pixel array of the
 * PNG's real {@code w*h} length, while every later consumer (mipmap
 * generation, {@code glTexSubImage2D} upload) walks {@code width*height} of
 * the square we declared. That mismatch either overruns the array or uploads
 * shifted garbage. So the frame data is padded to the declared square
 * (transparent fill, image anchored top-left) once it has been read.
 */
@Mixin(TextureAtlasSprite.class)
public abstract class MixinTextureAtlasSprite {

    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow private String iconName;
    @Shadow private AnimationMetadataSection animationMetadata;
    @Shadow protected int frameCounter;
    @Shadow protected int tickCounter;
    @Shadow protected List<int[][]> framesTextureData;

    @Shadow public abstract void setFramesTextureData(List<int[][]> newFramesTextureData);

    /** Source PNG dimensions when this sprite was declared square; 0 otherwise. */
    @Unique private int ldog$padSrcWidth;
    @Unique private int ldog$padSrcHeight;

    /**
     * Replaces vanilla's {@code loadSprite} for the non-square, non-animated
     * case: same reset vanilla does (metadata, frame list, counters), but the
     * dimensions become the larger side instead of throwing.
     */
    @Inject(method = "loadSprite", at = @At("HEAD"), cancellable = true)
    private void ldog$handleHDTextures(PngSizeInfo sizeInfo, boolean hasAnimation,
                                        CallbackInfo ci) {
        ldog$padSrcWidth = 0;
        ldog$padSrcHeight = 0;
        // Runs once per sprite at stitch time — the cached OptiFine interop
        // lookup rides along with the config toggle at no meaningful cost.
        if (!LDOGConfig.enableHDTextures || !OptiFineCompat.shouldHandleHDTextures()) return;
        // VintageFix's parallel texture preload calls loadSprite with no size
        // info. Without the PNG size there is nothing to square up, and
        // dereferencing it here threw on every sprite, which left the whole
        // atlas as missing textures.
        if (sizeInfo == null) return;

        int w = sizeInfo.pngWidth;
        int h = sizeInfo.pngHeight;
        if (hasAnimation || w == h) return;

        // Mirror of the private resetSprite() vanilla calls first.
        this.animationMetadata = null;
        this.setFramesTextureData(new ArrayList<>());
        this.frameCounter = 0;
        this.tickCounter = 0;

        int size = Math.max(w, h);
        this.width = size;
        this.height = size;
        ldog$padSrcWidth = w;
        ldog$padSrcHeight = h;
        LDOGMod.LOGGER.debug("LDOG: Fixed non-square texture '{}' ({}x{} -> {}x{})",
            iconName, w, h, size, size);
        ci.cancel();
    }

    /**
     * After vanilla has read the PNG into level-0 frame data, pad each frame
     * from the PNG's real dimensions to the square declared above. Only fires
     * for sprites the HEAD inject re-dimensioned; every other sprite is
     * untouched.
     */
    @Inject(method = "loadSpriteFrames", at = @At("RETURN"))
    private void ldog$padFramesToSquare(IResource resource, int mipmaplevels, CallbackInfo ci) {
        int srcW = ldog$padSrcWidth;
        int srcH = ldog$padSrcHeight;
        if (srcW <= 0 || srcH <= 0) return;
        int size = this.width;
        if (srcW == size && srcH == size) return;

        for (int f = 0; f < framesTextureData.size(); f++) {
            int[][] levels = framesTextureData.get(f);
            if (levels == null || levels.length == 0 || levels[0] == null) continue;
            int[] src = levels[0];
            if (src.length != srcW * srcH) continue; // already square, or not ours
            levels[0] = SquarePadding.padToSquare(src, srcW, srcH, size);
        }
    }
}
