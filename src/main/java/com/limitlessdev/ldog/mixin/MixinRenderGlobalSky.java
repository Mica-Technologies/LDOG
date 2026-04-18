package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Scales the sun + moon visual size in the sky.
 *
 * Vanilla {@code RenderGlobal.renderSky} uses two literal floats to set the
 * half-extent of the sun (30.0F) and moon (20.0F) quads. ModifyConstant on
 * those literals lets us scale them by the user's multiplier without
 * touching surrounding code.
 *
 * The two constants happen to be unique within renderSky (no other 30.0F
 * or 20.0F float literals appear there in MC 1.12.2), so ordinal isn't
 * needed. If a future MC version introduces collisions we'll add ordinal
 * pinning to keep the right constant matched.
 *
 * Note: the textures themselves don't scale — the quad gets larger and
 * stretches the sun.png / moon_phases.png across more screen real estate.
 * That's how OF's sun/moon scale works too. Image quality at 4x scale on
 * the default 32x sun texture is mediocre; users who want a bigger HD sun
 * should also install a HD sky resource pack.
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobalSky {

    @ModifyConstant(method = "renderSky(FI)V", constant = @Constant(floatValue = 30.0F))
    private float ldog$sunSize(float orig) {
        return orig * (float) LDOGConfig.sunSizeMultiplier;
    }

    @ModifyConstant(method = "renderSky(FI)V", constant = @Constant(floatValue = 20.0F))
    private float ldog$moonSize(float orig) {
        return orig * (float) LDOGConfig.moonSizeMultiplier;
    }
}
