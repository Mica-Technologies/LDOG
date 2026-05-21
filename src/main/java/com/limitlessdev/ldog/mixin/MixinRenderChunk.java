package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.emissive.EmissiveRenderLayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into chunk rebuild to:
 * 1. Store per-thread reference to the chunk compile generator (for emissive rendering)
 * 2. Finalize the CUTOUT_MIPPED buffer after block rendering if we added
 *    emissive quads to it (vanilla only finalizes layers that had blocks)
 */
@Mixin(RenderChunk.class)
public abstract class MixinRenderChunk {

    @Shadow public BlockPos position;
    @Shadow protected World world;

    @Inject(method = "rebuildChunk", at = @At("HEAD"), cancellable = true)
    private void ldog$skipEmptySections(float x, float y, float z,
                                         ChunkCompileTaskGenerator generator,
                                         CallbackInfo ci) {
        if (!LDOGConfig.skipEmptyChunkSections) return;
        World w = this.world;
        if (w == null) return;
        Chunk chunk = w.getChunk(this.position);
        // Storage array index = sectionY (chunk-local Y / 16).
        int sectionY = this.position.getY() >> 4;
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        if (sectionY < 0 || sectionY >= storages.length) return;
        ExtendedBlockStorage storage = storages[sectionY];
        if (storage == null || storage == Chunk.NULL_BLOCK_STORAGE || storage.isEmpty()) {
            // Initialize the CompiledChunk to vanilla's post-build empty state so
            // RenderGlobal sees the section as built (no quads, no TEs). Without
            // this, RenderGlobal would think the chunk still needs a rebuild.
            ChunkCompileTaskGenerator.Status status = generator.getStatus();
            if (status == ChunkCompileTaskGenerator.Status.COMPILING) {
                // generator already holds a default CompiledChunk via setCompiledChunk()
                // path; vanilla rebuildChunk sets it explicitly. We mirror that here.
                generator.setCompiledChunk(new CompiledChunk());
            }
            ci.cancel();
        }
    }

    @Inject(method = "rebuildChunk", at = @At("HEAD"))
    private void ldog$captureGenerator(float x, float y, float z,
                                        ChunkCompileTaskGenerator generator,
                                        CallbackInfo ci) {
        EmissiveRenderLayer.set(generator);
    }

    @Inject(method = "rebuildChunk", at = @At("RETURN"))
    private void ldog$finalizeEmissiveBuffer(float x, float y, float z,
                                              ChunkCompileTaskGenerator generator,
                                              CallbackInfo ci) {
        if (EmissiveRenderLayer.wasEmissiveBufferStarted()) {
            RegionRenderCacheBuilder cacheBuilder = EmissiveRenderLayer.getCacheBuilder();
            CompiledChunk compiledChunk = EmissiveRenderLayer.getCompiledChunk();

            if (cacheBuilder != null && compiledChunk != null) {
                BufferBuilder buffer = cacheBuilder.getWorldRendererByLayer(BlockRenderLayer.CUTOUT_MIPPED);

                // Only finalize if the buffer is still in drawing mode
                // (vanilla may have already finalized it if the chunk has native CUTOUT_MIPPED blocks)
                if (((AccessorBufferBuilder) buffer).ldog$isDrawing()) {
                    ((AccessorCompiledChunk) compiledChunk).ldog$setLayerUsed(BlockRenderLayer.CUTOUT_MIPPED);
                    buffer.finishDrawing();
                }
            }
        }

        EmissiveRenderLayer.clear();
    }
}
