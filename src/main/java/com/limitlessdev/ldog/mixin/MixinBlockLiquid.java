package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes water transparent by reducing its fog density.
 * Replaces the standalone Clear Water mod.
 */
@Mixin(BlockLiquid.class)
public abstract class MixinBlockLiquid {

    /**
     * Override the "packed lightmap coords" to allow more light through water,
     * making it appear clearer. This affects how the water surface renders.
     */
    @Inject(method = "getPackedLightmapCoords", at = @At("HEAD"), cancellable = true)
    private void ldog$clearWaterLight(IBlockState state, IBlockAccess source, BlockPos pos,
                                      CallbackInfoReturnable<Integer> cir) {
        if (LDOGConfig.enableClearWater) {
            // Use max brightness for water blocks to remove the dark murky overlay
            // 240 = max light in both sky and block components (15 << 4 | 15 << 20)
            cir.setReturnValue(240 | (240 << 16));
        }
    }
}
