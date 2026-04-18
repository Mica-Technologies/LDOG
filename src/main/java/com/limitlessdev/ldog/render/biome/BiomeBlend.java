package com.limitlessdev.ldog.render.biome;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.Biome;

/**
 * Variable-radius biome color smoothing — Phase "future expansion" answer
 * to OptiFine's smooth biomes feature.
 *
 * Vanilla MC 1.12.2 already smooths biome-tinted colors (grass, foliage,
 * water) over a hardcoded 3x3 = 9-block area centered on the query position.
 * This utility extends that to 5x5 (25 blocks) or 7x7 (49 blocks) when the
 * user asks for it, producing softer color transitions across biome borders
 * at the cost of N² per-pixel work.
 *
 * <h3>Why a separate helper instead of mixing the private method</h3>
 *
 * Vanilla {@code BiomeColorHelper.getColorAtPos} is private + uses a
 * package-private {@code ColorResolver} inner interface. Mixing it directly
 * would require an Access Transformer to expose the interface. Cleaner to
 * mixin the three public callers ({@code getGrassColorAtPos} etc.) and
 * dispatch to this helper, which calls the relevant {@code Biome} method
 * directly per neighbor.
 *
 * <h3>Performance notes</h3>
 *
 * Called from chunk meshing (per-block-side, not per-frame), so cost is
 * paid once per chunk-rebuild. At radius 3 (49 blocks) on a typical chunk
 * with thousands of grass-tinted blocks, this adds noticeable but not
 * crippling chunk-rebuild time. Users on weak CPUs should keep it at 1
 * (vanilla) or 2.
 */
public final class BiomeBlend {

    private BiomeBlend() {}

    public enum Channel {
        GRASS,
        FOLIAGE,
        WATER
    }

    /**
     * Average the resolved channel color over a (2*radius+1)² neighborhood
     * centered on {@code pos}. Returns the standard ARGB int with alpha = 0
     * matching vanilla's bit layout.
     */
    public static int blend(IBlockAccess access, BlockPos pos, int radius, Channel channel) {
        int side = 2 * radius + 1;
        int total = side * side;

        int rSum = 0;
        int gSum = 0;
        int bSum = 0;

        BlockPos min = pos.add(-radius, 0, -radius);
        BlockPos max = pos.add( radius, 0,  radius);
        for (BlockPos.MutableBlockPos p : BlockPos.getAllInBoxMutable(min, max)) {
            Biome biome = access.getBiome(p);
            int c;
            switch (channel) {
                case GRASS:   c = biome.getGrassColorAtPos(p);   break;
                case FOLIAGE: c = biome.getFoliageColorAtPos(p); break;
                case WATER:   c = biome.getWaterColor();         break;
                default:      c = 0xFFFFFF;                       break;
            }
            rSum += (c >> 16) & 0xFF;
            gSum += (c >> 8) & 0xFF;
            bSum += c & 0xFF;
        }

        int r = (rSum / total) & 0xFF;
        int g = (gSum / total) & 0xFF;
        int b = (bSum / total) & 0xFF;
        return (r << 16) | (g << 8) | b;
    }
}
