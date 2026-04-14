package com.limitlessdev.ldog.render.emissive;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
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

/**
 * Discovers and registers emissive texture overlays.
 *
 * For a block texture "blocks/stone", checks if "blocks/stone_e" exists
 * as a PNG in any loaded resource pack. If so, registers it with the
 * texture atlas so it gets stitched, then maps base -> emissive for
 * the render handler to use.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class EmissiveTextureRegistry {

    public static String emissiveSuffix = "_e";

    /** Map of base sprite name -> emissive sprite (populated after stitching) */
    private static final Map<String, TextureAtlasSprite> emissiveSprites = new HashMap<>();
    /** Map of base sprite name -> emissive resource location (populated during pre-stitch) */
    private static final Map<String, ResourceLocation> emissiveLocations = new HashMap<>();

    /**
     * During texture stitching, scan all registered sprites for _e variants
     * and register those variants with the atlas.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        emissiveSprites.clear();
        emissiveLocations.clear();
        loadEmissiveProperties();

        TextureMap map = event.getMap();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() == null) return;

        // Get all currently registered sprites and check for _e variants
        Map<String, TextureAtlasSprite> registered = getRegisteredSprites(map);
        int found = 0;

        for (String spriteName : registered.keySet()) {
            // Skip sprites that are already emissive variants
            if (spriteName.endsWith(emissiveSuffix)) continue;

            String emissiveName = spriteName + emissiveSuffix;

            // Check if the emissive texture PNG exists in any resource pack
            ResourceLocation emissiveLoc = toResourceLocation(emissiveName);
            if (emissiveLoc != null && resourceExists(mc, emissiveLoc)) {
                // Register the emissive sprite with the atlas
                map.registerSprite(emissiveLoc);
                emissiveLocations.put(spriteName, emissiveLoc);
                found++;
            }
        }

        LDOGMod.LOGGER.info("LDOG: Registered {} emissive texture overlays (suffix='{}')",
            found, emissiveSuffix);
    }

    /**
     * After stitching, resolve the registered emissive locations to actual sprites.
     */
    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        TextureMap map = event.getMap();
        emissiveSprites.clear();

        for (Map.Entry<String, ResourceLocation> entry : emissiveLocations.entrySet()) {
            String baseName = entry.getKey();
            String emissiveName = entry.getValue().toString();

            TextureAtlasSprite emissiveSprite = map.getAtlasSprite(emissiveName);
            if (emissiveSprite != null && !emissiveSprite.getIconName().equals("missingno")) {
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

    public static boolean hasEmissiveSprite(TextureAtlasSprite baseSprite) {
        if (baseSprite == null) return false;
        return emissiveSprites.containsKey(baseSprite.getIconName());
    }

    public static int getEmissiveSpriteCount() {
        return emissiveSprites.size();
    }

    private static void loadEmissiveProperties() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getResourceManager() == null) return;

            ResourceLocation propsLoc = new ResourceLocation("minecraft",
                "optifine/emissive.properties");
            try {
                IResource resource = mc.getResourceManager().getResource(propsLoc);
                java.util.Properties props = new java.util.Properties();
                props.load(resource.getInputStream());
                resource.close();

                String suffix = props.getProperty("suffix.emissive", "_e");
                emissiveSuffix = suffix;
                LDOGMod.LOGGER.info("LDOG: Loaded emissive.properties, suffix='{}'", suffix);
            } catch (java.io.FileNotFoundException ignored) {
                // No properties file, use default
            } catch (Exception e) {
                LDOGMod.LOGGER.warn("LDOG: Failed to read emissive.properties", e);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Convert a sprite name like "minecraft:blocks/stone_e" to a ResourceLocation.
     */
    private static ResourceLocation toResourceLocation(String spriteName) {
        int colonIdx = spriteName.indexOf(':');
        if (colonIdx >= 0) {
            return new ResourceLocation(spriteName.substring(0, colonIdx),
                spriteName.substring(colonIdx + 1));
        }
        return new ResourceLocation(spriteName);
    }

    /**
     * Check if a texture PNG exists as a resource.
     * Textures are at assets/<domain>/textures/<path>.png
     */
    private static boolean resourceExists(Minecraft mc, ResourceLocation loc) {
        ResourceLocation pngLoc = new ResourceLocation(loc.getNamespace(),
            "textures/" + loc.getPath() + ".png");
        try {
            IResource resource = mc.getResourceManager().getResource(pngLoc);
            resource.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TextureAtlasSprite> getRegisteredSprites(TextureMap map) {
        try {
            // mapRegisteredSprites is a protected field
            java.lang.reflect.Field field = TextureMap.class.getDeclaredField("mapRegisteredSprites");
            field.setAccessible(true);
            return new HashMap<>((Map<String, TextureAtlasSprite>) field.get(map));
        } catch (NoSuchFieldException e) {
            // Try obfuscated name fallback
            for (java.lang.reflect.Field field : TextureMap.class.getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        Map<?, ?> m = (Map<?, ?>) field.get(map);
                        if (m != null && !m.isEmpty()) {
                            Object firstKey = m.keySet().iterator().next();
                            if (firstKey instanceof String) {
                                Object firstVal = m.values().iterator().next();
                                if (firstVal instanceof TextureAtlasSprite) {
                                    return new HashMap<>((Map<String, TextureAtlasSprite>) m);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LDOGMod.LOGGER.warn("LDOG: Failed to access registered sprites", e);
        }
        return new HashMap<>();
    }
}
