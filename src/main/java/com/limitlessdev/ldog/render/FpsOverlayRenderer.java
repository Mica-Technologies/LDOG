package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Renders a small FPS indicator in the corner of the screen when
 * FPS is being reduced (unfocused window or AFK).
 * Similar to FPS Reducer mod's on-screen indicator.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class FpsOverlayRenderer {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!LDOGConfig.enableFpsReducer) return;

        int targetFps = FpsReducerHandler.getTargetFpsLimit();
        if (targetFps <= 0) return;

        // Don't show overlay when debug screen (F3) is visible
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) return;

        FontRenderer font = mc.fontRenderer;
        ScaledResolution res = new ScaledResolution(mc);

        String reason;
        int color;
        if (!org.lwjgl.opengl.Display.isActive()) {
            reason = "Unfocused";
            color = 0xFFAAAA; // light red
        } else {
            reason = "AFK";
            color = 0xFFFF88; // light yellow
        }

        String text = "LDOG: " + targetFps + " FPS (" + reason + ")";
        int textWidth = font.getStringWidth(text);

        // Draw in top-right corner with a semi-transparent background
        int x = res.getScaledWidth() - textWidth - 4;
        int y = 4;

        // Background
        net.minecraft.client.gui.Gui.drawRect(x - 2, y - 1, x + textWidth + 2, y + font.FONT_HEIGHT + 1, 0x80000000);
        // Text
        font.drawStringWithShadow(text, x, y, color);
    }
}
