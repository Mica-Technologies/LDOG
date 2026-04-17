package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.LDOGMod;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * GL texture fed by a pre-computed {@link TTFFontRasterizer.Result}. Takes the
 * same upload path as {@link HDFontTexture} via {@link FontTextureUploader}, so
 * the filter / mipmap / LOD-bias / anisotropic behavior is identical. The only
 * difference is the image source: a runtime AWT rasterization instead of a
 * pack-provided PNG.
 */
public class TTFFontTexture extends SimpleTexture {

    private final TTFFontRasterizer.Result rasterization;
    private boolean hasMipmaps = false;

    public TTFFontTexture(ResourceLocation label, TTFFontRasterizer.Result rasterization) {
        super(label);
        this.rasterization = rasterization;
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) throws IOException {
        this.deleteGlTexture();
        hasMipmaps = FontTextureUploader.upload(this.getGlTextureId(), rasterization.atlas);
        LDOGMod.LOGGER.info("LDOG: Uploaded TTF font atlas ({}x{}, mode={}, mipmaps={})",
            rasterization.atlas.getWidth(), rasterization.atlas.getHeight(),
            FontAAMode.current().name().toLowerCase(), hasMipmaps);
    }

    public int[] getLogicalWidths() {
        return rasterization.logicalWidths;
    }

    public void refreshFilter() {
        FontTextureUploader.refreshFilter(this.getGlTextureId(), hasMipmaps);
    }

    public boolean needsReloadToSwitchTo(FontAAMode target) {
        return target.needsMipmaps() != this.hasMipmaps;
    }
}
