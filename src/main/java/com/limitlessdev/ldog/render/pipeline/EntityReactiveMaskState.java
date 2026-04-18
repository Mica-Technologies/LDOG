package com.limitlessdev.ldog.render.pipeline;

/**
 * Phase 9c.3-A: tiny shared flag bridging the pipeline binding mixin
 * (which sets up MRT + colorMaski state on sceneFbo) and the entity-loop
 * mixin (which flips colorMaski(1) on/off around RenderGlobal.renderEntities
 * to populate the reactive-mask attachment).
 *
 * Why a static rather than an inter-mixin call: keeps both mixins free of
 * any cross-class compile-time coupling, and the binding mixin is the only
 * place that knows whether MRT setup succeeded this frame (config could
 * be on but framebuffer allocation could have failed). The entity loop
 * mixin reads this each entity-render call to decide whether to bother
 * with colorMaski calls at all.
 */
public final class EntityReactiveMaskState {

    private static volatile boolean active = false;

    private EntityReactiveMaskState() {}

    public static void setActive(boolean v) { active = v; }
    public static boolean isActive() { return active; }
}
