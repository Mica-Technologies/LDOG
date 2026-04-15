package com.limitlessdev.ldog.render.emissive;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers emissive texture overlays by scanning resource packs directly
 * for files ending with the emissive suffix (default "_e.png").
 *
 * Scanning happens at TextureStitchEvent.Pre so emissive sprites get
 * registered with the atlas before stitching.
 *
 * At ModelBakeEvent, identifies which blocks use emissive textures so
 * they can be rendered in the CUTOUT_MIPPED layer (for alpha testing).
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class EmissiveTextureRegistry {

    public static String emissiveSuffix = "_e";

    private static final Map<String, TextureAtlasSprite> emissiveSprites = new HashMap<>();
    private static final Map<String, String> emissiveNames = new HashMap<>();
    private static final Set<Block> emissiveBlocks = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableEmissiveTextures) return;

        emissiveSprites.clear();
        emissiveNames.clear();
        emissiveBlocks.clear();
        loadEmissiveProperties();

        TextureMap map = event.getMap();
        Minecraft mc = Minecraft.getMinecraft();

        // Scan resource packs directly for _e.png files
        // (can't enumerate registered sprites -- map is cleared before this event)
        int found = scanResourcePacksForEmissives(mc, map);

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

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        if (!LDOGConfig.enableEmissiveTextures) return;
        if (emissiveSprites.isEmpty()) return;

        emissiveBlocks.clear();

        // Check each block's model to see if its textures have emissive overlays
        for (Block block : Block.REGISTRY) {
            ResourceLocation regName = block.getRegistryName();
            if (regName == null) continue;

            // Check the default model variant
            for (Object key : event.getModelRegistry().getKeys()) {
                if (key instanceof ModelResourceLocation) {
                    ModelResourceLocation mrl = (ModelResourceLocation) key;
                    if (mrl.getPath().equals(regName.getPath()) &&
                        mrl.getNamespace().equals(regName.getNamespace())) {
                        IBakedModel model = event.getModelRegistry().getObject(mrl);
                        if (model != null && modelHasEmissive(model, block)) {
                            emissiveBlocks.add(block);
                            break;
                        }
                    }
                }
            }
        }

        if (!emissiveBlocks.isEmpty()) {
            LDOGMod.LOGGER.info("LDOG: {} blocks have emissive textures", emissiveBlocks.size());
        }
    }

    private static boolean modelHasEmissive(IBakedModel model, Block block) {
        try {
            for (EnumFacing face : EnumFacing.values()) {
                for (BakedQuad quad : model.getQuads(block.getDefaultState(), face, 0L)) {
                    if (emissiveSprites.containsKey(quad.getSprite().getIconName())) {
                        return true;
                    }
                }
            }
            for (BakedQuad quad : model.getQuads(block.getDefaultState(), null, 0L)) {
                if (emissiveSprites.containsKey(quad.getSprite().getIconName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Some models may throw when getQuads is called outside rendering context
        }
        return false;
    }

    public static TextureAtlasSprite getEmissiveSprite(TextureAtlasSprite baseSprite) {
        if (baseSprite == null) return null;
        return emissiveSprites.get(baseSprite.getIconName());
    }

    public static int getEmissiveSpriteCount() {
        return emissiveSprites.size();
    }

    public static boolean isEmissiveBlock(Block block) {
        return emissiveBlocks.contains(block);
    }

    /**
     * Scan resource packs for texture files ending with the emissive suffix.
     * For each "textures/blocks/foo_e.png" found, register "blocks/foo_e" as a sprite
     * and map "blocks/foo" -> "blocks/foo_e".
     */
    private static int scanResourcePacksForEmissives(Minecraft mc, TextureMap map) {
        File resourcePacksDir = new File(mc.gameDir, "resourcepacks");
        if (!resourcePacksDir.exists()) return 0;

        int found = 0;
        File[] packs = resourcePacksDir.listFiles();
        if (packs == null) return 0;

        for (File packFile : packs) {
            if (packFile.isDirectory()) {
                found += scanDirectoryForEmissives(packFile, map);
            } else if (packFile.getName().endsWith(".zip")) {
                found += scanZipForEmissives(packFile, map);
            }
        }
        return found;
    }

    private static int scanDirectoryForEmissives(File packDir, TextureMap map) {
        // Look for textures/blocks/*_e.png and textures/items/*_e.png
        int found = 0;
        for (String subDir : new String[]{"textures/blocks", "textures/items"}) {
            File texDir = new File(packDir, "assets/minecraft/" + subDir);
            if (!texDir.exists() || !texDir.isDirectory()) continue;

            File[] files = texDir.listFiles();
            if (files == null) continue;

            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(emissiveSuffix + ".png")) {
                    String fileName = f.getName();
                    // e.g., "diamond_ore_e.png" -> sprite name "blocks/diamond_ore_e"
                    String spritePath = subDir.substring("textures/".length()) + "/"
                        + fileName.substring(0, fileName.length() - 4); // remove .png
                    String basePath = spritePath.substring(0, spritePath.length() - emissiveSuffix.length());

                    registerEmissive(map, basePath, spritePath);
                    found++;
                }
            }
        }
        return found;
    }

    private static int scanZipForEmissives(File zipFile, TextureMap map) {
        int found = 0;
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // Look for assets/minecraft/textures/blocks/*_e.png
                if (name.endsWith(emissiveSuffix + ".png") &&
                    name.startsWith("assets/minecraft/textures/") &&
                    (name.contains("/blocks/") || name.contains("/items/"))) {

                    // Extract sprite path from full zip path
                    // "assets/minecraft/textures/blocks/diamond_ore_e.png"
                    // -> "blocks/diamond_ore_e"
                    String afterTextures = name.substring("assets/minecraft/textures/".length());
                    String spritePath = afterTextures.substring(0, afterTextures.length() - 4); // remove .png
                    String basePath = spritePath.substring(0, spritePath.length() - emissiveSuffix.length());

                    registerEmissive(map, basePath, spritePath);
                    found++;
                }
            }
        } catch (Exception e) {
            LDOGMod.LOGGER.debug("LDOG: Failed to scan zip for emissives: {}", zipFile, e);
        }
        return found;
    }

    private static void registerEmissive(TextureMap map, String basePath, String emissivePath) {
        // basePath = "blocks/diamond_ore"
        // emissivePath = "blocks/diamond_ore_e"

        // Only register if the file exists in an *active* resource pack.
        // We scan all packs on disk (including inactive ones) to discover names,
        // so we must guard here to avoid atlas-stitch failures for inactive packs.
        ResourceLocation pngLoc = new ResourceLocation("minecraft", "textures/" + emissivePath + ".png");
        try {
            Minecraft.getMinecraft().getResourceManager().getResource(pngLoc).close();
        } catch (Exception e) {
            return; // texture not available in any active resource pack
        }

        String baseName = "minecraft:" + basePath;
        String emissiveName = "minecraft:" + emissivePath;

        map.registerSprite(new ResourceLocation("minecraft", emissivePath));
        emissiveNames.put(baseName, emissiveName);

        LDOGMod.LOGGER.info("LDOG: Found emissive: {} -> {}", baseName, emissiveName);
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
}
