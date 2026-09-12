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
 * biome-tinted top texture ("Better Grass" effect) and optionally with
 * the snow texture when the block is snowy ("Better Snow" for grass).
 *
 * In Fast mode, all horizontal side faces unconditionally show the top texture.
 * In Fancy mode, sides only show the top texture when the block diagonally
 * below-adjacent (pos.offset(side).down()) is also a grass-type block,
 * preserving natural-looking cliff edges.
 *
 * The face's base (untinted) quad is retextured with grass_top and gains
 * tintIndex=0 for biome color; the tinted overlay quad is dropped since the
 * full face is now tinted. Base and overlay are identified by structure
 * rather than by sprite name so re-textured packs keep working — see
 * {@link GrassSideSelection}.
 * When Better Snow is enabled and the block is snowy (snowed side sprite
 * detected), side quads are replaced with the snow texture instead, so
 * lighting is handled by the vanilla pipeline (smooth AO) rather than
 * flat overlay quads.
 *
 * Reuses CTMRenderContext (set by MixinBlockRendererDispatcher) for
 * world access in Fancy mode.
 */
public class BetterGrassBakedModel extends BakedModelWrapper<IBakedModel> {

    private final TextureAtlasSprite topSprite;
    @Nullable
    private final String overlaySpriteName;
    @Nullable
    private final String snowedSideSpriteName;
    @Nullable
    private final TextureAtlasSprite snowSprite;

    public BetterGrassBakedModel(IBakedModel original, TextureAtlasSprite topSprite,
                                  @Nullable String overlaySpriteName,
                                  @Nullable String snowedSideSpriteName,
                                  @Nullable TextureAtlasSprite snowSprite) {
        super(original);
        this.topSprite = topSprite;
        this.overlaySpriteName = overlaySpriteName;
        this.snowedSideSpriteName = snowedSideSpriteName;
        this.snowSprite = snowSprite;
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

        // Better Snow for grass: when snowy, replace snowed side texture with snow
        if (LDOGConfig.enableBetterSnow && snowSprite != null && snowedSideSpriteName != null) {
            boolean hasSnowedSide = false;
            for (BakedQuad quad : original) {
                if (snowedSideSpriteName.equals(quad.getSprite().getIconName())) {
                    hasSnowedSide = true;
                    break;
                }
            }
            if (hasSnowedSide) {
                List<BakedQuad> result = new ArrayList<>(original.size());
                for (BakedQuad quad : original) {
                    if (snowedSideSpriteName.equals(quad.getSprite().getIconName())) {
                        // Replace grass_side_snowed with snow — vanilla AO handles lighting
                        result.add(retextureWithTint(quad, snowSprite, -1));
                    } else {
                        result.add(quad);
                    }
                }
                return result;
            }
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

        // Replace the face's base quad with the top texture and drop the overlay.
        //
        // Which quad is which is decided structurally (untinted base + tinted
        // overlay), NOT by sprite name: a pack is free to re-texture the grass
        // model, and Stratum 256x does exactly that — its grass_normal.json sets
        // "side": "blocks/dirt" and carries the whole fringe in the tinted
        // overlay. Matching the literal name minecraft:blocks/grass_side found
        // nothing there, so Better Grass silently no-opped in every mode.
        // See GrassSideSelection.
        int n = original.size();
        String[] sprites = new String[n];
        int[] tints = new int[n];
        for (int i = 0; i < n; i++) {
            BakedQuad q = original.get(i);
            sprites[i] = q.getSprite().getIconName();
            tints[i] = q.getTintIndex();
        }
        int[] plan = GrassSideSelection.classify(sprites, tints, overlaySpriteName);

        List<BakedQuad> result = new ArrayList<>(n);
        boolean anyReplaced = false;
        for (int i = 0; i < n; i++) {
            switch (plan[i]) {
                case GrassSideSelection.RETEXTURE:
                    // Tint index 0 so the biome colouriser greens the whole face.
                    result.add(retextureWithTint(original.get(i), topSprite, 0));
                    anyReplaced = true;
                    break;
                case GrassSideSelection.DROP:
                    anyReplaced = true;
                    break;
                default:
                    result.add(original.get(i));
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

        // Stride from the quad's own format rather than a hard-coded 7: the
        // terrain format carries a normal when TerrainVertexFormat extended it,
        // and a quad baked against that layout is 8 ints per vertex. The UV
        // pair stays at ints 4-5 either way (position 0-2, colour 3, uv 4-5).
        int stride = original.getFormat().getIntegerSize() / 4;
        if (stride < 6) stride = 7;

        for (int v = 0; v < 4; v++) {
            int offset = v * stride;
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
