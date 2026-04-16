package com.limitlessdev.ldog.render.dynamiclights;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/**
 * Tracks a single dynamic light emitter (an entity holding a light-emitting item).
 */
public class DynamicLightSource {

    public final Entity entity;
    public int lightLevel;
    public BlockPos lastPos;

    public DynamicLightSource(Entity entity, int lightLevel) {
        this.entity = entity;
        this.lightLevel = lightLevel;
        this.lastPos = entity.getPosition();
    }

    public BlockPos currentPos() {
        return entity.getPosition();
    }
}
