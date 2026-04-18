package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-right corner info HUD: coords, facing, time-of-day, biome, light
 * levels. Each row is independently toggleable; the box only renders if
 * at least one is on. Hidden while vanilla F3 debug screen is open so we
 * don't fight for screen real estate.
 *
 * All data is read from the client snapshot — read-only, no game state
 * mutation. Cheap enough to run every frame.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class InfoOverlayRenderer {

    private static final int COLOR_LABEL = 0xAAAAAA;
    private static final int COLOR_VALUE = 0xFFFFFF;
    private static final int BG_COLOR    = 0x90000000;
    private static final int PADDING     = 4;

    private InfoOverlayRenderer() {}

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!anyEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) return;
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null) return;

        List<String> lines = buildLines(mc, player);
        if (lines.isEmpty()) return;

        FontRenderer font = mc.fontRenderer;
        int lineHeight = font.FONT_HEIGHT + 1;
        int boxWidth = 0;
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, font.getStringWidth(line));
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int x = screenW - boxWidth - PADDING * 2;
        int y = PADDING;

        // Background panel.
        Gui.drawRect(x - PADDING, y - PADDING,
                     x + boxWidth + PADDING, y + lines.size() * lineHeight + PADDING,
                     BG_COLOR);

        for (String line : lines) {
            font.drawStringWithShadow(line, x, y, COLOR_VALUE);
            y += lineHeight;
        }
    }

    private static boolean anyEnabled() {
        return LDOGConfig.showCoordsHud
            || LDOGConfig.showFacingHud
            || LDOGConfig.showTimeHud
            || LDOGConfig.showBiomeHud
            || LDOGConfig.showLightLevelHud;
    }

    private static List<String> buildLines(Minecraft mc, EntityPlayerSP player) {
        List<String> out = new ArrayList<>();
        BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);

        if (LDOGConfig.showCoordsHud) {
            out.add(label("XYZ") + String.format("%d %d %d",
                MathHelper.floor(player.posX),
                MathHelper.floor(player.posY),
                MathHelper.floor(player.posZ)));
        }

        if (LDOGConfig.showFacingHud) {
            EnumFacing facing = player.getHorizontalFacing();
            // Yaw → cardinal letter, plus pitch for up/down hint
            String cardinal = facing.getName().toUpperCase();
            float pitch = player.rotationPitch;
            String up = pitch > 30 ? " ▾" : pitch < -30 ? " ▴" : "";
            out.add(label("Facing") + cardinal + up);
        }

        if (LDOGConfig.showTimeHud) {
            // World time mod 24000; 0 = 06:00 in MC. Day starts at 0, dusk ~12000, midnight ~18000.
            long t = mc.world.getWorldTime() % 24000L;
            // MC tick 0 corresponds to 06:00; full day = 24000 ticks = 24 hours.
            double hours24 = ((t / 1000.0) + 6.0) % 24.0;
            int hours = (int) Math.floor(hours24);
            int minutes = (int) Math.floor((hours24 - hours) * 60.0);
            out.add(label("Time") + String.format("%02d:%02d", hours, minutes));
        }

        if (LDOGConfig.showBiomeHud) {
            String biomeName = mc.world.getBiome(pos).getBiomeName();
            out.add(label("Biome") + biomeName);
        }

        if (LDOGConfig.showLightLevelHud) {
            int sky = mc.world.getLightFor(EnumSkyBlock.SKY, pos);
            int block = mc.world.getLightFor(EnumSkyBlock.BLOCK, pos);
            out.add(label("Light") + "sky " + sky + " / block " + block);
        }

        return out;
    }

    private static String label(String name) {
        // Grey label "Foo: " followed by the value in normal colour.
        // Using §-codes inline so each column lines up visually.
        return "\u00A78" + name + ": \u00A7r";
    }
}
