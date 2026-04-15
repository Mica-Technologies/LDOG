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

import java.lang.reflect.Field;
import java.util.*;

/**
 * Discovers and registers emissive texture overlays (_e suffix).
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class EmissiveTextureRegistry {

    public static String emissiveSuffix = "_e";

    private static final Map<String, TextureAtlasSprite> emissiveSprites = new HashMap<>();
    private static final Map<String, String> emissiveNames = new HashMap<>();

    // Cached field reference for mapRegisteredSprites
    private static Field registeredSpritesField = null;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        emissiveSprites.clear();
        emissiveNames.clear();
        loadEmissiveProperties();

        TextureMap map = event.getMap();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() == null) return;

        // Get registered sprites via reflection (works in both dev and prod)
        Map<String, TextureAtlasSprite> registeredMap = getRegisteredSprites(map);
        if (registeredMap == null || registeredMap.isEmpty()) {
            LDOGMod.LOGGER.warn("LDOG: Could not access registered sprites map - emissive textures disabled");
            return;
        }

        LDOGMod.LOGGER.info("LDOG: Scanning {} registered sprites for emissive variants",
            registeredMap.size());

        Set<String> spriteNames = new HashSet<>(registeredMap.keySet());
        int found = 0;

        for (String spriteName : spriteNames) {
            if (spriteName.endsWith(emissiveSuffix)) continue;

            String emissiveName = spriteName + emissiveSuffix;
            ResourceLocation emissivePngLoc = spriteNameToPngLocation(emissiveName);
            if (emissivePngLoc == null) continue;

            if (resourceExists(mc, emissivePngLoc)) {
                ResourceLocation emissiveSpriteLoc = spriteNameToSpriteLocation(emissiveName);
                if (emissiveSpriteLoc != null) {
                    map.registerSprite(emissiveSpriteLoc);
                    emissiveNames.put(spriteName, emissiveName);
                    found++;
                    LDOGMod.LOGGER.info("LDOG: Found emissive: {} -> {}", spriteName, emissivePngLoc);
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
            for (String basePath : new String[]{"optifine", "mcpatcher"}) {
                ResourceLocation propsLoc = new ResourceLocation("minecraft",
                    basePath + "/emissive.properties");
                try {
                    IResource resource = mc.getResourceManager().getResource(propsLoc);
                    Properties props = new Properties();
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

    @SuppressWarnings("unchecked")
    private static Map<String, TextureAtlasSprite> getRegisteredSprites(TextureMap map) {
        if (registeredSpritesField == null) {
            // Try both dev name and possible SRG names
            for (String fieldName : new String[]{
                "mapRegisteredSprites",  // dev name
                "field_110574_e",        // SRG name
            }) {
                try {
                    registeredSpritesField = TextureMap.class.getDeclaredField(fieldName);
                    registeredSpritesField.setAccessible(true);
                    LDOGMod.LOGGER.info("LDOG: Found sprites field as '{}'", fieldName);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }

            // Fallback: search all fields
            if (registeredSpritesField == null) {
                for (Field field : TextureMap.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        try {
                            field.setAccessible(true);
                            Map<?, ?> m = (Map<?, ?>) field.get(map);
                            if (m != null && m.size() > 10) {
                                // Likely the registered sprites map (has many entries)
                                Object firstVal = m.values().iterator().next();
                                if (firstVal instanceof TextureAtlasSprite) {
                                    registeredSpritesField = field;
                                    LDOGMod.LOGGER.info("LDOG: Found sprites field by search: '{}'",
                                        field.getName());
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (registeredSpritesField != null) {
            try {
                return (Map<String, TextureAtlasSprite>) registeredSpritesField.get(map);
            } catch (Exception e) {
                LDOGMod.LOGGER.warn("LDOG: Failed to read sprites field", e);
            }
        }
        return null;
    }
}
