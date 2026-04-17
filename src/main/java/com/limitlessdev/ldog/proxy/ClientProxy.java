package com.limitlessdev.ldog.proxy;

import com.limitlessdev.ldog.render.color.CustomColorHandler;
import com.limitlessdev.ldog.render.dynamiclights.DynamicLightTickHandler;
import com.limitlessdev.ldog.render.font.SmoothFontHandler;
import com.limitlessdev.ldog.render.font.TTFFontCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Create the user-font directory and register any .ttf/.otf already in it
        // with AWT's GraphicsEnvironment so the TTF rasterizer can pick them up
        // by family name just like built-in fonts.
        TTFFontCatalog.init(event.getModConfigurationDirectory());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new DynamicLightTickHandler());

        // Register custom color reload listener (after vanilla's colorizer listeners)
        IReloadableResourceManager resourceManager =
            (IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
        resourceManager.registerReloadListener(CustomColorHandler.INSTANCE);
        resourceManager.registerReloadListener(SmoothFontHandler.INSTANCE);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
