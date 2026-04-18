package com.limitlessdev.ldog.render.particles;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.particle.Particle;

/**
 * Per-type particle filter — Phase "future expansion" feature inspired by
 * OptiFine's per-particle toggles. Categorises a particle by its class name
 * and returns whether the user has its category enabled.
 *
 * <h3>Why class-name matching, not instanceof</h3>
 *
 * Hard {@code import net.minecraft.client.particle.ParticleFirework}
 * statements would couple LDOG to specific Forge MC class layouts. Class-name
 * suffix matching is robust to renames inside the {@code net.minecraft.client.particle}
 * package and trivially extends to new categories — we just add a string
 * pattern. Per-particle filtering runs in the spawn path (a few hundred calls
 * per second worst-case), so the small overhead of {@code Class.getSimpleName()}
 * is negligible compared to actually constructing + ticking the particle.
 *
 * <h3>Categories</h3>
 *
 * Mapped against vanilla MC 1.12.2 particle class names:
 *
 * <ul>
 *   <li><b>Firework</b>: ParticleFirework$Spark / $Starter / $Overlay</li>
 *   <li><b>Portal</b>: ParticlePortal, ParticleEnchantmentTable (visually similar)</li>
 *   <li><b>Potion</b>: ParticleSpell + inner classes (AmbientMobSpell, MobSpell,
 *       InstantSpell, WitchMagic) — beacon, splash potions, witch magic</li>
 *   <li><b>Water</b>: ParticleSplash, ParticleBubble, ParticleSuspend — visible
 *       water droplets + underwater bubbles + ambient suspends</li>
 *   <li><b>Dripping</b>: ParticleDrip — water/lava drip from blocks above</li>
 * </ul>
 *
 * Particles outside these categories (smoke, flame, redstone, explosions,
 * critical hit, dust, etc.) are not filtered — they go through unchanged.
 */
public final class ParticleTypeFilter {

    private ParticleTypeFilter() {}

    /**
     * Returns false when the particle's category is disabled in config.
     * True for any category we don't recognise — never blocks unknown
     * particles, since the alternative (block by default) would silently
     * delete modded particles.
     */
    public static boolean shouldRender(Particle particle) {
        if (particle == null) return true;

        // Cheap class-name suffix match. getSimpleName() returns the last
        // segment for inner classes too (e.g. ParticleFirework$Spark →
        // "Spark"), so we check getName() suffix instead for accuracy.
        String name = particle.getClass().getName();

        // Strip the package prefix and inner-class containing-class for the
        // category check. Using contains() rather than endsWith() so inner
        // classes like ParticleFirework$Spark still match the "ParticleFirework"
        // family check.
        if (name.contains("ParticleFirework"))         return LDOGConfig.enableParticleFirework;
        if (name.contains("ParticlePortal")
            || name.contains("ParticleEnchantmentTable")) return LDOGConfig.enableParticlePortal;
        if (name.contains("ParticleSpell"))            return LDOGConfig.enableParticlePotion;
        if (name.contains("ParticleSplash")
            || name.contains("ParticleBubble")
            || name.contains("ParticleSuspend"))       return LDOGConfig.enableParticleWater;
        if (name.contains("ParticleDrip"))             return LDOGConfig.enableParticleDripping;

        return true;
    }
}
