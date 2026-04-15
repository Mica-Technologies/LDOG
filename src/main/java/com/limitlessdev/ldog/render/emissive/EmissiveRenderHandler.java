package com.limitlessdev.ldog.render.emissive;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/**
 * Renders emissive (fullbright) overlay quads for blocks that have
 * emissive texture overlays (*_e.png files).
 *
 * Emissive quads are rendered directly to the BufferBuilder with
 * fullbright lightmap (sky=15, block=15), bypassing the vanilla
 * AO/smooth lighting pipeline so the glow is always at max brightness.
 *
 * A small position offset along the face normal prevents z-fighting
 * with the base block texture.
 */
public final class EmissiveRenderHandler {

    /** Position offset along face normal to prevent z-fighting with base quad */
    private static final float Z_OFFSET = 0.001f;

    private EmissiveRenderHandler() {}

    /**
     * Render emissive overlay quads for a block model.
     * Called after the normal block rendering pass completes.
     */
    public static void renderEmissiveOverlay(IBakedModel model, IBlockState state,
                                              IBlockAccess world, BlockPos pos,
                                              BufferBuilder buffer, boolean checkSides,
                                              long rand) {
        // Sided quads (one per face direction)
        for (EnumFacing face : EnumFacing.values()) {
            if (checkSides && !state.shouldSideBeRendered(world, pos, face)) continue;

            List<BakedQuad> quads = model.getQuads(state, face, rand);
            for (BakedQuad quad : quads) {
                TextureAtlasSprite emissive = EmissiveTextureRegistry.getEmissiveSprite(quad.getSprite());
                if (emissive != null) {
                    addFullbrightQuad(buffer, quad, emissive, pos, face);
                }
            }
        }

        // General quads (null face, always rendered)
        List<BakedQuad> generalQuads = model.getQuads(state, null, rand);
        for (BakedQuad quad : generalQuads) {
            TextureAtlasSprite emissive = EmissiveTextureRegistry.getEmissiveSprite(quad.getSprite());
            if (emissive != null) {
                addFullbrightQuad(buffer, quad, emissive, pos, quad.getFace());
            }
        }
    }

    /**
     * Add a single fullbright quad to the buffer with the emissive texture.
     * Reads vertex positions from the original quad, remaps UV to the emissive sprite,
     * and sets fullbright lightmap (240, 240 = sky 15, block 15).
     */
    private static void addFullbrightQuad(BufferBuilder buffer, BakedQuad original,
                                           TextureAtlasSprite emissiveSprite,
                                           BlockPos pos, EnumFacing face) {
        int[] vertexData = original.getVertexData();
        TextureAtlasSprite oldSprite = original.getSprite();

        // Small offset along face normal to prevent z-fighting
        float nx = face.getXOffset() * Z_OFFSET;
        float ny = face.getYOffset() * Z_OFFSET;
        float nz = face.getZOffset() * Z_OFFSET;

        // BLOCK vertex format: pos(3f) + color(4ub) + tex(2f) + lightmap(2s) = 7 ints per vertex
        for (int v = 0; v < 4; v++) {
            int offset = v * 7;

            float x = Float.intBitsToFloat(vertexData[offset]) + pos.getX() + nx;
            float y = Float.intBitsToFloat(vertexData[offset + 1]) + pos.getY() + ny;
            float z = Float.intBitsToFloat(vertexData[offset + 2]) + pos.getZ() + nz;

            // Remap UV coordinates from base sprite to emissive sprite
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);
            float unmappedU = oldSprite.getUnInterpolatedU(u);
            float unmappedV = oldSprite.getUnInterpolatedV(vCoord);
            float newU = emissiveSprite.getInterpolatedU(unmappedU);
            float newV = emissiveSprite.getInterpolatedV(unmappedV);

            buffer.pos(x, y, z)
                  .color(255, 255, 255, 255)
                  .tex(newU, newV)
                  .lightmap(240, 240)
                  .endVertex();
        }
    }
}
