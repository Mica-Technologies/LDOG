package com.limitlessdev.ldog.render.bettergrass;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Renders snow-textured side quads on any opaque block that has a snow
 * layer on top. Called from MixinBlockModelRenderer after the block's
 * base quads have been rendered.
 *
 * Snow quads are written directly into the block's rendering buffer
 * with flat lighting (sampled from the face's neighbor position) and a
 * small Z-offset to prevent z-fighting with the original side texture.
 *
 * Directional shading is applied to match vanilla's face brightness:
 * UP=1.0, NORTH/SOUTH=0.8, EAST/WEST=0.6, DOWN=0.5.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class BetterSnowHandler {

    private static final float Z_OFFSET = 0.002f;
    private static final String SNOW_SPRITE_NAME = "minecraft:blocks/snow";

    private static TextureAtlasSprite snowSprite;

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!LDOGConfig.enableBetterSnow) return;
        snowSprite = event.getMap().getAtlasSprite(SNOW_SPRITE_NAME);
    }

    /**
     * Add snow-textured side quads for a block that has snow on top.
     * Called at the end of BlockModelRenderer.renderModel().
     */
    public static void renderSnowSides(IBlockAccess world, IBlockState state,
                                         BlockPos pos, BufferBuilder buffer) {
        if (snowSprite == null) return;
        if (!state.isOpaqueCube()) return;

        // Grass and mycelium snow is handled by BetterGrassBakedModel (model wrapper)
        // which replaces the snowed side texture directly, getting proper AO lighting
        Block block = state.getBlock();
        if (block == Blocks.GRASS || block == Blocks.MYCELIUM) return;

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
