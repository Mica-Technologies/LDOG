package com.limitlessdev.ldog.render.bettergrass;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Registers Better Grass model wrappers at ModelBakeEvent.
 *
 * Wraps grass and mycelium block models with BetterGrassBakedModel, which
 * replaces side textures with the biome-tinted top texture for a seamless
 * grassy appearance.
 *
 * Sprite references are captured after texture stitching (Post event) so
 * they point to valid atlas entries.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class BetterGrassHandler {

    private static final String GRASS_TOP = "minecraft:blocks/grass_top";
    private static final String GRASS_SIDE = "minecraft:blocks/grass_side";
    private static final String GRASS_SIDE_OVERLAY = "minecraft:blocks/grass_side_overlay";
    private static final String MYCELIUM_TOP = "minecraft:blocks/mycelium_top";
    private static final String MYCELIUM_SIDE = "minecraft:blocks/mycelium_side";

    private static TextureAtlasSprite grassTopSprite;
    private static TextureAtlasSprite myceliumTopSprite;

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if ("off".equalsIgnoreCase(LDOGConfig.betterGrass)) return;

        TextureMap map = event.getMap();
        grassTopSprite = map.getAtlasSprite(GRASS_TOP);
        myceliumTopSprite = map.getAtlasSprite(MYCELIUM_TOP);
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        if ("off".equalsIgnoreCase(LDOGConfig.betterGrass)) return;
        if (grassTopSprite == null) return;

        int wrapped = 0;
        wrapped += wrapBlockModels(event, Blocks.GRASS, grassTopSprite,
            GRASS_SIDE, GRASS_SIDE_OVERLAY);
        wrapped += wrapBlockModels(event, Blocks.MYCELIUM, myceliumTopSprite,
            MYCELIUM_SIDE, null);

        if (wrapped > 0) {
            LDOGMod.LOGGER.info("LDOG: Better Grass wrapped {} models (mode={})",
                wrapped, LDOGConfig.betterGrass);
        }
    }

    /**
     * Find all model variants for a block and wrap them with BetterGrassBakedModel.
     *
     * @return number of models wrapped
     */
    private static int wrapBlockModels(ModelBakeEvent event, Block block,
                                        TextureAtlasSprite topSprite,
                                        String sideSpriteName, String overlaySpriteName) {
        ResourceLocation regName = block.getRegistryName();
        if (regName == null) return 0;

        int count = 0;
        for (Object key : event.getModelRegistry().getKeys()) {
            if (!(key instanceof ModelResourceLocation)) continue;

            ModelResourceLocation mrl = (ModelResourceLocation) key;
            if (!mrl.getPath().equals(regName.getPath()) ||
                !mrl.getNamespace().equals(regName.getNamespace())) continue;

            IBakedModel original = event.getModelRegistry().getObject(mrl);
            if (original != null) {
                event.getModelRegistry().putObject(mrl,
                    new BetterGrassBakedModel(original, topSprite,
                        sideSpriteName, overlaySpriteName));
                count++;
            }
        }
        return count;
    }
}
