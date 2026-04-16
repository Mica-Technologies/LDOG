package com.limitlessdev.ldog.render.dynamiclights;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Drives the dynamic light update loop once per client tick.
 * Register on MinecraftForge.EVENT_BUS in ClientProxy.
 */
public class DynamicLightTickHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!LDOGConfig.enableDynamicLights) return;
        if (Minecraft.getMinecraft().world == null) return;

        DynamicLightManager.getInstance().update();
    }
}
