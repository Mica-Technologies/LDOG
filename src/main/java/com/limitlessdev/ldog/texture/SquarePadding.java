package com.limitlessdev.ldog.texture;

/**
 * Pads a row-major ARGB pixel array from {@code srcW × srcH} to
 * {@code size × size}, anchoring the source at the top-left and filling the
 * rest with transparent black. Pure function so the mixin that needs it
 * ({@code MixinTextureAtlasSprite}) stays a thin hook and this stays testable.
 */
public final class SquarePadding {

    private SquarePadding() {}

    /**
     * @return a new {@code size*size} array, or {@code src} itself when it is
     *         already {@code size × size}.
     * @throws IllegalArgumentException when {@code src} is not {@code srcW*srcH}
     *         long or the source does not fit inside {@code size}.
     */
    public static int[] padToSquare(int[] src, int srcW, int srcH, int size) {
        if (src == null) throw new IllegalArgumentException("src is null");
        if (srcW <= 0 || srcH <= 0) throw new IllegalArgumentException("bad source dims " + srcW + "x" + srcH);
        if (src.length != srcW * srcH) {
            throw new IllegalArgumentException("src length " + src.length + " != " + srcW + "*" + srcH);
        }
        if (srcW > size || srcH > size) {
            throw new IllegalArgumentException(srcW + "x" + srcH + " does not fit in " + size + "x" + size);
        }
        if (srcW == size && srcH == size) return src;

        int[] dst = new int[size * size];
        for (int y = 0; y < srcH; y++) {
            System.arraycopy(src, y * srcW, dst, y * size, srcW);
        }
        return dst;
    }
}
