package com.limitlessdev.ldog.texture;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * Applies GL_TEXTURE_MAX_ANISOTROPY_EXT to stitched texture atlases.
 *
 * Sharpens surfaces viewed at glancing angles (floors/walls in the distance).
 * The level is clamped to the GPU's reported maximum.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class AnisotropicFilteringHandler {

    private static boolean warnedUnsupported = false;

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        applyTo(event.getMap());
    }

    /**
     * Re-applies (or disables) AF on the block/item atlas. Called from the
     * settings GUI when AF toggles change without a full resource reload.
     */
    public static void refreshMainAtlas() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureMapBlocks() == null) return;
        applyTo(mc.getTextureMapBlocks());
    }

    private static void applyTo(TextureMap map) {
        if (!GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            if (!warnedUnsupported && LDOGConfig.enableAnisotropicFiltering) {
                warnedUnsupported = true;
                LDOGMod.LOGGER.warn("LDOG: GPU does not support GL_EXT_texture_filter_anisotropic — AF disabled");
            }
            return;
        }

        float level;
        if (LDOGConfig.enableAnisotropicFiltering) {
            float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            level = Math.min(LDOGConfig.anisotropicLevel, max);
        } else {
            level = 1.0f; // GL default: AF off
        }

        GlStateManager.bindTexture(map.getGlTextureId());
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
            EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, level);

        LDOGMod.LOGGER.info("LDOG: Set AF {}x on atlas {}", (int) level, map.getBasePath());
    }
}
