package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.emissive.EmissiveRenderHandler;
import com.limitlessdev.ldog.render.emissive.EmissiveTextureRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into block model rendering to add emissive texture overlays.
 *
 * Injects AFTER the normal rendering pass completes, then renders
 * emissive quads directly to the buffer with fullbright lightmap.
 * This avoids the vanilla AO/lighting pass overwriting our lightmap values.
 */
@Mixin(BlockModelRenderer.class)
public abstract class MixinBlockModelRenderer {

    @Inject(method = "renderModelSmooth", at = @At("RETURN"))
    private void ldog$renderEmissiveSmooth(IBlockAccess worldIn, IBakedModel modelIn,
                                            IBlockState stateIn, BlockPos posIn,
                                            BufferBuilder buffer, boolean checkSides,
                                            long rand, CallbackInfoReturnable<Boolean> cir) {
        if (LDOGConfig.enableEmissiveTextures && EmissiveTextureRegistry.getEmissiveSpriteCount() > 0) {
            EmissiveRenderHandler.renderEmissiveOverlay(
                modelIn, stateIn, worldIn, posIn, buffer, checkSides, rand);
        }
    }

    @Inject(method = "renderModelFlat", at = @At("RETURN"))
    private void ldog$renderEmissiveFlat(IBlockAccess worldIn, IBakedModel modelIn,
                                          IBlockState stateIn, BlockPos posIn,
                                          BufferBuilder buffer, boolean checkSides,
                                          long rand, CallbackInfoReturnable<Boolean> cir) {
        if (LDOGConfig.enableEmissiveTextures && EmissiveTextureRegistry.getEmissiveSpriteCount() > 0) {
            EmissiveRenderHandler.renderEmissiveOverlay(
                modelIn, stateIn, worldIn, posIn, buffer, checkSides, rand);
        }
    }
}
