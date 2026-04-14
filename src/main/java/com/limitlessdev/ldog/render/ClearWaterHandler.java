package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.block.material.Material;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Reduces underwater fog density when clear water is enabled,
 * so you can see further underwater. Uses Forge's FogDensity event.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class ClearWaterHandler {

    @SubscribeEvent
    public static void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (!LDOGConfig.enableClearWater) return;

        if (event.getState().getMaterial() == Material.WATER) {
            // Vanilla water fog density is 0.1F (very murky)
            // Scale it down based on our waterOpacity setting
            // At waterOpacity=0.0 -> density 0.005 (very clear)
            // At waterOpacity=1.0 -> density 0.1 (vanilla)
            float density = 0.005F + (float) LDOGConfig.waterOpacity * 0.095F;
            event.setDensity(density);
            event.setCanceled(true);
        }
    }
}
