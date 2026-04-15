package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.material.Material;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Handles water rendering enhancements:
 * - Reduces underwater fog density (clear water)
 * - Tints underwater fog color to match water tint settings
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class ClearWaterHandler {

    @SubscribeEvent
    public static void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (!LDOGConfig.enableClearWater) return;

        if (event.getState().getMaterial() == Material.WATER) {
            // Quadratic curve: keeps opacity=1.0 calibrated to vanilla (~0.1),
            // but ramps steeply at higher values for genuinely murky water.
            double o = LDOGConfig.waterOpacity;
            float density = 0.005F + (float)(o * o) * 0.095F;
            event.setDensity(density);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFogColor(EntityViewRenderEvent.FogColors event) {
        if (!LDOGConfig.enableWaterTint) return;

        if (event.getState().getMaterial() == Material.WATER) {
            event.setRed(event.getRed() * (float) LDOGConfig.waterTintRed);
            event.setGreen(event.getGreen() * (float) LDOGConfig.waterTintGreen);
            event.setBlue(event.getBlue() * (float) LDOGConfig.waterTintBlue);
        }
    }
}
