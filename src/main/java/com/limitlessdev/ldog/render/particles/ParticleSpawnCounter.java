package com.limitlessdev.ldog.render.particles;

import com.limitlessdev.ldog.Tags;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Per-tick counter for particle spawn rate limiting (Phase 1 backlog C2).
 *
 * <p>Static state — only one {@code ParticleManager} exists for the client's
 * lifetime, so a single counter suffices. Reset at the START phase of each
 * client tick so a fresh tick gets the full budget.
 *
 * <p>Lives in this package (not {@code .mixin}) because it's a regular event
 * subscriber that gets autoloaded via {@code @Mod.EventBusSubscriber}.
 * Classes inside the mixin-owned package cannot be referenced directly by
 * the runtime (mixin booter throws {@code IllegalClassLoadError} — this
 * bit us when the counter originally lived as a nested class on
 * {@link com.limitlessdev.ldog.mixin.MixinParticleManagerFilter}).
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class ParticleSpawnCounter {

    public static int spawnedThisTick;
    public static int dropped;

    private ParticleSpawnCounter() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) spawnedThisTick = 0;
    }
}
