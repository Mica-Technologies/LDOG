package com.limitlessdev.ldog.render.ctm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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
 *
 * For glass panes: pane arms only cover half the block face width (7 of
 * 16 pixels each side), so each arm shows only half the CTM tile. When
 * an arm is absent (no neighbor in that direction), synthetic quads are
 * added for the missing arm area so that CTM borders appear at the correct
 * outer face edges rather than being invisible.
 */
public class CTMBakedModel extends BakedModelWrapper<IBakedModel> {

    /** Local block coordinate of pane glass surfaces (east/west sides of 2px pane). */
    private static final float PANE_X_WEST = 7f / 16f;
    private static final float PANE_X_EAST = 9f / 16f;
    /** Local block z of the center post's north/south faces (junction between post and arms). */
    private static final float PANE_Z_NORTH_JUNCTION = 7f / 16f;
    private static final float PANE_Z_SOUTH_JUNCTION = 9f / 16f;

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

        IBlockAccess world = CTMRenderContext.getWorld();
        BlockPos pos = CTMRenderContext.getPos();

        if (world == null || pos == null) {
            return originalQuads;
        }

        List<BakedQuad> result = new ArrayList<>(originalQuads.size());
        for (BakedQuad quad : originalQuads) {
            EnumFacing face = side != null ? side : quad.getFace();

            // For null-face quads (e.g. glass pane arms without cullface), infer
            // the facing direction from vertex geometry so CTM can be applied.
            if (face == null) {
                face = inferFace(quad);
            }

            // Check face restriction from CTM properties
            if (!properties.appliesToFace(faceName(face))) {
                result.add(quad);
                continue;
            }

            // Only retexture quads using the primary glass/block sprite.
            // Pane models have secondary textures (e.g. glass_pane_top on arm
            // end-cap faces with cullface) that must not be overwritten with
            // glass CTM tiles.
            if (targetSpriteNames != null
                    && !targetSpriteNames.contains(quad.getSprite().getIconName())) {
                result.add(quad);
                continue;
            }

            // Pane models mirror the U axis on WEST and NORTH faces so both sides
            // of each arm show the same texture at the same world position. Mirror
            // the CTM left-right neighbor check to match the mirrored UV orientation.
            boolean mirrorH = targetBlock instanceof BlockPane
                    && (face == EnumFacing.WEST || face == EnumFacing.NORTH);
            int tileIndex = calculateTileIndex(world, pos, face, mirrorH);

            if (tileIndex >= 0 && tileIndex < tileSprites.size()) {
                TextureAtlasSprite tileSprite = tileSprites.get(tileIndex);
                if (tileSprite != null && !"missingno".equals(tileSprite.getIconName())) {
                    result.add(retextureQuad(quad, tileSprite));
                    continue;
                }
            }
            result.add(quad);
        }

        // For glass panes, add synthetic quads covering absent arm areas so that
        // CTM borders appear at the correct outer face edges. Without this, a pane
        // with north=false would have no geometry at z=[0,7/16] and the CTM tile's
        // north border would be invisible.
        if (side == null && targetBlock instanceof BlockPane) {
            addSyntheticPaneQuads(result, state, world, pos);
        }

