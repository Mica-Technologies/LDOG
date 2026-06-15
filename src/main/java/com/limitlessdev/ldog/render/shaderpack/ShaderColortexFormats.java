package com.limitlessdev.ldog.render.shaderpack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses OptiFine/Iris buffer-format declarations from shader source and maps
 * them to GL internal formats, so LDOG allocates each {@code colortex} with the
 * precision the pack expects.
 *
 * <p>Packs declare formats as top-level constants in any program (usually
 * {@code final}/{@code composite}):
 * <pre>
 *   const int colortex0Format = R11F_G11F_B10F;  // HDR main scene
 *   const int colortex2Format = RGBA16;          // temporal data
 *   const int gaux2Format     = RGB10_A2;        // reflections
 * </pre>
 * The legacy {@code gaux1..4} names alias {@code colortex4..7} (OptiFine's fixed
 * historical mapping). Without honouring these, everything falls back to
 * {@code RGBA8}: HDR buffers clip to [0,1] (the "dark/washed" look on packs like
 * BSL) and 16-bit temporal/normal buffers lose precision.
 *
 * <p>Pure parsing + a name→GL-enum table; unit-testable without a GL context.
 * Unknown/unsupported format names return {@link #DEFAULT_FORMAT} so a typo or
 * exotic format degrades to RGBA8 rather than failing the pack.
 */
public final class ShaderColortexFormats {

    private ShaderColortexFormats() {}

    /** Fallback when a buffer has no declared (or no recognised) format. */
    public static final int DEFAULT_FORMAT = GL11.GL_RGBA8;

    /** gaux1..4 map to colortex4..7 (OptiFine legacy aliasing). */
    private static final int GAUX_BASE = 4;

    // const int colortex5Format = RGBA16F; / const int gaux2Format = RGB10_A2;
    private static final Pattern DECL = Pattern.compile(
        "const\\s+int\\s+(colortex(\\d+)|gaux([1-4]))Format\\s*=\\s*([A-Za-z0-9_]+)\\s*;");

    private static final Map<String, Integer> NAME_TO_GL = buildTable();

    /**
     * Scan {@code src} for buffer-format declarations and write each into
     * {@code into} (indexed by colortex number). Entries not present in {@code src}
     * are left untouched, so callers can accumulate across multiple program
     * sources. Indices beyond {@code into.length-1} are ignored.
     *
     * @param src  shader source (include-expanded)
     * @param into target array (index = colortex number); may be pre-seeded
     * @return {@code into}, for chaining
     */
    public static int[] parseInto(String src, int[] into) {
        if (src == null || into == null) return into;
        Matcher m = DECL.matcher(src);
        while (m.find()) {
            int index;
            if (m.group(2) != null) {
                index = Integer.parseInt(m.group(2));          // colortexN
            } else {
                index = GAUX_BASE + Integer.parseInt(m.group(3)) - 1; // gauxN -> colortex(4+N-1)
            }
            if (index < 0 || index >= into.length) continue;
            into[index] = glInternalFormat(m.group(4));
        }
        return into;
    }

    /** Map a format identifier (e.g. {@code "RGBA16F"}) to its GL internal format. */
    public static int glInternalFormat(String name) {
        if (name == null) return DEFAULT_FORMAT;
        Integer gl = NAME_TO_GL.get(name.trim());
        return gl != null ? gl : DEFAULT_FORMAT;
    }

    /** The pixel {@code format} arg for {@code glTexImage2D} given an internal format. */
    public static int uploadFormat(int internalFormat) {
        switch (channels(internalFormat)) {
            case 1:  return GL11.GL_RED;
            case 2:  return GL30.GL_RG;
            case 3:  return GL11.GL_RGB;
            default: return GL11.GL_RGBA;
        }
    }

    /** The pixel {@code type} arg for {@code glTexImage2D} given an internal format. */
    public static int uploadType(int internalFormat) {
        return isFloat(internalFormat) ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
    }

    private static boolean isFloat(int internal) {
        switch (internal) {
            case GL30.GL_R16F: case GL30.GL_RG16F: case GL30.GL_RGB16F: case GL30.GL_RGBA16F:
            case GL30.GL_R32F: case GL30.GL_RG32F: case GL30.GL_RGB32F: case GL30.GL_RGBA32F:
            case GL30.GL_R11F_G11F_B10F:
                return true;
            default:
                return false;
        }
    }

    private static int channels(int internal) {
        switch (internal) {
            case GL30.GL_R8: case GL30.GL_R16: case GL30.GL_R16F: case GL30.GL_R32F:
                return 1;
            case GL30.GL_RG8: case GL30.GL_RG16: case GL30.GL_RG16F: case GL30.GL_RG32F:
                return 2;
            case GL11.GL_RGB8: case GL11.GL_RGB16: case GL30.GL_RGB16F: case GL30.GL_RGB32F:
            case GL30.GL_R11F_G11F_B10F:
                return 3;
            default:
                return 4; // RGBA8/16/16F/32F, RGB10_A2, RGB5_A1, RGBA4, ...
        }
    }

    private static Map<String, Integer> buildTable() {
        Map<String, Integer> m = new HashMap<>();
        // 8-bit normalized
        m.put("R8", GL30.GL_R8);
        m.put("RG8", GL30.GL_RG8);
        m.put("RGB8", GL11.GL_RGB8);
        m.put("RGBA8", GL11.GL_RGBA8);
        // 16-bit normalized
        m.put("R16", GL30.GL_R16);
        m.put("RG16", GL30.GL_RG16);
        m.put("RGB16", GL11.GL_RGB16);
        m.put("RGBA16", GL11.GL_RGBA16);
        // 16-bit float
        m.put("R16F", GL30.GL_R16F);
        m.put("RG16F", GL30.GL_RG16F);
        m.put("RGB16F", GL30.GL_RGB16F);
        m.put("RGBA16F", GL30.GL_RGBA16F);
        // 32-bit float
        m.put("R32F", GL30.GL_R32F);
        m.put("RG32F", GL30.GL_RG32F);
        m.put("RGB32F", GL30.GL_RGB32F);
        m.put("RGBA32F", GL30.GL_RGBA32F);
        // packed
        m.put("R11F_G11F_B10F", GL30.GL_R11F_G11F_B10F);
        m.put("RGB10_A2", GL11.GL_RGB10_A2);
        m.put("RGB5_A1", GL11.GL_RGB5_A1);
        m.put("RGBA4", GL11.GL_RGBA4);
        m.put("RGBA2", GL11.GL_RGBA2);
        return m;
    }
}
