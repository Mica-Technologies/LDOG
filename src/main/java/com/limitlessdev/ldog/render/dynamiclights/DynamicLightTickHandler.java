package com.limitlessdev.ldog.render.dynamiclights;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Drives the dynamic light update loop.
 *
 * Entity scanning (checking held items) always runs per client tick.
 * Position tracking and re-render triggering runs either per tick or
 * per render frame, depending on the update interval setting:
 *   - interval > 0: per-tick updates at the configured interval
 *   - interval = 0: "Smooth" mode — position updates every render frame
 *     for fluid light movement on fast PCs
 */
public class DynamicLightTickHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        DynamicLightManager manager = DynamicLightManager.getInstance();
        if (!LDOGConfig.enableDynamicLights || Minecraft.getMinecraft().world == null) {
            // Drop tracked sources once on logout (or when the feature is turned
            // off). Each source pins an Entity, and through it a whole dead World
            // graph, which would otherwise survive for the entire menu session.
            if (manager.getActiveLightCount() > 0) {
                manager.clear();
            }
            return;
        }

        manager.tickUpdate();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!LDOGConfig.enableDynamicLights) return;
        if (LDOGConfig.dynamicLightsUpdateInterval > 0) return; // not in smooth mode
        if (Minecraft.getMinecraft().world == null) return;

        DynamicLightManager.getInstance().frameUpdate();
    }
}
