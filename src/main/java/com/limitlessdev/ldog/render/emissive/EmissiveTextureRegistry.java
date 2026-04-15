package com.limitlessdev.ldog.render.emissive;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.mixin.AccessorTextureMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Discovers and registers emissive texture overlays (_e suffix).
 * Scans all registered block/item sprites for corresponding _e variants
 * in the resource pack and registers them with the texture atlas.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class EmissiveTextureRegistry {

    public static String emissiveSuffix = "_e";

    private static final Map<String, TextureAtlasSprite> emissiveSprites = new HashMap<>();
    private static final Map<String, String> emissiveNames = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        emissiveSprites.clear();
        emissiveNames.clear();
        loadEmissiveProperties();

        TextureMap map = event.getMap();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() == null) return;

        // Get all currently registered sprite names via accessor mixin
        Map<String, TextureAtlasSprite> registeredMap =
            ((AccessorTextureMap) map).ldog$getMapRegisteredSprites();
        Set<String> registeredNames = new java.util.HashSet<>(registeredMap.keySet());
        int found = 0;

        for (String spriteName : registeredNames) {
            if (spriteName.endsWith(emissiveSuffix)) continue;

            // Build the emissive sprite name
            String emissiveName = spriteName + emissiveSuffix;

            // Build the resource path for the _e PNG file
            // Sprite name format: "minecraft:blocks/stone" or "blocks/stone"
            // PNG path format: "assets/minecraft/textures/blocks/stone_e.png"
            ResourceLocation emissivePngLoc = spriteNameToPngLocation(emissiveName);
            if (emissivePngLoc == null) continue;

            if (resourceExists(mc, emissivePngLoc)) {
                // Register the emissive sprite with the atlas
                ResourceLocation emissiveSpriteLoc = spriteNameToSpriteLocation(emissiveName);
                if (emissiveSpriteLoc != null) {
                    map.registerSprite(emissiveSpriteLoc);
                    emissiveNames.put(spriteName, emissiveName);
                    found++;
                    LDOGMod.LOGGER.debug("LDOG: Found emissive texture: {} -> {}",
                        spriteName, emissiveName);
                }
            }
        }

        LDOGMod.LOGGER.info("LDOG: Registered {} emissive texture overlays (suffix='{}')",
            found, emissiveSuffix);
    }

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        TextureMap map = event.getMap();
        emissiveSprites.clear();

        for (Map.Entry<String, String> entry : emissiveNames.entrySet()) {
            String baseName = entry.getKey();
            String emissiveName = entry.getValue();

            TextureAtlasSprite emissiveSprite = map.getAtlasSprite(emissiveName);
            if (emissiveSprite != null && !"missingno".equals(emissiveSprite.getIconName())) {
                emissiveSprites.put(baseName, emissiveSprite);
            }
        }

        if (!emissiveSprites.isEmpty()) {
            LDOGMod.LOGGER.info("LDOG: {} emissive textures loaded into atlas", emissiveSprites.size());
        }
    }

    public static TextureAtlasSprite getEmissiveSprite(TextureAtlasSprite baseSprite) {
        if (baseSprite == null) return null;
        return emissiveSprites.get(baseSprite.getIconName());
    }

    public static int getEmissiveSpriteCount() {
        return emissiveSprites.size();
    }

    /**
     * Convert sprite name to PNG resource location.
     * "minecraft:blocks/stone_e" -> ResourceLocation("minecraft", "textures/blocks/stone_e.png")
     */
    private static ResourceLocation spriteNameToPngLocation(String spriteName) {
        String domain = "minecraft";
        String path = spriteName;

        int colonIdx = spriteName.indexOf(':');
        if (colonIdx >= 0) {
            domain = spriteName.substring(0, colonIdx);
            path = spriteName.substring(colonIdx + 1);
        }

        return new ResourceLocation(domain, "textures/" + path + ".png");
    }

    /**
     * Convert sprite name to sprite ResourceLocation for registerSprite().
     * "minecraft:blocks/stone_e" -> ResourceLocation("minecraft", "blocks/stone_e")
     */
    private static ResourceLocation spriteNameToSpriteLocation(String spriteName) {
        int colonIdx = spriteName.indexOf(':');
        if (colonIdx >= 0) {
            return new ResourceLocation(spriteName.substring(0, colonIdx),
                spriteName.substring(colonIdx + 1));
        }
        return new ResourceLocation(spriteName);
    }

    private static boolean resourceExists(Minecraft mc, ResourceLocation loc) {
        try {
            IResource resource = mc.getResourceManager().getResource(loc);
            resource.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void loadEmissiveProperties() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getResourceManager() == null) return;

            // Try optifine path first, then mcpatcher
            for (String basePath : new String[]{"optifine", "mcpatcher"}) {
                ResourceLocation propsLoc = new ResourceLocation("minecraft",
                    basePath + "/emissive.properties");
                try {
                    IResource resource = mc.getResourceManager().getResource(propsLoc);
                    java.util.Properties props = new java.util.Properties();
                    props.load(resource.getInputStream());
                    resource.close();

                    String suffix = props.getProperty("suffix.emissive", "_e");
                    emissiveSuffix = suffix;
                    LDOGMod.LOGGER.info("LDOG: Loaded emissive.properties from {}, suffix='{}'",
                        basePath, suffix);
                    return;
                } catch (java.io.FileNotFoundException ignored) {
                } catch (Exception e) {
                    LDOGMod.LOGGER.warn("LDOG: Failed to read emissive.properties", e);
                }
            }
        } catch (Exception ignored) {}
    }

    // Sprite map access is now handled by AccessorTextureMap mixin
}
