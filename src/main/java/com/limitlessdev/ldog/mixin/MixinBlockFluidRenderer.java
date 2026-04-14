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
 * Modifies water rendering:
 * - Adjustable alpha (clear water / transparency)
 * - Color tinting (RGB multipliers for realistic water colors)
 *
 * Only affects water, not lava. All modifications are gated by config.
 */
@Mixin(BlockFluidRenderer.class)
public abstract class MixinBlockFluidRenderer {

    private boolean ldog$isWater = false;

    @Inject(method = "renderFluid", at = @At("HEAD"))
    private void ldog$trackFluidType(IBlockAccess blockAccess, IBlockState blockStateIn,
                                      BlockPos blockPosIn, BufferBuilder bufferBuilderIn,
                                      CallbackInfoReturnable<Boolean> cir) {
        ldog$isWater = blockStateIn.getMaterial() == Material.WATER;
    }

    @ModifyArg(
        method = "renderFluid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;color(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;"
        ),
        index = 0
    )
    private float ldog$modifyWaterRed(float red) {
        if (ldog$isWater && LDOGConfig.enableWaterTint) {
            return Math.min(red * (float) LDOGConfig.waterTintRed, 1.0F);
        }
        return red;
    }

    @ModifyArg(
        method = "renderFluid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;color(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;"
        ),
        index = 1
    )
    private float ldog$modifyWaterGreen(float green) {
        if (ldog$isWater && LDOGConfig.enableWaterTint) {
            return Math.min(green * (float) LDOGConfig.waterTintGreen, 1.0F);
        }
        return green;
    }

    @ModifyArg(
        method = "renderFluid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;color(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;"
        ),
        index = 2
    )
    private float ldog$modifyWaterBlue(float blue) {
        if (ldog$isWater && LDOGConfig.enableWaterTint) {
            return Math.min(blue * (float) LDOGConfig.waterTintBlue, 1.0F);
        }
        return blue;
    }

    @ModifyArg(
        method = "renderFluid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;color(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;"
        ),
        index = 3
    )
    private float ldog$modifyWaterAlpha(float alpha) {
        if (ldog$isWater && LDOGConfig.enableClearWater) {
            return (float) LDOGConfig.waterOpacity;
        }
        return alpha;
    }
}
