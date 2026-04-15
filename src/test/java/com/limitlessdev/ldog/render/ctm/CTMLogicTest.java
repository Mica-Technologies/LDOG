package com.limitlessdev.ldog.render.ctm;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CTM connection logic and tile index mapping.
 */
class CTMLogicTest {

    @Test
    @DisplayName("No connections returns tile 0 (isolated)")
    void noConnectionsReturnsZero() {
        int index = CTMLogic.encodeCTMIndex(false, false, false, false,
            false, false, false, false);
        assertEquals(0, index);
    }

    @Test
    @DisplayName("All edges + all corners returns center tile (26)")
    void allConnectionsReturnsCenter() {
        int index = CTMLogic.encodeCTMIndex(true, true, true, true,
            true, true, true, true);
        assertEquals(26, index);
    }

    @Test
    @DisplayName("All edges, no corners returns tile 17")
    void allEdgesNoCorners() {
        int index = CTMLogic.encodeCTMIndex(true, true, true, true,
            false, false, false, false);
        assertEquals(17, index);
    }

    @Test
    @DisplayName("Perpendicular face directions are computed correctly")
    void perpendicularDirections() {
        EnumFacing[] dirs = CTMLogic.getPerpendicularDirections(EnumFacing.NORTH);
        assertNotNull(dirs);
        assertEquals(2, dirs.length);
    }

    @Test
    @DisplayName("CTM index range is 0-46 for all 256 patterns")
    void indexRangeValid() {
        for (int pattern = 0; pattern < 256; pattern++) {
            boolean u = (pattern & 1) != 0;
            boolean d = (pattern & 2) != 0;
            boolean l = (pattern & 4) != 0;
            boolean r = (pattern & 8) != 0;
            boolean ul = (pattern & 16) != 0;
            boolean ur = (pattern & 32) != 0;
            boolean dl = (pattern & 64) != 0;
            boolean dr = (pattern & 128) != 0;

            int index = CTMLogic.encodeCTMIndex(u, d, l, r, ul, ur, dl, dr);
            assertTrue(index >= 0 && index <= 46,
                "Index " + index + " out of range for pattern " + pattern);
        }
    }

    @Test
    @DisplayName("Vertical strip (up+down only) maps correctly")
    void verticalStrip() {
        int index = CTMLogic.encodeCTMIndex(true, true, false, false,
            false, false, false, false);
        assertEquals(7, index);
    }

    @Test
    @DisplayName("Horizontal strip (left+right only) maps correctly")
    void horizontalStrip() {
        int index = CTMLogic.encodeCTMIndex(false, false, true, true,
            false, false, false, false);
        assertEquals(8, index);
    }
}
