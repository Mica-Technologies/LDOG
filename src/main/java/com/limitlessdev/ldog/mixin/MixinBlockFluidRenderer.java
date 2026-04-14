package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes water surface semi-transparent by modifying the alpha value
 * passed to BufferBuilder.color() during water quad rendering.
 *
 * Vanilla hardcodes alpha to 1.0F for all fluid quads. We intercept
 * the color() calls and reduce alpha for water (not lava).
 */
@Mixin(BlockFluidRenderer.class)
public abstract class MixinBlockFluidRenderer {

    // Track whether the current renderFluid call is for water (not lava)
    private boolean ldog$isWater = false;

    @Inject(method = "renderFluid", at = @At("HEAD"))
    private void ldog$trackFluidType(IBlockAccess blockAccess, IBlockState blockStateIn,
                                      BlockPos blockPosIn, BufferBuilder bufferBuilderIn,
                                      CallbackInfoReturnable<Boolean> cir) {
        ldog$isWater = blockStateIn.getMaterial() == Material.WATER;
    }

    /**
     * Modify the alpha argument (4th float, index 3) of every
     * bufferBuilder.color(float, float, float, float) call in renderFluid.
     * Water quads get reduced alpha; lava stays at 1.0.
     */
    @ModifyArg(
        method = "renderFluid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;color(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;"
        ),
        index = 3
    )
    private float ldog$modifyWaterAlpha(float alpha) {
        if (LDOGConfig.enableClearWater && ldog$isWater) {
            return (float) LDOGConfig.waterOpacity;
        }
        return alpha;
    }
}
