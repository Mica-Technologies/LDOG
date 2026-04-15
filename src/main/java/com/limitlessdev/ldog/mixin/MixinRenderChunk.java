package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.emissive.EmissiveRenderLayer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
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
