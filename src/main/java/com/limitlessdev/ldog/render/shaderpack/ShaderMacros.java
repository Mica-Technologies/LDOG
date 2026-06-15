package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OptiFine/Iris standard preprocessor macros — the builtin {@code MC_*} defines
 * every real shader pack expects the loader to inject before compilation.
 *
 * <p>Packs gate hardware/version-specific code (and, crucially for us, their
 * {@code DRAWBUFFERS} sets) on these:
 * <pre>
 *   #if MC_VERSION &gt;= 11300
 *   /* DRAWBUFFERS:0123 *​/      // 1.13+ path — must NOT be taken on 1.12.2
 *   #else
 *   /* DRAWBUFFERS:01 *​/
 *   #endif
 * </pre>
 * Without these defined, an identifier like {@code MC_VERSION} evaluates to 0 in
 * both the driver and {@link GlslPreprocessor}, so the wrong branch is chosen.
 * Injecting accurate values makes version-gated modern paths correctly switch
 * OFF (they can't run on GL 2.1 anyway) and lets the 1.12.2 path light up.
 *
 * <p>Two outputs, kept in lockstep:
 * <ul>
 *   <li>{@link #injectDefines} prepends the macros into the source the GL driver
 *       compiles (after {@code #version}, which GLSL requires to come first);</li>
 *   <li>the same {@link #standardDefines()} map is handed to
 *       {@link GlslPreprocessor#stripInactiveBranches} so our DRAWBUFFERS
 *       resolution sees exactly what the driver sees.</li>
 * </ul>
 *
 * <p><b>Deliberately omitted:</b> {@code MC_NORMAL_MAP} / {@code MC_SPECULAR_MAP}.
 * LDOG doesn't yet supply normal/specular textures to gbuffer programs (those
 * samplers read black), so leaving these undefined makes packs take their
 * no-map fallback — honest capability signalling rather than a black-normal
 * artifact. Add them only once those textures are real.
 *
 * <p>The pure builders ({@link #buildDefines}, {@link #injectDefines}) take their
 * inputs as plain strings so they're unit-testable without a GL context.
 */
public final class ShaderMacros {

    private ShaderMacros() {}

    /** 1.12.2, in OptiFine's {@code major*10000 + minor*100 + patch} encoding. */
    private static final String MC_VERSION_VALUE = "11202";

    private static Map<String, String> cached;

    /**
     * The standard macro set for the current GL context + OS. Queried once and
     * cached (GL strings are constant for the process). Must be called on the GL
     * thread (it is — pack compilation runs on the render thread).
     */
    public static Map<String, String> standardDefines() {
        if (cached != null) return cached;
        String glVersion = safeGet(GL11.GL_VERSION);
        String glslVersion = safeGet(GL20.GL_SHADING_LANGUAGE_VERSION);
        String vendor = safeGet(GL11.GL_VENDOR);
        String renderer = safeGet(GL11.GL_RENDERER);
        String os = System.getProperty("os.name", "");
        cached = buildDefines(glVersion, glslVersion, vendor, renderer, os);
        LDOGMod.LOGGER.info("LDOG: shader macros — MC_GL_VERSION={}, MC_GLSL_VERSION={}, vendor/renderer/os flags from '{}' / '{}' / '{}'",
            cached.get("MC_GL_VERSION"), cached.get("MC_GLSL_VERSION"), vendor, renderer, os);
        return cached;
    }

