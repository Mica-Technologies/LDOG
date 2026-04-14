package com.limitlessdev.ldog.render.ctm;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CTM connection logic -- verifying tile index calculation
 * for various neighbor configurations.
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
    @DisplayName("All cardinal connections with all corners returns max tile")
    void allConnectionsReturnsHighIndex() {
        int index = CTMLogic.encodeCTMIndex(true, true, true, true,
            true, true, true, true);
        // All 4 edges + all 4 corners = 31 + 15 = 46
        assertEquals(46, index);
    }

    @Test
    @DisplayName("Single edge connections return tiles 1-4")
    void singleEdgeConnections() {
        assertEquals(1, CTMLogic.encodeCTMIndex(true, false, false, false,
            false, false, false, false)); // up only
        assertEquals(2, CTMLogic.encodeCTMIndex(false, true, false, false,
            false, false, false, false)); // down only
        assertEquals(3, CTMLogic.encodeCTMIndex(false, false, true, false,
            false, false, false, false)); // left only
        assertEquals(4, CTMLogic.encodeCTMIndex(false, false, false, true,
            false, false, false, false)); // right only
    }

    @Test
    @DisplayName("Two opposite edges return tiles 5-6")
    void oppositeEdgeConnections() {
        assertEquals(5, CTMLogic.encodeCTMIndex(true, true, false, false,
            false, false, false, false)); // up+down
        assertEquals(6, CTMLogic.encodeCTMIndex(false, false, true, true,
            false, false, false, false)); // left+right
    }

    @Test
    @DisplayName("Perpendicular face directions are computed correctly")
    void perpendicularDirections() {
        EnumFacing[] dirs = CTMLogic.getPerpendicularDirections(EnumFacing.NORTH);
        assertNotNull(dirs);
        assertEquals(2, dirs.length);
        // North face should check up/down and left/right in its plane
        assertEquals(EnumFacing.UP, dirs[0]);
    }

    @Test
    @DisplayName("CTM index range is 0-46")
    void indexRangeValid() {
        // Test all 256 possible 8-bit patterns
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
}
