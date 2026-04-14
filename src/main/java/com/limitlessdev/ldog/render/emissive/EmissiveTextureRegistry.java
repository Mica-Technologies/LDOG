package com.limitlessdev.ldog.render.emissive;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

/**
 * Discovers and registers emissive texture overlays.
 *
 * Emissive textures follow the OptiFine convention: for a block texture
 * "blocks/stone.png", the emissive overlay is "blocks/stone_e.png".
 * The suffix is configurable (default "_e").
 *
 * When rendering, if a block/item quad's sprite has a corresponding emissive
 * sprite registered here, a second fullbright pass is rendered on top.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class EmissiveTextureRegistry {

    /** Suffix appended to texture names to find emissive variants */
    public static String emissiveSuffix = "_e";

    /** Map of base sprite name -> emissive sprite (only for sprites that have emissive variants) */
    private static final Map<String, TextureAtlasSprite> emissiveSprites = new HashMap<>();

    /**
     * During texture stitching, scan for emissive texture variants.
     * For every registered sprite "blocks/foo", check if "blocks/foo_e" exists.
     */
    @SubscribeEvent
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        emissiveSprites.clear();
        TextureMap map = event.getMap();

        // Try to load emissive.properties from resource pack (OptiFine format)
        loadEmissiveProperties();

        // We'll detect emissive sprites in the Post event after all textures are stitched
    }

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        TextureMap map = event.getMap();
        int found = 0;

        // After stitching, check which sprites have emissive variants
        // We iterate all uploaded sprites and check if an "_e" variant exists
        for (Map.Entry<String, TextureAtlasSprite> entry :
                ((Map<String, TextureAtlasSprite>) getUploadedSprites(map)).entrySet()) {
            String baseName = entry.getKey();
            String emissiveName = baseName + emissiveSuffix;

            TextureAtlasSprite emissiveSprite = map.getAtlasSprite(emissiveName);
            // getAtlasSprite returns missingno for unknown textures, so check it's not that
            if (emissiveSprite != null && !emissiveSprite.getIconName().equals("missingno")) {
                emissiveSprites.put(baseName, emissiveSprite);
                found++;
            }
        }

        if (found > 0) {
            LDOGMod.LOGGER.info("LDOG: Found {} emissive texture overlays", found);
        }
    }

    /**
     * Returns the emissive overlay sprite for a base texture, or null if none exists.
     */
    public static TextureAtlasSprite getEmissiveSprite(String baseTextureName) {
        return emissiveSprites.get(baseTextureName);
    }

    /**
     * Returns the emissive overlay for a sprite, or null if none exists.
     */
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
        // Try to read assets/minecraft/optifine/emissive.properties
        try {
            Minecraft mc = Minecraft.getMinecraft();
            ResourceLocation propsLoc = new ResourceLocation("minecraft",
                "optifine/emissive.properties");

            if (mc.getResourceManager() != null) {
                try {
                    java.io.InputStream stream = mc.getResourceManager()
                        .getResource(propsLoc).getInputStream();
                    java.util.Properties props = new java.util.Properties();
                    props.load(stream);
                    stream.close();

                    String suffix = props.getProperty("suffix.emissive", "_e");
                    emissiveSuffix = suffix;
                    LDOGMod.LOGGER.info("LDOG: Loaded emissive.properties, suffix='{}'", suffix);
                } catch (java.io.FileNotFoundException ignored) {
                    // No properties file, use default suffix
                } catch (java.io.IOException e) {
                    LDOGMod.LOGGER.warn("LDOG: Failed to read emissive.properties", e);
                }
            }
        } catch (Exception e) {
            // Resource manager not ready yet, use defaults
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TextureAtlasSprite> getUploadedSprites(TextureMap map) {
        // Access the mapUploadedSprites field via reflection or AT
        // For now, return empty map -- the actual sprite discovery happens
        // via the atlas sprite lookup in getAtlasSprite()
        try {
            java.lang.reflect.Field field = TextureMap.class.getDeclaredField("mapUploadedSprites");
            field.setAccessible(true);
            return (Map<String, TextureAtlasSprite>) field.get(map);
        } catch (Exception e) {
            // Obfuscated name fallback
            for (java.lang.reflect.Field field : TextureMap.class.getDeclaredFields()) {
                if (field.getType() == Map.class) {
                    try {
                        field.setAccessible(true);
                        Map<?, ?> m = (Map<?, ?>) field.get(map);
                        if (!m.isEmpty()) {
                            Object firstValue = m.values().iterator().next();
                            if (firstValue instanceof TextureAtlasSprite) {
                                return (Map<String, TextureAtlasSprite>) m;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return new HashMap<>();
    }
}
