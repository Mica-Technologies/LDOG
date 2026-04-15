package com.limitlessdev.ldog.render.ctm;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CTM connection logic using the OptiFine/MCPatcher tile mapping.
 * Expected values are derived from the MCPatcherForge neighborMap.
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
    @DisplayName("All edges, no corners returns tile 46")
    void allEdgesNoCorners() {
        // OptiFine: L+D+R+U with no corners = bits 0+2+4+6 = 0x55 → tile 46
        int index = CTMLogic.encodeCTMIndex(true, true, true, true,
            false, false, false, false);
        assertEquals(46, index);
    }

    @Test
    @DisplayName("Perpendicular face directions are computed correctly")
    void perpendicularDirections() {
        EnumFacing[] dirs = CTMLogic.getPerpendicularDirections(EnumFacing.NORTH);
        assertNotNull(dirs);
        assertEquals(2, dirs.length);
        assertEquals(EnumFacing.UP, dirs[0]);   // up
        assertEquals(EnumFacing.WEST, dirs[1]); // right
    }

    @Test
    @DisplayName("UP face perpendicular has north as up direction")
    void upFacePerpendicularDirections() {
        EnumFacing[] dirs = CTMLogic.getPerpendicularDirections(EnumFacing.UP);
        assertEquals(EnumFacing.NORTH, dirs[0]); // up = north (matches V=0)
        assertEquals(EnumFacing.EAST, dirs[1]);  // right = east (matches U=max)
    }

    @Test
    @DisplayName("CTM index range is 0-46 for all 256 patterns")
    void indexRangeValid() {
        // Test all 256 raw patterns against the neighborMap
        for (int pattern = 0; pattern < 256; pattern++) {
            // Decode OptiFine bit layout: L=0, DL=1, D=2, DR=3, R=4, UR=5, U=6, UL=7
            boolean l  = (pattern & 1) != 0;
            boolean dl = (pattern & 2) != 0;
            boolean d  = (pattern & 4) != 0;
            boolean dr = (pattern & 8) != 0;
            boolean r  = (pattern & 16) != 0;
            boolean ur = (pattern & 32) != 0;
            boolean u  = (pattern & 64) != 0;
            boolean ul = (pattern & 128) != 0;

            int index = CTMLogic.encodeCTMIndex(u, d, l, r, ul, ur, dl, dr);
            assertTrue(index >= 0 && index <= 46,
                "Index " + index + " out of range for pattern " + pattern);
        }
    }

    @Test
    @DisplayName("Up only maps to tile 36")
    void upOnly() {
        // OptiFine: U only = bit 6 = 0x40 → tile 36
        int index = CTMLogic.encodeCTMIndex(true, false, false, false,
            false, false, false, false);
        assertEquals(36, index);
    }

    @Test
    @DisplayName("Down only maps to tile 12")
    void downOnly() {
        // OptiFine: D only = bit 2 = 0x04 → tile 12
        int index = CTMLogic.encodeCTMIndex(false, true, false, false,
            false, false, false, false);
        assertEquals(12, index);
    }

    @Test
    @DisplayName("Left only maps to tile 3")
    void leftOnly() {
        // OptiFine: L only = bit 0 = 0x01 → tile 3
        int index = CTMLogic.encodeCTMIndex(false, false, true, false,
            false, false, false, false);
        assertEquals(3, index);
    }

    @Test
    @DisplayName("Right only maps to tile 1")
    void rightOnly() {
        // OptiFine: R only = bit 4 = 0x10 → tile 1
        int index = CTMLogic.encodeCTMIndex(false, false, false, true,
            false, false, false, false);
        assertEquals(1, index);
    }

    @Test
    @DisplayName("Vertical strip (up+down only) maps to tile 24")
    void verticalStrip() {
        // OptiFine: U+D = bits 2+6 = 0x44 → tile 24
        int index = CTMLogic.encodeCTMIndex(true, true, false, false,
            false, false, false, false);
        assertEquals(24, index);
    }

    @Test
    @DisplayName("Horizontal strip (left+right only) maps to tile 2")
    void horizontalStrip() {
        // OptiFine: L+R = bits 0+4 = 0x11 → neighborMap[17] = 2
        int index = CTMLogic.encodeCTMIndex(false, false, true, true,
            false, false, false, false);
        assertEquals(2, index);
    }

    @Test
    @DisplayName("Corner gating: diagonal ignored when adjacent edges missing")
    void cornerGatingImplicit() {
        // ul set but u and l are false → same as no connections
        int withCorner = CTMLogic.encodeCTMIndex(false, false, false, false,
            true, false, false, false);
        int withoutCorner = CTMLogic.encodeCTMIndex(false, false, false, false,
            false, false, false, false);
        assertEquals(withoutCorner, withCorner,
            "Diagonal should be irrelevant when adjacent edges are not connected");
    }

    @Test
    @DisplayName("All edges + 3 corners (missing UL) maps to tile 45")
    void threeCornersMissingUL() {
        // All edges + UR, DL, DR but NOT UL → 0x7F → tile 45
        int index = CTMLogic.encodeCTMIndex(true, true, true, true,
            false, true, true, true);
        assertEquals(45, index);
    }

    @Test
    @DisplayName("All edges + 3 corners (missing DL) maps to tile 33")
    void threeCornersMissingDL() {
        // All edges + UL, UR, DR but NOT DL → 0xFD → tile 33
        int index = CTMLogic.encodeCTMIndex(true, true, true, true,
            true, true, false, true);
        assertEquals(33, index);
    }
}
