package com.limitlessdev.ldog.render.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Core CTM connection logic. Checks adjacent blocks to determine
 * which connected texture tile index to use.
 *
 * For the full CTM method (47 tiles), this implements the standard
 * OptiFine CTM tile index calculation based on 8-neighbor connectivity.
 */
public final class CTMLogic {

    private CTMLogic() {}

    /**
     * Calculate the CTM tile index for a full CTM (47-tile) connection.
     * Checks 4 cardinal + 4 diagonal neighbors on the given face plane.
     *
     * @param world      Block access
     * @param pos        Position of the block being rendered
     * @param face       Face being rendered (determines which plane to check neighbors in)
     * @param targetBlock The block type to connect to
     * @return Tile index (0-46) for the full CTM tileset
     */
    public static int getFullCTMIndex(IBlockAccess world, BlockPos pos, EnumFacing face,
                                       Block targetBlock) {
        // Get the two axes perpendicular to the face
        EnumFacing[] dirs = getPerpendicularDirections(face);
        EnumFacing up = dirs[0];
        EnumFacing right = dirs[1];

        // Check 8 neighbors in the face plane
        boolean u = connects(world, pos, up, targetBlock);
        boolean d = connects(world, pos, up.getOpposite(), targetBlock);
        boolean l = connects(world, pos, right.getOpposite(), targetBlock);
        boolean r = connects(world, pos, right, targetBlock);
        boolean ul = u && l && connects(world, pos.offset(up), right.getOpposite(), targetBlock);
        boolean ur = u && r && connects(world, pos.offset(up), right, targetBlock);
        boolean dl = d && l && connects(world, pos.offset(up.getOpposite()), right.getOpposite(), targetBlock);
        boolean dr = d && r && connects(world, pos.offset(up.getOpposite()), right, targetBlock);

        // Encode the 8-bit pattern into a tile index (0-46)
        return encodeCTMIndex(u, d, l, r, ul, ur, dl, dr);
    }

    /**
     * Calculate the horizontal CTM index (0-3) for horizontal connections.
     */
    public static int getHorizontalCTMIndex(IBlockAccess world, BlockPos pos,
                                             EnumFacing face, Block targetBlock) {
        boolean left = connects(world, pos, getLeftDirection(face), targetBlock);
        boolean right = connects(world, pos, getRightDirection(face), targetBlock);

        if (left && right) return 1; // middle
        if (left) return 2;          // right end
        if (right) return 3;         // left end
        return 0;                     // isolated
    }

    /**
     * Calculate the vertical CTM index (0-3) for vertical connections.
     */
    public static int getVerticalCTMIndex(IBlockAccess world, BlockPos pos,
                                           Block targetBlock) {
        boolean above = connects(world, pos, EnumFacing.UP, targetBlock);
        boolean below = connects(world, pos, EnumFacing.DOWN, targetBlock);

        if (above && below) return 1; // middle
        if (below) return 2;          // top
        if (above) return 3;          // bottom
        return 0;                      // isolated
    }

    private static boolean connects(IBlockAccess world, BlockPos pos, EnumFacing dir,
                                     Block targetBlock) {
        BlockPos neighbor = pos.offset(dir);
        IBlockState neighborState = world.getBlockState(neighbor);
        return neighborState.getBlock() == targetBlock;
    }

    /**
     * Get the two directions perpendicular to a face (for checking neighbors in the face plane).
     */
    static EnumFacing[] getPerpendicularDirections(EnumFacing face) {
        switch (face) {
            case UP:    return new EnumFacing[]{EnumFacing.NORTH, EnumFacing.EAST};
            case DOWN:  return new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.EAST};
            case NORTH: return new EnumFacing[]{EnumFacing.UP, EnumFacing.WEST};
            case SOUTH: return new EnumFacing[]{EnumFacing.UP, EnumFacing.EAST};
            case WEST:  return new EnumFacing[]{EnumFacing.UP, EnumFacing.SOUTH};
            case EAST:  return new EnumFacing[]{EnumFacing.UP, EnumFacing.NORTH};
            default:    return new EnumFacing[]{EnumFacing.UP, EnumFacing.EAST};
        }
    }

    private static EnumFacing getLeftDirection(EnumFacing face) {
        return face.rotateYCCW();
    }

    private static EnumFacing getRightDirection(EnumFacing face) {
        return face.rotateY();
    }

    /**
     * Encode 8 neighbor booleans into a CTM tile index (0-46).
     * Uses the standard OptiFine CTM mapping.
     */
    static int encodeCTMIndex(boolean u, boolean d, boolean l, boolean r,
                               boolean ul, boolean ur, boolean dl, boolean dr) {
        // No connections at all
        if (!u && !d && !l && !r) return 0;

        // Single edge connections
        if (u && !d && !l && !r) return 1;
        if (!u && d && !l && !r) return 2;
        if (!u && !d && l && !r) return 3;
        if (!u && !d && !l && r) return 4;

        // Two opposite edges
        if (u && d && !l && !r) return 5;
        if (!u && !d && l && r) return 6;

        // Two adjacent edges (L-shapes)
        if (u && !d && l && !r) return ul ? 8 : 7;
        if (u && !d && !l && r) return ur ? 10 : 9;
        if (!u && d && l && !r) return dl ? 12 : 11;
        if (!u && d && !l && r) return dr ? 14 : 13;

        // Three edges (T-shapes)
        if (u && d && l && !r) return 15 + (ul ? 1 : 0) + (dl ? 2 : 0);
        if (u && d && !l && r) return 19 + (ur ? 1 : 0) + (dr ? 2 : 0);
        if (u && !d && l && r) return 23 + (ul ? 1 : 0) + (ur ? 2 : 0);
        if (!u && d && l && r) return 27 + (dl ? 1 : 0) + (dr ? 2 : 0);

        // All four edges (center)
        if (u && d && l && r) {
            int corners = (ul ? 1 : 0) + (ur ? 2 : 0) + (dl ? 4 : 0) + (dr ? 8 : 0);
            return 31 + corners; // 31-46
        }

        return 0;
    }
}
