package com.limitlessdev.ldog.render.font;

/**
 * Measures per-character widths from a 16x16-cell font sheet, the way vanilla's
 * {@code FontRenderer.readFontTexture} does for {@code textures/font/ascii.png}:
 * the width of a glyph is the rightmost column of its cell that has any ink,
 * scaled to the 8-units-per-cell space the font renderer advances in, plus one
 * unit of spacing.
 *
 * <p>Needed for HD font sheets: vanilla only ever measures its own
 * {@code ascii.png}, so a pack font drawn narrower or wider than vanilla's got
 * vanilla's advances, which left a gap after every narrow glyph.
 *
 * <p>Pure: no Minecraft types, so it is unit-tested.
 */
public final class FontWidthMeasure {

    /** Alpha at or below this is treated as empty, so antialiasing fringes do not widen a glyph. */
    static final int ALPHA_THRESHOLD = 16;

    private FontWidthMeasure() {}

    /**
     * @param argb   the sheet's pixels, row-major, {@code width * height} long
     * @param width  sheet width in pixels (16 cells)
     * @param height sheet height in pixels (16 cells)
     * @return 256 widths in font-renderer units (ink width rounded up, plus 1), or
     *         {@code null} when the sheet is too small or the array does not match
     */
    public static int[] measure(int[] argb, int width, int height) {
        int cellW = width / 16;
        int cellH = height / 16;
        if (cellW <= 0 || cellH <= 0 || argb == null || argb.length < width * height) return null;
        int[] out = new int[256];
        for (int ch = 0; ch < 256; ch++) {
            int cellX = (ch % 16) * cellW;
            int cellY = (ch / 16) * cellH;
            int ink = 0;
            for (int col = cellW - 1; col >= 0 && ink == 0; col--) {
                int x = cellX + col;
                for (int row = 0; row < cellH; row++) {
                    int alpha = argb[x + (cellY + row) * width] >>> 24;
                    if (alpha > ALPHA_THRESHOLD) {
                        ink = col + 1;
                        break;
                    }
                }
            }
            // Round up, never down: the renderer samples exactly this much of the
            // cell, so rounding down would clip the glyph's right edge.
            int units = (ink * 8 + cellW - 1) / cellW;
            out[ch] = units + 1;
        }
        return out;
    }
}
