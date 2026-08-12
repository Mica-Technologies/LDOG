package com.limitlessdev.ldog.render.emissive;

import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.util.math.BlockPos;

/**
 * Stores per-thread references to the current chunk's buffer builder set
 * and compiled chunk during chunk rebuilds. This allows the emissive
 * renderer to write quads into the CUTOUT_MIPPED buffer (which has
 * alpha testing) even when the current block renders in the SOLID layer.
 */
public final class EmissiveRenderLayer {

    private static final ThreadLocal<ChunkCompileTaskGenerator> GENERATOR = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EMISSIVE_QUADS_WRITTEN = ThreadLocal.withInitial(() -> false);

    /**
     * Last block position the emissive overlay was emitted for, as
     * {@code [hasClaim, BlockPos.toLong()]}. Vanilla's rebuild loop calls
     * BlockModelRenderer.renderModel once per render layer the block declares
     * via {@code canRenderInLayer}, so a multi-layer block would otherwise get
     * its overlay quads emitted several times over.
     */
    private static final ThreadLocal<long[]> LAST_OVERLAY_POS =
        ThreadLocal.withInitial(() -> new long[2]);

    private EmissiveRenderLayer() {}

    public static void set(ChunkCompileTaskGenerator generator) {
        GENERATOR.set(generator);
        EMISSIVE_QUADS_WRITTEN.set(false);
        LAST_OVERLAY_POS.get()[0] = 0L;
    }

    /**
     * Claims a block position for emissive overlay emission within the current
     * rebuild. Returns false if the overlay was already emitted for this exact
     * position, i.e. this is a repeat visit from another render layer.
     */
    public static boolean claimOverlayPos(BlockPos pos) {
        long key = pos.toLong();
        long[] slot = LAST_OVERLAY_POS.get();
        if (slot[0] != 0L && slot[1] == key) return false;
        slot[0] = 1L;
        slot[1] = key;
        return true;
    }

    public static RegionRenderCacheBuilder getCacheBuilder() {
        ChunkCompileTaskGenerator gen = GENERATOR.get();
        return gen != null ? gen.getRegionRenderCacheBuilder() : null;
    }

    public static CompiledChunk getCompiledChunk() {
        ChunkCompileTaskGenerator gen = GENERATOR.get();
        return gen != null ? gen.getCompiledChunk() : null;
    }

    /**
     * Records that at least one emissive quad went into the CUTOUT_MIPPED buffer
     * during this rebuild — which is what decides whether the layer has to be
     * marked used, independently of who started the buffer.
     */
    public static void markEmissiveQuadsWritten() {
        EMISSIVE_QUADS_WRITTEN.set(true);
    }

    public static boolean wereEmissiveQuadsWritten() {
        return EMISSIVE_QUADS_WRITTEN.get();
    }

    public static void clear() {
        GENERATOR.remove();
        EMISSIVE_QUADS_WRITTEN.remove();
        LAST_OVERLAY_POS.remove();
    }
}
