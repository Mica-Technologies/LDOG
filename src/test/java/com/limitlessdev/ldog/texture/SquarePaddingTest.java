package com.limitlessdev.ldog.texture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SquarePaddingTest {

    @Test
    @DisplayName("wide source lands top-left, rows re-strided to the square width")
    void wideSourceIsAnchoredTopLeft() {
        // 3x2 source: row0 = 1 2 3, row1 = 4 5 6
        int[] src = {1, 2, 3, 4, 5, 6};
        int[] out = SquarePadding.padToSquare(src, 3, 2, 3);
        assertEquals(9, out.length);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 0, 0, 0}, out);
    }

    @Test
    @DisplayName("tall source pads each row to the right with transparent black")
    void tallSourceIsPaddedRight() {
        // 1x3 source
        int[] src = {7, 8, 9};
        int[] out = SquarePadding.padToSquare(src, 1, 3, 3);
        assertArrayEquals(new int[]{7, 0, 0, 8, 0, 0, 9, 0, 0}, out);
    }

    @Test
    @DisplayName("already-square input is returned as the same array")
    void squareInputIsIdentity() {
        int[] src = {1, 2, 3, 4};
        assertSame(src, SquarePadding.padToSquare(src, 2, 2, 2));
    }

    @Test
    @DisplayName("length mismatch and overflow are rejected rather than silently mis-strided")
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> SquarePadding.padToSquare(new int[5], 3, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> SquarePadding.padToSquare(new int[6], 3, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> SquarePadding.padToSquare(null, 1, 1, 1));
    }
}
