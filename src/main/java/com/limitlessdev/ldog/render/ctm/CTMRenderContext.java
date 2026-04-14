package com.limitlessdev.ldog.render.ctm;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * ThreadLocal context that provides IBlockAccess and BlockPos to
 * CTMBakedModel.getQuads() during chunk rebuilds.
 *
 * Set by MixinBlockRendererDispatcher before rendering each block,
 * read by CTMBakedModel to check neighbor connections.
 */
public final class CTMRenderContext {

    private static final ThreadLocal<IBlockAccess> WORLD = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> POS = new ThreadLocal<>();

    private CTMRenderContext() {}

    public static void set(IBlockAccess world, BlockPos pos) {
        WORLD.set(world);
        POS.set(pos);
    }

    public static void clear() {
        WORLD.remove();
        POS.remove();
    }

    public static IBlockAccess getWorld() {
        return WORLD.get();
    }

    public static BlockPos getPos() {
        return POS.get();
    }
}
