package com.limitlessdev.ldog.render.font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FontWidthMeasureTest {

    private static final int OPAQUE = 0xFFFFFFFF;

    /** A 16x16-cell sheet with square cells of the given size, all transparent. */
    private static int[] sheet(int cell) {
        return new int[(cell * 16) * (cell * 16)];
    }

    /** Inks columns 0..inkColumns-1 of one row in the cell of character {@code ch}. */
    private static void ink(int[] argb, int cell, int ch, int inkColumns, int argbValue) {
        int width = cell * 16;
        int x0 = (ch % 16) * cell;
        int y0 = (ch / 16) * cell + cell / 2;
        for (int c = 0; c < inkColumns; c++) argb[x0 + c + y0 * width] = argbValue;
    }

    @Test
    @DisplayName("an empty cell measures as one unit of spacing")
    void emptyCell() {
        int[] widths = FontWidthMeasure.measure(sheet(8), 128, 128);
        assertNotNull(widths);
        assertEquals(256, widths.length);
        assertEquals(1, widths['A']);
    }

    @Test
    @DisplayName("a vanilla-size sheet gives ink width plus one")
    void vanillaSizeSheet() {
        int[] argb = sheet(8);
        ink(argb, 8, 'A', 5, OPAQUE);
        ink(argb, 8, 'i', 1, OPAQUE);
        int[] widths = FontWidthMeasure.measure(argb, 128, 128);
        assertEquals(6, widths['A']);
        assertEquals(2, widths['i']);
    }

    @Test
    @DisplayName("an HD sheet scales to 8 units per cell and rounds up so glyphs are not clipped")
    void hdSheetScalesAndRoundsUp() {
        int[] argb = sheet(256);
        ink(argb, 256, 'A', 128, OPAQUE); // exactly half the cell -> 4 units
        ink(argb, 256, 'B', 129, OPAQUE); // a pixel over half -> 5 units, not 4
        int[] widths = FontWidthMeasure.measure(argb, 4096, 4096);
        assertEquals(5, widths['A']);
        assertEquals(6, widths['B']);
    }

    @Test
    @DisplayName("faint antialiasing fringe does not widen a glyph")
    void fringeIsIgnored() {
        int[] argb = sheet(8);
        ink(argb, 8, 'A', 3, OPAQUE);
        // column 5 carries alpha 8, below the threshold
        argb[('A' % 16) * 8 + 5 + (('A' / 16) * 8 + 4) * 128] = 0x08FFFFFF;
        assertEquals(4, FontWidthMeasure.measure(argb, 128, 128)['A']);
    }

    @Test
    @DisplayName("a sheet too small for 16 cells, or a short pixel array, is rejected")
    void badInputIsRejected() {
        assertNull(FontWidthMeasure.measure(new int[64], 8, 8));
        assertNull(FontWidthMeasure.measure(new int[10], 128, 128));
        assertNull(FontWidthMeasure.measure(null, 128, 128));
    }
}
