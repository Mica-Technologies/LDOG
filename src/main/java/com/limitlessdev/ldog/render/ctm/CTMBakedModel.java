package com.limitlessdev.ldog.render.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a baked block model to provide connected textures.
 * Intercepts getQuads() calls and swaps texture sprites based on
 * neighbor block connections.
 *
 * This model wrapper is applied at ModelBakeEvent time by CTMRegistry
 * for blocks that have CTM definitions.
 */
public class CTMBakedModel extends BakedModelWrapper<IBakedModel> {

    private final Block targetBlock;
    private final CTMProperties properties;
    private final List<TextureAtlasSprite> tileSprites;

    public CTMBakedModel(IBakedModel original, Block targetBlock,
                          CTMProperties properties, List<TextureAtlasSprite> tileSprites) {
        super(original);
        this.targetBlock = targetBlock;
        this.properties = properties;
        this.tileSprites = tileSprites;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side,
                                     long rand) {
        List<BakedQuad> originalQuads = originalModel.getQuads(state, side, rand);

        // If no state info (item rendering) or no side, return original
        if (state == null || side == null || tileSprites.isEmpty()) {
            return originalQuads;
        }

        // For now, return original quads -- the actual texture swapping
        // requires IBlockAccess (world) which isn't available in getQuads().
        // Full CTM implementation needs either:
        // 1. IExtendedBlockState with neighbor data baked in
        // 2. A custom chunk rebuild hook that passes world context
        //
        // This wrapper is the framework; the actual neighbor-based texture
        // swapping will be completed when we add the chunk rebuild hook.
        return originalQuads;
    }

    /**
     * Retexture a quad with a different sprite.
     * Copies vertex data and remaps UV coordinates from the original sprite
     * to the new sprite.
     */
    public static BakedQuad retextureQuad(BakedQuad original, TextureAtlasSprite newSprite) {
        TextureAtlasSprite oldSprite = original.getSprite();
        if (oldSprite == newSprite) return original;

        int[] vertexData = original.getVertexData().clone();

        // Each vertex has 7 ints: x, y, z, color, u, v, lightmap
        // UV is at indices 4 and 5 within each vertex
        for (int v = 0; v < 4; v++) {
            int offset = v * 7;
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);

            // Unmap from old sprite's UV space to 0-16 space
            float unmappedU = oldSprite.getUnInterpolatedU(u);
            float unmappedV = oldSprite.getUnInterpolatedV(vCoord);

            // Remap to new sprite's UV space
            float newU = newSprite.getInterpolatedU(unmappedU);
            float newV = newSprite.getInterpolatedV(unmappedV);

            vertexData[offset + 4] = Float.floatToRawIntBits(newU);
            vertexData[offset + 5] = Float.floatToRawIntBits(newV);
        }

        return new BakedQuad(vertexData, original.getTintIndex(), original.getFace(),
            newSprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }
}
