package com.limitlessdev.ldog.render.natural;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Registers Natural Texture model wrappers at ModelBakeEvent.
 *
 * Reads optifine/natural.properties (or mcpatcher/natural.properties) from
 * the active resource pack. If no properties file is found, uses a default
 * set of common blocks (stone, dirt, sand, gravel, etc.).
 *
 * Each listed block's model is wrapped with NaturalTextureBakedModel, which
 * applies position-based UV rotation/flip for visual variety.
 *
 * Uses LOW priority so other model wrappers (CTM, Better Grass) run first.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class NaturalTextureHandler {

    /** Default blocks to apply natural textures when no properties file exists. */
    private static final String[][] DEFAULTS = {
        {"stone", "2"},
        {"dirt", "2"},
        {"sand", "2"},
        {"red_sand", "2"},
        {"gravel", "2"},
        {"cobblestone", "2"},
        {"mossy_cobblestone", "2"},
        {"netherrack", "2"},
        {"end_stone", "2"},
        {"sandstone", "1"},  // only rotate (top has directional pattern)
        {"red_sandstone", "1"},
        {"clay", "2"},
        {"hardened_clay", "2"},
        {"mycelium", "1"},
        {"soul_sand", "2"},
        {"obsidian", "2"},
    };

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onModelBake(ModelBakeEvent event) {
        if (!LDOGConfig.enableNaturalTextures) return;

        Map<String, NaturalTextureMode> blockModes = loadNaturalProperties();

        int wrapped = 0;
        for (Map.Entry<String, NaturalTextureMode> entry : blockModes.entrySet()) {
            String blockName = entry.getKey();
            NaturalTextureMode mode = entry.getValue();

            // Resolve block name to Block instance
            Block block = Block.getBlockFromName(blockName);
            if (block == null) {
                block = Block.getBlockFromName("minecraft:" + blockName);
            }
            if (block == null) continue;

            wrapped += wrapBlockModels(event, block, mode);
        }

        if (wrapped > 0) {
            LDOGMod.LOGGER.info("LDOG: Natural Textures wrapped {} models ({} blocks configured)",
                wrapped, blockModes.size());
        }
    }

    private static Map<String, NaturalTextureMode> loadNaturalProperties() {
        Map<String, NaturalTextureMode> result = new HashMap<>();

        // Try reading from active resource pack
        boolean loaded = false;
        for (String basePath : new String[]{"optifine", "mcpatcher"}) {
            ResourceLocation loc = new ResourceLocation("minecraft",
                basePath + "/natural.properties");
            try {
                IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(loc);
                Properties props = new Properties();
                props.load(resource.getInputStream());
                resource.close();

                for (Map.Entry<Object, Object> entry : props.entrySet()) {
                    String blockName = entry.getKey().toString().trim();
                    if (blockName.startsWith("#") || blockName.isEmpty()) continue;
                    try {
                        int modeId = Integer.parseInt(entry.getValue().toString().trim());
                        result.put(blockName, NaturalTextureMode.fromId(modeId));
                    } catch (NumberFormatException ignored) {}
                }

                LDOGMod.LOGGER.info("LDOG: Loaded natural.properties from {} ({} entries)",
                    basePath, result.size());
                loaded = true;
                break;
            } catch (IOException ignored) {
                // File not found in this path, try next
            }
        }

        // Use defaults if no properties file found
        if (!loaded) {
            for (String[] def : DEFAULTS) {
                result.put(def[0], NaturalTextureMode.fromId(Integer.parseInt(def[1])));
            }
            LDOGMod.LOGGER.info("LDOG: Using default natural texture config ({} blocks)", result.size());
        }

        return result;
    }

    private static int wrapBlockModels(ModelBakeEvent event, Block block, NaturalTextureMode mode) {
        ResourceLocation regName = block.getRegistryName();
        if (regName == null) return 0;

        int count = 0;
        for (Object key : event.getModelRegistry().getKeys()) {
            if (!(key instanceof ModelResourceLocation)) continue;

            ModelResourceLocation mrl = (ModelResourceLocation) key;
            if (!mrl.getPath().equals(regName.getPath()) ||
                !mrl.getNamespace().equals(regName.getNamespace())) continue;

            // Skip inventory models
            if ("inventory".equals(mrl.getVariant())) continue;

            IBakedModel original = event.getModelRegistry().getObject(mrl);
            if (original != null) {
                event.getModelRegistry().putObject(mrl,
                    new NaturalTextureBakedModel(original, mode));
                count++;
            }
        }
        return count;
    }
}
