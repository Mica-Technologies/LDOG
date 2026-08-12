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

    /** Length of a Minecraft day in ticks. */
    public static final int DAY_TICKS = 24000;

    /**
     * Compute the alpha (0.0-1.0) for this layer based on the current world time.
     * Returns 0 if the layer is fully faded out.
     */
    public float getAlpha(long worldTime) {
        return computeAlpha((int) (worldTime % DAY_TICKS),
            startFadeIn, endFadeIn, startFadeOut, endFadeOut);
    }

    /**
     * Piecewise fade-in / hold / fade-out alpha, evaluated on the 24000-tick
     * circle rather than the linear tick line.
     *
     * <p>All four keyframes are first rotated so that {@code startFadeIn} sits at
     * 0; every configuration then reduces to the plain ordered case
     * {@code 0 <= endFadeIn <= startFadeOut <= endFadeOut}, which is how OptiFine
     * evaluates sky layers. Comparing raw tick values instead breaks for any
     * schedule that crosses midnight — e.g. fade in 21000-22000 with fade out
     * 2000-3000 read as fully transparent at t=1000, and a wrapped fade-in
     * (23000 -> 1000) vanished the instant it finished fading in.
     */
    public static float computeAlpha(int timeOfDay,
                                      int startFadeIn, int endFadeIn,
                                      int startFadeOut, int endFadeOut) {
        int time = normalize(timeOfDay - startFadeIn);
        int fadeInEnd = normalize(endFadeIn - startFadeIn);
        int fadeOutStart = normalize(startFadeOut - startFadeIn);
        int fadeOutEnd = normalize(endFadeOut - startFadeIn);

        if (time < fadeInEnd) {
            return (float) time / (float) fadeInEnd;
        }
        if (time < fadeOutStart) {
            return 1.0f;
        }
        if (time < fadeOutEnd) {
            return 1.0f - (float) (time - fadeOutStart) / (float) (fadeOutEnd - fadeOutStart);
        }
        return 0.0f;
    }

    /** Wraps a tick delta into [0, 24000). */
    private static int normalize(int ticks) {
        int t = ticks % DAY_TICKS;
        return t < 0 ? t + DAY_TICKS : t;
    }
}
