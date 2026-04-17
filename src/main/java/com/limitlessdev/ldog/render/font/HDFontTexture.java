package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.LDOGMod;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

/**
 * SimpleTexture that loads a pack-provided HD font PNG
 * ({@code optifine/font/ascii.png} or {@code mcpatcher/font/ascii.png}) and
 * uploads it with the shared {@link FontTextureUploader} filter stack.
 */
public class HDFontTexture extends SimpleTexture {

    private final ResourceLocation sourceLocation;
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
            hasMipmaps = FontTextureUploader.upload(this.getGlTextureId(), image);
            LDOGMod.LOGGER.info("LDOG: Loaded HD font texture {} ({}x{}, mode={}, mipmaps={})",
                this.sourceLocation, image.getWidth(), image.getHeight(),
                FontAAMode.current().name().toLowerCase(), hasMipmaps);
        } finally {
            IOUtils.closeQuietly((Closeable) iresource);
        }
    }

    public void refreshFilter() {
        FontTextureUploader.refreshFilter(this.getGlTextureId(), hasMipmaps);
    }

    public boolean needsReloadToSwitchTo(FontAAMode target) {
        return target.needsMipmaps() != this.hasMipmaps;
    }
}
