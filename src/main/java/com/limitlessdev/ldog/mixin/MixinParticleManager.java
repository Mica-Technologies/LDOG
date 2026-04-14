package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.LDOGStats;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adds frustum culling for particles. Vanilla renders every particle in the
 * particle layer lists regardless of whether they're on screen. With many
 * particles (explosions, redstone, mob farms), this wastes significant GPU time.
 *
 * This mixin checks if a particle's position is roughly within the player's
 * view before submitting it to the buffer. Uses a simplified distance + dot
 * product check rather than full frustum planes for speed.
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleManager {

    /**
     * Redirect the particle.renderParticle call to add a frustum check.
     */
    @Redirect(
        method = "renderParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;renderParticle(Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"
        )
    )
    private void ldog$cullParticleRender(Particle particle, BufferBuilder buffer,
                                          Entity entityIn, float partialTicks,
                                          float rotationX, float rotationZ,
                                          float rotationYZ, float rotationXY,
                                          float rotationXZ) {
        if (LDOGConfig.enableParticleCulling) {
            // Quick distance-based frustum cull: compute relative position
            AxisAlignedBB bb = particle.getBoundingBox();
            double particleX = (bb.minX + bb.maxX) * 0.5;
            double particleY = (bb.minY + bb.maxY) * 0.5;
            double particleZ = (bb.minZ + bb.maxZ) * 0.5;
            double dx = particleX - Particle.interpPosX;
            double dy = particleY - Particle.interpPosY;
            double dz = particleZ - Particle.interpPosZ;
            double distSq = dx * dx + dy * dy + dz * dz;

            // Always render very close particles (within 4 blocks)
            if (distSq > 16.0) {
                // For distant particles, check if they're roughly in front of the camera
                // using dot product with camera view direction
                double dirX = Particle.cameraViewDir.x;
                double dirY = Particle.cameraViewDir.y;
                double dirZ = Particle.cameraViewDir.z;
                double dot = dx * dirX + dy * dirY + dz * dirZ;

                // Skip particles that are behind the camera (negative dot product)
                // Use a generous threshold to avoid popping at screen edges
                if (dot < -2.0) {
                    LDOGStats.particlesCulled++;
                    return;
                }
            }
        }

        LDOGStats.particlesRendered++;
        particle.renderParticle(buffer, entityIn, partialTicks,
            rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }
}
