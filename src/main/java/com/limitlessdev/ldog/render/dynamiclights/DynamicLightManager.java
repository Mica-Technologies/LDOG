package com.limitlessdev.ldog.render.dynamiclights;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages dynamic light sources from entities holding light-emitting items.
 * Tracks active sources, updates positions each tick, and provides light
 * level queries for the world mixin.
 *
 * Client-side only. Thread-safe for the render thread (all access is on
 * the client thread).
 */
public final class DynamicLightManager {

    private static final DynamicLightManager INSTANCE = new DynamicLightManager();

    private final Map<Integer, DynamicLightSource> activeLights = new HashMap<>();
    private long lastScanTick = -1;

    private DynamicLightManager() {}

    public static DynamicLightManager getInstance() {
        return INSTANCE;
    }

    /**
     * Full update: scans entities for light-emitting items and updates positions.
     * Called once per client tick from the tick handler.
     */
    public void tickUpdate() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.player == null) {
            activeLights.clear();
            return;
        }

        long tick = world.getTotalWorldTime();
        int interval = LDOGConfig.dynamicLightsUpdateInterval;
        if (interval > 0 && tick == lastScanTick) return;
        if (interval > 1 && tick % interval != 0) return;
        lastScanTick = tick;

        // Mark all sources for potential removal
        for (DynamicLightSource source : activeLights.values()) {
            source.lightLevel = 0;
        }

        // Scan loaded entities
        for (Entity entity : world.loadedEntityList) {
            int light = getEntityLightLevel(entity);
            if (light <= 0) continue;

            DynamicLightSource source = activeLights.get(entity.getEntityId());
            if (source == null) {
                source = new DynamicLightSource(entity, light);
                activeLights.put(entity.getEntityId(), source);
            } else {
                source.lightLevel = light;
            }
        }

        // Remove dead sources
        Iterator<Map.Entry<Integer, DynamicLightSource>> it = activeLights.entrySet().iterator();
        while (it.hasNext()) {
            DynamicLightSource source = it.next().getValue();
            if (source.lightLevel <= 0 || source.entity.isDead) {
                markForRerender(source.lastPos, 15);
                it.remove();
            }
        }

        // Update positions and trigger re-renders
        updatePositions();
    }

    /**
     * Lightweight position-only update: checks if any light source has moved
     * and triggers chunk re-renders. Called every render frame in smooth mode
     * for fluid light movement without rescanning all entities.
     */
    public void frameUpdate() {
        if (activeLights.isEmpty()) return;
        updatePositions();
    }

    private void updatePositions() {
        for (DynamicLightSource source : activeLights.values()) {
            BlockPos newPos = source.currentPos();
            if (!newPos.equals(source.lastPos)) {
                markForRerender(source.lastPos, source.lightLevel);
                markForRerender(newPos, source.lightLevel);
                source.lastPos = newPos;
            }
        }
    }

    /**
     * Returns the maximum dynamic light level at the given block position,
     * considering all active light sources with distance attenuation.
     */
    public int getDynamicLightLevel(BlockPos pos) {
        if (activeLights.isEmpty()) return 0;

        int maxLight = 0;
        for (DynamicLightSource source : activeLights.values()) {
            if (source.lightLevel <= 0) continue;

            // Manhattan distance for fast light falloff (matches vanilla light propagation)
            int dx = Math.abs(pos.getX() - source.entity.getPosition().getX());
            int dy = Math.abs(pos.getY() - source.entity.getPosition().getY());
            int dz = Math.abs(pos.getZ() - source.entity.getPosition().getZ());
            int dist = dx + dy + dz;

            if (dist > source.lightLevel) continue;

            int light = source.lightLevel - dist;
            if (light > maxLight) {
                maxLight = light;
            }
        }
        return maxLight;
    }

    /**
     * Determines the light emission level of an entity based on its held items
     * or its type (e.g. dropped items, fire entities).
     */
    private static int getEntityLightLevel(Entity entity) {
        // Dropped items on the ground
        if (entity instanceof EntityItem) {
            return ItemLightRegistry.getLightLevel(((EntityItem) entity).getItem());
        }

        // Entities on fire emit light
        int fireLightLevel = entity.isBurning() ? 15 : 0;

        // Entities holding items (players, zombies, skeletons, etc.)
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            int mainHand = ItemLightRegistry.getLightLevel(living.getHeldItemMainhand());
            int offHand = ItemLightRegistry.getLightLevel(living.getHeldItemOffhand());
            return Math.max(Math.max(mainHand, offHand), fireLightLevel);
        }

        return fireLightLevel;
    }

    private static void markForRerender(BlockPos center, int radius) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal != null) {
            mc.renderGlobal.markBlockRangeForRenderUpdate(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius);
        }
    }

    public void clear() {
        activeLights.clear();
    }

    public int getActiveLightCount() {
        return activeLights.size();
    }
}
