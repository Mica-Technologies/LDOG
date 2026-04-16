package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.dynamiclights.LightTemperaturePreset;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies global light color temperature tinting to the lightmap.
 *
 * The lightmap is a 16x16 texture where index = skyLight*16 + blockLight.
 * Each entry is an ARGB color that determines the final tint for blocks
 * at that light level combination. By shifting RGB channels here, we can
 * make all lighting warmer (orange/amber) or cooler (blue) without shaders.
 *
 * Supports named presets (warm_white, candlelight, fluorescent, moonlight,
 * purple_haze, etc.) or custom RGB multipliers.
 *
 * Injection point: just before lightmapTexture.updateDynamicTexture(),
 * after vanilla has computed all 256 lightmap entries.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererLightmap {

    @Shadow @Final private int[] lightmapColors;

    @Inject(
        method = "updateLightmap",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;updateDynamicTexture()V"
        )
    )
    private void ldog$applyLightTemperature(float partialTicks, CallbackInfo ci) {
        if (!LDOGConfig.enableLightTemperature) return;

        // Resolve RGB multipliers from preset or custom config
        float rMul, gMul, bMul;
        String presetName = LDOGConfig.lightTemperaturePreset;
        if ("custom".equalsIgnoreCase(presetName)) {
            rMul = (float) LDOGConfig.lightTintRed;
            gMul = (float) LDOGConfig.lightTintGreen;
            bMul = (float) LDOGConfig.lightTintBlue;
        } else {
            LightTemperaturePreset preset = LightTemperaturePreset.fromConfig(presetName);
            rMul = preset.red;
            gMul = preset.green;
            bMul = preset.blue;
        }
        if (rMul == 1f && gMul == 1f && bMul == 1f) return;

        for (int i = 0; i < 256; i++) {
            int color = lightmapColors[i];
            int r = Math.min(255, (int) (((color >> 16) & 0xFF) * rMul));
            int g = Math.min(255, (int) (((color >> 8) & 0xFF) * gMul));
            int b = Math.min(255, (int) ((color & 0xFF) * bMul));
            lightmapColors[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }
}
