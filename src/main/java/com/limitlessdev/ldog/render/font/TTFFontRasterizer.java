package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.LDOGMod;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Rasterizes the 256-character default-font page from an AWT {@link Font} into
 * a 16×16 grid atlas and returns it alongside per-glyph logical widths.
 *
 * <p>The result is a pixel-perfect antialiased atlas at the chosen cell
 * resolution — this is what Smooth Font's default path effectively does, and
 * what a post-hoc filter on a pre-rasterized pack PNG can only approximate.
 *
 * <p>Sampling with AWT uses {@code TEXT_ANTIALIAS_ON} plus
 * {@code FRACTIONAL_METRICS_ON} so subpixel glyph advances round off naturally
 * rather than snapping. White glyphs on a fully transparent background so MC's
 * color modulation (chat colors, etc.) keeps working.
 */
public final class TTFFontRasterizer {

    private TTFFontRasterizer() {}

    /**
     * Character layout matching {@code FontRenderer.renderChar}. Position
     * {@code N} in this string is the character that MC expects to find in
     * atlas cell {@code (N%16, N/16)}. Must stay in exact sync with vanilla —
     * MC looks up chars via {@code .indexOf(ch)} on this string.
     */
    static final String CHARSET =
        "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130" +
        "\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000" +
        " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000" +
        "\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5" +
        "\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192" +
        "\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb" +
        "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510" +
        "\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567" +
        "\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580" +
        "\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229" +
        "\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";

    /**
     * @param familyName AWT family name. Logical names ("SansSerif", "Serif",
     *   "Monospaced") are always resolvable; OS-specific names fall back to
     *   the system default when absent.
     * @param bold whether to request a bold face
     * @param italic whether to request an italic face
     * @param fontSize AWT point size for {@link Font}. Good values are roughly
     *   70-80% of {@code cellSize}; larger values risk clipping top/bottom.
     * @param cellSize pixel size of each atlas cell. The atlas is 16 × {@code cellSize}
     *   on a side. Larger = sharper at high GUI scales (mipmaps handle the
     *   downsampling for smaller GUI scales).
     */
    public static Result rasterize(String familyName, boolean bold, boolean italic,
                                   int fontSize, int cellSize) {
        int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
        Font font = new Font(familyName, style, fontSize);
        int atlasSize = 16 * cellSize;

        BufferedImage img = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            // Fully-transparent background. Using SRC composite for the clear so we
            // don't blend against whatever default color Graphics2D starts with.
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, atlasSize, atlasSize);
            g.setComposite(AlphaComposite.SrcOver);

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            g.setFont(font);
            // White glyphs so MC's per-string color modulation works as expected.
            g.setColor(Color.WHITE);

            FontMetrics fm = g.getFontMetrics();
            int ascent = fm.getAscent();
            int[] pixelWidths = new int[256];
            int rendered = 0;
            int skipped = 0;

            for (int i = 0; i < 256 && i < CHARSET.length(); i++) {
                char ch = CHARSET.charAt(i);
                pixelWidths[i] = 0;
                if (ch == 0) continue;
                if (!font.canDisplay(ch)) {
                    skipped++;
                    continue;
                }
                int col = i % 16;
                int row = i / 16;
                // Draw at left edge of cell; baseline = row*cellSize + ascent, so the
                // glyph's top aligns with the cell top for typical fonts. Some fonts
                // have tall caps that overshoot — that's a tradeoff of single-cell
                // rendering; the vanilla pack font does the same.
                int x = col * cellSize;
                int y = row * cellSize + ascent;
                g.drawString(String.valueOf(ch), x, y);
                pixelWidths[i] = fm.charWidth(ch);
                rendered++;
            }

            // Convert pixel widths to MC's logical-8-per-cell scale and add +1 for
            // vanilla's trailing spacing convention (see FontRenderer.readFontTexture).
            int[] logicalWidths = new int[256];
            double scale = 8.0 / cellSize;
            for (int i = 0; i < 256; i++) {
                logicalWidths[i] = (pixelWidths[i] > 0)
                    ? (int) Math.ceil(pixelWidths[i] * scale) + 1
                    : -1;  // -1 = no override, keep vanilla
            }
            // Space is hardcoded to 4 in FontRenderer.renderChar; leave that alone.
            logicalWidths[32] = -1;

            LDOGMod.LOGGER.info(
                "LDOG: Rasterized TTF atlas {} {} {}pt at {}px cells ({} glyphs drawn, {} unsupported)",
                familyName, style == 0 ? "plain" : (bold && italic ? "bold-italic" : bold ? "bold" : "italic"),
                fontSize, cellSize, rendered, skipped);

            return new Result(img, logicalWidths);
        } finally {
            g.dispose();
        }
    }

    /** Rasterization output bundled for atomic handoff to the upload path. */
    public static final class Result {
        public final BufferedImage atlas;
        /** Per-char logical widths; {@code -1} means "no override, use vanilla". */
        public final int[] logicalWidths;

        public Result(BufferedImage atlas, int[] logicalWidths) {
            this.atlas = atlas;
            this.logicalWidths = logicalWidths;
        }
    }
}
