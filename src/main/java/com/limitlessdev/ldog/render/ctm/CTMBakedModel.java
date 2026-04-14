package com.limitlessdev.ldog.render.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.BakedModelWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a baked block model to provide connected textures.
 * Reads neighbor data from CTMRenderContext (set via ThreadLocal
 * by MixinBlockRendererDispatcher during chunk rebuilds) and swaps
 * texture sprites based on connection patterns.
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

        if (state == null || side == null || tileSprites.isEmpty()) {
            return originalQuads;
        }

        // Get world context from ThreadLocal (set by MixinBlockRendererDispatcher)
        IBlockAccess world = CTMRenderContext.getWorld();
        BlockPos pos = CTMRenderContext.getPos();

        if (world == null || pos == null) {
            return originalQuads;
        }

        // Calculate the tile index based on neighbor connections
        int tileIndex = calculateTileIndex(world, pos, side);
        if (tileIndex < 0 || tileIndex >= tileSprites.size()) {
            return originalQuads;
        }

        TextureAtlasSprite tileSprite = tileSprites.get(tileIndex);
        if (tileSprite == null) {
            return originalQuads;
        }

        // Retexture all quads on this face with the connected texture
        List<BakedQuad> result = new ArrayList<>(originalQuads.size());
        for (BakedQuad quad : originalQuads) {
            result.add(retextureQuad(quad, tileSprite));
        }
        return result;
    }

    private int calculateTileIndex(IBlockAccess world, BlockPos pos, EnumFacing side) {
        switch (properties.getMethod()) {
            case CTM:
                return CTMLogic.getFullCTMIndex(world, pos, side, targetBlock);
            case HORIZONTAL:
                return CTMLogic.getHorizontalCTMIndex(world, pos, side, targetBlock);
            case VERTICAL:
                return CTMLogic.getVerticalCTMIndex(world, pos, targetBlock);
            case FIXED:
                return 0;
            default:
                return 0;
        }
    }

    /**
     * Retexture a quad with a different sprite.
     * Copies vertex data and remaps UV coordinates.
     */
    public static BakedQuad retextureQuad(BakedQuad original, TextureAtlasSprite newSprite) {
        TextureAtlasSprite oldSprite = original.getSprite();
        if (oldSprite == newSprite) return original;

        int[] vertexData = original.getVertexData().clone();

        for (int v = 0; v < 4; v++) {
            int offset = v * 7;
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);

            float unmappedU = oldSprite.getUnInterpolatedU(u);
            float unmappedV = oldSprite.getUnInterpolatedV(vCoord);

            vertexData[offset + 4] = Float.floatToRawIntBits(
                newSprite.getInterpolatedU(unmappedU));
            vertexData[offset + 5] = Float.floatToRawIntBits(
                newSprite.getInterpolatedV(unmappedV));
        }

        return new BakedQuad(vertexData, original.getTintIndex(), original.getFace(),
            newSprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }
}
