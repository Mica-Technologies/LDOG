package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiled, executable form of an activated {@link ShaderPack}. Owns the
 * GL programs for the composite chain ({@code composite.{vsh,fsh}} through
 * {@code composite15}) plus the terminal {@code final.{vsh,fsh}} stage.
 *
 * <p>Construction reads + compiles every stage the pack supplies. Missing
 * stages are silently skipped (most packs only ship a subset). Compile
 * failures on individual stages drop just that stage from the chain rather
 * than aborting the whole pack — a half-working pack is more useful than
 * no pack.
 *
 * <p>Scope: the composite + final stages drive the post-process layer
 * ({@link com.limitlessdev.ldog.render.pipeline.passes.ShaderPackCompositePass}),
 * and the {@code gbuffers_*} programs drive per-object draws when the
 * gbuffer dispatcher is enabled (see {@link ShaderPackGbufferManager}).
 * The {@code shadow} programs are compiled and held but the shadow render
 * pass itself is not yet wired — that's the last remaining OF-parity chunk.
 *
 * <p>{@link #dispose()} frees all GL programs; callers must call it before
 * dropping the runtime reference.
 */
public final class ShaderPackRuntime {

    /** Highest composite index OF/Iris conventionally support. */
    private static final int MAX_COMPOSITE = 15;

    /** Per-stage compiled program + a human-readable name for logging. */
    public static final class Stage {
        public final String name;
        public final ShaderProgram program;
        /**
         * Color attachment indices this program writes, parsed from its
         * {@code /* DRAWBUFFERS:NNN *​/} directive (e.g. {@code DRAWBUFFERS:028}
         * → {0, 2, 8}). Maps {@code gl_FragData[i]} → {@code colortex[drawBuffers[i]]}.
         * Defaults to {@code {0}} when the directive is absent.
         */
        public final int[] drawBuffers;
        public Stage(String name, ShaderProgram program, int[] drawBuffers) {
            this.name = name;
            this.program = program;
            this.drawBuffers = drawBuffers;
        }
    }

    private final ShaderPack pack;
    private final List<Stage> composites = new ArrayList<>();
    private Stage finalStage;

    /**
     * Compiled per-object draw programs, keyed by base name
     * ({@code "gbuffers_terrain"}, {@code "shadow"}). Only the programs the
     * pack actually ships + that compiled cleanly are present.
     */
    private final Map<String, Stage> gbufferPrograms = new LinkedHashMap<>();

    /** Highest colortex index any compiled program writes to (for MRT allocation). */
    private int maxColortex;

    public ShaderPackRuntime(ShaderPack pack) {
        this.pack = pack;
        compileChain();
        compileGbuffers();
    }

    /** Active composite stages in execution order. Read-only. */
    public List<Stage> composites() { return composites; }

    /**
     * Resolve the compiled program for a draw category by walking its
     * fallback chain ({@link GbufferProgram#candidates}). Returns the first
     * candidate the pack compiled, or null when the pack ships none of them
     * (caller then leaves vanilla fixed-function rendering in place).
     */
    public Stage resolveGbuffer(GbufferProgram category) {
        for (String base : category.candidates) {
            Stage s = gbufferPrograms.get(base);
            if (s != null) return s;
        }
        return null;
    }

    /** True when the pack supplied at least one usable gbuffer program. */
    public boolean hasGbuffers() { return !gbufferPrograms.isEmpty(); }

    /** Highest colortex index written by any program — drives MRT aux allocation. */
    public int maxColortex() { return maxColortex; }

    /** True when there's a composite/final chain for the post-process pass to run. */
    public boolean hasCompositeChain() { return !composites.isEmpty() || finalStage != null; }

    /**
     * The {@code final.{vsh,fsh}} stage, or null when the pack didn't ship
     * one. Without a final stage, the composite chain's last buffer has to
     * be blitted to the main framebuffer directly — caller decides.
     */
    public Stage finalStage() { return finalStage; }

    public boolean isEmpty() { return !hasCompositeChain() && !hasGbuffers(); }

    public String packName() { return pack.name; }

    public void dispose() {
        for (Stage s : composites) {
            if (s.program != null) s.program.dispose();
        }
        composites.clear();
        if (finalStage != null && finalStage.program != null) finalStage.program.dispose();
        finalStage = null;
        for (Stage s : gbufferPrograms.values()) {
            if (s != null && s.program != null) s.program.dispose();
        }
        gbufferPrograms.clear();
    }

    private void compileChain() {
        // composite.{vsh,fsh}: the base composite stage.
        Stage base = tryCompile("composite");
        if (base != null) composites.add(base);
        // composite1..composite15: optional chained stages.
        for (int i = 1; i <= MAX_COMPOSITE; i++) {
            Stage s = tryCompile("composite" + i);
            if (s != null) composites.add(s);
        }
        // final.{vsh,fsh}: terminal stage that writes to the screen.
        finalStage = tryCompile("final");

        if (isEmpty()) {
            LDOGMod.LOGGER.info(
                "LDOG: Shader pack '{}' activated but ships no composite/final stages — nothing to run",
                pack.name);
        } else {
            LDOGMod.LOGGER.info(
                "LDOG: Shader pack '{}' compiled — {} composite stage(s){}",
                pack.name, composites.size(),
                finalStage != null ? " + final" : " (no final stage)");
        }
    }

    private static final int[] DEFAULT_DRAW_BUFFERS = {0};

    /**
     * Parse the MRT output mapping from a fragment shader. Supports OptiFine's
     * {@code DRAWBUFFERS:0231} (one digit per output) and Iris's
     * {@code RENDERTARGETS: 0,1,2,3} (comma list). Returns {@code {0}} when
     * neither directive is present (single-target, writes colortex0).
     */
    static int[] parseDrawBuffers(String fragSrc) {
        int i = fragSrc.indexOf("RENDERTARGETS:");
        if (i >= 0) {
            String rest = fragSrc.substring(i + "RENDERTARGETS:".length());
            int end = rest.indexOf("*/");
            if (end >= 0) rest = rest.substring(0, end);
            String[] toks = rest.trim().split("[,\\s]+");
            List<Integer> out = new ArrayList<>();
            for (String t : toks) {
                if (t.isEmpty()) continue;
                try { out.add(Integer.parseInt(t.trim())); } catch (NumberFormatException ignored) { break; }
            }
            if (!out.isEmpty()) return out.stream().mapToInt(Integer::intValue).toArray();
        }
        i = fragSrc.indexOf("DRAWBUFFERS:");
        if (i >= 0) {
            int p = i + "DRAWBUFFERS:".length();
            List<Integer> out = new ArrayList<>();
            while (p < fragSrc.length()) {
                char c = fragSrc.charAt(p);
                if (c >= '0' && c <= '9') out.add(c - '0');
                else if (c == ' ' || c == '\t') { /* skip */ }
                else break;
                p++;
            }
            if (!out.isEmpty()) return out.stream().mapToInt(Integer::intValue).toArray();
        }
        return DEFAULT_DRAW_BUFFERS;
    }

    /**
     * Compile every {@code gbuffers_*} (and {@code shadow}) program the pack
     * ships. Each is independent — a compile failure on one drops just that
     * program, leaving the rest available for their fallback consumers.
     */
    private void compileGbuffers() {
        for (String vsh : ShaderProgramId.STANDARD_PAIRS.keySet()) {
            // STANDARD_PAIRS also holds final.vsh, which the composite chain
            // already owns — skip it here.
            if (vsh.equals("final.vsh")) continue;
            String base = vsh.substring(0, vsh.length() - ".vsh".length());
            Stage s = tryCompile(base);
            if (s != null) gbufferPrograms.put(base, s);
        }
        if (!gbufferPrograms.isEmpty()) {
            LDOGMod.LOGGER.info("LDOG: Shader pack '{}' compiled {} gbuffer/shadow program(s): {}",
                pack.name, gbufferPrograms.size(), gbufferPrograms.keySet());
        }
    }

    /**
     * Try to load + compile a single stage by base name (e.g. "composite",
     * "composite3", "final"). Returns null when either the .vsh or .fsh is
     * missing in the pack, or when compilation fails. Failures are logged
     * but don't propagate — one broken stage shouldn't kill the rest.
     */
    private Stage tryCompile(String baseName) {
        // Dimension-aware resolution: prefer worldN/<stage> over the root copy.
        String vertPath = pack.resolvePath(baseName + ".vsh");
        String fragPath = pack.resolvePath(baseName + ".fsh");
        if (vertPath == null || fragPath == null) {
            return null;
        }
        String vertSrc, fragSrc;
        try {
            // Inline #include directives so packs that split shared code across
            // lib/ files compile as a single translation unit.
            vertSrc = pack.readShaderWithIncludes(vertPath);
            fragSrc = pack.readShaderWithIncludes(fragPath);
        } catch (IOException e) {
            LDOGMod.LOGGER.warn("LDOG: Could not read shader stage '{}' from pack '{}': {}",
                baseName, pack.name, e.toString());
            return null;
        }
        try {
            ShaderProgram program = new ShaderProgram("ldogPack:" + baseName, vertSrc, fragSrc);
            int[] drawBuffers = parseDrawBuffers(fragSrc);
            for (int idx : drawBuffers) if (idx > maxColortex) maxColortex = idx;
            return new Stage(baseName, program, drawBuffers);
        } catch (ShaderProgram.ShaderCompileException e) {
            // Logged at WARN rather than ERROR — the pack still has other
            // stages we might be able to use.
            LDOGMod.LOGGER.warn(
                "LDOG: Shader pack '{}' stage '{}' failed to compile, skipping. Cause:\n{}",
                pack.name, baseName, e.getMessage());
            return null;
        }
    }
}