        return result;
    }

    /**
     * @param mirrorH Mirror the horizontal (left-right) neighbor pattern. Used for pane
     *                WEST and NORTH faces where the model intentionally mirrors the U axis
     *                so both sides of each arm show the same texture at the same world
     *                position. Without mirroring, the CTM tile's border orientation
     *                doesn't match the mirrored UV, causing borders at wrong edges.
     */
    private int calculateTileIndex(IBlockAccess world, BlockPos pos, EnumFacing side,
                                    boolean mirrorH) {
        switch (properties.getMethod()) {
            case CTM:
                return CTMLogic.getFullCTMIndex(world, pos, side, targetBlock, mirrorH);
            case HORIZONTAL:
                return CTMLogic.getHorizontalCTMIndex(world, pos, side, targetBlock, mirrorH);
            case VERTICAL:
                return CTMLogic.getVerticalCTMIndex(world, pos, targetBlock);
            case FIXED:
                return 0;
            default:
                return 0;
        }
    }

    private int calculateTileIndex(IBlockAccess world, BlockPos pos, EnumFacing side) {
        return calculateTileIndex(world, pos, side, false);
    }

    /**
     * Adds synthetic BakedQuads for absent pane arm areas.
     *
     * A glass pane arm (e.g. north arm) covers z=[0,7/16] and provides EAST/WEST
     * face quads that show U=[9,16] of the CTM tile (with the CTM border at U=16
     * appearing at z=0, the outer north edge). When the arm is absent (north=false),
     * there is no geometry in z=[0,7/16], so the CTM border at the outer edge is
     * invisible. Synthetic quads fill in this missing geometry so borders render
     * correctly at block face edges.
     *
     * Synthetic quads are not added for fully isolated panes (no connections).
     */
    private void addSyntheticPaneQuads(List<BakedQuad> result, IBlockState state,
                                        IBlockAccess world, BlockPos pos) {
        boolean north = state.getValue(BlockPane.NORTH);
        boolean south = state.getValue(BlockPane.SOUTH);
        boolean east  = state.getValue(BlockPane.EAST);
        boolean west  = state.getValue(BlockPane.WEST);

        // Only add synthetic quads when at least one arm on the same axis is present.
        // Synthetic EAST/WEST face quads (for absent N/S arms) only make sense when
        // the pane has at least one N or S arm — these quads fill the missing half of
        // the N-S arm glass surface so CTM borders appear at the outer edge.
        // Synthetic NORTH/SOUTH face quads similarly require at least one E/W arm.
        // Without this guard, a flat N-S wall (north+south only, east=false, west=false)
        // would get phantom glass surfaces added in the east/west arm areas (x=[0,7/16]
        // and x=[9/16,1]) where no physical arm exists.
        boolean hasNS = north || south;
        boolean hasEW = east  || west;

        if (!hasNS && !hasEW) return; // fully isolated pane, nothing to fill

        if (hasNS) {
            // North arm absent on the N/S axis: add EAST + WEST quads for z=[0, PANE_Z_NORTH_JUNCTION]
            if (!north) {
                addSyntheticEWQuad(result, world, pos, EnumFacing.EAST,
                    PANE_X_EAST, 0f, PANE_Z_NORTH_JUNCTION);
                addSyntheticEWQuad(result, world, pos, EnumFacing.WEST,
                    PANE_X_WEST, 0f, PANE_Z_NORTH_JUNCTION);
            }
            // South arm absent on the N/S axis: add EAST + WEST quads for z=[PANE_Z_SOUTH_JUNCTION, 1]
            if (!south) {
                addSyntheticEWQuad(result, world, pos, EnumFacing.EAST,
                    PANE_X_EAST, PANE_Z_SOUTH_JUNCTION, 1f);
                addSyntheticEWQuad(result, world, pos, EnumFacing.WEST,
                    PANE_X_WEST, PANE_Z_SOUTH_JUNCTION, 1f);
            }
        }

        if (hasEW) {
            // East arm absent on the E/W axis: add NORTH + SOUTH quads for x=[PANE_X_EAST, 1]
            if (!east) {
                addSyntheticNSQuad(result, world, pos, EnumFacing.NORTH,
                    PANE_Z_NORTH_JUNCTION, PANE_X_EAST, 1f);
                addSyntheticNSQuad(result, world, pos, EnumFacing.SOUTH,
                    PANE_Z_SOUTH_JUNCTION, PANE_X_EAST, 1f);
            }
            // West arm absent on the E/W axis: add NORTH + SOUTH quads for x=[0, PANE_X_WEST]
            if (!west) {
                addSyntheticNSQuad(result, world, pos, EnumFacing.NORTH,
                    PANE_Z_NORTH_JUNCTION, 0f, PANE_X_WEST);
                addSyntheticNSQuad(result, world, pos, EnumFacing.SOUTH,
                    PANE_Z_SOUTH_JUNCTION, 0f, PANE_X_WEST);
            }
        }
    }

    /**
     * Adds a synthetic EAST or WEST face quad covering x=xPos, z=[zMin, zMax], y=[0,1].
     *
     * UV convention matches the vanilla pane model (verified from pane_side.json):
     *   pane_side EAST face: UV=[9,0,16,16]   → U=(1-z)*16 (U=16 at outer north edge z=0)
     *   pane_side WEST face: UV=[16,0,9,16]    → also U=(1-z)*16 (model mirrors WEST to match EAST)
     * Both faces of a N/S arm intentionally use the same formula so the same CTM tile
     * region appears at the same world position when viewed from either side.
     * V = (1 - y) * 16 for both.
     *
     * Vertex order follows EnumFaceDirection:
     *   EAST: [0]=(xPos,UP,zMax), [1]=(xPos,DOWN,zMax), [2]=(xPos,DOWN,zMin), [3]=(xPos,UP,zMin)
     *   WEST: [0]=(xPos,UP,zMin), [1]=(xPos,DOWN,zMin), [2]=(xPos,DOWN,zMax), [3]=(xPos,UP,zMax)
     */
    private void addSyntheticEWQuad(List<BakedQuad> result, IBlockAccess world, BlockPos pos,
                                     EnumFacing face, float xPos, float zMin, float zMax) {
        if (!properties.appliesToFace(faceName(face))) return;

        boolean mirrorH = (face == EnumFacing.WEST);
        int tileIndex = calculateTileIndex(world, pos, face, mirrorH);
        if (tileIndex < 0 || tileIndex >= tileSprites.size()) return;
        TextureAtlasSprite tile = tileSprites.get(tileIndex);
        if (tile == null || "missingno".equals(tile.getIconName())) return;

        // Both EAST and WEST faces use U=(1-z)*16 to match the pane model's UV convention.
        float uAtZMin = (1f - zMin) * 16f;
        float uAtZMax = (1f - zMax) * 16f;

        // EAST: V0,V1 at zMax (SOUTH_Z), V2,V3 at zMin (NORTH_Z)
        // WEST: V0,V1 at zMin (NORTH_Z), V2,V3 at zMax (SOUTH_Z)
        float u01, u23, z01, z23;
        if (face == EnumFacing.EAST) {
            u01 = uAtZMax; z01 = zMax;
            u23 = uAtZMin; z23 = zMin;
        } else {
            u01 = uAtZMin; z01 = zMin;
            u23 = uAtZMax; z23 = zMax;
        }

        int[] vd = new int[28];
        writeVertex(vd, 0, xPos, 1f, z01, u01, 0f,  tile);
        writeVertex(vd, 1, xPos, 0f, z01, u01, 16f, tile);
        writeVertex(vd, 2, xPos, 0f, z23, u23, 16f, tile);
        writeVertex(vd, 3, xPos, 1f, z23, u23, 0f,  tile);

        result.add(new BakedQuad(vd, -1, face, tile, true, DefaultVertexFormats.BLOCK));
    }

    /**
     * Adds a synthetic NORTH or SOUTH face quad covering z=zPos, x=[xMin, xMax], y=[0,1].
     *
     * UV convention matches the vanilla pane model after Y=90 rotation (verified from pane_side.json
     * rotated into east/west arm positions):
     *   east arm SOUTH face (rotated from pane_side EAST): UV → U=x*16 (U=16 at outer east edge x=1)
     *   east arm NORTH face (rotated from pane_side WEST): UV → also U=x*16 (model mirrors NORTH)
     * Both faces of an E/W arm use the same formula so the same CTM region appears at the same
     * world position from either side.
     * V = (1 - y) * 16 for both.
     *
     * Vertex order follows EnumFaceDirection:
     *   NORTH: [0]=(xMax,UP,zPos), [1]=(xMax,DOWN,zPos), [2]=(xMin,DOWN,zPos), [3]=(xMin,UP,zPos)
     *   SOUTH: [0]=(xMin,UP,zPos), [1]=(xMin,DOWN,zPos), [2]=(xMax,DOWN,zPos), [3]=(xMax,UP,zPos)
     */
    private void addSyntheticNSQuad(List<BakedQuad> result, IBlockAccess world, BlockPos pos,
                                     EnumFacing face, float zPos, float xMin, float xMax) {
        if (!properties.appliesToFace(faceName(face))) return;

        boolean mirrorH = (face == EnumFacing.NORTH);
        int tileIndex = calculateTileIndex(world, pos, face, mirrorH);
        if (tileIndex < 0 || tileIndex >= tileSprites.size()) return;
        TextureAtlasSprite tile = tileSprites.get(tileIndex);
        if (tile == null || "missingno".equals(tile.getIconName())) return;

        // Both NORTH and SOUTH faces use U=x*16 to match the pane model's UV convention.
        float uAtXMin = xMin * 16f;
        float uAtXMax = xMax * 16f;

        // NORTH: V0,V1 at xMax (EAST_X), V2,V3 at xMin (WEST_X)
        // SOUTH: V0,V1 at xMin (WEST_X), V2,V3 at xMax (EAST_X)
        float u01, u23, x01, x23;
        if (face == EnumFacing.NORTH) {
            u01 = uAtXMax; x01 = xMax;
            u23 = uAtXMin; x23 = xMin;
        } else {
            u01 = uAtXMin; x01 = xMin;
            u23 = uAtXMax; x23 = xMax;
        }

        int[] vd = new int[28];
        writeVertex(vd, 0, x01, 1f, zPos, u01, 0f,  tile);
        writeVertex(vd, 1, x01, 0f, zPos, u01, 16f, tile);
        writeVertex(vd, 2, x23, 0f, zPos, u23, 16f, tile);
        writeVertex(vd, 3, x23, 1f, zPos, u23, 0f,  tile);

        result.add(new BakedQuad(vd, -1, face, tile, true, DefaultVertexFormats.BLOCK));
    }

    /** Writes one vertex (7 ints) into the vertex data array at slot idx. */
    private static void writeVertex(int[] vd, int idx, float x, float y, float z,
                                     float u, float v, TextureAtlasSprite sprite) {
        int off = idx * 7;
        vd[off]     = Float.floatToRawIntBits(x);
        vd[off + 1] = Float.floatToRawIntBits(y);
        vd[off + 2] = Float.floatToRawIntBits(z);
        vd[off + 3] = 0xFFFFFFFF; // white; diffuse lighting applied by renderer
        vd[off + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(u));
        vd[off + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(v));
        vd[off + 6] = 0;          // lightmap (overwritten by chunk builder)
    }

    /**
     * Retexture a quad by remapping its UV coordinates from the original sprite
     * to the new sprite. Uses getUnInterpolatedU/V to convert atlas coords back
     * to 0-16 texture space, then getInterpolatedU/V to convert to the new atlas.
     *
     * This preserves the original model's UV layout (which is correct for pane arms
     * as the model JSON places U=[9,16] on the north arm and U=[0,7] on the south arm,
     * so CTM tile borders naturally land at the physical outer edges).
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

            vertexData[offset + 4] = Float.floatToRawIntBits(newSprite.getInterpolatedU(unmappedU));
            vertexData[offset + 5] = Float.floatToRawIntBits(newSprite.getInterpolatedV(unmappedV));
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
