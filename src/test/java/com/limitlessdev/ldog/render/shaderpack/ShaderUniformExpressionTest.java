package com.limitlessdev.ldog.render.shaderpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ShaderUniformExpressionTest {

    /** Minimal env: a map of names, plus a smooth() that just returns the value. */
    private static ShaderUniformExpression.Env env(Object... kv) {
        Map<String, float[]> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            m.put((String) kv[i], v instanceof float[] ? (float[]) v : new float[]{ ((Number) v).floatValue() });
        }
        return new ShaderUniformExpression.Env() {
            @Override public float[] get(String name) { return m.get(name); }
            @Override public float smooth(int id, float value, float up, float down) { return value; }
        };
    }

    private static float f(String src, ShaderUniformExpression.Env e) {
        return ShaderUniformExpression.evaluate(src, e)[0];
    }

    @Test
    @DisplayName("precedence: * before +, unary minus, parentheses, modulo")
    void precedence() {
        ShaderUniformExpression.Env e = env("frameCounter", 13);
        assertEquals(7f, f("1 + 2 * 3", e), 1e-6);
        assertEquals(9f, f("(1 + 2) * 3", e), 1e-6);
        assertEquals(-1f, f("-(3 - 2)", e), 1e-6);
        assertEquals(5f, f("frameCounter % 8", e), 1e-6);
        assertEquals(0.5f, f("2.0 / 4", e), 1e-6);
    }

    @Test
    @DisplayName("comparison and logical operators produce 1/0 and short-circuit")
    void logic() {
        ShaderUniformExpression.Env e = env("a", 0.7, "b", 0.2);
        assertEquals(1f, f("a > 0.5", e));
        assertEquals(0f, f("b >= 0.5", e));
        assertEquals(1f, f("a > 0.5 && b < 0.5", e));
        assertEquals(1f, f("a < 0.5 || b < 0.5", e));
        assertEquals(0f, f("!(a > 0.5)", e));
        assertEquals(1f, f("a != b", e));
    }

    @Test
    @DisplayName("BSL v8 timeAngle chain evaluates to the expected daytime values")
    void bslTimeAngleChain() {
        // OptiFine's sunAngle: 0 = sunrise, 0.25 = noon, 0.5 = sunset, 0.75 = midnight.
        ShaderUniformExpression.Env e = env("sunAngle", 0.25);
        float tAmin = f("frac(sunAngle - 0.033333333)", e);
        assertEquals(0.2166667f, tAmin, 1e-5);
        ShaderUniformExpression.Env e2 = env("tAmin", tAmin);
        float tAlin = f("if(tAmin < 0.433333333, tAmin * 1.15384615385, tAmin * 0.882352941176 + 0.117647058824)", e2);
        assertEquals(0.25f, tAlin, 1e-5);
        ShaderUniformExpression.Env e3 = env("tAlin", tAlin);
        assertEquals(0f, f("if(tAlin > 0.5, 1.0, 0.0)", e3));
        // shadowFade is fully 1 at noon …
        assertEquals(1f, f("clamp(1.0 - (abs(abs(sunAngle - 0.5) - 0.25) - 0.23) * 100.0, 0.0, 1.0)", e), 1e-6);
        // … and 0 with the sun on the horizon (sunrise = 0.0, sunset = 0.5).
        assertEquals(0f, f("clamp(1.0 - (abs(abs(sunAngle - 0.5) - 0.25) - 0.23) * 100.0, 0.0, 1.0)", env("sunAngle", 0.0)), 1e-6);
        assertEquals(0f, f("clamp(1.0 - (abs(abs(sunAngle - 0.5) - 0.25) - 0.23) * 100.0, 0.0, 1.0)", env("sunAngle", 0.5)), 1e-6);
        // MC raw celestial noon (0.0) fed unshifted would read as sunrise → 0. That was the bug.
    }

    @Test
    @DisplayName("in(), if() with multiple branches, smooth(), swizzle, vec constructors")
    void functions() {
        ShaderUniformExpression.Env e = env("biome", 17, "cameraPosition", new float[]{1f, 93f, 2f});
        assertEquals(1f, f("if(in(biome, 2, 17, 130), 1, 0)", e));
        assertEquals(0f, f("if(in(biome, 2, 130), 1, 0)", e));
        assertEquals(2f, f("if(biome < 10, 1, biome < 20, 2, 3)", e));
        assertEquals(1f, f("if(cameraPosition.y >= 93.0, 1, 0)", e));
        assertEquals(1f, f("smooth(2, if(in(biome, 17), 1, 0), 10, 10)", e));
        float[] v = ShaderUniformExpression.evaluate("vec3(cameraPosition.x, 0.5, cameraPosition.z) * 2.0", e);
        assertArrayEquals(new float[]{2f, 1f, 4f}, v, 1e-6f);
        assertEquals(0.5f, f("max(sin(0.5235988 * 6.28318530718 / 6.28318530718), 0.0)", e), 1e-5);
        assertEquals(0.5f, f("frac(2.5)", e), 1e-6);
        assertEquals(3f, f("max(1, 3, 2)", e), 1e-6);
    }

    @Test
    @DisplayName("unknown functions and trailing garbage are parse errors, unknown names are eval errors")
    void errors() {
        assertThrows(IllegalArgumentException.class, () -> ShaderUniformExpression.parse("bogus(1)"));
        assertThrows(IllegalArgumentException.class, () -> ShaderUniformExpression.parse("1 + 2 )"));
        assertThrows(IllegalArgumentException.class, () -> ShaderUniformExpression.parse("if(1, 2)"));
        assertThrows(IllegalArgumentException.class, () -> ShaderUniformExpression.evaluate("nope + 1", env()));
    }

    @Test
    @DisplayName("custom uniforms resolve variable chains in any definition order and feed by type")
    void customUniformChain() {
        Properties p = new Properties();
        p.setProperty("uniform.float.timeAngle", "(tAfrc * (1.0 - tAmix) + tAfrs * tAmix + hA) * 0.5");
        p.setProperty("variable.float.tAmix", "if(hA < 0.5, 0.3, -0.1)");
        p.setProperty("variable.float.hA", "if(tAlin > 0.5, 1.0, 0.0)");
        p.setProperty("variable.float.tAfrs", "tAfrc * tAfrc * (3.0 - 2.0 * tAfrc)");
        p.setProperty("variable.float.tAfrc", "frac(tAlin * 2.0)");
        p.setProperty("variable.float.tAlin", "if(tAmin < 0.433333333, tAmin * 1.15384615385, tAmin * 0.882352941176 + 0.117647058824)");
        p.setProperty("variable.float.tAmin", "frac(sunAngle - 0.033333333)");
        p.setProperty("uniform.float.shadowFade", "clamp(1.0 - (abs(abs(sunAngle - 0.5) - 0.25) - 0.23) * 100.0, 0.0, 1.0)");
        p.setProperty("uniform.int.framemod8", "frameCounter % 8");
        p.setProperty("uniform.float.broken", "nosuchthing * 2");
        p.setProperty("uniform.float.loop", "loop + 1");
        p.setProperty("uniform.float.unparsable", "clamp(1,");
        p.setProperty("uniform.vec3.camScaled", "cameraPosition * 0.5");

        ShaderCustomUniforms cu = new ShaderCustomUniforms(p);
        assertFalse(cu.isEmpty());
        assertFalse(cu.uniformNames().contains("unparsable"), "unparsable definitions are dropped at load");

        cu.evaluateFrame(env("sunAngle", 0.25, "frameCounter", 21, "cameraPosition", new float[]{2f, 4f, 6f}), 1f / 60f);
        assertEquals(1f, cu.value("shadowFade")[0], 1e-6);
        assertEquals(5f, cu.value("framemod8")[0], 1e-6);
        assertArrayEquals(new float[]{1f, 2f, 3f}, cu.value("camScaled"), 1e-6f);
        // Noon (sunAngle 0.25): BSL's remap puts timeAngle in the first half (day side).
        float timeAngle = cu.value("timeAngle")[0];
        assertTrue(timeAngle > 0.0f && timeAngle < 0.5f, "timeAngle=" + timeAngle);
        // Broken definitions feed zero rather than poisoning the rest.
        assertEquals(0f, cu.value("broken")[0]);
        assertEquals(0f, cu.value("loop")[0]);
    }

    @Test
    @DisplayName("smooth() converges toward the target over the fade time")
    void smoothConverges() {
        Properties p = new Properties();
        p.setProperty("uniform.float.isDesert", "smooth(2, if(in(biome, 2), 1, 0), 1, 1)");
        ShaderCustomUniforms cu = new ShaderCustomUniforms(p);
        // First frame seeds the state directly.
        cu.evaluateFrame(env("biome", 0), 0.1f);
        assertEquals(0f, cu.value("isDesert")[0], 1e-6);
        float prev = 0f;
        for (int i = 0; i < 5; i++) {
            cu.evaluateFrame(env("biome", 2), 0.1f);
            float v = cu.value("isDesert")[0];
            assertTrue(v > prev && v < 1f, "frame " + i + " v=" + v);
            prev = v;
        }
    }
}
