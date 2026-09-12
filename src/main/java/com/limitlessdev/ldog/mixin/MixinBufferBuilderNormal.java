package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.shaderpack.TerrainVertexFormat;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps hand-written BLOCK vertices valid once {@link TerrainVertexFormat}
 * has appended a normal to the format.
 *
 * <p>{@code BufferBuilder} cycles through the format's elements as
 * {@code pos().color().tex().lightmap()} are called and does NOT reset that
 * cursor in {@code endVertex}. Code that writes the four vanilla elements
 * and stops (FastTESRs, falling blocks, LDOG's own emissive overlay) leaves
 * the cursor on NORMAL, so the next vertex's {@code pos()} would land in the
 * normal slot and everything after it would be shifted. Filling the missing
 * normal here advances the cursor through NORMAL and PADDING back to the
 * start of the next vertex. Chunk geometry never hits this path: Forge's
 * lighter writes every element, normal included.
 */
@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilderNormal {

    @Shadow private int vertexFormatIndex;
    @Shadow private VertexFormatElement vertexFormatElement;
    @Shadow private VertexFormat vertexFormat;

    @Shadow public abstract BufferBuilder normal(float x, float y, float z);

    @Inject(method = "endVertex", at = @At("HEAD"))
    private void ldog$fillMissingNormal(CallbackInfo ci) {
        if (!TerrainVertexFormat.isExtended()) return;
        if (vertexFormatIndex == 0 || vertexFormat != DefaultVertexFormats.BLOCK) return;
        if (vertexFormatElement != null
            && vertexFormatElement.getUsage() == VertexFormatElement.EnumUsage.NORMAL) {
            // Up-facing default: matches what a writer that never set a normal
            // got from vanilla's leftover glNormal(0,1,0) more often than not.
            normal(0.0F, 1.0F, 0.0F);
        }
    }
}
