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
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

/**
 * Font-texture variant of {@link SimpleTexture} that honors
 * {@link LDOGConfig#antialiasedFont}. Sampling a 4096×4096 atlas down to typical
 * GUI-scale on-screen glyph sizes is ≳16:1 downsampling, which plain {@code GL_LINEAR}
 * cannot antialias (it only averages 2×2 source texels per screen pixel — missing
 * most of the source signal, hence visible aliasing).
 *
 * <p>The fix is a proper mip chain plus trilinear filtering: generate mipmaps so
 * the GPU can pick a pre-downsampled level that matches the screen footprint, and
 * use {@code GL_LINEAR_MIPMAP_LINEAR} for {@code MIN_FILTER}. With NEAREST mode
 * (user toggle off) we skip mipmap generation and behave like vanilla.
 */
public class HDFontTexture extends SimpleTexture {

    private final ResourceLocation sourceLocation;
    /** Highest mip level present on this texture; 0 means only base level. */
    private int maxMipLevel = 0;

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

            int glId = this.getGlTextureId();
            // Base upload via vanilla helper — allocates level 0 storage, uploads
            // pixels, and sets GL_LINEAR or GL_NEAREST on MIN/MAG.
            TextureUtil.uploadTextureImageAllocate(glId, image, blur, false);

            if (blur && GLContext.getCapabilities().OpenGL30) {
                // Raise the mip-level cap that allocateTextureImpl pinned to 0, then
                // let the driver build the full chain from our level-0 data and flip
                // MIN_FILTER to the mipmap-aware variant so downscaled rendering
                // samples from the right level.
                int maxLevel = log2(Math.max(image.getWidth(), image.getHeight()));
                GlStateManager.bindTexture(glId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                this.maxMipLevel = maxLevel;
                LDOGMod.LOGGER.info("LDOG: Loaded HD font texture {} ({}x{}, filter=LINEAR_MIPMAP_LINEAR, mipLevels={})",
                    this.sourceLocation, image.getWidth(), image.getHeight(), maxLevel);
            } else {
                this.maxMipLevel = 0;
                LDOGMod.LOGGER.info("LDOG: Loaded HD font texture {} ({}x{}, filter={})",
                    this.sourceLocation, image.getWidth(), image.getHeight(),
                    blur ? "LINEAR (no GL3)" : "NEAREST");
            }
        } finally {
            IOUtils.closeQuietly((Closeable) iresource);
        }
    }

    /**
     * Re-applies the current filter choice to the already-uploaded texture.
     * Used when the antialias toggle flips at runtime so the user doesn't have
     * to wait for a full resource reload to see the change.
     *
     * <p>If mipmaps are present, we prefer {@code LINEAR_MIPMAP_LINEAR}; if the
     * chain hasn't been built yet (filter was previously off), we keep plain
     * {@code LINEAR}. Toggling NEAREST→LINEAR after startup therefore won't get
     * full mipmap AA until the next resource reload rebuilds the chain.
     */
    public void refreshFilter() {
        int id = this.getGlTextureId();
        if (id == -1) return;
        GlStateManager.bindTexture(id);
        if (LDOGConfig.antialiasedFont) {
            int minFilter = (maxMipLevel > 0) ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
    }

    private static int log2(int value) {
        int log = 0;
        while ((value >>= 1) > 0) log++;
        return log;
    }
}
