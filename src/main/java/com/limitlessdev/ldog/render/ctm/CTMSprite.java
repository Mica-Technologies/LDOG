package com.limitlessdev.ldog.render.ctm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Function;

/**
 * Custom TextureAtlasSprite that loads its PNG from a non-standard path.
 * MCPatcher/OptiFine CTM tiles live at "mcpatcher/ctm/..." not "textures/...",
 * so the standard sprite loading (which prepends "textures/") can't find them.
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
            BufferedImage image = TextureUtil.readBufferedImage(
                manager.getResource(actualPngLocation).getInputStream());

            if (image == null) return true;

            this.width = image.getWidth();
            this.height = image.getHeight();

            // Match vanilla's frame data format exactly (TextureAtlasSprite.loadSpriteFrames):
            // Array size = mipmapLevels + 1, with only [0] populated.
            // generateMipmapData() fills the rest and handles any size.
            int mipmapLevels = Minecraft.getMinecraft().gameSettings.mipmapLevels;
            int[][] frameData = new int[mipmapLevels + 1][];
            frameData[0] = new int[this.width * this.height];
            image.getRGB(0, 0, this.width, this.height, frameData[0], 0, this.width);

            this.framesTextureData.clear();
            this.framesTextureData.add(frameData);

            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
