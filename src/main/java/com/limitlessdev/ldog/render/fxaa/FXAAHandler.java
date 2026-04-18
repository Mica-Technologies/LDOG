package com.limitlessdev.ldog.render.fxaa;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.mixin.AccessorEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Loads/unloads vanilla MC's shipped FXAA shader pass
 * (assets/minecraft/shaders/post/fxaa.json) via EntityRenderer's
 * ShaderGroup system.
 *
 * Applied on first client tick (so EntityRenderer is fully initialized)
 * and again whenever the settings GUI toggles the FXAA config.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public final class FXAAHandler {

    private static boolean initialApplyDone = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (initialApplyDone || event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().entityRenderer == null) return;
        initialApplyDone = true;
        apply();
    }

    private static final ResourceLocation FXAA_SHADER =
        new ResourceLocation("shaders/post/fxaa.json");

    private static boolean currentlyLoaded = false;

    private FXAAHandler() {}

    /**
     * Reconciles EntityRenderer's shader state with LDOGConfig.enableFXAA.
     * Safe to call repeatedly — only loads/unloads when state actually changes.
     *
     * Yields to LDOG's pipeline FXAA (with quality levels) when the pipeline
     * is on: unloads MC's fixed-quality shader in that case so we don't
     * double-FXAA the frame. When the pipeline is off, MC's shader runs as
     * before — users without the pipeline still get FXAA.
     */
    public static void apply() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.entityRenderer == null) return;

        boolean pipelineHandlesFxaa = LDOGConfig.enablePostProcessPipeline;
        boolean shouldBeLoaded = LDOGConfig.enableFXAA && !pipelineHandlesFxaa;
        if (shouldBeLoaded == currentlyLoaded) return;

        if (shouldBeLoaded) {
            try {
                mc.entityRenderer.loadShader(FXAA_SHADER);
                currentlyLoaded = true;
                LDOGMod.LOGGER.info("LDOG: FXAA shader loaded");
            } catch (Exception e) {
                LDOGMod.LOGGER.error("LDOG: Failed to load FXAA shader", e);
                currentlyLoaded = false;
            }
        } else {
            unload(mc.entityRenderer);
            currentlyLoaded = false;
            LDOGMod.LOGGER.info("LDOG: FXAA shader unloaded");
        }
    }

    private static void unload(EntityRenderer renderer) {
        AccessorEntityRenderer accessor = (AccessorEntityRenderer) renderer;
        ShaderGroup current = accessor.ldog$getShaderGroup();
        if (current != null) {
            current.deleteShaderGroup();
        }
        accessor.ldog$setShaderGroup(null);
        accessor.ldog$setUseShader(false);
    }
}
