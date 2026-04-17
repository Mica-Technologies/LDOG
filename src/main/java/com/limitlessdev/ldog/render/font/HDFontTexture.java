package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

/**
 * Font-texture variant of {@link SimpleTexture} that honors
 * {@link LDOGConfig#fontAntialiasing}. See {@link FontAAMode} for the meaning
 * of each step; the short version is that only TRILINEAR builds a mipmap chain,
 * which is the only mode that actually antialiases at GUI scale (the HD atlas
 * is downsampled ≳16:1 there, which plain bilinear can't resolve).
 */
public class HDFontTexture extends SimpleTexture {

    private final ResourceLocation sourceLocation;
    /** True if a mip chain was built on the current GL texture. */
    private boolean hasMipmaps = false;

    public HDFontTexture(ResourceLocation sourceLocation) {
        super(sourceLocation);
        this.sourceLocation = sourceLocation;
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) throws IOException {
        this.deleteGlTexture();
        IResource iresource = null;
        try {
            iresource = resourceManager.getResource(this.sourceLocation);
            BufferedImage image = TextureUtil.readBufferedImage(iresource.getInputStream());
            FontAAMode mode = FontAAMode.current();

            int glId = this.getGlTextureId();
            // Upload base level via the vanilla helper. The `blur` arg here just sets
            // the initial MIN/MAG filters to LINEAR/NEAREST; we overwrite below so the
            // mode-specific decision is the authoritative one.
            TextureUtil.uploadTextureImageAllocate(glId, image, mode.isSmooth(), false);

            GlStateManager.bindTexture(glId);
            hasMipmaps = false;
            float anisoApplied = 1.0f;
            float lodBiasApplied = 0.0f;
            if (mode.needsMipmaps() && GLContext.getCapabilities().OpenGL30) {
                // allocateTextureImpl pinned GL_TEXTURE_MAX_LEVEL to 0. Raise it so
                // glGenerateMipmap actually builds a chain.
                int maxLevel = log2(Math.max(image.getWidth(), image.getHeight()));
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                hasMipmaps = true;

                // Negative LOD bias biases the GPU toward sharper mip levels. Without
                // it, box-filter mipmap generation at ≳16:1 downsampling produces a
                // softer result than users expect from "antialiased HD font" — Smooth
                // Font does the same trick. Clamped against the config range below.
                lodBiasApplied = clamp((float) LDOGConfig.fontLodBias, -4.0f, 4.0f);
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, lodBiasApplied);

                // Anisotropic sampling takes more source texels per screen pixel along
                // the axis of maximum change. For GUI text this mostly helps recover
                // sub-pixel edge detail that trilinear loses to mipmap blurring.
                if (LDOGConfig.fontAnisotropic > 1
                    && GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                    float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                    anisoApplied = Math.min(LDOGConfig.fontAnisotropic, max);
                    GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, anisoApplied);
                }
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mode.minFilter(hasMipmaps));
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mode.magFilter());

            LDOGMod.LOGGER.info("LDOG: Loaded HD font texture {} ({}x{}, mode={}, mipmaps={}, lodBias={}, aniso={}x)",
                this.sourceLocation, image.getWidth(), image.getHeight(),
                mode.name().toLowerCase(), hasMipmaps, lodBiasApplied, (int) anisoApplied);
        } finally {
            IOUtils.closeQuietly((Closeable) iresource);
        }
    }

    /**
     * Re-applies the current filter choice to the already-uploaded texture.
     * Used when the AA mode flips at runtime so the user doesn't have to wait
     * for a full resource reload to see the change.
     *
     * <p>If the flip requires a mip chain that isn't built (was previously off
     * or bilinear and now trilinear), we fall back to plain LINEAR; a full
     * resource reload will rebuild the chain and restore trilinear.
     */
    public void refreshFilter() {
        int id = this.getGlTextureId();
        if (id == -1) return;
        FontAAMode mode = FontAAMode.current();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mode.minFilter(hasMipmaps));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mode.magFilter());
    }

    /**
     * True if switching to the given mode requires a fresh upload (i.e. we
     * need to build or drop a mip chain). Used by the settings GUI to decide
     * between a cheap filter flip and a full resource reload.
     */
    public boolean needsReloadToSwitchTo(FontAAMode target) {
        return target.needsMipmaps() != this.hasMipmaps;
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
