package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.emissive.EmissiveTextureRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows blocks with emissive textures to also render in CUTOUT_MIPPED.
 * The emissive overlay needs alpha testing to discard transparent pixels,
 * which only happens in CUTOUT_MIPPED (not SOLID).
 */
@Mixin(Block.class)
public abstract class MixinBlock {

    @Inject(method = "canRenderInLayer", at = @At("RETURN"), cancellable = true, remap = false)
    private void ldog$allowEmissiveCutoutLayer(IBlockState state, BlockRenderLayer layer,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && layer == BlockRenderLayer.CUTOUT_MIPPED
            && LDOGConfig.enableEmissiveTextures
            && EmissiveTextureRegistry.isEmissiveBlock((Block) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
