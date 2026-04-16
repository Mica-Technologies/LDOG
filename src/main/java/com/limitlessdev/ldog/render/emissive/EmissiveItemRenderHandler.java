package com.limitlessdev.ldog.render.emissive;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

/**
 * Renders emissive (fullbright) overlay quads for items in inventory and hand.
 *
 * Unlike block emissive rendering (which writes per-vertex lightmap into the
 * chunk buffer), item rendering uses the ITEM vertex format which has no
 * per-vertex lightmap. Fullbright is achieved by setting the global lightmap
 * texture coordinates to (240, 240) via OpenGlHelper before the draw call.
 *
 * Polygon offset is used to prevent z-fighting between the base item quads
 * and the emissive overlay quads.
 */
public final class EmissiveItemRenderHandler {

    private EmissiveItemRenderHandler() {}

    /**
     * Render emissive overlay quads for an item model.
     * Called after the base item model has been tessellated and drawn.
     *
     * Uses lazy initialization: GL state is only modified if the model
     * actually contains quads with emissive textures, so non-emissive
     * items pay only the cost of a few HashMap lookups.
     */
    public static void renderEmissiveOverlay(IBakedModel model) {
        boolean started = false;
        float savedBrightnessX = 0f;
        float savedBrightnessY = 0f;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Iterate all 6 directional faces + null (general quads) in a single pass
        for (int faceIdx = 0; faceIdx < 7; faceIdx++) {
            EnumFacing face = faceIdx < 6 ? EnumFacing.values()[faceIdx] : null;

            for (BakedQuad quad : model.getQuads(null, face, 0L)) {
                TextureAtlasSprite emissive = EmissiveTextureRegistry.getEmissiveSprite(quad.getSprite());
                if (emissive != null) {
                    if (!started) {
                        started = true;
                        savedBrightnessX = OpenGlHelper.lastBrightnessX;
                        savedBrightnessY = OpenGlHelper.lastBrightnessY;

                        // Fullbright: sky=15, block=15 (both * 16 = 240)
                        OpenGlHelper.setLightmapTextureCoords(
                            OpenGlHelper.lightmapTexUnit, 240f, 240f);

                        GlStateManager.enablePolygonOffset();
                        GlStateManager.doPolygonOffset(-1.0f, -1.0f);

                        buffer.begin(7, DefaultVertexFormats.ITEM);
                    }
                    addRetexturedQuad(buffer, quad, emissive);
                }
            }
        }

        if (started) {
            tessellator.draw();

            GlStateManager.doPolygonOffset(0.0f, 0.0f);
            GlStateManager.disablePolygonOffset();
            OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit, savedBrightnessX, savedBrightnessY);
        }
    }

    /**
     * Write a single quad retextured with the emissive sprite into the ITEM buffer.
     * Color is always white (no tinting) so the emissive texture renders at its
     * native colors. Normal is derived from the quad's face direction.
     *
     * Vertex format: ITEM = position(3f) + color(4ub) + tex(2f) + normal(3b+pad)
     * BakedQuad vertex data is always BLOCK format: 7 ints per vertex
     *   [0]=x, [1]=y, [2]=z, [3]=color, [4]=u, [5]=v, [6]=lightmap
     */
    private static void addRetexturedQuad(BufferBuilder buffer, BakedQuad original,
                                           TextureAtlasSprite emissiveSprite) {
        int[] vertexData = original.getVertexData();
        TextureAtlasSprite oldSprite = original.getSprite();
        EnumFacing face = original.getFace();

        for (int v = 0; v < 4; v++) {
            int offset = v * 7;

            float x = Float.intBitsToFloat(vertexData[offset]);
            float y = Float.intBitsToFloat(vertexData[offset + 1]);
            float z = Float.intBitsToFloat(vertexData[offset + 2]);

            // Remap UV from original sprite's atlas region to emissive sprite's
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);
            float unmappedU = oldSprite.getUnInterpolatedU(u);
            float unmappedV = oldSprite.getUnInterpolatedV(vCoord);
            float newU = emissiveSprite.getInterpolatedU(unmappedU);
            float newV = emissiveSprite.getInterpolatedV(unmappedV);

            buffer.pos(x, y, z)
                  .color(255, 255, 255, 255)
                  .tex(newU, newV)
                  .normal(face.getXOffset(), face.getYOffset(), face.getZOffset())
                  .endVertex();
        }
    }
}
