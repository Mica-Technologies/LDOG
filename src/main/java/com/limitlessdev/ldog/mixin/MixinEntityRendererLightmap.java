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
 * Full lighting customization via the lightmap texture.
 *
 * The lightmap is 16x16 (256 entries): index = skyLight * 16 + blockLight.
 * Each entry is an ARGB color determining the final tint at that light level.
 *
 * Effects applied (in order):
 * 1. Separate block/sky light color tinting (warm torches, cool moonlight)
 * 2. Night darkness multiplier (darken low-sky-light entries)
 * 3. Brightness boost (shift all entries brighter or darker)
 * 4. Pseudo-HDR tonemapping (ACES-like curve for cinematic contrast)
 *
 * All effects modify the lightmap once per frame — zero per-block cost.
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
    private void ldog$applyLightCustomization(float partialTicks, CallbackInfo ci) {
        if (!LDOGConfig.enableLightTemperature) return;

        float blockR = (float) LDOGConfig.blockLightRed;
        float blockG = (float) LDOGConfig.blockLightGreen;
        float blockB = (float) LDOGConfig.blockLightBlue;
        float skyR = (float) LDOGConfig.skyLightRed;
        float skyG = (float) LDOGConfig.skyLightGreen;
        float skyB = (float) LDOGConfig.skyLightBlue;
        float brightness = (float) LDOGConfig.lightBrightnessBoost;
        float nightDark = (float) LDOGConfig.nightDarkness;
        boolean hdr = LDOGConfig.enableHDR;

        for (int i = 0; i < 256; i++) {
            int skyLevel = i >> 4;    // 0-15
            int blockLevel = i & 0xF; // 0-15

            int color = lightmapColors[i];
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            // 1. Separate block/sky light color tinting
            // Blend between sky tint and block tint based on which light source
            // dominates. When block light is high relative to sky, use block tint.
            float blockWeight = blockLevel / 15f;
            float skyWeight = skyLevel / 15f;
            float totalWeight = blockWeight + skyWeight;
            if (totalWeight > 0) {
                float bw = blockWeight / totalWeight;
                float sw = skyWeight / totalWeight;
                float tintR = bw * blockR + sw * skyR;
                float tintG = bw * blockG + sw * skyG;
                float tintB = bw * blockB + sw * skyB;
                r *= tintR;
                g *= tintG;
                b *= tintB;
            }

            // 2. Brightness boost — shift all values up or down.
            // Applied BEFORE night darkness so darkness isn't undone by brightness.
            if (brightness != 0f) {
                r += brightness * 0.3f;
                g += brightness * 0.3f;
                b += brightness * 0.3f;
            }

            // 3. Night darkness — darken entries with low sky light.
            // Uses CLAMPING: caps the maximum brightness that sky-lit areas can
            // reach. This overrides vanilla's gamma/brightness — no matter how
            // bright vanilla makes things, the clamp enforces the ceiling.
            // Block-lit areas (torches) are protected from darkening.
            if (nightDark != 1.0f && skyLevel < 15) {
                float skyFactor = skyLevel / 15f;

                if (nightDark > 1.0f) {
                    // Block light protection: torches shouldn't be darkened.
                    // Full protection at blockLevel >= 8.
                    float blockProtection = Math.min(1.0f, blockLevel / 8.0f);

                    // Maximum brightness ceiling: at nightDark=100, sky=4 →
                    // ceiling = 0.003 (pitch black). Torches add up to 1.0.
                    float ceiling = skyFactor / nightDark + blockProtection;
                    ceiling = Math.min(1.0f, ceiling);

                    r = Math.min(r, ceiling);
                    g = Math.min(g, ceiling);
                    b = Math.min(b, ceiling);
                } else {
                    // Brighter nights (nightDark < 1): boost low-sky-light entries
                    float brightMul = 1.0f + (1.0f - nightDark) * (1.0f - skyFactor) * 2.0f;
                    r *= brightMul;
                    g *= brightMul;
                    b *= brightMul;
                }
            }

            // 4. Pseudo-HDR tonemapping (simplified ACES)
            // S-curve that compresses highlights and lifts shadows slightly,
            // giving more "cinematic" contrast.
            if (hdr) {
                r = acesTonemap(r);
                g = acesTonemap(g);
                b = acesTonemap(b);
            }

            // Clamp and pack
            int ri = Math.min(255, Math.max(0, (int) (r * 255f)));
            int gi = Math.min(255, Math.max(0, (int) (g * 255f)));
            int bi = Math.min(255, Math.max(0, (int) (b * 255f)));
            lightmapColors[i] = 0xFF000000 | (ri << 16) | (gi << 8) | bi;
        }
    }

    /**
     * Simplified ACES filmic tonemapping curve.
     * Maps [0,inf) to [0,1) with an S-curve that compresses highlights
     * and adds subtle shadow lift for a "cinematic" look.
     */
    private static float acesTonemap(float x) {
        if (x <= 0) return 0;
        float a = x * (2.51f * x + 0.03f);
        float b = x * (2.43f * x + 0.59f) + 0.14f;
        return Math.min(1.0f, a / b);
    }
}
