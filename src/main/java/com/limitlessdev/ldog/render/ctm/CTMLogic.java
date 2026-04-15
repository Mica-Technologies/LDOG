package com.limitlessdev.ldog.render.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Core CTM connection logic using the exact OptiFine/MCPatcher
 * 47-tile mapping table (from MCPatcherForge TileOverrideImpl$CTM).
 *
 * Bit layout for the 8-neighbor pattern (clockwise from left, as
 * viewed looking at the face from outside the block):
 *
 *   bit7(UL)  bit6(U)   bit5(UR)
 *   bit0(L)      *      bit4(R)
 *   bit1(DL)  bit2(D)   bit3(DR)
 */
public final class CTMLogic {

    private CTMLogic() {}

    /**
     * Exact OptiFine/MCPatcher 256-entry neighbor-to-tile lookup table.
     * Source: MCPatcherForge TileOverrideImpl$CTM.neighborMap
     *
     * Index = 8-bit neighbor pattern using the bit layout above.
     * Value = tile index 0-46 (47 unique tiles).
     *
     * The table implicitly handles corner gating: diagonal bits are
     * irrelevant when adjacent edges aren't connected, because the
     * table maps those entries to the same tile regardless.
     */
    private static final int[] NEIGHBOR_MAP = {
        // 0x00-0x0F
         0,  3,  0,  3, 12,  5, 12, 15,  0,  3,  0,  3, 12,  5, 12, 15,
        // 0x10-0x1F
         1,  2,  1,  2,  4,  7,  4, 29,  1,  2,  1,  2, 13, 31, 13, 14,
        // 0x20-0x2F
         0,  3,  0,  3, 12,  5, 12, 15,  0,  3,  0,  3, 12,  5, 12, 15,
        // 0x30-0x3F
         1,  2,  1,  2,  4,  7,  4, 29,  1,  2,  1,  2, 13, 31, 13, 14,
        // 0x40-0x4F
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        // 0x50-0x5F
        16, 18, 16, 18,  6, 46,  6, 21, 16, 18, 16, 18, 28,  9, 28, 22,
        // 0x60-0x6F
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        // 0x70-0x7F
        37, 40, 37, 40, 30,  8, 30, 34, 37, 40, 37, 40, 25, 23, 25, 45,
        // 0x80-0x8F
         0,  3,  0,  3, 12,  5, 12, 15,  0,  3,  0,  3, 12,  5, 12, 15,
        // 0x90-0x9F
         1,  2,  1,  2,  4,  7,  4, 29,  1,  2,  1,  2, 13, 31, 13, 14,
        // 0xA0-0xAF
         0,  3,  0,  3, 12,  5, 12, 15,  0,  3,  0,  3, 12,  5, 12, 15,
        // 0xB0-0xBF
         1,  2,  1,  2,  4,  7,  4, 29,  1,  2,  1,  2, 13, 31, 13, 14,
        // 0xC0-0xCF
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        // 0xD0-0xDF
        16, 42, 16, 42,  6, 20,  6, 10, 16, 42, 16, 42, 28, 35, 28, 44,
        // 0xE0-0xEF
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        // 0xF0-0xFF
        37, 38, 37, 38, 30, 11, 30, 32, 37, 38, 37, 38, 25, 33, 25, 26
    };

    public static int getFullCTMIndex(IBlockAccess world, BlockPos pos, EnumFacing face,
                                       Block targetBlock) {
        EnumFacing[] dirs = getPerpendicularDirections(face);
        EnumFacing up = dirs[0];
        EnumFacing right = dirs[1];
        EnumFacing down = up.getOpposite();
        EnumFacing left = right.getOpposite();

        boolean u  = connects(world, pos, up, targetBlock);
        boolean d  = connects(world, pos, down, targetBlock);
        boolean l  = connects(world, pos, left, targetBlock);
        boolean r  = connects(world, pos, right, targetBlock);
        boolean ul = u && l && connects(world, pos.offset(up).offset(left), targetBlock);
        boolean ur = u && r && connects(world, pos.offset(up).offset(right), targetBlock);
        boolean dl = d && l && connects(world, pos.offset(down).offset(left), targetBlock);
        boolean dr = d && r && connects(world, pos.offset(down).offset(right), targetBlock);

        // OptiFine bit layout: L=0, DL=1, D=2, DR=3, R=4, UR=5, U=6, UL=7
        int pattern = (l  ? 1   : 0) | (dl ? 2   : 0) | (d  ? 4   : 0) | (dr ? 8   : 0)
                    | (r  ? 16  : 0) | (ur ? 32  : 0) | (u  ? 64  : 0) | (ul ? 128 : 0);

        return NEIGHBOR_MAP[pattern];
    }

