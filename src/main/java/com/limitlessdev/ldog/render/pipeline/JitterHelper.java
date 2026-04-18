package com.limitlessdev.ldog.render.pipeline;

/**
 * Halton(2, 3)-based sub-pixel jitter generator for Phase 9c.1 TAA.
 *
 * Halton is the standard choice for TAA jitter: evenly-distributed sub-
 * pixel samples over a short window (16 frames is typical), no repeating
 * pattern that would alias with frame rate. Frame index cycles through
 * [0, HALTON_LENGTH) and increments at the start of each world-pass.
 *
 * Jitter range is [-0.5, 0.5] in texel units. Clip-space conversion done
 * at application site because the caller knows the source render-target
 * width (scene target when upscaling, main framebuffer otherwise).
 */
public final class JitterHelper {

    private static final int HALTON_LENGTH = 16;

    private static long frameCount = 0;

    private JitterHelper() {}

    /** Call once per rendered world-pass. Wrap-around is intentional — 16-frame cycle. */
    public static void advanceFrame() {
        frameCount = (frameCount + 1) % HALTON_LENGTH;
    }

    /** Reset jitter state — called on world changes, resource reloads, etc. */
    public static void reset() {
        frameCount = 0;
    }

    public static long currentFrame() {
        return frameCount;
    }

    /** Sub-pixel X offset in [-0.5, 0.5] texels. */
    public static float jitterX() {
        return halton((int) frameCount + 1, 2) - 0.5f;
    }

    /** Sub-pixel Y offset in [-0.5, 0.5] texels. */
    public static float jitterY() {
        return halton((int) frameCount + 1, 3) - 0.5f;
    }

    private static float halton(int index, int base) {
        float result = 0f;
        float f = 1f / base;
        int i = index;
        while (i > 0) {
            result += f * (i % base);
            i /= base;
            f /= base;
        }
        return result;
    }
}
