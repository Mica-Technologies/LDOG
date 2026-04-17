package com.limitlessdev.ldog.texture;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;

/**
 * Implements OptiFine-style "extended border mipmaps" to eliminate anisotropic
 * atlas bleed. Each sprite is padded on every side with a halo of its own
 * edge-extended pixel color, wide enough that even at the deepest mip level
 * there is at least one halo texel between the sprite and any neighbor.
 *
 * <p>State transitions:
 * <ol>
 *   <li>{@link #beginStitch(int)} is called at the start of {@code TextureMap.loadTextureAtlas}
 *       to snapshot the atlas's mipmap level and decide (once) whether this stitch pass
 *       will use extended borders. Snapshotting avoids mid-stitch config flips.</li>
 *   <li>During stitching, mixins on {@code Stitcher.Holder} read {@link #isActive()} and
 *       inflate each sprite's reported dimensions by {@code 2 * getBorderSize()} so the
 *       packer leaves halo space between sprites.</li>
 *   <li>{@code Stitcher.getStichSlots()} is redirected to shift each sprite's origin
 *       inward by {@code getBorderSize()} so its UVs address only the inner region.</li>
 *   <li>{@code TextureMap.finishLoading()}'s upload loop is redirected through
 *       {@link #padFrameData(int[][], int, int, int, int)} to write the padded pixel data.</li>
 *   <li>{@link #endStitch()} clears the active flag.</li>
 * </ol>
 *
 * <p>Border size is {@code 2^mipmapLevels} (16 px at the default level 4), which is the
 * minimum that still leaves ≥1 halo texel at the deepest mip level.
 */
public final class ExtendedBorderHandler {

    private ExtendedBorderHandler() {}

    private static int currentMipmapLevels = 4;
    private static boolean activeDuringStitch = false;

    public static boolean isEnabled() {
        return LDOGConfig.enableExtendedBorderMipmaps;
    }

    /** True only while a stitch pass that opted in to borders is in progress. */
    public static boolean isActive() {
        return activeDuringStitch;
    }

    public static int getBorderSize() {
        return 1 << currentMipmapLevels;
    }

    public static int getMipmapLevels() {
        return currentMipmapLevels;
    }

    public static void beginStitch(int mipmapLevels) {
        currentMipmapLevels = Math.max(0, mipmapLevels);
        activeDuringStitch = isEnabled();
        if (activeDuringStitch) {
            LDOGMod.LOGGER.info("LDOG: Extended-border mipmaps active for this stitch (border={}px, mipLevels={})",
                getBorderSize(), currentMipmapLevels);
        }
    }

    public static void endStitch() {
        activeDuringStitch = false;
    }

    /**
     * Produces a padded mipmap chain where each level has a halo of
     * {@code border >> level} pixels of edge-extended color on every side.
     *
     * <p>The caller uploads the result at atlas coordinates
     * {@code (innerOriginX - border, innerOriginY - border)} with dimensions
     * {@code (width + 2*border, height + 2*border)}. Because Stitcher always
     * rounds slot dimensions up to a multiple of {@code 2^mipmapLevels},
     * every level's upload offset remains integer-aligned.
     *
     * @param original vanilla mipmap chain with index 0 at full size
     * @param width inner sprite width (unpadded)
     * @param height inner sprite height (unpadded)
     * @param border per-side halo size at mip 0; must be ≥ {@code 2^mipmapLevels}
     * @param mipmapLevels highest mip index to populate (inclusive)
     */
    public static int[][] padFrameData(int[][] original, int width, int height, int border, int mipmapLevels) {
        int[][] padded = new int[mipmapLevels + 1][];
        for (int level = 0; level <= mipmapLevels; level++) {
            int[] src = original[level];
            int levelW = Math.max(1, width >> level);
            int levelH = Math.max(1, height >> level);
            int levelBorder = border >> level;
            int paddedW = levelW + 2 * levelBorder;
            int paddedH = levelH + 2 * levelBorder;
            int[] dst = new int[paddedW * paddedH];

            // For each output pixel, clamp-sample from the inner sprite region. This
            // extends edge pixels outward into the halo, which is what makes the
            // neighbor-sprite bleed disappear at high mip levels.
            for (int y = 0; y < paddedH; y++) {
                int sy = y - levelBorder;
                if (sy < 0) sy = 0;
                else if (sy >= levelH) sy = levelH - 1;
                int srcRow = sy * levelW;
                int dstRow = y * paddedW;
                for (int x = 0; x < paddedW; x++) {
                    int sx = x - levelBorder;
                    if (sx < 0) sx = 0;
                    else if (sx >= levelW) sx = levelW - 1;
                    dst[dstRow + x] = src[srcRow + sx];
                }
            }
            padded[level] = dst;
        }
        return padded;
    }
}
