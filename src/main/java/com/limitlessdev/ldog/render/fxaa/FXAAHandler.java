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

    /**
     * The exact {@link ShaderGroup} instance FXAAHandler itself installed via
     * {@link EntityRenderer#loadShader(ResourceLocation)}. Compared by
     * identity in {@link #unload} so we only ever tear down our own shader —
     * see that method for why.
     */
    private static ShaderGroup fxaaShaderGroup = null;

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

        // The accessor mixin can fail to apply (another coremod replacing
        // EntityRenderer, a refmap mismatch in a broken build). A raw cast then
        // throws ClassCastException on every tick. Degrade to "FXAA unmanaged"
        // with one warning instead.
        if (!(mc.entityRenderer instanceof AccessorEntityRenderer)) {
            if (!warnedNoAccessor) {
                warnedNoAccessor = true;
                LDOGMod.LOGGER.warn("LDOG: AccessorEntityRenderer mixin not applied; FXAA shader cannot be managed");
            }
            return;
        }
        AccessorEntityRenderer accessor = (AccessorEntityRenderer) mc.entityRenderer;

        boolean pipelineHandlesFxaa = LDOGConfig.enablePostProcessPipeline;
        boolean shouldBeLoaded = LDOGConfig.enableFXAA && !pipelineHandlesFxaa;
        if (shouldBeLoaded == currentlyLoaded) return;

        if (shouldBeLoaded) {
            boolean ok = false;
            try {
                mc.entityRenderer.loadShader(FXAA_SHADER);
                // Vanilla's loadShader swallows IOException / JsonSyntaxException
                // and just clears useShader, so "no exception" is not "loaded".
                ok = accessor.ldog$getUseShader() && accessor.ldog$getShaderGroup() != null;
            } catch (Exception e) {
                LDOGMod.LOGGER.error("LDOG: Failed to load FXAA shader", e);
            }
            if (ok) {
                fxaaShaderGroup = accessor.ldog$getShaderGroup();
                currentlyLoaded = true;
                LDOGMod.LOGGER.info("LDOG: FXAA shader loaded");
            } else {
                // loadShader deleted whatever group was there before it started,
                // so anything left in the slot now is the half-built FXAA group
                // (framebuffers allocated, program failed). Release it rather
                // than leaking GL objects on every retry.
                ShaderGroup partial = accessor.ldog$getShaderGroup();
                if (partial != null) {
                    try { partial.deleteShaderGroup(); } catch (Exception ignored) { }
                    accessor.ldog$setShaderGroup(null);
                }
                accessor.ldog$setUseShader(false);
                currentlyLoaded = false;
                fxaaShaderGroup = null;
                LDOGMod.LOGGER.warn("LDOG: FXAA shader did not load; MC's post shader slot cleared");
            }
        } else {
            unload(accessor);
            currentlyLoaded = false;
            LDOGMod.LOGGER.info("LDOG: FXAA shader unloaded");
        }
    }

    private static boolean warnedNoAccessor = false;

    private static void unload(AccessorEntityRenderer accessor) {
        ShaderGroup current = accessor.ldog$getShaderGroup();
        // EntityRenderer's shaderGroup slot is shared with vanilla's mob-vision
        // shaders (spectating through a creeper/enderman swaps in
        // shaders/post/creeper.json or spider.json independently of LDOG).
        // Only tear down the group if it's the exact instance we loaded —
        // otherwise mob vision (or anything else) owns it right now, and
        // deleting it out from under the player would kill their vision shader
        // mid-spectate. Just drop our own tracking in that case.
        if (current != null && current == fxaaShaderGroup) {
            current.deleteShaderGroup();
            accessor.ldog$setShaderGroup(null);
            accessor.ldog$setUseShader(false);
        }
        fxaaShaderGroup = null;
    }
}
