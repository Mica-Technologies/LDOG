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
 * Wraps grass/mycelium baked models to replace side textures with the
 * biome-tinted top texture ("Better Grass" effect).
 *
 * In Fast mode, all horizontal side faces unconditionally show the top texture.
 * In Fancy mode, sides only show the top texture when the block diagonally
 * below-adjacent (pos.offset(side).down()) is also a grass-type block,
 * preserving natural-looking cliff edges.
 *
 * The model wrapper checks quad sprites by name: grass_side quads are
 * retextured with grass_top (gaining tintIndex=0 for biome color), and
 * grass_side_overlay quads are removed since the full face is now tinted.
 * Snowy variants pass through unchanged because their side sprite
 * (grass_side_snowed) doesn't match the target sprite name.
 *
 * Reuses CTMRenderContext (set by MixinBlockRendererDispatcher) for
 * world access in Fancy mode.
 */
public class BetterGrassBakedModel extends BakedModelWrapper<IBakedModel> {

    private final TextureAtlasSprite topSprite;
    private final String sideSpriteName;
    @Nullable
    private final String overlaySpriteName;

    public BetterGrassBakedModel(IBakedModel original, TextureAtlasSprite topSprite,
                                  String sideSpriteName, @Nullable String overlaySpriteName) {
        super(original);
        this.topSprite = topSprite;
        this.sideSpriteName = sideSpriteName;
        this.overlaySpriteName = overlaySpriteName;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side,
                                     long rand) {
        List<BakedQuad> original = super.getQuads(state, side, rand);

        // Only modify horizontal side faces (not top, bottom, or general quads)
        if (side == null || side.getAxis() == EnumFacing.Axis.Y) {
            return original;
        }

        // Null state = inventory rendering, pass through
        if (state == null) {
            return original;
        }

        if ("off".equalsIgnoreCase(LDOGConfig.betterGrass)) {
            return original;
        }

        // Fancy mode: check if the block below-adjacent is also a grass-type block
        if ("fancy".equalsIgnoreCase(LDOGConfig.betterGrass)) {
            if (!shouldApplyFancy(side)) {
                return original;
            }
        }

        // Replace side quads with top texture
        List<BakedQuad> result = new ArrayList<>(original.size());
        boolean anyReplaced = false;

        for (BakedQuad quad : original) {
            String spriteName = quad.getSprite().getIconName();

            if (sideSpriteName.equals(spriteName)) {
                // Replace grass_side with grass_top, set tintIndex=0 for biome coloring
                result.add(retextureWithTint(quad, topSprite, 0));
                anyReplaced = true;
            } else if (overlaySpriteName != null && overlaySpriteName.equals(spriteName)) {
                // Skip the overlay quad — the full face is now the tinted top texture
                anyReplaced = true;
            } else {
                result.add(quad);
            }
        }

        return anyReplaced ? result : original;
    }

    private boolean shouldApplyFancy(EnumFacing side) {
        IBlockAccess world = CTMRenderContext.getWorld();
        BlockPos pos = CTMRenderContext.getPos();
        if (world == null || pos == null) return false;

        // Check if the block diagonally below the neighbor is also grass-like.
        // This means the grass surface visually continues around the corner.
        BlockPos neighborBelow = pos.offset(side).down();
        Block below = world.getBlockState(neighborBelow).getBlock();
        return below == Blocks.GRASS || below == Blocks.MYCELIUM;
    }

    /**
     * Retexture a quad with a new sprite and tintIndex.
     * UV coordinates are remapped from the old sprite's atlas region to the new one.
     */
    private static BakedQuad retextureWithTint(BakedQuad original, TextureAtlasSprite newSprite,
                                                int tintIndex) {
        TextureAtlasSprite oldSprite = original.getSprite();
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

        return new BakedQuad(vertexData, tintIndex, original.getFace(),
            newSprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }
}
