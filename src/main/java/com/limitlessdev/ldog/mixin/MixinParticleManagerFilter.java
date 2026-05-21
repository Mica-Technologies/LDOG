package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.particles.ParticleTypeFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
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
 * resets every client tick via the inner event handler.
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
        if (cap > 0 && SpawnCounter.spawnedThisTick >= cap) {
            SpawnCounter.dropped++;
            ci.cancel();
        } else {
            SpawnCounter.spawnedThisTick++;
        }
    }

    /**
     * Per-tick counter for particle spawn rate limiting. Static — no
     * per-instance state since there's only one ParticleManager in the
     * client's lifetime. Reset at the start of each client tick.
     */
    @Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
    public static final class SpawnCounter {
        public static int spawnedThisTick;
        public static int dropped;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) spawnedThisTick = 0;
        }
    }
}
