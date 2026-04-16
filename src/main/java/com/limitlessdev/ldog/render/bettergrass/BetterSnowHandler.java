package com.limitlessdev.ldog.render.bettergrass;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashSet;
import java.util.Set;

/**
 * Better Snow handler with two rendering paths:
 *
 * 1. MODEL WRAPPER (preferred): Common blocks (dirt, stone, sand, etc.) are
 *    wrapped with BetterSnowBakedModel at ModelBakeEvent. The wrapper replaces
 *    side textures with snow in getQuads(), so vanilla's AO pipeline handles
 *    lighting correctly.
 *
 * 2. OVERLAY FALLBACK: Blocks not wrapped by the model wrapper get snow overlay
 *    quads added at render time (flat lighting + Z-offset). This covers modded
 *    blocks and uncommon vanilla blocks.
 *
 * Grass and mycelium are handled by BetterGrassBakedModel instead.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class BetterSnowHandler {

    private static final float Z_OFFSET = 0.002f;
    private static final String SNOW_SPRITE_NAME = "minecraft:blocks/snow";

    private static TextureAtlasSprite snowSprite;

    /** Blocks wrapped with BetterSnowBakedModel — skip these in the overlay path. */
    private static final Set<Block> wrappedBlocks = new HashSet<>();

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

        wrappedBlocks.clear();
        int wrapped = 0;

        for (Block block : SNOW_WRAP_BLOCKS) {
            int count = wrapBlockModels(event, block);
            if (count > 0) {
                wrappedBlocks.add(block);
                wrapped += count;
            }
        }

        if (wrapped > 0) {
            LDOGMod.LOGGER.info("LDOG: Better Snow wrapped {} models ({} block types)",
                wrapped, wrappedBlocks.size());
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

    /**
     * Add snow-textured side quads for a block that has snow on top.
     * Called at the end of BlockModelRenderer.renderModel().
     */
    public static void renderSnowSides(IBlockAccess world, IBlockState state,
                                         BlockPos pos, BufferBuilder buffer) {
        if (snowSprite == null) return;
        if (!state.isOpaqueCube()) return;

        // Grass/mycelium handled by BetterGrassBakedModel, common blocks by BetterSnowBakedModel
        Block block = state.getBlock();
        if (block == Blocks.GRASS || block == Blocks.MYCELIUM) return;
        if (wrappedBlocks.contains(block)) return;

        IBlockState above = world.getBlockState(pos.up());
        Block aboveBlock = above.getBlock();
        if (aboveBlock != Blocks.SNOW_LAYER && aboveBlock != Blocks.SNOW) return;

        float minU = snowSprite.getMinU();
        float maxU = snowSprite.getMaxU();
        float minV = snowSprite.getMinV();
        float maxV = snowSprite.getMaxV();

        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            if (!state.shouldSideBeRendered(world, pos, face)) continue;

            // Flat lighting: sample light at the face's neighbor position
            int packed = state.getPackedLightmapCoords(world, pos.offset(face));
            int blockCoord = packed & 0xFFFF;
            int skyCoord = (packed >> 16) & 0xFFFF;

            // Directional shade to match vanilla face brightness
            int shade = getFaceShade(face);

            float px = pos.getX();
            float py = pos.getY();
            float pz = pos.getZ();
            float nx = face.getXOffset() * Z_OFFSET;
            float nz = face.getZOffset() * Z_OFFSET;

            // Full block side face with Z-offset along face normal.
            // Vertex winding is CCW when viewed from outside (matches vanilla).
            switch (face) {
                case NORTH:
                    vertex(buffer, px + 1 + nx, py + 1, pz + nz, minU, minV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + 1 + nx, py,     pz + nz, minU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px     + nx, py,     pz + nz, maxU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px     + nx, py + 1, pz + nz, maxU, minV, shade, blockCoord, skyCoord);
                    break;
                case SOUTH:
                    vertex(buffer, px     + nx, py + 1, pz + 1 + nz, minU, minV, shade, blockCoord, skyCoord);
                    vertex(buffer, px     + nx, py,     pz + 1 + nz, minU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + 1 + nx, py,     pz + 1 + nz, maxU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + 1 + nx, py + 1, pz + 1 + nz, maxU, minV, shade, blockCoord, skyCoord);
                    break;
                case EAST:
                    vertex(buffer, px + 1 + nx, py + 1, pz + 1, minU, minV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + 1 + nx, py,     pz + 1, minU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + 1 + nx, py,     pz,     maxU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + 1 + nx, py + 1, pz,     maxU, minV, shade, blockCoord, skyCoord);
                    break;
                case WEST:
                    vertex(buffer, px + nx, py + 1, pz,     minU, minV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + nx, py,     pz,     minU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + nx, py,     pz + 1, maxU, maxV, shade, blockCoord, skyCoord);
                    vertex(buffer, px + nx, py + 1, pz + 1, maxU, minV, shade, blockCoord, skyCoord);
                    break;
                default:
                    break;
            }
        }
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z,
                                float u, float v, int shade,
                                int blockCoord, int skyCoord) {
        buffer.pos(x, y, z)
              .color(shade, shade, shade, 255)
              .tex(u, v)
              .lightmap(blockCoord, skyCoord)
              .endVertex();
    }

    /** Vanilla directional face shading as 0-255 color values. */
    private static int getFaceShade(EnumFacing face) {
        switch (face) {
            case UP:    return 255;  // 1.0
            case DOWN:  return 128;  // 0.5
            case NORTH:
            case SOUTH: return 204;  // 0.8
            case EAST:
            case WEST:  return 153;  // 0.6
            default:    return 255;
        }
    }
}
