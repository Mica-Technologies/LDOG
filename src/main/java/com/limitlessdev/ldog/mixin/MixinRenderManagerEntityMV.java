package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.EntityRenderStateCache;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 9c.3-C: capture every entity vanilla draws so the MV pass can
 * stamp screen-space velocity into the motion-vector target.
 *
 * <p>Hooking {@code RenderManager.renderEntity} (rather than the per-entity
 * {@code Render<T>.doRender} subclasses) gets us universal coverage in one
 * place: every entity vanilla dispatches goes through this entrypoint —
 * mobs, items, XP orbs, projectiles, paintings, item frames. Modded
 * entities use the same dispatcher, so they're covered too.
 *
 * <p>What we capture:
 *
 * <ul>
 *   <li>The entity itself (for UUID-keyed lookup in the cache).</li>
 *   <li>This frame's interpolated world position
 *       ({@code prevPosX + (posX - prevPosX) * partialTicks}). This is
 *       where the entity APPEARS this frame.</li>
 *   <li>The entity's bounding box, for screen-space coverage in the MV
 *       stamp pass.</li>
 * </ul>
 *
 * <p>The cache rotates last-frame → previous and stores this-frame, so the
 * MV pass can compute the world-space delta between consecutive frames
 * and project it through the current view matrix to get screen-space
 * velocity.
 */
@Mixin(RenderManager.class)
public abstract class MixinRenderManagerEntityMV {

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void ldog$captureEntity(Entity entity, double x, double y, double z,
                                     float yaw, float partialTicks, boolean p7,
                                     CallbackInfo ci) {
        if (!LDOGConfig.enableEntityMotionVectors) return;
        if (!LDOGConfig.enablePostProcessPipeline) return;
        if (entity == null) return;

        // World-space interpolated position. The (x,y,z) parameter is camera-
        // relative; we want the world coord so MV projection through the
        // captured view matrix produces correct screen-space velocity.
        double ix = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
        double iy = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks;
        double iz = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;

        EntityRenderStateCache.get().captureForRender(entity, ix, iy, iz,
            entity.getEntityBoundingBox());
    }
}
