package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.ctm.CTMRenderContext;
import com.limitlessdev.ldog.render.emissive.EmissiveRenderHandler;
import com.limitlessdev.ldog.render.emissive.EmissiveTextureRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sets the CTM render context (world + position) before each block renders,
 * and handles emissive texture rendering in the CUTOUT_MIPPED pass.
 */
@Mixin(BlockRendererDispatcher.class)
public abstract class MixinBlockRendererDispatcher {

    /** Track current render layer -- Forge's field is package-private */
    private static BlockRenderLayer ldog$currentLayer;

    public static void ldog$setRenderLayer(BlockRenderLayer layer) {
        ldog$currentLayer = layer;
    }

    public static BlockRenderLayer ldog$getRenderLayer() {
        return ldog$currentLayer;
    }

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void ldog$onRenderBlockHead(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                         BufferBuilder bufferBuilderIn,
                                         CallbackInfoReturnable<Boolean> cir) {
        CTMRenderContext.set(blockAccess, pos);

        // If we're in the CUTOUT_MIPPED pass and this block is only here for emissive rendering,
        // skip the normal block render and only render emissive overlay quads.
        if (LDOGConfig.enableEmissiveTextures
            && EmissiveTextureRegistry.getEmissiveSpriteCount() > 0
            && ldog$currentLayer == BlockRenderLayer.CUTOUT_MIPPED
            && state.getBlock().getRenderLayer() != BlockRenderLayer.CUTOUT_MIPPED
            && EmissiveTextureRegistry.isEmissiveBlock(state.getBlock())) {

            BlockRendererDispatcher self = (BlockRendererDispatcher) (Object) this;
            IBakedModel model = self.getModelForState(state);
            EmissiveRenderHandler.renderEmissiveOverlay(
                model, state, blockAccess, pos, bufferBuilderIn, true, pos.hashCode());
            CTMRenderContext.clear();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void ldog$onRenderBlockReturn(IBlockState state, BlockPos pos, IBlockAccess blockAccess,
                                           BufferBuilder bufferBuilderIn,
                                           CallbackInfoReturnable<Boolean> cir) {
        CTMRenderContext.clear();
    }
}
