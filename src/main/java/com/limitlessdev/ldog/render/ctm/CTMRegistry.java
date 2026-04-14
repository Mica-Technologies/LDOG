package com.limitlessdev.ldog.render.ctm;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Discovers and registers CTM definitions from resource packs.
 * Scans for OptiFine-format .properties files in assets/minecraft/optifine/ctm/
 * and wraps matching block models with CTM-aware wrappers at bake time.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class CTMRegistry {

    // Map of block registry name -> CTM properties
    private static final Map<String, CTMProperties> ctmDefinitions = new HashMap<>();
    // Map of block registry name -> list of CTM tile sprites
    private static final Map<String, List<TextureAtlasSprite>> ctmSprites = new HashMap<>();

    /**
     * During texture stitching, scan for CTM properties files and register
     * the CTM tile textures with the atlas.
     */
    @SubscribeEvent
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableConnectedTextures) return;

        ctmDefinitions.clear();
        ctmSprites.clear();

        TextureMap map = event.getMap();
        Minecraft mc = Minecraft.getMinecraft();

        // Scan for OptiFine CTM properties files
        // Convention: assets/minecraft/optifine/ctm/<name>/<name>.properties
        // We also check assets/minecraft/ldog/ctm/ for our own format
        String[] searchPaths = {
            "optifine/ctm",
            "ldog/ctm"
        };

        for (String basePath : searchPaths) {
            scanCTMDirectory(mc, map, "minecraft", basePath);
        }

        LDOGMod.LOGGER.info("LDOG: Loaded {} CTM definitions", ctmDefinitions.size());
    }

    private static void scanCTMDirectory(Minecraft mc, TextureMap map,
                                          String domain, String basePath) {
        // Resource packs expose files; we try common block names
        // In practice, CTM properties are discovered by iterating resource pack contents
        // For now, we register sprites for any definitions found via properties files
        // that are placed in the expected OptiFine locations
    }

    /**
     * After models are baked, wrap CTM-eligible models with our CTM wrapper.
     */
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        if (!LDOGConfig.enableConnectedTextures) return;
        if (ctmDefinitions.isEmpty()) return;

        int wrapped = 0;
        for (Map.Entry<String, CTMProperties> entry : ctmDefinitions.entrySet()) {
            String blockName = entry.getKey();
            CTMProperties props = entry.getValue();

            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
            if (block == null) continue;

            // Wrap all model variants for this block
            for (Object key : event.getModelRegistry().getKeys()) {
                if (key instanceof ModelResourceLocation) {
                    ModelResourceLocation mrl = (ModelResourceLocation) key;
                    if (mrl.getNamespace().equals("minecraft") || blockName.contains(mrl.getPath())) {
                        IBakedModel original = event.getModelRegistry().getObject(mrl);
                        if (original != null) {
                            List<TextureAtlasSprite> sprites = ctmSprites.get(blockName);
                            if (sprites != null && !sprites.isEmpty()) {
                                event.getModelRegistry().putObject(mrl,
                                    new CTMBakedModel(original, block, props, sprites));
                                wrapped++;
                            }
                        }
                    }
                }
            }
        }

        if (wrapped > 0) {
            LDOGMod.LOGGER.info("LDOG: Wrapped {} models with CTM", wrapped);
        }
    }

    /**
     * Register a CTM definition programmatically (for testing or API use).
     */
    public static void registerCTM(String blockName, CTMProperties props,
                                    List<TextureAtlasSprite> sprites) {
        ctmDefinitions.put(blockName, props);
        ctmSprites.put(blockName, sprites);
    }

    public static Map<String, CTMProperties> getDefinitions() {
        return Collections.unmodifiableMap(ctmDefinitions);
    }
}