    private static String safeGet(int name) {
        try {
            String s = GL11.glGetString(name);
            return s == null ? "" : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Build the macro map from raw GL/OS strings. Pure — no GL calls.
     *
     * @param glVersion   {@code GL_VERSION}, e.g. {@code "2.1.0 NVIDIA ..."}
     * @param glslVersion {@code GL_SHADING_LANGUAGE_VERSION}, e.g. {@code "1.20 ..."}
     * @param vendor      {@code GL_VENDOR}, e.g. {@code "NVIDIA Corporation"}
     * @param renderer    {@code GL_RENDERER}, e.g. {@code "GeForce RTX 3080/PCIe/SSE2"}
     * @param osName      {@code os.name} system property
     */
    static Map<String, String> buildDefines(String glVersion, String glslVersion,
                                            String vendor, String renderer, String osName) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MC_VERSION", MC_VERSION_VALUE);
        m.put("MC_GL_VERSION", Integer.toString(parseGlVersion(glVersion)));
        m.put("MC_GLSL_VERSION", Integer.toString(parseGlslVersion(glslVersion)));

        // OS flag (valueless — presence only).
        String os = osName == null ? "" : osName.toLowerCase();
        if (os.contains("win"))                              m.put("MC_OS_WINDOWS", "");
        else if (os.contains("mac") || os.contains("darwin")) m.put("MC_OS_MAC", "");
        else if (os.contains("linux") || os.contains("nix") || os.contains("nux")) m.put("MC_OS_LINUX", "");
        else                                                  m.put("MC_OS_OTHER", "");

        // Vendor flag.
        String v = vendor == null ? "" : vendor.toLowerCase();
        if (v.contains("nvidia"))      m.put("MC_GL_VENDOR_NVIDIA", "");
        else if (v.contains("amd"))    m.put("MC_GL_VENDOR_AMD", "");
        else if (v.contains("ati"))    m.put("MC_GL_VENDOR_ATI", "");
        else if (v.contains("intel"))  m.put("MC_GL_VENDOR_INTEL", "");
        else if (v.contains("x.org"))  m.put("MC_GL_VENDOR_XORG", "");
        else if (v.contains("mesa"))   m.put("MC_GL_VENDOR_MESA", "");
        else                           m.put("MC_GL_VENDOR_OTHER", "");

        // Renderer flag.
        String r = renderer == null ? "" : renderer.toLowerCase();
        if (r.contains("geforce"))      m.put("MC_GL_RENDERER_GEFORCE", "");
        else if (r.contains("quadro"))  m.put("MC_GL_RENDERER_QUADRO", "");
        else if (r.contains("radeon"))  m.put("MC_GL_RENDERER_RADEON", "");
        else if (r.contains("gallium")) m.put("MC_GL_RENDERER_GALLIUM", "");
        else if (r.contains("intel"))   m.put("MC_GL_RENDERER_INTEL", "");
        else if (r.contains("mesa"))    m.put("MC_GL_RENDERER_MESA", "");
        else                            m.put("MC_GL_RENDERER_OTHER", "");

        // Quality / misc constants OptiFine always provides. LDOG renders the
        // world at native for packs (effectiveRenderScale) and shadows at 1:1.
        m.put("MC_RENDER_QUALITY", "1.0");
        m.put("MC_SHADOW_QUALITY", "1.0");
        m.put("MC_HAND_DEPTH", "0.125");
        return m;
    }

    /**
     * {@code GL_VERSION} -> OptiFine's {@code major*100 + minor*10} integer
     * (e.g. {@code "2.1.0 ..."} -> 210, {@code "4.6"} -> 460). Returns 0 if
     * unparseable so version-gated branches stay conservatively off.
     */
    static int parseGlVersion(String glVersion) {
        int[] mm = leadingMajorMinor(glVersion);
        if (mm == null) return 0;
        return mm[0] * 100 + mm[1] * 10;
    }

    /**
     * {@code GL_SHADING_LANGUAGE_VERSION} -> OptiFine's {@code major*100 + minor}
     * integer ({@code "1.20"} -> 120, {@code "4.60"} -> 460, {@code "3.30"} -> 330).
     * The minor is the literal two-digit fraction; a single-digit minor is scaled
     * to tens ({@code "1.2"} -> 120). Returns 0 if unparseable.
     */
    static int parseGlslVersion(String glslVersion) {
        String s = glslVersion == null ? "" : glslVersion.trim();
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0) return 0;
        int major = Integer.parseInt(s.substring(0, i));
        int minor = 0;
        if (i < s.length() && s.charAt(i) == '.') {
            int j = i + 1;
            int start = j;
            while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
            if (j > start) {
                String minorStr = s.substring(start, j);
                minor = Integer.parseInt(minorStr);
                if (minorStr.length() == 1) minor *= 10; // "1.2" -> 20
            }
        }
        return major * 100 + minor;
    }

    /** Parse the leading {@code major.minor} of a GL version string; null if none. */
    private static int[] leadingMajorMinor(String s) {
        if (s == null) return null;
        s = s.trim();
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0) return null;
        int major = Integer.parseInt(s.substring(0, i));
        int minor = 0;
        if (i < s.length() && s.charAt(i) == '.') {
            int j = i + 1;
            int start = j;
            while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
            if (j > start) minor = Integer.parseInt(s.substring(start, j));
        }
        return new int[]{major, minor};
    }

    /**
     * Prepend the {@code #define} block to a shader source, after its
     * {@code #version} line (GLSL requires {@code #version} to be the first
     * non-comment token). If no {@code #version} is present, the block goes at
     * the very top. Pure — no GL calls. Line count grows by the block size; the
     * inserted lines carry a marker comment for debuggability.
     */
    static String injectDefines(String source, Map<String, String> defines) {
        if (defines == null || defines.isEmpty()) return source;
        StringBuilder block = new StringBuilder();
        block.append("// [ldog] injected macros\n");
        for (Map.Entry<String, String> e : defines.entrySet()) {
            block.append("#define ").append(e.getKey());
            String val = e.getValue();
            if (val != null && !val.isEmpty()) block.append(' ').append(val);
            block.append('\n');
        }

        // Find the first #version line (ignoring leading whitespace).
        String[] lines = source.split("\n", -1);
        int versionIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i];
            int k = 0;
            while (k < t.length() && (t.charAt(k) == ' ' || t.charAt(k) == '\t')) k++;
            if (t.startsWith("#version", k)) { versionIdx = i; break; }
        }

        if (versionIdx < 0) {
            return block + source;
        }
        StringBuilder out = new StringBuilder(source.length() + block.length());
        for (int i = 0; i <= versionIdx; i++) {
            out.append(lines[i]).append('\n');
        }
        out.append(block);
        for (int i = versionIdx + 1; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    /** Convenience for callers: inject the standard set in one call (GL thread). */
    public static String inject(String source) {
        return injectDefines(source, standardDefines());
    }
}
