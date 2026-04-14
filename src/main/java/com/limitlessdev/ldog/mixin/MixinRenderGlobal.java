package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optimizes entity rendering by adding a configurable render distance check
 * before the vanilla shouldRender call. Entities beyond the configured distance
 * are skipped entirely, saving the cost of shouldRender + the actual render.
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    /**
     * Redirect the shouldRender call in renderEntities to add distance culling.
     * If entityRenderDistance is configured (> 0), skip entities beyond that distance.
     */
    @Redirect(
        method = "renderEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;DDD)Z"
        )
    )
    private boolean ldog$entityDistanceCull(net.minecraft.client.renderer.entity.RenderManager renderManager,
                                            Entity entity, ICamera camera,
                                            double camX, double camY, double camZ) {
        if (LDOGConfig.entityRenderDistance > 0) {
            double dx = entity.posX - camX;
            double dy = entity.posY - camY;
            double dz = entity.posZ - camZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            double maxDist = LDOGConfig.entityRenderDistance;
            if (distSq > maxDist * maxDist) {
                return false;
            }
        }
        return renderManager.shouldRender(entity, camera, camX, camY, camZ);
    }
}
