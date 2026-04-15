package com.limitlessdev.ldog.render.ctm;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

/**
 * Custom TextureAtlasSprite that loads its PNG from a non-standard path.
 * MCPatcher/OptiFine CTM tiles live at "mcpatcher/ctm/..." not "textures/...",
 * so the standard sprite loading (which prepends "textures/") can't find them.
 *
 * This sprite overrides hasCustomLoader to load from the actual file path.
 */
public class CTMSprite extends TextureAtlasSprite {

    private final ResourceLocation actualPngLocation;

    public CTMSprite(String spriteName, ResourceLocation actualPngLocation) {
        super(spriteName);
        this.actualPngLocation = actualPngLocation;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location,
                        Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        try {
            InputStream stream = manager.getResource(actualPngLocation).getInputStream();
            BufferedImage image = ImageIO.read(stream);
            stream.close();

            if (image == null) return true; // true = error, use missing texture

            int width = image.getWidth();
            int height = image.getHeight();

            this.width = width;
            this.height = height;

            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);

            // Convert ARGB to ABGR (Minecraft's internal format)
            for (int i = 0; i < pixels.length; i++) {
                int a = (pixels[i] >> 24) & 0xFF;
                int r = (pixels[i] >> 16) & 0xFF;
                int g = (pixels[i] >> 8) & 0xFF;
                int b = pixels[i] & 0xFF;
                pixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }

            int[][] frameData = new int[1][];
            frameData[0] = pixels;
            this.framesTextureData.clear();
            this.framesTextureData.add(frameData);

            return false; // false = success
        } catch (IOException e) {
            return true; // true = error
        }
    }
}
