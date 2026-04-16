package com.limitlessdev.ldog.render.bettergrass;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Better Snow: wraps common terrain blocks with BetterSnowBakedModel at
 * ModelBakeEvent. The wrapper replaces side textures with snow in getQuads()
 * when a snow layer sits on top, so vanilla's AO pipeline handles lighting.
 *
 * Only terrain blocks (dirt, stone, sand, gravel, etc.) are wrapped — crafted
 * and functional blocks (bookshelves, furnaces, etc.) are not affected.
 * Grass and mycelium are handled separately by BetterGrassBakedModel.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class BetterSnowHandler {

    private static final String SNOW_SPRITE_NAME = "minecraft:blocks/snow";

    private static TextureAtlasSprite snowSprite;

    /** Common blocks that appear under snow and should use the model wrapper. */
    private static final Block[] SNOW_WRAP_BLOCKS = {
        Blocks.DIRT, Blocks.STONE, Blocks.SAND, Blocks.GRAVEL,
        Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
        Blocks.SANDSTONE, Blocks.RED_SANDSTONE,
        Blocks.PLANKS, Blocks.LOG, Blocks.LOG2,
        Blocks.STONEBRICK, Blocks.BRICK_BLOCK,
        Blocks.HARDENED_CLAY, Blocks.STAINED_HARDENED_CLAY,
        Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.END_STONE,
        Blocks.CLAY, Blocks.OBSIDIAN, Blocks.PACKED_ICE,
    };

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!LDOGConfig.enableBetterSnow) return;
        snowSprite = event.getMap().getAtlasSprite(SNOW_SPRITE_NAME);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onModelBake(ModelBakeEvent event) {
        if (!LDOGConfig.enableBetterSnow) return;
        if (snowSprite == null) return;

        int wrapped = 0;

        for (Block block : SNOW_WRAP_BLOCKS) {
            wrapped += wrapBlockModels(event, block);
        }

        if (wrapped > 0) {
            LDOGMod.LOGGER.info("LDOG: Better Snow wrapped {} models", wrapped);
        }
    }

    private static int wrapBlockModels(ModelBakeEvent event, Block block) {
        ResourceLocation regName = block.getRegistryName();
        if (regName == null) return 0;

        int count = 0;
        for (Object key : event.getModelRegistry().getKeys()) {
            if (!(key instanceof ModelResourceLocation)) continue;
            ModelResourceLocation mrl = (ModelResourceLocation) key;
            if (!mrl.getPath().equals(regName.getPath()) ||
                !mrl.getNamespace().equals(regName.getNamespace())) continue;
            if ("inventory".equals(mrl.getVariant())) continue;

            IBakedModel original = event.getModelRegistry().getObject(mrl);
            if (original != null) {
                event.getModelRegistry().putObject(mrl,
                    new BetterSnowBakedModel(original, snowSprite));
                count++;
            }
        }
        return count;
    }
}
