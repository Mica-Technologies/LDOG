package com.limitlessdev.ldog.mixin;

import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.LDOGStats;
import com.limitlessdev.ldog.render.pipeline.EntityReactiveMaskState;
import com.limitlessdev.ldog.render.shaderpack.GbufferProgram;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager;
import com.limitlessdev.ldog.render.sky.CustomSkyRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity rendering optimizations:
 * 1. Distance culling: skip entities beyond configurable distance
 * 2. LOD: reduce render frequency for distant entities
 * 3. Stats tracking for the debug overlay
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Unique
    private static long ldog$frameCounter = 0;

    /**
     * Reset stats and increment frame counter at the start of each renderEntities call.
     * Phase 9c.3-A: also opens the per-attachment colour write mask for the
     * reactive-mask attachment (sceneFbo COLOR1) so entity draws populate it.
     * The pipeline binding mixin keeps that mask closed during sky/chunk draws,
     * so toggling it open here scopes the "this pixel is an entity" stamp to
     * exactly the entity loop. Closed again at RETURN below.
     */
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void ldog$resetStats(Entity renderViewEntity, ICamera camera, float partialTicks,
                                  CallbackInfo ci) {
        LDOGStats.resetFrame();
        ldog$frameCounter++;
        if (EntityReactiveMaskState.isActive()) {
            GL30.glColorMaski(1, true, true, true, true);
        }
        // Shader pack shadow map: opaque terrain + chunk list are ready by now,
        // so render the sun-POV depth pass here (before the entity gbuffer bind).
        com.limitlessdev.ldog.render.shaderpack.ShadowMapManager.render(partialTicks);
        // Shader pack gbuffer dispatch: bind gbuffers_entities around the whole
        // entity loop so the pack shades mobs/items/projectiles. No-op unless
        // the gbuffer dispatcher is active and the pack ships a usable program.
        ShaderPackGbufferManager.begin(GbufferProgram.ENTITIES);
    }

    /**
     * Phase 9c.3-A: re-mask sceneFbo COLOR1 writes after the entity loop so
     * post-entity passes (translucent blocks, weather, particles) don't add
     * themselves to the reactive mask. Particles are intentionally NOT marked
     * — the deep-dive doc accepts particle ghosting as low-impact (small
     * screen footprint) and rendering them as reactive would tank temporal
     * AA quality on every spark/smoke puff.
     */
    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void ldog$closeReactiveMask(Entity renderViewEntity, ICamera camera, float partialTicks,
                                         CallbackInfo ci) {
        ShaderPackGbufferManager.end();
        if (EntityReactiveMaskState.isActive()) {
            GL30.glColorMaski(1, false, false, false, false);
        }
    }

    /**
     * Redirect shouldRender to add distance culling and LOD.
     */
    @Redirect(
        method = "renderEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;DDD)Z"
        )
    )
    private boolean ldog$entityCullingAndLOD(net.minecraft.client.renderer.entity.RenderManager renderManager,
                                              Entity entity, ICamera camera,
                                              double camX, double camY, double camZ) {
        double dx = entity.posX - camX;
        double dy = entity.posY - camY;
        double dz = entity.posZ - camZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // Bosses (Ender Dragon, Wither, ...) and other players are exempt from
        // the distance cull below. entityRenderDistance defaults to 64 blocks —
        // applied uniformly that would make a dragon, wither, or a nearby
        // player vanish well within normal engagement/visibility range, a
        // visible vanilla regression. They still render vanilla-far and are
        // still subject to the entity-LOD skip-frame logic further down.
        boolean ldog$exemptFromDistanceCull = entity instanceof EntityPlayer || !entity.isNonBoss();

        // Distance culling
        if (!ldog$exemptFromDistanceCull && LDOGConfig.entityRenderDistance > 0) {
            double maxDist = LDOGConfig.entityRenderDistance;
            if (distSq > maxDist * maxDist) {
                LDOGStats.entitiesCulledByDistance++;
                return false;
            }
        }

        // LOD: skip distant entity renders on some frames
        if (LDOGConfig.enableEntityLOD && distSq > 64.0 * 64.0) {
            // Hash entity ID + frame counter to get stable skip pattern per entity
            // This prevents all distant entities from disappearing on the same frame
            int hash = entity.getEntityId();
            if (distSq > 128.0 * 128.0) {
                // Very far (>128 blocks): render every 4th frame
                if (((hash + ldog$frameCounter) & 3) != 0) {
                    LDOGStats.entitiesSkippedByLOD++;
                    return false;
                }
            } else {
                // Far (64-128 blocks): render every other frame
                if (((hash + ldog$frameCounter) & 1) != 0) {
                    LDOGStats.entitiesSkippedByLOD++;
                    return false;
                }
            }
        }

        boolean result = renderManager.shouldRender(entity, camera, camX, camY, camZ);
        if (result) {
            LDOGStats.entitiesRendered++;
        }
        return result;
    }

    /**
     * Render custom sky layers after vanilla sky rendering.
     *
     * Explicit descriptor (FI)V distinguishes from the private helper
     * renderSky(BufferBuilder, float, boolean) which has descriptor
     * (Lnet/minecraft/client/renderer/BufferBuilder;FZ)V.
     */
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void ldog$renderCustomSkyHead(float partialTicks, int pass, CallbackInfo ci) {
        if (!ldog$skyMixinConfirmed) {
            ldog$skyMixinConfirmed = true;
            com.limitlessdev.ldog.LDOGMod.LOGGER.info("LDOG: renderSky mixin CONFIRMED (pass={}, customSky={})",
                pass, LDOGConfig.enableCustomSky);
        }
        // renderSky is the first per-frame world draw — reset the gbuffer
        // dispatcher's per-frame uniform snapshot latch here.
        //
        // NOTE: the sky itself isn't gbuffer-dispatched in v1. renderSky draws
        // both the untextured gradient (gbuffers_skybasic) AND the textured
        // sun/moon/stars (gbuffers_skytextured) in one method; binding a single
        // program across both would render the sun as an untextured quad. The
        // proper split needs hooks at the celestial texture binds — a follow-up.
        ShaderPackGbufferManager.beginFrame();
    }

    // pass semantics (EntityRenderer.renderWorld):
    //   0 = anaglyph red pass, 1 = anaglyph cyan pass, 2 = normal (non-anaglyph)
    // We want to render in every pass the vanilla sky renders in — no skip.
    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void ldog$renderCustomSkyPost(float partialTicks, int pass, CallbackInfo ci) {
        if (LDOGConfig.enableCustomSky) {
            CustomSkyRenderer.renderCustomSky(partialTicks);
        }
    }

    @Unique
    private static boolean ldog$skyMixinConfirmed = false;

    // --- Shader pack gbuffer dispatch: clouds + terrain layers ---

    // MC 1.12.2 signature: renderClouds(float partialTicks, int pass, double x, double y, double z).
    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void ldog$cloudsGbufferBegin(float partialTicks, int pass, double x, double y, double z,
                                         CallbackInfo ci) {
        ShaderPackGbufferManager.begin(GbufferProgram.CLOUDS);
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void ldog$cloudsGbufferEnd(float partialTicks, int pass, double x, double y, double z,
                                       CallbackInfo ci) {
        ShaderPackGbufferManager.end();
    }

    /**
     * Bind the matching terrain gbuffer program around each block-render-layer
     * draw. MC renders the world in four passes — SOLID, CUTOUT, CUTOUT_MIPPED,
     * TRANSLUCENT — and the pack ships separate programs for opaque terrain vs
     * water/translucent. Returns int (chunk count), so the callback is
     * {@link CallbackInfoReturnable}.
     */
    @Inject(method = "renderBlockLayer", at = @At("HEAD"))
    private void ldog$terrainGbufferBegin(BlockRenderLayer blockLayerIn, double partialTicks,
                                          int pass, Entity entityIn,
                                          CallbackInfoReturnable<Integer> cir) {
        ShaderPackGbufferManager.begin(ldog$terrainCategory(blockLayerIn));
    }

    @Inject(method = "renderBlockLayer", at = @At("RETURN"))
    private void ldog$terrainGbufferEnd(BlockRenderLayer blockLayerIn, double partialTicks,
                                        int pass, Entity entityIn,
                                        CallbackInfoReturnable<Integer> cir) {
        ShaderPackGbufferManager.end();
    }

    @Unique
    private static GbufferProgram ldog$terrainCategory(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT) return GbufferProgram.TERRAIN_TRANSLUCENT;
        if (layer == BlockRenderLayer.SOLID)       return GbufferProgram.TERRAIN_SOLID;
        // CUTOUT + CUTOUT_MIPPED — alpha-tested foliage/glass/etc.
        return GbufferProgram.TERRAIN_CUTOUT;
    }
}
