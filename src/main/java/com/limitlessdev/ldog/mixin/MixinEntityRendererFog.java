package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fog distance multiplier. Scales every {@code GlStateManager.setFogStart}
 * and {@code GlStateManager.setFogEnd} call inside {@code setupFog} by the
 * user's multiplier — keeps the relative shape of the fog gradient (start
 * to end ratio) intact while pushing the whole curve nearer or farther.
 *
 * Vanilla setupFog computes a base distance (the "f" local) from render
 * distance + dimension settings, then calls setFogStart(f * 0.75F) and
 * setFogEnd(f). Multiplying both by the same factor preserves their 3:4
 * ratio for the normal fog mode and 0:1 for the sky fog mode.
 *
 * @Redirect on GlStateManager.setFogStart/setFogEnd is safer than @ModifyArg
 * on the local f computation because Forge mods (e.g. Better Foliage)
 * sometimes mixin into setupFog themselves; redirecting at the GL boundary
 * means we layer correctly with whatever else has run.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererFog {

    @Redirect(
        method = "setupFog",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;setFogStart(F)V"))
    private void ldog$scaleFogStart(float v) {
        GlStateManager.setFogStart(v * (float) LDOGConfig.fogDistanceMultiplier);
    }

    @Redirect(
        method = "setupFog",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;setFogEnd(F)V"))
    private void ldog$scaleFogEnd(float v) {
        GlStateManager.setFogEnd(v * (float) LDOGConfig.fogDistanceMultiplier);
    }
}
