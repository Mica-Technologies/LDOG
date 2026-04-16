package com.limitlessdev.ldog.render.sky;

import net.minecraft.util.ResourceLocation;

/**
 * A single custom sky layer parsed from an OptiFine-format .properties file.
 *
 * Properties format (optifine/sky/world0/skyN.properties):
 *   source=path/to/texture.png
 *   startFadeIn=time (0-24000 MC ticks)
 *   endFadeIn=time
 *   startFadeOut=time
 *   endFadeOut=time
 *   blend=add|multiply|overlay (default: add)
 *   rotate=true|false (default: true)
 *   speed=1.0 (rotation speed multiplier)
 *   axis=x y z (rotation axis, default: 0 0 1 = south-north pole axis)
 */
public class CustomSkyLayer {

    public final ResourceLocation texture;
    public final int startFadeIn;
    public final int endFadeIn;
    public final int startFadeOut;
    public final int endFadeOut;
    public final String blend;
    public final boolean rotate;
    public final float speed;
    public final float axisX, axisY, axisZ;

    public CustomSkyLayer(ResourceLocation texture,
                           int startFadeIn, int endFadeIn,
                           int startFadeOut, int endFadeOut,
                           String blend, boolean rotate, float speed,
                           float axisX, float axisY, float axisZ) {
        this.texture = texture;
        this.startFadeIn = startFadeIn;
        this.endFadeIn = endFadeIn;
        this.startFadeOut = startFadeOut;
        this.endFadeOut = endFadeOut;
        this.blend = blend;
        this.rotate = rotate;
        this.speed = speed;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
    }

    /**
     * Compute the alpha (0.0-1.0) for this layer based on the current world time.
     * Returns 0 if the layer is fully faded out.
     */
    public float getAlpha(long worldTime) {
        int time = (int) (worldTime % 24000L);

        // Handle wrap-around (e.g., fadeIn starts at 23000, ends at 1000)
        float fadeInAlpha = getFadeAlpha(time, startFadeIn, endFadeIn);
        float fadeOutAlpha = 1.0f - getFadeAlpha(time, startFadeOut, endFadeOut);

        return Math.min(fadeInAlpha, fadeOutAlpha);
    }

    private static float getFadeAlpha(int time, int start, int end) {
        if (start == end) return time >= start ? 1.0f : 0.0f;

        int duration;
        int elapsed;

        if (end > start) {
            // Normal range (e.g., 6000 to 8000)
            duration = end - start;
            if (time < start) return 0.0f;
            if (time >= end) return 1.0f;
            elapsed = time - start;
        } else {
            // Wrapping range (e.g., 23000 to 1000)
            duration = (24000 - start) + end;
            if (time >= start) {
                elapsed = time - start;
            } else if (time < end) {
                elapsed = (24000 - start) + time;
            } else {
                return 0.0f;
            }
            if (elapsed >= duration) return 1.0f;
        }

        return (float) elapsed / (float) duration;
    }
}
