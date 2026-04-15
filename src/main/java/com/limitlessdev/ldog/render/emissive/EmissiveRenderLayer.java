package com.limitlessdev.ldog.render.emissive;

import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;

/**
 * Stores per-thread references to the current chunk's buffer builder set
 * and compiled chunk during chunk rebuilds. This allows the emissive
 * renderer to write quads into the CUTOUT_MIPPED buffer (which has
 * alpha testing) even when the current block renders in the SOLID layer.
 */
public final class EmissiveRenderLayer {

    private static final ThreadLocal<ChunkCompileTaskGenerator> GENERATOR = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EMISSIVE_BUFFER_STARTED = ThreadLocal.withInitial(() -> false);

    private EmissiveRenderLayer() {}

    public static void set(ChunkCompileTaskGenerator generator) {
        GENERATOR.set(generator);
        EMISSIVE_BUFFER_STARTED.set(false);
    }

    public static RegionRenderCacheBuilder getCacheBuilder() {
        ChunkCompileTaskGenerator gen = GENERATOR.get();
        return gen != null ? gen.getRegionRenderCacheBuilder() : null;
    }

    public static CompiledChunk getCompiledChunk() {
        ChunkCompileTaskGenerator gen = GENERATOR.get();
        return gen != null ? gen.getCompiledChunk() : null;
    }

    public static void markEmissiveBufferStarted() {
        EMISSIVE_BUFFER_STARTED.set(true);
    }

    public static boolean wasEmissiveBufferStarted() {
        return EMISSIVE_BUFFER_STARTED.get();
    }

    public static void clear() {
        GENERATOR.remove();
        EMISSIVE_BUFFER_STARTED.remove();
    }
}
