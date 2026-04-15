package com.limitlessdev.ldog.render.emissive;

import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;

/**
 * Stores per-thread references to the current chunk's buffer builder set
 * and compiled chunk during chunk rebuilds. This allows the emissive
 * renderer to write quads into the CUTOUT_MIPPED buffer (which has
 * alpha testing) even when the current block renders in the SOLID layer.
 */
public final class EmissiveRenderLayer {

    private static final ThreadLocal<RegionRenderCacheBuilder> CACHE_BUILDER = new ThreadLocal<>();
    private static final ThreadLocal<CompiledChunk> COMPILED_CHUNK = new ThreadLocal<>();

    private EmissiveRenderLayer() {}

    public static void set(RegionRenderCacheBuilder builder, CompiledChunk compiled) {
        CACHE_BUILDER.set(builder);
        COMPILED_CHUNK.set(compiled);
    }

    public static RegionRenderCacheBuilder getCacheBuilder() {
        return CACHE_BUILDER.get();
    }

    public static CompiledChunk getCompiledChunk() {
        return COMPILED_CHUNK.get();
    }

    public static void clear() {
        CACHE_BUILDER.remove();
        COMPILED_CHUNK.remove();
    }
}
