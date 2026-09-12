package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The {@code uniform.<type>.<name> = expr} and {@code variable.<type>.<name> = expr}
 * entries of a pack's {@code shaders.properties}, evaluated once per frame and
 * pushed to every program alongside the built-in uniform set.
 *
 * <p>This is what BSL, Sildur's and most modern 1.12.2 packs use to derive
 * their lighting drivers ({@code shadowFade}, {@code timeAngle},
 * {@code timeBrightness}, biome flags) from the built-ins. Without it those
 * uniforms read as zero and the direct-light term collapses — the "uniformly
 * dark BSL" symptom that survived every buffer/shadow fix.
 *
 * <p>Definitions may reference each other in any order ({@code Properties}
 * loses file order anyway), so evaluation is lazy and memoised per frame with
 * a cycle guard. A definition that fails to parse is logged once and skipped;
 * a definition that fails at evaluation (unknown name) is logged once and
 * feeds zero, so one bad line never blanks the pack.
 */
public final class ShaderCustomUniforms {

    /** How many components each declared type carries. */
    private static int componentsOf(String type) {
        switch (type) {
            case "vec2": return 2;
            case "vec3": return 3;
            case "vec4": return 4;
            default: return 1; // float, int, bool
        }
    }

    private static final class Def {
        final String name;
        final String type;
        final boolean isUniform;
        final String source;
        ShaderUniformExpression.Node node;
        boolean warned;

        Def(String name, String type, boolean isUniform, String source) {
            this.name = name; this.type = type; this.isUniform = isUniform; this.source = source;
        }
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, float[]> frameValues = new HashMap<>();
    private final Set<String> evaluating = new HashSet<>();
    private final Map<Integer, Float> smoothState = new HashMap<>();
    private ShaderUniformExpression.Env builtins;
    private float frameDt = 1f / 60f;

    public ShaderCustomUniforms(Properties props) {
        if (props == null) return;
        for (String key : props.stringPropertyNames()) {
            boolean uniform = key.startsWith("uniform.");
            boolean variable = key.startsWith("variable.");
            if (!uniform && !variable) continue;
            String[] parts = key.split("\\.", 3);
            if (parts.length != 3) continue;
            String type = parts[1].toLowerCase(Locale.ROOT);
            String name = parts[2].trim();
            String src = props.getProperty(key).trim();
            Def d = new Def(name, type, uniform, src);
            try {
                d.node = ShaderUniformExpression.parse(src);
            } catch (IllegalArgumentException e) {
                LDOGMod.LOGGER.warn("LDOG: shaders.properties {} '{}' skipped: {}", uniform ? "uniform" : "variable", name, e.getMessage());
                continue;
            }
            defs.put(name, d);
        }
    }

    public boolean isEmpty() { return defs.isEmpty(); }

    public int uniformCount() {
        int n = 0;
        for (Def d : defs.values()) if (d.isUniform) n++;
        return n;
    }

    /** Names of the custom uniforms (not variables), for diagnostics. */
    public Set<String> uniformNames() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (Def d : defs.values()) if (d.isUniform) out.add(d.name);
        return out;
    }

    /**
     * Re-evaluate every definition against this frame's built-ins.
     *
     * @param builtins resolver for the built-in uniform names ({@code sunAngle},
     *                 {@code cameraPosition}, {@code biome}, {@code BIOME_*} …)
     * @param dtSeconds time since the previous frame, for {@code smooth()}
     */
    public void evaluateFrame(ShaderUniformExpression.Env builtins, float dtSeconds) {
        this.builtins = builtins;
        this.frameDt = dtSeconds > 0f && dtSeconds < 1f ? dtSeconds : 1f / 60f;
        frameValues.clear();
        evaluating.clear();
        for (Def d : defs.values()) {
            if (d.isUniform) resolve(d.name);
        }
    }

    /** Push this frame's uniform values to a program (call after evaluateFrame). */
    public void feedTo(ShaderProgram program) {
        for (Def d : defs.values()) {
            if (!d.isUniform) continue;
            float[] v = frameValues.get(d.name);
            if (v == null) continue;
            switch (d.type) {
                case "int":
                case "bool":
                    program.setUniform1i(d.name, (int) v[0]);
                    break;
                case "vec2":
                    program.setUniform2f(d.name, v[0], v.length > 1 ? v[1] : 0f);
                    break;
                case "vec3":
                    program.setUniform3f(d.name, v[0], v.length > 1 ? v[1] : 0f, v.length > 2 ? v[2] : 0f);
                    break;
                case "vec4":
                    program.setUniform4f(d.name, v[0], v.length > 1 ? v[1] : 0f,
                        v.length > 2 ? v[2] : 0f, v.length > 3 ? v[3] : 0f);
                    break;
                default:
                    program.setUniform1f(d.name, v[0]);
            }
        }
    }

    /** Value of a custom uniform/variable this frame (tests + diagnostics). */
    public float[] value(String name) {
        return frameValues.get(name);
    }

    private final ShaderUniformExpression.Env scope = new ShaderUniformExpression.Env() {
        @Override public float[] get(String name) {
            if (defs.containsKey(name)) return resolve(name);
            return builtins == null ? null : builtins.get(name);
        }

        @Override public float smooth(int id, float value, float fadeUp, float fadeDown) {
            Float prev = smoothState.get(id);
            if (prev == null) { smoothState.put(id, value); return value; }
            float fade = value > prev ? fadeUp : fadeDown;
            float t = fade <= 0f ? 1f : Math.min(1f, frameDt / fade);
            float next = prev + (value - prev) * t;
            smoothState.put(id, next);
            return next;
        }
    };

    private float[] resolve(String name) {
        float[] cached = frameValues.get(name);
        if (cached != null) return cached;
        Def d = defs.get(name);
        if (d == null) return null;
        int n = componentsOf(d.type);
        float[] out;
        if (!evaluating.add(name)) {
            // Cycle: unwind to the outermost evaluation of this name, which
            // catches below and feeds zero for the whole definition.
            throw new IllegalArgumentException("'" + name + "' references itself");
        } else {
            try {
                float[] v = d.node.eval(scope);
                out = new float[n];
                for (int i = 0; i < n; i++) out[i] = v[v.length == 1 ? 0 : Math.min(i, v.length - 1)];
                if ("int".equals(d.type)) out[0] = (float) Math.floor(out[0]);
                if ("bool".equals(d.type)) out[0] = out[0] != 0f ? 1f : 0f;
            } catch (RuntimeException e) {
                if (!d.warned) { d.warned = true; LDOGMod.LOGGER.warn("LDOG: shaders.properties '{}' = {} failed: {}; feeding zero", name, d.source, e.getMessage()); }
                out = new float[n];
            } finally {
                evaluating.remove(name);
            }
        }
        frameValues.put(name, out);
        return out;
    }
}
