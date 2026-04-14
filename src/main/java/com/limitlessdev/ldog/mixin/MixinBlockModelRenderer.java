package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.emissive.EmissiveRenderHandler;
import com.limitlessdev.ldog.render.emissive.EmissiveTextureRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Hooks into block model rendering to add emissive texture overlays.
 * Intercepts the getQuads() call and appends emissive quads when
 * a texture has a corresponding _e suffix emissive overlay.
 */
@Mixin(BlockModelRenderer.class)
public abstract class MixinBlockModelRenderer {

    /**
     * Intercept getQuads calls in renderModelSmooth to add emissive quads.
     * The emissive quads will be rendered with the same AO/lighting as normal
     * quads, but the EmissiveRenderHandler creates them with fullbright data
     * that shines through.
     */
    @Redirect(
        method = "renderModelSmooth",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/model/IBakedModel;getQuads(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;"
        )
    )
    private List<BakedQuad> ldog$addEmissiveQuadsSmooth(IBakedModel model, IBlockState state,
                                                         EnumFacing side, long rand) {
        List<BakedQuad> quads = model.getQuads(state, side, rand);
        if (LDOGConfig.enableEmissiveTextures && EmissiveTextureRegistry.getEmissiveSpriteCount() > 0) {
            return EmissiveRenderHandler.addEmissiveQuads(quads);
        }
        return quads;
    }

    /**
     * Same for flat (non-AO) rendering.
     */
    @Redirect(
        method = "renderModelFlat",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/model/IBakedModel;getQuads(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/EnumFacing;J)Ljava/util/List;"
        )
    )
    private List<BakedQuad> ldog$addEmissiveQuadsFlat(IBakedModel model, IBlockState state,
                                                       EnumFacing side, long rand) {
        List<BakedQuad> quads = model.getQuads(state, side, rand);
        if (LDOGConfig.enableEmissiveTextures && EmissiveTextureRegistry.getEmissiveSpriteCount() > 0) {
            return EmissiveRenderHandler.addEmissiveQuads(quads);
        }
        return quads;
    }
}
