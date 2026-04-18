package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.render.particles.ParticleTypeFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase "future expansion" — per-type particle filtering inspired by OF.
 *
 * Cancels particle add at HEAD when {@link ParticleTypeFilter} rejects the
 * particle's category. Lighter than vanilla's spawn path (we kill the
 * particle before it joins any layer list, so it costs zero per-frame
 * tick + render work).
 *
 * Separate mixin from {@link MixinParticleManager} (which adds frustum
 * culling at render time) because the concerns are independent — culling
 * runs every frame on alive particles; filtering runs once per spawn.
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleManagerFilter {

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void ldog$filterByType(Particle particle, CallbackInfo ci) {
        if (!ParticleTypeFilter.shouldRender(particle)) {
            ci.cancel();
        }
    }
}
