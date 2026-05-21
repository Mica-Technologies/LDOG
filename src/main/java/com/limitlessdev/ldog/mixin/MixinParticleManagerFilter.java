package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.particles.ParticleSpawnCounter;
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
 * Phase 1 backlog C2: also enforces a soft per-tick spawn limit when
 * {@link LDOGConfig#particleSpawnsPerTickLimit} is non-zero. The counter
 * lives at {@link ParticleSpawnCounter} (outside the mixin package — mixin
 * booter refuses to load regular classes from inside its owned package).
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleManagerFilter {

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void ldog$filterByType(Particle particle, CallbackInfo ci) {
        if (!ParticleTypeFilter.shouldRender(particle)) {
            ci.cancel();
            return;
        }
        int cap = LDOGConfig.particleSpawnsPerTickLimit;
        if (cap > 0 && ParticleSpawnCounter.spawnedThisTick >= cap) {
            ParticleSpawnCounter.dropped++;
            ci.cancel();
        } else {
            ParticleSpawnCounter.spawnedThisTick++;
        }
    }
}
