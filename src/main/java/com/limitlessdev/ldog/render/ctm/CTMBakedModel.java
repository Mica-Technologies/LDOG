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
import java.util.Set;

/**
 * Wraps a baked block model to provide connected textures.
 * Reads neighbor data from CTMRenderContext (set via ThreadLocal
 * by MixinBlockRendererDispatcher during chunk rebuilds) and swaps
 * texture sprites based on connection patterns.
 *
 * targetSpriteNames: if non-null, only quads using one of these sprites
 * are retextured. This prevents accidentally CTM-ing secondary textures
 * (e.g. glass_pane_top edge-cap faces on glass pane arms).
 * If null, all side-face quads are eligible (used for full-block models
 * where all quads are the primary texture).
 */
public class CTMBakedModel extends BakedModelWrapper<IBakedModel> {

    private final Block targetBlock;
    private final CTMProperties properties;
    private final List<TextureAtlasSprite> tileSprites;
    @Nullable
    private final Set<String> targetSpriteNames;

    public CTMBakedModel(IBakedModel original, Block targetBlock,
                          CTMProperties properties, List<TextureAtlasSprite> tileSprites,
                          @Nullable Set<String> targetSpriteNames) {
        super(original);
        this.targetBlock = targetBlock;
        this.properties = properties;
        this.tileSprites = tileSprites;
        this.targetSpriteNames = targetSpriteNames;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side,
                                     long rand) {
        List<BakedQuad> originalQuads = originalModel.getQuads(state, side, rand);

        if (state == null || tileSprites.isEmpty()) {
            return originalQuads;
        }

        // Get world context from ThreadLocal (set by MixinBlockRendererDispatcher)
        IBlockAccess world = CTMRenderContext.getWorld();
        BlockPos pos = CTMRenderContext.getPos();

        if (world == null || pos == null) {
            return originalQuads;
        }

        // --- TEMPORARY DEBUG: log null-side quad info to diagnose vertical CTM seam ---
        // For null side (general quads), retexture using the quad's own face.
        // For specific sides, use that side for neighbor calculation.
        // Glass pane surfaces are null-face quads (they're not at the block boundary),
        // so we infer the facing from the quad's normal vector when getFace() is null.
        List<BakedQuad> result = new ArrayList<>(originalQuads.size());
        for (BakedQuad quad : originalQuads) {
            EnumFacing face = side != null ? side : quad.getFace();

            // For null-face quads, infer direction from geometry so that
            // pane/thin-block surfaces still get CTM (vertical glass pane connections).
            if (face == null) {
                face = inferFace(quad);
            }

            // Glass-pane edge fix: pane_side.json has UP/DOWN faces (glass_pane_top border
            // texture) with no cullface. FaceBakery sets getFace()=UP/DOWN so they come
            // through the null-side call. When panes are stacked both rows render their
            // border strip at the same Y, creating a visible seam. Since faces=sides
            // excludes top/bottom, CTM never replaces them. Suppress them instead when
            // the adjacent block is the same type, which removes the seam entirely.
            // Guard on side==null so we never suppress quads from the explicit side call.
            if (side == null
                    && (face == EnumFacing.UP || face == EnumFacing.DOWN)
                    && !properties.appliesToFace(faceName(face))
                    && world.getBlockState(pos.offset(face)).getBlock() == targetBlock) {
                continue; // suppress edge-strip face to eliminate vertical seam
            }

            // Check face restriction from properties
            if (!properties.appliesToFace(faceName(face))) {
                result.add(quad);
                continue;
            }

            // Only retexture quads using the primary glass/block sprite.
            // Pane models have secondary textures (e.g. glass_pane_top on arm
            // end-cap faces with cullface) that must not be overwritten with
            // glass CTM tiles — those end-caps provide the visible border strip.
            if (targetSpriteNames != null
                    && !targetSpriteNames.contains(quad.getSprite().getIconName())) {
                result.add(quad);
                continue;
            }

            int tileIndex = calculateTileIndex(world, pos, face);

            if (tileIndex >= 0 && tileIndex < tileSprites.size()) {
                TextureAtlasSprite tileSprite = tileSprites.get(tileIndex);
                if (tileSprite != null && !"missingno".equals(tileSprite.getIconName())) {
                    result.add(retextureQuad(quad, tileSprite, face));
                    continue;
                }
            }
            result.add(quad); // Fallback to original
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
     * Retexture a quad with a different sprite using geometry-based UV mapping.
     *
     * Instead of preserving the original sprite's UV offsets (which are wrong for
     * partial-face quads like glass pane arms — each arm only covers half the block
     * face width and therefore only samples half the CTM tile), this method computes
     * UV from vertex world-space positions and the face direction.
     *
     * For horizontal faces (EAST/WEST/NORTH/SOUTH):
     *   - U is derived from the position along the face-perpendicular axis (Z for E/W,
     *     X for N/S), STRETCHED to span [0,16] across the quad's actual extent.
     *     This ensures pane arms and other sub-face quads always show the full CTM
     *     tile, so borders appear at the quad's edges rather than outside it.
     *   - V is derived from absolute Y position (no stretch), so slabs/stairs still
     *     show the proportional vertical slice of the tile.
     *
     * For horizontal-face quads (UP/DOWN), absolute X and Z are used (no stretch).
     */
    public static BakedQuad retextureQuad(BakedQuad original, TextureAtlasSprite newSprite,
                                           @Nullable EnumFacing face) {
        if (original.getSprite() == newSprite) return original;

        int[] vertexData = original.getVertexData().clone();

        // Pre-scan vertices to find the extent along the face-perpendicular axis.
        // This is only needed for vertical faces (E/W/N/S) to enable U-stretching.
        float axisMin = Float.MAX_VALUE, axisMax = -Float.MAX_VALUE;
        if (face == EnumFacing.EAST || face == EnumFacing.WEST) {
            for (int v = 0; v < 4; v++) {
                float z = Float.intBitsToFloat(vertexData[v * 7 + 2]);
                if (z < axisMin) axisMin = z;
                if (z > axisMax) axisMax = z;
            }
        } else if (face == EnumFacing.NORTH || face == EnumFacing.SOUTH) {
            for (int v = 0; v < 4; v++) {
                float x = Float.intBitsToFloat(vertexData[v * 7]);
                if (x < axisMin) axisMin = x;
                if (x > axisMax) axisMax = x;
            }
        }
        float axisSpan = (axisMax > axisMin) ? (axisMax - axisMin) : 1f;

        for (int v = 0; v < 4; v++) {
            int offset = v * 7;
            float x = Float.intBitsToFloat(vertexData[offset]);
            float y = Float.intBitsToFloat(vertexData[offset + 1]);
            float z = Float.intBitsToFloat(vertexData[offset + 2]);

            float newU, newV;
            if (face == null) {
                // Unknown face: fall back to original sprite UV remapping
                float origU = Float.intBitsToFloat(vertexData[offset + 4]);
                float origV = Float.intBitsToFloat(vertexData[offset + 5]);
                newU = original.getSprite().getUnInterpolatedU(origU);
                newV = original.getSprite().getUnInterpolatedV(origV);
            } else {
                // V is always absolute Y position (no stretch across height)
                newV = (1f - y) * 16f;

                switch (face) {
                    // EAST/WEST: U varies along Z, stretched to quad Z extent.
                    // EAST looks toward -X; "right" = North (-Z) so U increases as z decreases.
                    case EAST:
                        newU = (1f - (z - axisMin) / axisSpan) * 16f;
                        break;
                    // WEST looks toward +X; "right" = South (+Z) so U increases as z increases.
                    case WEST:
                        newU = ((z - axisMin) / axisSpan) * 16f;
                        break;
                    // NORTH/SOUTH: U varies along X, stretched to quad X extent.
                    // NORTH looks toward +Z; "right" = East (+X) so U increases as x increases.
                    case NORTH:
                        newU = ((x - axisMin) / axisSpan) * 16f;
                        break;
                    // SOUTH looks toward -Z; "right" = West (-X) so U increases as x decreases.
                    case SOUTH:
                        newU = (1f - (x - axisMin) / axisSpan) * 16f;
                        break;
                    // UP/DOWN: use absolute X/Z (tiles already span the full block face).
                    case UP:
                        newU = x * 16f;
                        newV = z * 16f;
                        break;
                    case DOWN:
                        newU = x * 16f;
                        newV = (1f - z) * 16f;
                        break;
                    default:
                        float origU = Float.intBitsToFloat(vertexData[offset + 4]);
                        float origV = Float.intBitsToFloat(vertexData[offset + 5]);
                        newU = original.getSprite().getUnInterpolatedU(origU);
                        newV = original.getSprite().getUnInterpolatedV(origV);
                }
            }

            vertexData[offset + 4] = Float.floatToRawIntBits(newSprite.getInterpolatedU(newU));
            vertexData[offset + 5] = Float.floatToRawIntBits(newSprite.getInterpolatedV(newV));
        }

        return new BakedQuad(vertexData, original.getTintIndex(), original.getFace(),
            newSprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }

    private static String faceName(EnumFacing face) {
        if (face == null) return "all";
        switch (face) {
            case UP:    return "top";
            case DOWN:  return "bottom";
            default:    return face.getName();
        }
    }

    /**
     * Infer the outward-facing direction of a quad from its vertex normal.
     * Used for null-face quads (e.g. glass pane surfaces) that aren't registered
     * as block-boundary faces but still need CTM applied.
     *
     * Vertex format: 7 ints/vertex — pos(3f), color(1i), tex(2f), lightmap(1i).
     * Winding is CCW when viewed from outside, so (v1−v0)×(v2−v0) points outward.
     */
    private static EnumFacing inferFace(BakedQuad quad) {
        int[] vd = quad.getVertexData();
        float x0 = Float.intBitsToFloat(vd[0]);
        float y0 = Float.intBitsToFloat(vd[1]);
        float z0 = Float.intBitsToFloat(vd[2]);
        float x1 = Float.intBitsToFloat(vd[7]);
        float y1 = Float.intBitsToFloat(vd[8]);
        float z1 = Float.intBitsToFloat(vd[9]);
        float x2 = Float.intBitsToFloat(vd[14]);
        float y2 = Float.intBitsToFloat(vd[15]);
        float z2 = Float.intBitsToFloat(vd[16]);

        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;

        float absX = Math.abs(nx), absY = Math.abs(ny), absZ = Math.abs(nz);
        if (absY >= absX && absY >= absZ) return ny > 0 ? EnumFacing.UP    : EnumFacing.DOWN;
        if (absX >= absZ)                 return nx > 0 ? EnumFacing.EAST  : EnumFacing.WEST;
        return                                   nz > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }
}
