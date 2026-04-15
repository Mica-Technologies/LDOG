package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.emissive.EmissiveRenderLayer;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into chunk rebuild to store per-thread references to the
 * chunk buffer builders. This allows emissive rendering to write
 * quads into the CUTOUT_MIPPED buffer (for alpha testing) even when
 * the current block is rendered in the SOLID layer.
 */
@Mixin(RenderChunk.class)
public abstract class MixinRenderChunk {

    @Inject(method = "rebuildChunk", at = @At("HEAD"))
    private void ldog$captureBuffers(float x, float y, float z,
                                      ChunkCompileTaskGenerator generator,
                                      CallbackInfo ci) {
        EmissiveRenderLayer.set(
            generator.getRegionRenderCacheBuilder(),
            generator.getCompiledChunk());
    }

    @Inject(method = "rebuildChunk", at = @At("RETURN"))
    private void ldog$clearBuffers(float x, float y, float z,
                                    ChunkCompileTaskGenerator generator,
                                    CallbackInfo ci) {
        EmissiveRenderLayer.clear();
    }
}
