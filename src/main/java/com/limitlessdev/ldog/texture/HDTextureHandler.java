package com.limitlessdev.ldog.texture;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Handles HD texture support features:
 * - Logs atlas size information after stitching
 * - Provides hook point for future texture resolution limiting
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class HDTextureHandler {

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!LDOGConfig.enableHDTextures) return;

        TextureMap map = event.getMap();
        int glMaxSize = Minecraft.getGLMaximumTextureSize();

        LDOGMod.LOGGER.info("LDOG: Texture atlas stitched. GL max texture size: {}",
            glMaxSize);
    }
}
