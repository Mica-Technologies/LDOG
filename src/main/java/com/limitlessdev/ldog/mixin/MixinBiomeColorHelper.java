package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.biome.BiomeBlend;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smooth biomes — extends vanilla's hardcoded 3x3 color smoothing radius
 * to a user-configurable 5x5 or 7x7 via {@link LDOGConfig#biomeBlendRadius}.
 *
 * Three identical injects (one per public color accessor) because vanilla's
 * private getColorAtPos uses a package-private inner interface we can't
 * easily reference from this package without an Access Transformer. The
 * code duplication is mild and each injected method is two lines.
 *
 * Vanilla path runs unchanged when radius == 1.
 */
@Mixin(BiomeColorHelper.class)
public abstract class MixinBiomeColorHelper {

    @Inject(method = "getGrassColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void ldog$smoothGrass(IBlockAccess access, BlockPos pos,
                                          CallbackInfoReturnable<Integer> cir) {
        int r = LDOGConfig.biomeBlendRadius;
        if (r > 1) cir.setReturnValue(BiomeBlend.blend(access, pos, r, BiomeBlend.Channel.GRASS));
    }

    @Inject(method = "getFoliageColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void ldog$smoothFoliage(IBlockAccess access, BlockPos pos,
                                            CallbackInfoReturnable<Integer> cir) {
        int r = LDOGConfig.biomeBlendRadius;
        if (r > 1) cir.setReturnValue(BiomeBlend.blend(access, pos, r, BiomeBlend.Channel.FOLIAGE));
    }

    @Inject(method = "getWaterColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void ldog$smoothWater(IBlockAccess access, BlockPos pos,
                                          CallbackInfoReturnable<Integer> cir) {
        int r = LDOGConfig.biomeBlendRadius;
        if (r > 1) cir.setReturnValue(BiomeBlend.blend(access, pos, r, BiomeBlend.Channel.WATER));
    }
}
