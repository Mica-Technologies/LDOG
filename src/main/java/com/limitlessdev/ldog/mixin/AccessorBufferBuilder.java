package com.limitlessdev.ldog.mixin;

import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BufferBuilder.class)
public interface AccessorBufferBuilder {

    @Accessor("isDrawing")
    boolean ldog$isDrawing();
}
