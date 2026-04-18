package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two weather visual controls — both inside {@code renderRainSnow}, which
 * runs every frame during precipitation:
 *
 * <ul>
 *   <li><b>enableWeatherRender = false</b>: cancel the entire method at
 *       HEAD so no rain/snow particles get generated. Gameplay weather
 *       (wet entities, lightning strikes, mob fire dousing) is unaffected
 *       since those run on the server / world tick path, not in renderer.
 *       Big FPS win on lower-end hardware during storms.</li>
 *   <li><b>weatherDensity</b>: scales the iteration radius (vanilla = 5
 *       fast / 10 fancy) used to find blocks above the player to spawn
 *       drops at. Particle count grows quadratically with radius, so a
 *       0.5x density factor yields ~25% the particles. Capped at 0.1 to
 *       avoid degenerate empty rain.</li>
 * </ul>
 *
 * Both vanilla constants (5 fast, 10 fancy) get the same multiplier so
 * the relative density between fancy and fast modes is preserved.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererWeather {

    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void ldog$skipWeather(float partialTicks, CallbackInfo ci) {
        if (!LDOGConfig.enableWeatherRender) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderRainSnow",
                    constant = @Constant(intValue = 10, ordinal = 0))
    private int ldog$fancyRainRadius(int orig) {
        return Math.max(1, (int) Math.round(orig * LDOGConfig.weatherDensity));
    }

    @ModifyConstant(method = "renderRainSnow",
                    constant = @Constant(intValue = 5, ordinal = 0))
    private int ldog$fastRainRadius(int orig) {
        return Math.max(1, (int) Math.round(orig * LDOGConfig.weatherDensity));
    }
}
