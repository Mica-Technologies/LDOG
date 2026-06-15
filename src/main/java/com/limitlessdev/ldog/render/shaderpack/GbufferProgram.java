package com.limitlessdev.ldog.render.shaderpack;

/**
 * The per-object draw-call categories a shader pack can override, paired
 * with the OptiFine / Iris program-fallback chain for each.
 *
 * <p>OptiFine packs ship a subset of {@code gbuffers_*} programs and rely on
 * a documented fallback order: e.g. a pack with only {@code gbuffers_textured}
 * still shades terrain because {@code gbuffers_terrain} falls back to
 * {@code gbuffers_textured} and ultimately {@code gbuffers_basic}. Each enum
 * constant lists its candidate program base-names in priority order; the
 * {@link ShaderPackRuntime} resolves the first one the active pack actually
 * compiled.
 *
 * <p>The base-names match {@link ShaderProgramId#STANDARD_PAIRS} keys minus
 * the {@code .vsh}/{@code .fsh} suffix (so {@code "gbuffers_terrain"}).
 *
 * <p>Per the LDOG external-references policy this is a clean re-derivation of
 * the publicly documented fallback table — no code copied from OF or Iris.
 */
public enum GbufferProgram {

    /** Sky gradient / void color quad ({@code RenderGlobal.renderSky}). */
    SKY_BASIC("gbuffers_skybasic", "gbuffers_basic"),

    /** Textured sky elements — sun, moon, stars, custom skybox. */
    SKY_TEXTURED("gbuffers_skytextured", "gbuffers_textured", "gbuffers_basic"),

    /** Cloud layer. */
    CLOUDS("gbuffers_clouds", "gbuffers_textured", "gbuffers_basic"),

    /** Opaque terrain (SOLID block render layer). */
    TERRAIN_SOLID("gbuffers_terrain", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic"),

    /** Cutout terrain (CUTOUT / CUTOUT_MIPPED layers — leaves, grass, etc.). */
    TERRAIN_CUTOUT("gbuffers_terrain_cutout", "gbuffers_terrain", "gbuffers_textured_lit",
                   "gbuffers_textured", "gbuffers_basic"),

    /** Translucent terrain (water, ice, stained glass — TRANSLUCENT layer). */
    TERRAIN_TRANSLUCENT("gbuffers_water", "gbuffers_terrain", "gbuffers_textured_lit",
                        "gbuffers_textured", "gbuffers_basic"),

    /** Entities (mobs, items, projectiles — the whole {@code renderEntities} loop). */
    ENTITIES("gbuffers_entities", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic"),

    /** Rain / snow weather particles. */
    WEATHER("gbuffers_weather", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic"),

    /** First-person held item / arm. */
    HAND("gbuffers_hand", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic");

    /** Candidate program base-names in priority (most-specific-first) order. */
    public final String[] candidates;

    GbufferProgram(String... candidates) {
        this.candidates = candidates;
    }
}
