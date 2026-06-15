package com.limitlessdev.ldog.render.shaderpack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * The {@code noisetex} sampler most OptiFine / Iris packs expect — a small
 * tiled RGB(A) noise texture used for water ripples, cloud detail, dithering,
 * film grain, etc. Packs that sample it against an unbound (black) texture
 * lose those effects entirely, so providing a real one is a cheap, broadly
 * useful parity win.
 *
 * <p>Generated once with a fixed seed (deterministic across launches) and
 * {@code GL_REPEAT}-wrapped so shaders can tile it freely. Resolution matches
 * OptiFine's default {@code noiseTextureResolution} of 256.
 */
public final class ShaderNoiseTexture {

    /** OptiFine's default noise resolution; exposed as the noiseTextureResolution uniform. */
    public static final int RESOLUTION = 256;

    private static int texId;

    private ShaderNoiseTexture() {}

    /** Lazily generate + upload the noise texture, returning its GL handle. */
    public static int get() {
        if (texId != 0) return texId;
        texId = GL11.glGenTextures();

        ByteBuffer pixels = BufferUtils.createByteBuffer(RESOLUTION * RESOLUTION * 4);
        Random rng = new Random(0x1D06L); // fixed seed → stable across launches
        for (int i = 0; i < RESOLUTION * RESOLUTION; i++) {
            pixels.put((byte) rng.nextInt(256));
            pixels.put((byte) rng.nextInt(256));
            pixels.put((byte) rng.nextInt(256));
            pixels.put((byte) 255);
        }
        pixels.flip();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, RESOLUTION, RESOLUTION, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        // REPEAT so packs can tile the noise; LINEAR for smooth sampling.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texId;
    }

    public static void dispose() {
        if (texId != 0) { GL11.glDeleteTextures(texId); texId = 0; }
    }
}
