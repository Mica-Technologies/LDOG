package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.emissive.EmissiveRenderLayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into chunk rebuild to:
 * 1. Store per-thread reference to the chunk compile generator (for emissive rendering)
 * 2. Skip the block scan entirely for sections whose block storage is empty
 * 3. Finalize the CUTOUT_MIPPED buffer after block rendering if we added
 *    emissive quads to it (vanilla only marks layers that had blocks)
 */
@Mixin(RenderChunk.class)
public abstract class MixinRenderChunk {

    @Shadow public abstract BlockPos getPosition();
    @Shadow protected World world;

    /**
     * Whether the section this RenderChunk covers had no block storage when the
     * rebuild task was created. Computed on the client thread in
     * {@code makeCompileTaskChunk} (the same place vanilla snapshots its
     * {@code worldView}) because {@code rebuildChunk} runs on ChunkRenderWorker
     * threads, where touching {@code World#getChunk} races ChunkProviderClient's
     * non-concurrent chunk map. Volatile so the worker sees the capture even if
     * the hand-off queue's happens-before edge ever changes.
     */
    @Unique private volatile boolean ldog$sectionEmpty;

    @Inject(method = "makeCompileTaskChunk", at = @At("RETURN"))
    private void ldog$captureSectionEmpty(CallbackInfoReturnable<ChunkCompileTaskGenerator> cir) {
        this.ldog$sectionEmpty = LDOGConfig.skipEmptyChunkSections && ldog$isSectionEmpty();
    }

    @Unique
    private boolean ldog$isSectionEmpty() {
        World w = this.world;
        if (w == null) return false;
        BlockPos pos = this.getPosition();
        // Never force-load: an unloaded chunk is not "known empty".
        if (!w.isBlockLoaded(pos)) return false;
        Chunk chunk = w.getChunk(pos);
        // Storage array index = sectionY (world Y / 16).
        int sectionY = pos.getY() >> 4;
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        if (sectionY < 0 || sectionY >= storages.length) return false;
        // Chunk.NULL_BLOCK_STORAGE is literally null in 1.12.2.
        ExtendedBlockStorage storage = storages[sectionY];
        return storage == Chunk.NULL_BLOCK_STORAGE || storage.isEmpty();
    }

    /**
     * Single HEAD hook so the skip decision and the emissive capture cannot be
     * reordered against each other by the injector: if we cancel the rebuild we
     * must not leave a stale generator in the emissive ThreadLocal.
     */
    @Inject(method = "rebuildChunk", at = @At("HEAD"), cancellable = true)
    private void ldog$onRebuildStart(float x, float y, float z,
                                      ChunkCompileTaskGenerator generator,
                                      CallbackInfo ci) {
        if (LDOGConfig.skipEmptyChunkSections && this.ldog$sectionEmpty) {
            ldog$installEmptyCompiledChunk(generator);
            ci.cancel();
            return;
        }
        EmissiveRenderLayer.set(generator);
    }

    /**
     * Mirrors the tail of vanilla {@code rebuildChunk} for a section with no
     * geometry: publish a fresh CompiledChunk under the generator's lock and
     * give it an all-visible SetVisibility.
     *
     * <p>The visibility matters as much as the buffers do: a default
     * {@code CompiledChunk} carries an all-<i>false</i> SetVisibility, while
     * vanilla always ends with {@code VisGraph#computeVisibility()} — and an
     * empty section has no opaque cubes, so that graph reports every face pair
     * visible. {@code RenderGlobal#setupTerrain} prunes its BFS through
     * {@code getCompiledChunk().isVisible(...)} when renderChunksMany is on, so
     * an all-false section would cull every chunk behind it.
     */
    @Unique
    private void ldog$installEmptyCompiledChunk(ChunkCompileTaskGenerator generator) {
        CompiledChunk compiledChunk = new CompiledChunk();
        SetVisibility visibility = new SetVisibility();
        visibility.setAllVisible(true);
        compiledChunk.setVisibility(visibility);

        generator.getLock().lock();
        try {
            // Vanilla bails out (without publishing) when the task was aborted
            // between scheduling and execution; cancelling matches that return.
            if (generator.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING) return;
            generator.setCompiledChunk(compiledChunk);
        } finally {
            generator.getLock().unlock();
        }
    }

    @Inject(method = "rebuildChunk", at = @At("RETURN"))
    private void ldog$finalizeEmissiveBuffer(float x, float y, float z,
                                              ChunkCompileTaskGenerator generator,
                                              CallbackInfo ci) {
        if (EmissiveRenderLayer.wereEmissiveQuadsWritten()) {
            CompiledChunk compiledChunk = EmissiveRenderLayer.getCompiledChunk();

            if (compiledChunk != null) {
                // Always mark the layer used. Vanilla's post-loop finalizes every
                // *started* layer but only marks the ones a block actually drew
                // into, so a section whose only CUTOUT_MIPPED geometry is our
                // emissive overlay would stay isLayerEmpty and RenderGlobal would
                // never draw it (emissive ores in plain stone, etc.).
                ((AccessorCompiledChunk) compiledChunk).ldog$setLayerUsed(BlockRenderLayer.CUTOUT_MIPPED);

                RegionRenderCacheBuilder cacheBuilder = EmissiveRenderLayer.getCacheBuilder();
                if (cacheBuilder != null) {
                    BufferBuilder buffer = cacheBuilder.getWorldRendererByLayer(BlockRenderLayer.CUTOUT_MIPPED);
                    // Normally vanilla already finished it (we set layerStarted, and
                    // vanilla finalizes every started layer). Defensive only.
                    if (((AccessorBufferBuilder) buffer).ldog$isDrawing()) {
                        buffer.finishDrawing();
                    }
                }
            }
        }

        EmissiveRenderLayer.clear();
    }
}
