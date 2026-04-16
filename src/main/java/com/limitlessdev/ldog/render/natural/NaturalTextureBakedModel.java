package com.limitlessdev.ldog.render.natural;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.ctm.CTMRenderContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.BakedModelWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a baked block model to apply random rotation and/or flip to
 * texture UVs based on block position, breaking visual repetition.
 *
 * Uses a position-based hash (including face direction) so each face
 * of each block gets a deterministic but varied rotation. The same
 * block always looks the same — only different positions vary.
 *
 * UV transforms are applied in the sprite's uninterpolated 0-16 space:
 *   90° CW:  (u, v) → (16-v, u)
 *   180°:    (u, v) → (16-u, 16-v)
 *   270° CW: (u, v) → (v, 16-u)
 *   H-flip:  (u, v) → (16-u, v)
 *
 * Reuses CTMRenderContext for world position access.
 */
public class NaturalTextureBakedModel extends BakedModelWrapper<IBakedModel> {

    private final NaturalTextureMode mode;

    public NaturalTextureBakedModel(IBakedModel original, NaturalTextureMode mode) {
        super(original);
        this.mode = mode;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side,
                                     long rand) {
        List<BakedQuad> original = super.getQuads(state, side, rand);

        if (!LDOGConfig.enableNaturalTextures || state == null || side == null) {
            return original;
        }

        // Only apply to horizontal and vertical faces with cullface (full block faces)
        BlockPos pos = CTMRenderContext.getPos();
        if (pos == null) return original;

        int hash = positionHash(pos, side);
        int rotation = hash & 3;          // 0-3 for 0/90/180/270
        boolean flip = (hash & 4) != 0;   // bit 2 for flip

        // Determine actual transform based on mode
        switch (mode) {
            case ROTATE:
                if (rotation == 0) return original;
                flip = false;
                break;
            case ROTATE_FLIP:
                if (rotation == 0 && !flip) return original;
                break;
            case FLIP:
                rotation = 0;
                if (!flip) return original;
                break;
            case FIXED:
                return original;
        }

        final int rot = rotation;
        final boolean fl = flip;

        List<BakedQuad> result = new ArrayList<>(original.size());
        for (BakedQuad quad : original) {
            result.add(transformQuadUV(quad, rot, fl));
        }
        return result;
    }

    /**
     * Position-based hash that includes face direction for per-face variation.
     * Deterministic: same position + face always produces the same result.
     */
    private static int positionHash(BlockPos pos, EnumFacing face) {
        int hash = (pos.getX() * 3129871) ^ (pos.getZ() * 116129781) ^ (pos.getY() * 7919);
        hash = hash * hash * 42317861 + hash * 11;
        hash += face.ordinal() * 1013904223;
        return (hash >> 16) & 0x7;  // 3 bits: 0-7 (rotation 0-3 + flip bit)
    }

    /**
     * Apply rotation and/or flip to a quad's UV coordinates.
     * Works in the sprite's 0-16 uninterpolated space.
     */
    private static BakedQuad transformQuadUV(BakedQuad original, int rotation, boolean flip) {
        TextureAtlasSprite sprite = original.getSprite();
        int[] vertexData = original.getVertexData().clone();

        for (int v = 0; v < 4; v++) {
            int offset = v * 7;
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);

            // Unmap from atlas to 0-16 space
            float su = sprite.getUnInterpolatedU(u);
            float sv = sprite.getUnInterpolatedV(vCoord);

            // Apply flip first (before rotation)
            if (flip) {
                su = 16.0f - su;
            }

            // Apply rotation
            float tu, tv;
            switch (rotation) {
                case 1: // 90° CW
                    tu = 16.0f - sv;
                    tv = su;
                    break;
                case 2: // 180°
                    tu = 16.0f - su;
                    tv = 16.0f - sv;
                    break;
                case 3: // 270° CW
                    tu = sv;
                    tv = 16.0f - su;
                    break;
                default: // 0° (no rotation)
                    tu = su;
                    tv = sv;
                    break;
            }

            // Remap back to atlas space
            vertexData[offset + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(tu));
            vertexData[offset + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(tv));
        }

        return new BakedQuad(vertexData, original.getTintIndex(), original.getFace(),
            sprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }
}
