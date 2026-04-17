package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.awt.image.BufferedImage;

/**
 * Shared GL upload path for font-atlas textures. Extracted so both
 * {@link HDFontTexture} (resource-pack HD PNG) and {@link TTFFontTexture}
 * (runtime-rasterized AWT atlas) produce identical filter / mipmap /
 * LOD-bias / anisotropic state for a given {@link FontAAMode}.
 */
public final class FontTextureUploader {

    private FontTextureUploader() {}

    /**
     * Uploads {@code image} to {@code glId} and configures filtering according
     * to the current AA mode. Returns {@code true} if a mip chain was built.
     *
     * <p>The filter state cascade mirrors Smooth Font's default pipeline:
     * {@code GL_LINEAR_MIPMAP_LINEAR} for trilinear mode, with a negative LOD
     * bias and optional anisotropic sampling layered on top to recover the
     * crispness box-filter mipmap generation tends to soften at heavy
     * downsampling.
     */
    public static boolean upload(int glId, BufferedImage image) {
        FontAAMode mode = FontAAMode.current();
        // Base upload via the vanilla helper — allocates level 0 + uploads pixels.
        // The `blur` arg here only sets the initial filter; we overwrite below.
        TextureUtil.uploadTextureImageAllocate(glId, image, mode.isSmooth(), false);

        GlStateManager.bindTexture(glId);
        boolean hasMipmaps = false;
        if (mode.needsMipmaps() && GLContext.getCapabilities().OpenGL30) {
            int maxLevel = log2(Math.max(image.getWidth(), image.getHeight()));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            hasMipmaps = true;

            float lodBias = clamp((float) LDOGConfig.fontLodBias, -4.0f, 4.0f);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, lodBias);

            if (LDOGConfig.fontAnisotropic > 1
                && GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                float aniso = Math.min(LDOGConfig.fontAnisotropic, max);
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
            }
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mode.minFilter(hasMipmaps));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mode.magFilter());
        return hasMipmaps;
    }

    /**
     * Re-applies the current filter choice to the given GL texture without
     * re-uploading pixel data. Used by the live-flip path (off ↔ bilinear).
     */
    public static void refreshFilter(int glId, boolean hasMipmaps) {
        if (glId == -1) return;
        FontAAMode mode = FontAAMode.current();
        GlStateManager.bindTexture(glId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mode.minFilter(hasMipmaps));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mode.magFilter());
    }

    private static int log2(int value) {
        int log = 0;
        while ((value >>= 1) > 0) log++;
        return log;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
