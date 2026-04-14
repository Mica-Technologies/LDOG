package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a configurable render distance for tile entity special renderers.
 * Vanilla uses TileEntity.getMaxRenderDistanceSquared() (default 4096 = 64 blocks).
 * This mixin overrides the distance check with our configurable value when it's
 * lower than vanilla's default, saving GPU work on distant TESRs.
 */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcher {

    @Shadow
    public static double staticPlayerX;
    @Shadow
    public static double staticPlayerY;
    @Shadow
    public static double staticPlayerZ;

    /**
     * Inject at the head of the 3-arg render method (the one called from the main render loop)
     * to add our configurable distance check before vanilla's own check.
     */
    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V",
            at = @At("HEAD"), cancellable = true)
    private void ldog$tileEntityDistanceCull(TileEntity te, float partialTicks, int destroyStage,
                                             CallbackInfo ci) {
        if (LDOGConfig.tileEntityRenderDistance > 0) {
            double maxDist = LDOGConfig.tileEntityRenderDistance;
            double distSq = te.getDistanceSq(staticPlayerX, staticPlayerY, staticPlayerZ);
            if (distSq > maxDist * maxDist) {
                ci.cancel();
            }
        }
    }
}
