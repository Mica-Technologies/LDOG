package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * UUID-keyed cache of per-entity render-state snapshots for Phase 9c.3-C
 * entity motion vectors. Also exposes the per-frame "rendered this frame"
 * queue that {@link com.limitlessdev.ldog.render.pipeline.passes.EntityMotionVectorPass}
 * drains to stamp velocity into the MV target.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>World render begins → {@link #beginFrame()} clears the per-frame queue.</li>
 *   <li>For each entity vanilla draws, {@code MixinRenderManager} calls
 *       {@link #captureForRender(Entity, double, double, double, AxisAlignedBB)}
 *       to update the entity's state and add it to the queue.</li>
 *   <li>After scene render, {@code EntityMotionVectorPass} iterates the
 *       queue and stamps velocity onto the MV target.</li>
 * </ol>
 *
 * <p>Stale-entry eviction: every ~5 seconds we walk the WeakHashMap and
 * drop entries whose {@code lastFrameIndex} is more than a few hundred
 * frames stale. The {@code Entity} reference itself is a weak key, so if
 * the entity is GC'd (despawn + GC pressure) the entry vanishes anyway —
 * this loop just catches the case where an Entity is reachable but no
 * longer being rendered (other dimension, render-distance, etc.).
 *
 * <p>Single-threaded: only touched from the client render thread.
 */
public final class EntityRenderStateCache {

    private static final EntityRenderStateCache INSTANCE = new EntityRenderStateCache();

    /** How many frames of inactivity before we drop a cache entry. */
    private static final int STALE_FRAMES = 300;

    private final Map<UUID, EntityRenderState> states = new WeakHashMap<>();
    private final List<RenderedEntity> thisFrame = new ArrayList<>();
    private int frameIndex;
    private int sweepCounter;

    private EntityRenderStateCache() {}

    public static EntityRenderStateCache get() { return INSTANCE; }

    /** Clear the per-frame queue. Called from the binding mixin at HEAD. */
    public void beginFrame() {
        thisFrame.clear();
        frameIndex++;
        if ((sweepCounter++ & 0xFF) == 0) sweepStale();
    }

    /**
     * Record that {@code entity} drew this frame at interpolated world pos
     * ({@code interpX, interpY, interpZ}) with bounding box {@code box}.
     * Rotates the entity's prev/cur positions if it was already cached, or
     * seeds prev = cur on first sighting.
     */
    public void captureForRender(Entity entity, double interpX, double interpY, double interpZ,
                                  AxisAlignedBB box) {
        if (!LDOGConfig.enableEntityMotionVectors) return;
        UUID id = entity.getUniqueID();
        EntityRenderState s = states.get(id);
        if (s == null) {
            s = new EntityRenderState();
            states.put(id, s);
        }
        s.rotateAndCapture(interpX, interpY, interpZ, box, frameIndex);
        thisFrame.add(new RenderedEntity(s));
    }

    /** Read-only view of entities that drew this frame. */
    public List<RenderedEntity> currentFrameEntries() {
        return thisFrame;
    }

    public int getFrameIndex() { return frameIndex; }

    public void clear() {
        states.clear();
        thisFrame.clear();
        sweepCounter = 0;
    }

    private void sweepStale() {
        int cutoff = frameIndex - STALE_FRAMES;
        Iterator<Map.Entry<UUID, EntityRenderState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().lastFrameIndex < cutoff) it.remove();
        }
    }

    /** Lightweight reference passed to the MV pass per rendered entity. */
    public static final class RenderedEntity {
        public final EntityRenderState state;
        public RenderedEntity(EntityRenderState state) { this.state = state; }
    }
}
