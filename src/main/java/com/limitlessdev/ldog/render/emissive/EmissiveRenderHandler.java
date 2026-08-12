package com.limitlessdev.ldog.render.emissive;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.List;

/**
 * Renders emissive (fullbright) overlay quads for blocks that have
 * emissive texture overlays (*_e.png files).
 *
 * Emissive quads are rendered into the CUTOUT_MIPPED buffer (obtained
 * from the chunk rebuild ThreadLocal) so alpha testing discards
 * transparent pixels. Quads are written with fullbright lightmap
 * (sky=15, block=15), bypassing vanilla AO/smooth lighting.
 */
public final class EmissiveRenderHandler {

    /** Position offset along face normal to prevent z-fighting */
    private static final float Z_OFFSET = 0.001f;

    private EmissiveRenderHandler() {}

    /**
     * Render emissive overlay quads for a block model into the CUTOUT_MIPPED buffer.
     * The buffer is obtained from the chunk rebuild's ThreadLocal storage.
     */
    public static void renderEmissiveOverlay(IBakedModel model, IBlockState state,
                                              IBlockAccess world, BlockPos pos,
                                              boolean checkSides, long rand) {
        RegionRenderCacheBuilder cacheBuilder = EmissiveRenderLayer.getCacheBuilder();
        CompiledChunk compiledChunk = EmissiveRenderLayer.getCompiledChunk();
        if (cacheBuilder == null || compiledChunk == null) return;

        // Blocks that declare more than one render layer are dispatched through
        // BlockModelRenderer once per layer; the overlay is layer-independent, so
        // emit it only for the first visit to this position in this rebuild.
        if (!EmissiveRenderLayer.claimOverlayPos(pos)) return;

        BufferBuilder buffer = cacheBuilder.getWorldRendererByLayer(BlockRenderLayer.CUTOUT_MIPPED);
        boolean bufferReady = compiledChunk.isLayerStarted(BlockRenderLayer.CUTOUT_MIPPED);

        // Sided quads
        for (EnumFacing face : EnumFacing.values()) {
            if (checkSides && !state.shouldSideBeRendered(world, pos, face)) continue;

            List<BakedQuad> quads = model.getQuads(state, face, rand);
            for (BakedQuad quad : quads) {
                TextureAtlasSprite emissive = EmissiveTextureRegistry.getEmissiveSprite(quad.getSprite());
                if (emissive != null) {
                    bufferReady = startBuffer(buffer, compiledChunk, pos, bufferReady);
                    addFullbrightQuad(buffer, quad, emissive, pos, face);
                }
            }
        }

        // General quads (null face)
        List<BakedQuad> generalQuads = model.getQuads(state, null, rand);
        for (BakedQuad quad : generalQuads) {
            TextureAtlasSprite emissive = EmissiveTextureRegistry.getEmissiveSprite(quad.getSprite());
            if (emissive != null) {
                bufferReady = startBuffer(buffer, compiledChunk, pos, bufferReady);
                addFullbrightQuad(buffer, quad, emissive, pos, quad.getFace());
            }
        }
    }

    /**
     * Starts the CUTOUT_MIPPED layer buffer on first use, and always flags that
     * emissive geometry was written this rebuild.
     *
     * <p>Begun lazily (on the first emissive quad rather than up front) so a block
     * flagged emissive whose current state happens to have no emissive quads does
     * not open — and thereby force the render of — an empty layer.
     */
    private static boolean startBuffer(BufferBuilder buffer, CompiledChunk compiledChunk,
                                        BlockPos pos, boolean alreadyStarted) {
        EmissiveRenderLayer.markEmissiveQuadsWritten();
        if (alreadyStarted) return true;

        // Translation must match RenderChunk.preRenderBlocks: negative of chunk base position
        compiledChunk.setLayerStarted(BlockRenderLayer.CUTOUT_MIPPED);
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(
            -(double)(pos.getX() & ~15),
            -(double)(pos.getY() & ~15),
            -(double)(pos.getZ() & ~15));
        return true;
    }

    /**
     * Add a single fullbright quad to the buffer with the emissive texture.
     */
    private static void addFullbrightQuad(BufferBuilder buffer, BakedQuad original,
                                           TextureAtlasSprite emissiveSprite,
                                           BlockPos pos, EnumFacing face) {
        int[] vertexData = original.getVertexData();
        TextureAtlasSprite oldSprite = original.getSprite();

        float nx = face.getXOffset() * Z_OFFSET;
        float ny = face.getYOffset() * Z_OFFSET;
        float nz = face.getZOffset() * Z_OFFSET;

        for (int v = 0; v < 4; v++) {
            int offset = v * 7;

            float x = Float.intBitsToFloat(vertexData[offset]) + pos.getX() + nx;
            float y = Float.intBitsToFloat(vertexData[offset + 1]) + pos.getY() + ny;
            float z = Float.intBitsToFloat(vertexData[offset + 2]) + pos.getZ() + nz;

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
