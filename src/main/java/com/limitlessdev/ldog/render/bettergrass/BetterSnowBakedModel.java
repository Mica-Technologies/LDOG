package com.limitlessdev.ldog.render.bettergrass;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.ctm.CTMRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.BakedModelWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight model wrapper for common blocks (dirt, stone, sand, etc.)
 * that replaces side textures with the snow sprite when a snow layer
 * sits on top.
 *
 * Unlike the overlay approach in BetterSnowHandler (which adds extra
 * quads with flat lighting), this wrapper REPLACES the existing quads'
 * textures, so vanilla's smooth AO lighting pipeline handles the
 * shading correctly — matching the snow layer on top.
 *
 * Uses CTMRenderContext for world/position access.
 */
public class BetterSnowBakedModel extends BakedModelWrapper<IBakedModel> {

    private final TextureAtlasSprite snowSprite;

    public BetterSnowBakedModel(IBakedModel original, TextureAtlasSprite snowSprite) {
        super(original);
        this.snowSprite = snowSprite;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side,
                                     long rand) {
        List<BakedQuad> original = super.getQuads(state, side, rand);

        if (!LDOGConfig.enableBetterSnow || state == null || snowSprite == null) {
            return original;
        }

        // Only modify horizontal side faces
        if (side == null || side.getAxis() == EnumFacing.Axis.Y) {
            return original;
        }

        // Check for snow above via CTMRenderContext
        IBlockAccess world = CTMRenderContext.getWorld();
        BlockPos pos = CTMRenderContext.getPos();
        if (world == null || pos == null) return original;

        Block above = world.getBlockState(pos.up()).getBlock();
        if (above != Blocks.SNOW_LAYER && above != Blocks.SNOW) return original;

        // Replace all side quads with snow texture
        List<BakedQuad> result = new ArrayList<>(original.size());
        boolean anyReplaced = false;

        for (BakedQuad quad : original) {
            result.add(retexture(quad, snowSprite));
            anyReplaced = true;
        }

        return anyReplaced ? result : original;
    }

    private static BakedQuad retexture(BakedQuad original, TextureAtlasSprite newSprite) {
        TextureAtlasSprite oldSprite = original.getSprite();
        if (oldSprite == newSprite) return original;

        int[] vertexData = original.getVertexData().clone();

        for (int v = 0; v < 4; v++) {
            int offset = v * 7;
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);

            float unmappedU = oldSprite.getUnInterpolatedU(u);
            float unmappedV = oldSprite.getUnInterpolatedV(vCoord);

            vertexData[offset + 4] = Float.floatToRawIntBits(newSprite.getInterpolatedU(unmappedU));
            vertexData[offset + 5] = Float.floatToRawIntBits(newSprite.getInterpolatedV(unmappedV));
        }

        return new BakedQuad(vertexData, original.getTintIndex(), original.getFace(),
            newSprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }
}
