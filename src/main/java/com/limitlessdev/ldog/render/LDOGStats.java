package com.limitlessdev.ldog.render;

/**
 * Tracks per-frame rendering statistics for LDOG's optimizations.
 * Reset each frame, read by the debug overlay.
 */
public final class LDOGStats {

    // Entity culling
    public static int entitiesCulledByDistance = 0;
    public static int entitiesSkippedByLOD = 0;
    public static int entitiesRendered = 0;

    // Particle culling
    public static int particlesCulled = 0;
    public static int particlesRendered = 0;

    // Tile entity culling
    public static int tileEntitiesCulled = 0;

    private LDOGStats() {}

    public static void resetFrame() {
        entitiesCulledByDistance = 0;
        entitiesSkippedByLOD = 0;
        entitiesRendered = 0;
        particlesCulled = 0;
        particlesRendered = 0;
        tileEntitiesCulled = 0;
    }
}
