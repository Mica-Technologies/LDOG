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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies water rendering:
 * - Adjustable alpha (clear water / transparency)
 * - Color tinting (RGB multipliers for realistic water colors)
 *
 * Uses a ThreadLocal for the isWater flag because BlockFluidRenderer is a
 * singleton shared across concurrent chunk-build worker threads. Multiple
 * @ModifyArg on the same call-site is unreliable; a single @Redirect handles
 * all four channels atomically.
 */
@Mixin(BlockFluidRenderer.class)
public abstract class MixinBlockFluidRenderer {

    // ThreadLocal because BlockFluidRenderer is a singleton used from multiple
    // chunk-build threads simultaneously.
    private static final ThreadLocal<Boolean> ldog$isWater = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderFluid", at = @At("HEAD"))
    private void ldog$trackFluidType(IBlockAccess blockAccess, IBlockState blockStateIn,
                                      BlockPos blockPosIn, BufferBuilder bufferBuilderIn,
                                      CallbackInfoReturnable<Boolean> cir) {
        ldog$isWater.set(blockStateIn.getMaterial() == Material.WATER);
    }

    /**
     * Intercept every color(float,float,float,float) call inside renderFluid
     * and apply our opacity / tint on top. A single @Redirect replaces the
     * four separate @ModifyArg annotations that were silently failing.
     */
    @Redirect(
        method = "renderFluid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;color(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;"
        )
    )
    private BufferBuilder ldog$modifyWaterColor(BufferBuilder buf,
                                                 float r, float g, float b, float a) {
        if (ldog$isWater.get()) {
            if (LDOGConfig.enableWaterTint) {
                r = Math.min(r * (float) LDOGConfig.waterTintRed,   1.0F);
                g = Math.min(g * (float) LDOGConfig.waterTintGreen, 1.0F);
                b = Math.min(b * (float) LDOGConfig.waterTintBlue,  1.0F);
            }
            if (LDOGConfig.enableClearWater) {
                // Vertex alpha is 0-1; opacity > 1.0 only affects fog density
                a = (float) Math.min(LDOGConfig.waterOpacity, 1.0F);
            }
        }
        return buf.color(r, g, b, a);
    }
}
