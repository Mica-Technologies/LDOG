package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
 * <p>v1 scope: composite + final stages only. Gbuffer programs and shadow
 * pass are parsed (so {@code hasAnyProgram()} works) but not yet compiled
 * or hooked into the render pipeline.
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
        public Stage(String name, ShaderProgram program) {
            this.name = name;
            this.program = program;
        }
    }

    private final ShaderPack pack;
    private final List<Stage> composites = new ArrayList<>();
    private Stage finalStage;

    public ShaderPackRuntime(ShaderPack pack) {
        this.pack = pack;
        compileChain();
    }

    /** Active composite stages in execution order. Read-only. */
    public List<Stage> composites() { return composites; }

    /**
     * The {@code final.{vsh,fsh}} stage, or null when the pack didn't ship
     * one. Without a final stage, the composite chain's last buffer has to
     * be blitted to the main framebuffer directly — caller decides.
     */
    public Stage finalStage() { return finalStage; }

    public boolean isEmpty() { return composites.isEmpty() && finalStage == null; }

    public String packName() { return pack.name; }

    public void dispose() {
        for (Stage s : composites) {
            if (s.program != null) s.program.dispose();
        }
        composites.clear();
        if (finalStage != null && finalStage.program != null) finalStage.program.dispose();
        finalStage = null;
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

    /**
     * Try to load + compile a single stage by base name (e.g. "composite",
     * "composite3", "final"). Returns null when either the .vsh or .fsh is
     * missing in the pack, or when compilation fails. Failures are logged
     * but don't propagate — one broken stage shouldn't kill the rest.
     */
    private Stage tryCompile(String baseName) {
        String vertPath = baseName + ".vsh";
        String fragPath = baseName + ".fsh";
        if (!pack.hasShaderResource(vertPath) || !pack.hasShaderResource(fragPath)) {
            return null;
        }
        String vertSrc, fragSrc;
        try {
            vertSrc = pack.readShaderText(vertPath);
            fragSrc = pack.readShaderText(fragPath);
        } catch (IOException e) {
            LDOGMod.LOGGER.warn("LDOG: Could not read shader stage '{}' from pack '{}': {}",
                baseName, pack.name, e.toString());
            return null;
        }
        try {
            ShaderProgram program = new ShaderProgram("ldogPack:" + baseName, vertSrc, fragSrc);
            return new Stage(baseName, program);
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
