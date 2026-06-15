package com.limitlessdev.ldog.render.shaderpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ShaderMacros} pure helpers (no GL context needed) and
 * their interaction with {@link GlslPreprocessor} for version-gated DRAWBUFFERS.
 */
class ShaderMacrosTest {

    @Test
    @DisplayName("parseGlVersion: major*100 + minor*10")
    void glVersion() {
        assertEquals(210, ShaderMacros.parseGlVersion("2.1.0 NVIDIA 555.55"));
        assertEquals(460, ShaderMacros.parseGlVersion("4.6"));
        assertEquals(330, ShaderMacros.parseGlVersion("3.3.0 Core Profile"));
        assertEquals(0, ShaderMacros.parseGlVersion(""));
        assertEquals(0, ShaderMacros.parseGlVersion("not a version"));
    }

    @Test
    @DisplayName("parseGlslVersion: major*100 + minor (two-digit fraction)")
    void glslVersion() {
        assertEquals(120, ShaderMacros.parseGlslVersion("1.20"));
        assertEquals(120, ShaderMacros.parseGlslVersion("1.2"));   // single-digit minor scaled
        assertEquals(330, ShaderMacros.parseGlslVersion("3.30 NVIDIA"));
        assertEquals(460, ShaderMacros.parseGlslVersion("4.60"));
        assertEquals(0, ShaderMacros.parseGlslVersion(null));
    }

    @Test
    @DisplayName("buildDefines: core MC_* present with expected values")
    void coreDefines() {
        Map<String, String> m = ShaderMacros.buildDefines(
            "2.1.0", "1.20", "NVIDIA Corporation", "GeForce RTX 3080/PCIe/SSE2", "Windows 11");
        assertEquals("11202", m.get("MC_VERSION"));
        assertEquals("210", m.get("MC_GL_VERSION"));
        assertEquals("120", m.get("MC_GLSL_VERSION"));
        assertEquals("1.0", m.get("MC_RENDER_QUALITY"));
        assertEquals("1.0", m.get("MC_SHADOW_QUALITY"));
        assertEquals("0.125", m.get("MC_HAND_DEPTH"));
    }

    @Test
    @DisplayName("buildDefines: OS / vendor / renderer flags are mutually exclusive")
    void platformFlags() {
        Map<String, String> nv = ShaderMacros.buildDefines(
            "2.1", "1.20", "NVIDIA Corporation", "GeForce GTX 1060", "Windows 10");
        assertTrue(nv.containsKey("MC_OS_WINDOWS"));
        assertTrue(nv.containsKey("MC_GL_VENDOR_NVIDIA"));
        assertTrue(nv.containsKey("MC_GL_RENDERER_GEFORCE"));
        assertFalse(nv.containsKey("MC_OS_LINUX"));
        assertFalse(nv.containsKey("MC_GL_VENDOR_AMD"));

        Map<String, String> amd = ShaderMacros.buildDefines(
            "2.1", "1.20", "ATI Technologies Inc.", "AMD Radeon RX 580", "Linux");
        assertTrue(amd.containsKey("MC_OS_LINUX"));
        assertTrue(amd.containsKey("MC_GL_VENDOR_ATI"));
        assertTrue(amd.containsKey("MC_GL_RENDERER_RADEON"));

        Map<String, String> intel = ShaderMacros.buildDefines(
            "2.1", "1.20", "Intel", "Intel(R) HD Graphics 620", "Mac OS X");
        assertTrue(intel.containsKey("MC_OS_MAC"));
        assertTrue(intel.containsKey("MC_GL_VENDOR_INTEL"));
        assertTrue(intel.containsKey("MC_GL_RENDERER_INTEL"));
    }

    @Test
    @DisplayName("injectDefines: block goes right after #version")
    void injectAfterVersion() {
        String src = "#version 120\nuniform sampler2D x;\nvoid main(){}\n";
        Map<String, String> m = ShaderMacros.buildDefines("2.1", "1.20", "NVIDIA", "GeForce", "Windows");
        String out = ShaderMacros.injectDefines(src, m);
        int vIdx = out.indexOf("#version 120");
        int defIdx = out.indexOf("#define MC_VERSION 11202");
        int uIdx = out.indexOf("uniform sampler2D x;");
        assertTrue(vIdx >= 0 && defIdx > vIdx && uIdx > defIdx,
            "macros must sit between #version and the first real line");
        assertTrue(out.contains("#define MC_OS_WINDOWS\n"), "valueless define has no value");
    }

    @Test
    @DisplayName("injectDefines: no #version -> block at top")
    void injectNoVersion() {
        String src = "void main(){}\n";
        Map<String, String> m = ShaderMacros.buildDefines("2.1", "1.20", "NVIDIA", "GeForce", "Windows");
        String out = ShaderMacros.injectDefines(src, m);
        assertTrue(out.indexOf("#define MC_VERSION") < out.indexOf("void main"));
    }

    @Test
    @DisplayName("version-gated DRAWBUFFERS resolves to the 1.12.2 branch")
    void versionGatedDrawBuffers() {
        // A pack that uses extra targets on 1.13+ but a slim set on 1.12.2.
        String frag =
            "#version 120\n" +
            "#if MC_VERSION >= 11300\n" +
            "/* DRAWBUFFERS:01234 */\n" +
            "#else\n" +
            "/* DRAWBUFFERS:012 */\n" +
            "#endif\n" +
            "void main(){}\n";
        Map<String, String> m = ShaderMacros.buildDefines("2.1", "1.20", "NVIDIA", "GeForce", "Windows");
        String active = GlslPreprocessor.stripInactiveBranches(frag, m);
        assertArrayEquals(new int[]{0, 1, 2}, ShaderPackRuntime.parseDrawBuffers(active));
    }

    @Test
    @DisplayName("vendor-gated branch follows the injected vendor flag")
    void vendorGatedBranch() {
        String frag =
            "#ifdef MC_GL_VENDOR_NVIDIA\n" +
            "/* DRAWBUFFERS:0123 */\n" +
            "#else\n" +
            "/* DRAWBUFFERS:0 */\n" +
            "#endif\n";
        Map<String, String> nv = ShaderMacros.buildDefines("2.1", "1.20", "NVIDIA", "GeForce", "Windows");
        assertArrayEquals(new int[]{0, 1, 2, 3},
            ShaderPackRuntime.parseDrawBuffers(GlslPreprocessor.stripInactiveBranches(frag, nv)));
        Map<String, String> amd = ShaderMacros.buildDefines("2.1", "1.20", "AMD", "Radeon", "Windows");
        assertArrayEquals(new int[]{0},
            ShaderPackRuntime.parseDrawBuffers(GlslPreprocessor.stripInactiveBranches(frag, amd)));
    }
}
