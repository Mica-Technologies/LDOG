package com.limitlessdev.ldog.render.shaderpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GlslPreprocessor} and its integration with
 * {@link ShaderPackRuntime#parseDrawBuffers(String)} — verifying that the
 * <em>active</em> conditional branch's DRAWBUFFERS directive is the one selected.
 */
class GlslPreprocessorTest {

    private static Map<String, String> defs(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    // ---- expression evaluation -------------------------------------------

    @Test
    @DisplayName("evalExpr: integer literals and arithmetic")
    void evalArithmetic() {
        Map<String, String> e = Collections.emptyMap();
        assertTrue(GlslPreprocessor.evalExpr("1", e));
        assertFalse(GlslPreprocessor.evalExpr("0", e));
        assertTrue(GlslPreprocessor.evalExpr("2 + 3 == 5", e));
        assertTrue(GlslPreprocessor.evalExpr("(1 + 1) * 2 > 3", e));
        assertFalse(GlslPreprocessor.evalExpr("4 / 2 != 2", e));
    }

    @Test
    @DisplayName("evalExpr: logical operators and precedence")
    void evalLogical() {
        Map<String, String> e = Collections.emptyMap();
        assertTrue(GlslPreprocessor.evalExpr("1 && 1", e));
        assertFalse(GlslPreprocessor.evalExpr("1 && 0", e));
        assertTrue(GlslPreprocessor.evalExpr("0 || 1", e));
        assertTrue(GlslPreprocessor.evalExpr("!0", e));
        assertFalse(GlslPreprocessor.evalExpr("!1", e));
        // && binds tighter than ||
        assertTrue(GlslPreprocessor.evalExpr("0 && 1 || 1", e));
    }

    @Test
    @DisplayName("evalExpr: defined() and macro substitution")
    void evalDefinedAndMacros() {
        Map<String, String> m = defs("QUALITY", "3", "FLAG", "");
        assertTrue(GlslPreprocessor.evalExpr("defined(QUALITY)", m));
        assertTrue(GlslPreprocessor.evalExpr("defined QUALITY", m));
        assertFalse(GlslPreprocessor.evalExpr("defined(MISSING)", m));
        assertTrue(GlslPreprocessor.evalExpr("QUALITY == 3", m));
        assertFalse(GlslPreprocessor.evalExpr("QUALITY > 5", m));
        // valueless define behaves as true (1) in #if
        assertTrue(GlslPreprocessor.evalExpr("FLAG", m));
        // unknown identifier evaluates to 0
        assertFalse(GlslPreprocessor.evalExpr("UNKNOWN", m));
        assertTrue(GlslPreprocessor.evalExpr("!defined(MISSING) && QUALITY >= 3", m));
    }

    @Test
    @DisplayName("evalExpr: malformed expression fails closed")
    void evalMalformed() {
        assertFalse(GlslPreprocessor.evalExpr("( 1 + ", Collections.emptyMap()));
        assertFalse(GlslPreprocessor.evalExpr("", Collections.emptyMap()));
    }

    // ---- branch stripping -------------------------------------------------

    @Test
    @DisplayName("#ifdef selects active DRAWBUFFERS via in-source #define")
    void ifdefSelectsActiveDrawBuffers() {
        String src =
            "#version 120\n" +
            "#define COLORED_LIGHT\n" +
            "#ifdef COLORED_LIGHT\n" +
            "/* DRAWBUFFERS:0367 */\n" +
            "#else\n" +
            "/* DRAWBUFFERS:01 */\n" +
            "#endif\n" +
            "void main() {}\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        assertArrayEquals(new int[]{0, 3, 6, 7}, ShaderPackRuntime.parseDrawBuffers(stripped));
    }

    @Test
    @DisplayName("#ifndef false branch is taken when macro IS defined")
    void ifndefBranch() {
        String src =
            "#define SHADOW\n" +
            "#ifndef SHADOW\n" +
            "/* DRAWBUFFERS:0 */\n" +
            "#else\n" +
            "/* DRAWBUFFERS:012 */\n" +
            "#endif\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        assertArrayEquals(new int[]{0, 1, 2}, ShaderPackRuntime.parseDrawBuffers(stripped));
    }

    @Test
    @DisplayName("#if/#elif/#else chain picks the first true branch only")
    void elifChain() {
        String src =
            "#define MODE 2\n" +
            "#if MODE == 1\n" +
            "/* DRAWBUFFERS:0 */\n" +
            "#elif MODE == 2\n" +
            "/* DRAWBUFFERS:01 */\n" +
            "#elif MODE == 3\n" +
            "/* DRAWBUFFERS:012 */\n" +
            "#else\n" +
            "/* DRAWBUFFERS:0123 */\n" +
            "#endif\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        assertArrayEquals(new int[]{0, 1}, ShaderPackRuntime.parseDrawBuffers(stripped));
    }

    @Test
    @DisplayName("predefined macros drive branch selection")
    void predefinedDrivesSelection() {
        String src =
            "#ifdef GBUFFERS_DEFERRED\n" +
            "/* DRAWBUFFERS:0123 */\n" +
            "#else\n" +
            "/* DRAWBUFFERS:0 */\n" +
            "#endif\n";
        assertArrayEquals(new int[]{0, 1, 2, 3},
            ShaderPackRuntime.parseDrawBuffers(
                GlslPreprocessor.stripInactiveBranches(src, defs("GBUFFERS_DEFERRED", ""))));
        assertArrayEquals(new int[]{0},
            ShaderPackRuntime.parseDrawBuffers(
                GlslPreprocessor.stripInactiveBranches(src, null)));
    }

    @Test
    @DisplayName("nested conditionals resolve correctly")
    void nestedConditionals() {
        String src =
            "#define A\n" +
            "#ifdef A\n" +
            "  #ifdef B\n" +
            "  /* DRAWBUFFERS:0123 */\n" +
            "  #else\n" +
            "  /* DRAWBUFFERS:012 */\n" +
            "  #endif\n" +
            "#else\n" +
            "/* DRAWBUFFERS:0 */\n" +
            "#endif\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        // A defined, B not → inner #else → 012
        assertArrayEquals(new int[]{0, 1, 2}, ShaderPackRuntime.parseDrawBuffers(stripped));
    }

    @Test
    @DisplayName("#undef makes a later #ifdef fall through")
    void undefFallthrough() {
        String src =
            "#define X\n" +
            "#undef X\n" +
            "#ifdef X\n" +
            "/* DRAWBUFFERS:012 */\n" +
            "#else\n" +
            "/* DRAWBUFFERS:0 */\n" +
            "#endif\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        assertArrayEquals(new int[]{0}, ShaderPackRuntime.parseDrawBuffers(stripped));
    }

    @Test
    @DisplayName("RENDERTARGETS directive inside active branch is honoured")
    void rendertargetsActive() {
        String src =
            "#if 1\n" +
            "/* RENDERTARGETS: 0,2,8 */\n" +
            "#else\n" +
            "/* RENDERTARGETS: 0 */\n" +
            "#endif\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        assertArrayEquals(new int[]{0, 2, 8}, ShaderPackRuntime.parseDrawBuffers(stripped));
    }

    @Test
    @DisplayName("no directive anywhere defaults to colortex0")
    void noDirectiveDefaults() {
        String src = "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        assertArrayEquals(new int[]{0}, ShaderPackRuntime.parseDrawBuffers(stripped));
    }

    @Test
    @DisplayName("line count is preserved (blanked, not deleted)")
    void preservesLineCount() {
        String src =
            "#ifdef NOPE\n" +
            "dead line 1\n" +
            "dead line 2\n" +
            "#else\n" +
            "live line\n" +
            "#endif\n";
        String stripped = GlslPreprocessor.stripInactiveBranches(src, null);
        assertEquals(count(src, '\n'), count(stripped, '\n'),
            "newline count must match so GL error line numbers stay aligned");
        assertTrue(stripped.contains("live line"));
        assertFalse(stripped.contains("dead line"));
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
}
