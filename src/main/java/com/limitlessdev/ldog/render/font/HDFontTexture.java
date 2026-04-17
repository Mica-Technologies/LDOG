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
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

/**
 * Font-texture variant of {@link SimpleTexture} that honors
 * {@link LDOGConfig#antialiasedFont}: when the flag is on, the upload uses
 * GL_LINEAR for min/mag filtering so the HD glyph atlas is sampled smoothly
 * instead of blocky-nearest. When off, it behaves like a vanilla SimpleTexture
 * (GL_NEAREST), which is useful for side-by-side comparisons.
 */
public class HDFontTexture extends SimpleTexture {

    private final ResourceLocation sourceLocation;

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
            boolean blur = LDOGConfig.antialiasedFont;
            TextureUtil.uploadTextureImageAllocate(this.getGlTextureId(), image, blur, false);
            LDOGMod.LOGGER.info("LDOG: Loaded HD font texture {} ({}x{}, filter={})",
                this.sourceLocation, image.getWidth(), image.getHeight(),
                blur ? "LINEAR" : "NEAREST");
        } finally {
            IOUtils.closeQuietly((Closeable) iresource);
        }
    }

    /**
     * Re-applies the current LINEAR/NEAREST filter choice to the already-uploaded
     * texture. Used when the antialias toggle changes at runtime and a full
     * resource reload is avoidable.
     */
    public void refreshFilter() {
        int id = this.getGlTextureId();
        if (id == -1) return;
        int filter = LDOGConfig.antialiasedFont ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
    }
}
