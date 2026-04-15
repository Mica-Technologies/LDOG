package com.limitlessdev.ldog.render.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Core CTM connection logic. Uses the standard OptiFine/MCPatcher
 * 47-tile CTM mapping based on 4 cardinal + 4 diagonal neighbors.
 *
 * The tile index mapping follows the OptiFine convention where the
 * 256 possible 8-neighbor patterns map to 47 unique tile indices.
 */
public final class CTMLogic {

    private CTMLogic() {}

    /**
     * Standard OptiFine 47-tile CTM lookup table.
     * Index = 8-bit neighbor pattern (UDLR + corners), Value = tile index 0-46.
     *
     * Bit layout:
     *   0 = up, 1 = down, 2 = left, 3 = right
     *   4 = up-left, 5 = up-right, 6 = down-left, 7 = down-right
     *
     * Corners only matter when both adjacent edges connect.
     */
    private static final int[] FULL_CTM_MAP = buildCTMMap();

    public static int getFullCTMIndex(IBlockAccess world, BlockPos pos, EnumFacing face,
                                       Block targetBlock) {
        EnumFacing[] dirs = getPerpendicularDirections(face);
        EnumFacing up = dirs[0];
        EnumFacing right = dirs[1];
        EnumFacing down = up.getOpposite();
        EnumFacing left = right.getOpposite();

        boolean u = connects(world, pos, up, targetBlock);
        boolean d = connects(world, pos, down, targetBlock);
        boolean l = connects(world, pos, left, targetBlock);
        boolean r = connects(world, pos, right, targetBlock);

        // Corners only count if both adjacent edges connect
        boolean ul = u && l && connects(world, pos.offset(up).offset(left), targetBlock);
        boolean ur = u && r && connects(world, pos.offset(up).offset(right), targetBlock);
        boolean dl = d && l && connects(world, pos.offset(down).offset(left), targetBlock);
        boolean dr = d && r && connects(world, pos.offset(down).offset(right), targetBlock);

        int pattern = (u ? 1 : 0) | (d ? 2 : 0) | (l ? 4 : 0) | (r ? 8 : 0)
                    | (ul ? 16 : 0) | (ur ? 32 : 0) | (dl ? 64 : 0) | (dr ? 128 : 0);

        return FULL_CTM_MAP[pattern];
    }

