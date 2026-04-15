package com.limitlessdev.ldog.mixin;

import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CompiledChunk.class)
public interface AccessorCompiledChunk {

    @Invoker("setLayerUsed")
    void ldog$setLayerUsed(BlockRenderLayer layer);
}
