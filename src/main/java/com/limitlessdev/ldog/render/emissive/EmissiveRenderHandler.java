package com.limitlessdev.ldog.render.emissive;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the creation of emissive (fullbright) quads for blocks
 * that have emissive texture overlays.
 *
 * When a block face has a corresponding _e texture, this creates
 * additional quads that render at full brightness (lightmap 240/240)
 * with the emissive texture, producing a glow effect.
 */
public final class EmissiveRenderHandler {

    /** Fullbright lightmap value: sky=15, block=15 packed as (sky<<16)|block */
    public static final int FULLBRIGHT = 240 | (240 << 16);

    private EmissiveRenderHandler() {}

    /**
     * Given a list of quads, check each for an emissive overlay and create
     * additional fullbright quads for those that have one.
     *
     * @param originalQuads The original block face quads
     * @return The original quads plus any emissive overlay quads appended
     */
    public static List<BakedQuad> addEmissiveQuads(List<BakedQuad> originalQuads) {
        if (!LDOGConfig.enableEmissiveTextures || originalQuads.isEmpty()) {
            return originalQuads;
        }

        List<BakedQuad> emissiveQuads = null;

        for (BakedQuad quad : originalQuads) {
            TextureAtlasSprite sprite = quad.getSprite();
            TextureAtlasSprite emissiveSprite = EmissiveTextureRegistry.getEmissiveSprite(sprite);

            if (emissiveSprite != null) {
                if (emissiveQuads == null) {
                    emissiveQuads = new ArrayList<>(originalQuads);
                }
                emissiveQuads.add(createEmissiveQuad(quad, emissiveSprite));
            }
        }

        return emissiveQuads != null ? emissiveQuads : originalQuads;
    }

    /**
     * Create a fullbright copy of a quad with a different texture sprite.
     * The emissive quad renders at max brightness (lightmap 240/240).
     */
    private static BakedQuad createEmissiveQuad(BakedQuad original, TextureAtlasSprite emissiveSprite) {
        // Retexture the quad with the emissive sprite
        int[] vertexData = original.getVertexData().clone();
        TextureAtlasSprite oldSprite = original.getSprite();

        // Remap UV coordinates from old sprite to emissive sprite
        for (int v = 0; v < 4; v++) {
            int offset = v * 7;
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);

            float unmappedU = oldSprite.getUnInterpolatedU(u);
            float unmappedV = oldSprite.getUnInterpolatedV(vCoord);

            vertexData[offset + 4] = Float.floatToRawIntBits(
                emissiveSprite.getInterpolatedU(unmappedU));
            vertexData[offset + 5] = Float.floatToRawIntBits(
                emissiveSprite.getInterpolatedV(unmappedV));
        }

        return new BakedQuad(vertexData, original.getTintIndex(), original.getFace(),
            emissiveSprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }
}