    public static int getHorizontalCTMIndex(IBlockAccess world, BlockPos pos,
                                             EnumFacing face, Block targetBlock) {
        EnumFacing[] dirs = getPerpendicularDirections(face);
        EnumFacing right = dirs[1];
        EnumFacing left = right.getOpposite();
        boolean l = connects(world, pos, left, targetBlock);
        boolean r = connects(world, pos, right, targetBlock);

        // MCPatcher/OptiFine tile convention (verified against actual tile images):
        //   tile 0 = left-end piece  (border LEFT,  seamless RIGHT) → block connects right only
        //   tile 1 = middle piece    (seamless both sides)          → block connects both sides
        //   tile 2 = right-end piece (seamless LEFT, border RIGHT)  → block connects left only
        //   tile 3 = standalone      (border LEFT,  border RIGHT)   → no connections
        if (l && r) return 1;
        if (r)      return 0;   // right neighbor → left-end piece (tile 0)
        if (l)      return 2;   // left  neighbor → right-end piece (tile 2)
        return 3;               // standalone → tile 3
    }

    public static int getVerticalCTMIndex(IBlockAccess world, BlockPos pos,
                                           Block targetBlock) {
        boolean above = connects(world, pos, EnumFacing.UP, targetBlock);
        boolean below = connects(world, pos, EnumFacing.DOWN, targetBlock);

        if (above && below) return 1;
        if (below) return 2;
        if (above) return 3;
        return 0;
    }

    private static boolean connects(IBlockAccess world, BlockPos pos, EnumFacing dir,
                                     Block targetBlock) {
        return world.getBlockState(pos.offset(dir)).getBlock() == targetBlock;
    }

    private static boolean connects(IBlockAccess world, BlockPos pos, Block targetBlock) {
        return world.getBlockState(pos).getBlock() == targetBlock;
    }

    /**
     * Returns [up, right] directions for a face, matching the vanilla UV mapping
     * so that the CTM tile orientation aligns with the rendered texture.
     *
     * The "up" direction corresponds to the tile's top edge (V=0),
     * and "right" corresponds to the tile's right edge (U=max).
     */
    static EnumFacing[] getPerpendicularDirections(EnumFacing face) {
        switch (face) {
            // UP face: V=0 at north edge, U increases toward east
            case UP:    return new EnumFacing[]{EnumFacing.NORTH, EnumFacing.EAST};
            // DOWN face: V=0 at south edge (vanilla flips V for bottom face)
            case DOWN:  return new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.EAST};
            case NORTH: return new EnumFacing[]{EnumFacing.UP, EnumFacing.WEST};
            case SOUTH: return new EnumFacing[]{EnumFacing.UP, EnumFacing.EAST};
            case WEST:  return new EnumFacing[]{EnumFacing.UP, EnumFacing.SOUTH};
            case EAST:  return new EnumFacing[]{EnumFacing.UP, EnumFacing.NORTH};
            default:    return new EnumFacing[]{EnumFacing.UP, EnumFacing.EAST};
        }
    }

    /**
     * Encode a pattern and look up the CTM tile index. Uses OptiFine's bit layout.
     * Exposed for testing.
     */
    static int encodeCTMIndex(boolean u, boolean d, boolean l, boolean r,
                               boolean ul, boolean ur, boolean dl, boolean dr) {
        int pattern = (l  ? 1   : 0) | (dl ? 2   : 0) | (d  ? 4   : 0) | (dr ? 8   : 0)
                    | (r  ? 16  : 0) | (ur ? 32  : 0) | (u  ? 64  : 0) | (ul ? 128 : 0);
        return NEIGHBOR_MAP[pattern];
    }
}
