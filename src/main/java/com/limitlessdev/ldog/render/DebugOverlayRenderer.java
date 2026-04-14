package com.limitlessdev.ldog.render;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.List;

/**
 * Appends LDOG optimization stats to the F3 debug overlay (right side).
 * Shows entity/particle/TE culling counts so you can see the optimizations working.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class DebugOverlayRenderer {

    @SubscribeEvent
    public static void onDebugText(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.gameSettings.showDebugInfo) return;

        List<String> right = event.getRight();

        right.add("");
        right.add("\u00a7e[LDOG]");

        // Entity stats
        int totalEntities = LDOGStats.entitiesRendered + LDOGStats.entitiesCulledByDistance + LDOGStats.entitiesSkippedByLOD;
        right.add(String.format("Entities: %d rendered, %d dist-culled, %d LOD-skipped",
            LDOGStats.entitiesRendered, LDOGStats.entitiesCulledByDistance, LDOGStats.entitiesSkippedByLOD));

        // Particle stats
        if (LDOGConfig.enableParticleCulling) {
            int totalParticles = LDOGStats.particlesRendered + LDOGStats.particlesCulled;
            right.add(String.format("Particles: %d rendered, %d culled",
                LDOGStats.particlesRendered, LDOGStats.particlesCulled));
        }

        // TE stats
        if (LDOGConfig.tileEntityRenderDistance > 0 && LDOGStats.tileEntitiesCulled > 0) {
            right.add("TileEntities culled: " + LDOGStats.tileEntitiesCulled);
        }

        // FPS reducer state
        if (LDOGConfig.enableFpsReducer) {
            int limit = FpsReducerHandler.getTargetFpsLimit();
            if (limit > 0) {
                right.add("FPS limited: " + limit + (FpsReducerHandler.isAfk() ? " (AFK)" : " (Unfocused)"));
            }
        }
    }
}