    public static int getHorizontalCTMIndex(IBlockAccess world, BlockPos pos,
                                             EnumFacing face, Block targetBlock) {
        EnumFacing left = face.rotateYCCW();
        EnumFacing right = face.rotateY();
        boolean l = connects(world, pos, left, targetBlock);
        boolean r = connects(world, pos, right, targetBlock);

        if (l && r) return 1;
        if (l) return 2;
        if (r) return 3;
        return 0;
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

    static EnumFacing[] getPerpendicularDirections(EnumFacing face) {
        switch (face) {
            case UP:    return new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.EAST};
            case DOWN:  return new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.EAST};
            case NORTH: return new EnumFacing[]{EnumFacing.UP, EnumFacing.WEST};
            case SOUTH: return new EnumFacing[]{EnumFacing.UP, EnumFacing.EAST};
            case WEST:  return new EnumFacing[]{EnumFacing.UP, EnumFacing.SOUTH};
            case EAST:  return new EnumFacing[]{EnumFacing.UP, EnumFacing.NORTH};
            default:    return new EnumFacing[]{EnumFacing.UP, EnumFacing.EAST};
        }
    }

    /**
     * Build the 256-entry lookup table mapping 8-bit neighbor patterns to
     * 47 unique CTM tile indices. This is the standard OptiFine mapping.
     */
    private static int[] buildCTMMap() {
        int[] map = new int[256];

        for (int pattern = 0; pattern < 256; pattern++) {
            boolean u  = (pattern & 1) != 0;
            boolean d  = (pattern & 2) != 0;
            boolean l  = (pattern & 4) != 0;
            boolean r  = (pattern & 8) != 0;
            boolean ul = (pattern & 16) != 0;
            boolean ur = (pattern & 32) != 0;
            boolean dl = (pattern & 64) != 0;
            boolean dr = (pattern & 128) != 0;

            // Count connected edges and corners
            int edges = (u ? 1 : 0) + (d ? 1 : 0) + (l ? 1 : 0) + (r ? 1 : 0);

            if (edges == 0) {
                map[pattern] = 0; // Isolated
            } else if (edges == 1) {
                if (u) map[pattern] = 3;
                else if (d) map[pattern] = 1;
                else if (l) map[pattern] = 2;
                else map[pattern] = 4;
            } else if (edges == 2) {
                if (u && d) map[pattern] = 7;       // Vertical strip
                else if (l && r) map[pattern] = 8;  // Horizontal strip
                else if (u && r) map[pattern] = ur ? 10 : 14;
                else if (u && l) map[pattern] = ul ? 9 : 13;
                else if (d && r) map[pattern] = dr ? 46 : 42;
                else if (d && l) map[pattern] = dl ? 45 : 41;
            } else if (edges == 3) {
                if (!u) { // d+l+r (T down)
                    int c = (dl ? 1 : 0) + (dr ? 1 : 0);
                    if (c == 2) map[pattern] = 44;
                    else if (dl) map[pattern] = 40;
                    else if (dr) map[pattern] = 43;
                    else map[pattern] = 39;
                } else if (!d) { // u+l+r (T up)
                    int c = (ul ? 1 : 0) + (ur ? 1 : 0);
                    if (c == 2) map[pattern] = 12;
                    else if (ul) map[pattern] = 11;
                    else if (ur) map[pattern] = 15;
                    else map[pattern] = 6;
                } else if (!l) { // u+d+r (T right)
                    int c = (ur ? 1 : 0) + (dr ? 1 : 0);
                    if (c == 2) map[pattern] = 38;
                    else if (ur) map[pattern] = 34;
                    else if (dr) map[pattern] = 37;
                    else map[pattern] = 33;
                } else { // u+d+l (T left)
                    int c = (ul ? 1 : 0) + (dl ? 1 : 0);
                    if (c == 2) map[pattern] = 36;
                    else if (ul) map[pattern] = 32;
                    else if (dl) map[pattern] = 35;
                    else map[pattern] = 5;
                }
            } else { // edges == 4 (all edges connected)
                int c = (ul ? 1 : 0) + (ur ? 1 : 0) + (dl ? 1 : 0) + (dr ? 1 : 0);
                if (c == 4) map[pattern] = 26;        // Full center
                else if (c == 3) {
                    if (!ul) map[pattern] = 30;
                    else if (!ur) map[pattern] = 29;
                    else if (!dl) map[pattern] = 22;
                    else map[pattern] = 21;
                } else if (c == 2) {
                    if (ul && ur) map[pattern] = 18;
                    else if (dl && dr) map[pattern] = 25;
                    else if (ul && dl) map[pattern] = 20;
                    else if (ur && dr) map[pattern] = 23;
                    else if (ul && dr) map[pattern] = 27;
                    else map[pattern] = 28;            // ur && dl
                } else if (c == 1) {
                    if (ul) map[pattern] = 16;
                    else if (ur) map[pattern] = 19;
                    else if (dl) map[pattern] = 24;
                    else map[pattern] = 31;
                } else {
                    map[pattern] = 17;                 // No corners
                }
            }
        }

        return map;
    }

    // Keep for tests
    static int encodeCTMIndex(boolean u, boolean d, boolean l, boolean r,
                               boolean ul, boolean ur, boolean dl, boolean dr) {
        int pattern = (u ? 1 : 0) | (d ? 2 : 0) | (l ? 4 : 0) | (r ? 8 : 0)
                    | (ul ? 16 : 0) | (ur ? 32 : 0) | (dl ? 64 : 0) | (dr ? 128 : 0);
        return FULL_CTM_MAP[pattern];
    }
}
