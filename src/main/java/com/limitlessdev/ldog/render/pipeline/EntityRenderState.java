package com.limitlessdev.ldog.render.pipeline;

import net.minecraft.util.math.AxisAlignedBB;

/**
 * Per-entity render-state snapshot used by Phase 9c.3-C entity motion
 * vectors.
 *
 * <p>An entity has TWO positions worth tracking each frame:
 *
 * <ul>
 *   <li><b>{@code thisFrameInterpX/Y/Z}</b> — the position vanilla
 *       interpolated for this frame's render
 *       ({@code prevPosX + (posX - prevPosX) * partialTicks}). This is
 *       where the entity APPEARS on screen this frame.</li>
 *   <li><b>{@code lastFrameInterpX/Y/Z}</b> — the previous frame's
 *       {@code thisFrameInterp*}. Needed by the MV pass to compute the
 *       screen-space delta between the entity's last-frame and this-frame
 *       projected positions.</li>
 * </ul>
 *
 * <p>{@code aabb} is captured per-frame so the MV stamp covers exactly
 * the screen pixels the entity drew. Approximate — we use the AABB rather
 * than per-pixel geometry, which is the design tradeoff documented in
 * master plan §10 Strategy 4. Per-pixel MV requires re-rendering entity
 * geometry with a velocity-emitting shader (2× draw cost per entity);
 * BBox stamping covers ~95% of the perceptual win at ~5% the cost.
 *
 * <p>{@code lastFrameIndex} tracks when the state was last touched so the
 * cache can evict stale entries (entities that despawned, swapped
 * dimensions, etc. — their UUID won't reappear within a few frames).
 */
public final class EntityRenderState {

    public double thisFrameInterpX, thisFrameInterpY, thisFrameInterpZ;
    public double lastFrameInterpX, lastFrameInterpY, lastFrameInterpZ;
    public boolean hasLast;
    public AxisAlignedBB aabb;
    public int lastFrameIndex;

    public void rotateAndCapture(double x, double y, double z,
                                  AxisAlignedBB box, int frameIndex) {
        if (hasLast) {
            lastFrameInterpX = thisFrameInterpX;
            lastFrameInterpY = thisFrameInterpY;
            lastFrameInterpZ = thisFrameInterpZ;
        } else {
            lastFrameInterpX = x;
            lastFrameInterpY = y;
            lastFrameInterpZ = z;
        }
        thisFrameInterpX = x;
        thisFrameInterpY = y;
        thisFrameInterpZ = z;
        aabb = box;
        lastFrameIndex = frameIndex;
        hasLast = true;
    }
}
