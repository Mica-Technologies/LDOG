package com.limitlessdev.ldog.render.ctm;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.mixin.AccessorTextureMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Discovers CTM definitions from resource packs (mcpatcher/ctm and optifine/ctm paths),
 * registers tile textures with the atlas, and wraps matching block models at bake time.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class CTMRegistry {

    private static final Map<Integer, CTMEntry> ctmByBlockId = new HashMap<>();

    private static class CTMEntry {
        final CTMProperties properties;
        final List<ResourceLocation> tileLocations;
        List<TextureAtlasSprite> tileSprites;

        CTMEntry(CTMProperties properties, List<ResourceLocation> tileLocations) {
            this.properties = properties;
            this.tileLocations = tileLocations;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableConnectedTextures) return;

        ctmByBlockId.clear();
        TextureMap map = event.getMap();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() == null) return;

        // Scan resource packs for CTM properties files
        scanResourcePacks(mc, map);

        LDOGMod.LOGGER.info("LDOG: Loaded {} CTM definitions", ctmByBlockId.size());
    }

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!LDOGConfig.enableConnectedTextures) return;

        TextureMap map = event.getMap();

        // Resolve tile locations to actual sprites
        for (CTMEntry entry : ctmByBlockId.values()) {
            entry.tileSprites = new ArrayList<>();
            for (ResourceLocation loc : entry.tileLocations) {
                TextureAtlasSprite sprite = map.getAtlasSprite(loc.toString());
                if (sprite != null && !"missingno".equals(sprite.getIconName())) {
                    entry.tileSprites.add(sprite);
                } else {
                    entry.tileSprites.add(null);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        if (!LDOGConfig.enableConnectedTextures) return;
        if (ctmByBlockId.isEmpty()) return;

        int wrapped = 0;

        for (Map.Entry<Integer, CTMEntry> entry : ctmByBlockId.entrySet()) {
            int blockId = entry.getKey();
            CTMEntry ctmEntry = entry.getValue();

            if (ctmEntry.tileSprites == null || ctmEntry.tileSprites.isEmpty()) continue;

            // Check if any sprites are non-null
            boolean hasSprites = false;
            for (TextureAtlasSprite s : ctmEntry.tileSprites) {
                if (s != null) { hasSprites = true; break; }
            }
            if (!hasSprites) continue;

            Block block = Block.getBlockById(blockId);
            if (block == null) continue;

            ResourceLocation blockRegName = block.getRegistryName();
            if (blockRegName == null) continue;

            // Wrap all model variants for this block
            for (Object key : new ArrayList<>(event.getModelRegistry().getKeys())) {
                if (key instanceof ModelResourceLocation) {
                    ModelResourceLocation mrl = (ModelResourceLocation) key;
                    if (mrl.getPath().equals(blockRegName.getPath()) &&
                        mrl.getNamespace().equals(blockRegName.getNamespace())) {
                        IBakedModel original = event.getModelRegistry().getObject(mrl);
                        if (original != null && !(original instanceof CTMBakedModel)) {
                            event.getModelRegistry().putObject(mrl,
                                new CTMBakedModel(original, block,
                                    ctmEntry.properties, ctmEntry.tileSprites));
                            wrapped++;
                        }
                    }
                }
            }
        }

        if (wrapped > 0) {
            LDOGMod.LOGGER.info("LDOG: Wrapped {} models with CTM", wrapped);
        }
    }

    private static void scanResourcePacks(Minecraft mc, TextureMap map) {
        // Scan the resourcepacks directory for CTM properties files
        File resourcePacksDir = new File(mc.gameDir, "resourcepacks");
        if (!resourcePacksDir.exists()) return;

        for (File packFile : resourcePacksDir.listFiles()) {
            if (packFile.isDirectory()) {
                scanDirectoryPack(packFile, map, mc);
            } else if (packFile.getName().endsWith(".zip")) {
                scanZipPack(packFile, map, mc);
            }
        }
    }

    private static void scanDirectoryPack(File packDir, TextureMap map, Minecraft mc) {
        for (String basePath : new String[]{"mcpatcher/ctm", "optifine/ctm"}) {
            File ctmDir = new File(packDir, "assets/minecraft/" + basePath);
            if (!ctmDir.exists() || !ctmDir.isDirectory()) continue;

            scanDirectoryRecursive(ctmDir, ctmDir, basePath, map, mc);
        }
    }

    private static void scanDirectoryRecursive(File root, File dir, String basePath,
                                                TextureMap map, Minecraft mc) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(root, file, basePath, map, mc);
            } else if (file.getName().endsWith(".properties")) {
                try {
                    // Compute the relative directory path for tile textures
                    String relDir = root.toPath().relativize(file.getParentFile().toPath())
                        .toString().replace('\\', '/');
                    String ctmSubPath = basePath + (relDir.isEmpty() ? "" : "/" + relDir);

                    FileInputStream fis = new FileInputStream(file);
                    loadCTMDefinition(fis, ctmSubPath, map, mc);
                    fis.close();
                } catch (Exception e) {
                    LDOGMod.LOGGER.debug("LDOG: Failed to load CTM properties: {}", file, e);
                }
            }
        }
    }

    private static void scanZipPack(File zipFile, TextureMap map, Minecraft mc) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith(".properties") &&
                    (name.contains("mcpatcher/ctm") || name.contains("optifine/ctm"))) {

                    String dirPath = name.substring(0, name.lastIndexOf('/'));
                    // Extract the ctm sub-path (everything after assets/minecraft/)
                    int assetsIdx = dirPath.indexOf("assets/minecraft/");
                    if (assetsIdx < 0) continue;
                    String ctmSubPath = dirPath.substring(assetsIdx + "assets/minecraft/".length());

                    try (InputStream is = zip.getInputStream(entry)) {
                        loadCTMDefinition(is, ctmSubPath, map, mc);
                    }
                }
            }
        } catch (Exception e) {
            LDOGMod.LOGGER.debug("LDOG: Failed to scan zip pack: {}", zipFile, e);
        }
    }

    private static void loadCTMDefinition(InputStream stream, String ctmSubPath,
                                           TextureMap map, Minecraft mc) {
        ResourceLocation source = new ResourceLocation("minecraft", ctmSubPath);
        CTMProperties props = CTMProperties.parse(source, stream);
        if (props == null) return;

        // Parse block IDs from matchBlocks
        for (String blockRef : props.getMatchBlocks()) {
            int blockId = -1;
            try {
                blockId = Integer.parseInt(blockRef.trim());
            } catch (NumberFormatException e) {
                // Try as registry name
                Block block = Block.getBlockFromName(blockRef.trim());
                if (block != null) {
                    blockId = Block.getIdFromBlock(block);
                }
            }

            if (blockId < 0) continue;

            // Register tile textures with the atlas
            List<Integer> tileIndices = props.parseTileIndices();
            List<ResourceLocation> tileLocations = new ArrayList<>();

            Map<String, TextureAtlasSprite> registeredSprites =
                ((AccessorTextureMap) map).ldog$getMapRegisteredSprites();

            for (int tileIdx : tileIndices) {
                // Sprite name: minecraft:ldog_ctm/<ctmSubPath>/<tileIdx>
                String spriteName = "ldog_ctm/" + ctmSubPath + "/" + tileIdx;
                ResourceLocation tileLoc = new ResourceLocation("minecraft", spriteName);

                // Actual PNG location in the resource pack
                // MCPatcher tiles are at: assets/minecraft/<ctmSubPath>/<tileIdx>.png
                // NOT at: assets/minecraft/textures/<ctmSubPath>/<tileIdx>.png
                ResourceLocation pngLoc = new ResourceLocation("minecraft",
                    ctmSubPath + "/" + tileIdx + ".png");

                if (resourceExists(mc, pngLoc)) {
                    // Create a custom sprite that knows where its PNG actually lives
                    CTMSprite sprite = new CTMSprite(tileLoc.toString(), pngLoc);
                    registeredSprites.put(tileLoc.toString(), sprite);
                    tileLocations.add(tileLoc);
                } else {
                    // Try the standard textures/ path as fallback
                    ResourceLocation stdPngLoc = new ResourceLocation("minecraft",
                        "textures/" + ctmSubPath + "/" + tileIdx + ".png");
                    if (resourceExists(mc, stdPngLoc)) {
                        map.registerSprite(tileLoc);
                        tileLocations.add(tileLoc);
                    }
                }
            }

            ctmByBlockId.put(blockId, new CTMEntry(props, tileLocations));
            LDOGMod.LOGGER.debug("LDOG: CTM definition for block ID {} ({}), {} tiles, method={}",
                blockId, Block.getBlockById(blockId), tileLocations.size(), props.getMethod());
        }
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
}
