package com.limitlessdev.ldog.render.shaderpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for {@link ShaderColortexFormats} (pure parsing, no GL context). */
class ShaderColortexFormatsTest {

    @Test
    @DisplayName("known format names map to their GL internal format")
    void nameMapping() {
        assertEquals(GL11.GL_RGBA8, ShaderColortexFormats.glInternalFormat("RGBA8"));
        assertEquals(GL30.GL_RGBA16F, ShaderColortexFormats.glInternalFormat("RGBA16F"));
        assertEquals(GL30.GL_R11F_G11F_B10F, ShaderColortexFormats.glInternalFormat("R11F_G11F_B10F"));
        assertEquals(GL11.GL_RGB10_A2, ShaderColortexFormats.glInternalFormat("RGB10_A2"));
        assertEquals(GL11.GL_RGB16, ShaderColortexFormats.glInternalFormat("RGB16"));
        assertEquals(GL30.GL_R8, ShaderColortexFormats.glInternalFormat("R8"));
    }

    @Test
    @DisplayName("unknown / null format falls back to RGBA8")
    void unknownFallsBack() {
        assertEquals(ShaderColortexFormats.DEFAULT_FORMAT, ShaderColortexFormats.glInternalFormat("BOGUS"));
        assertEquals(ShaderColortexFormats.DEFAULT_FORMAT, ShaderColortexFormats.glInternalFormat(null));
    }

    @Test
    @DisplayName("parses BSL-style colortexNFormat declarations")
    void parsesColortexDecls() {
        String src =
            "const int colortex0Format = R11F_G11F_B10F; //main scene\n" +
            "const int colortex1Format = RGB8;\n" +
            "const int colortex2Format = RGBA16; //temporal\n";
        int[] f = ShaderColortexFormats.parseInto(src, new int[8]);
        assertEquals(GL30.GL_R11F_G11F_B10F, f[0]);
        assertEquals(GL11.GL_RGB8, f[1]);
        assertEquals(GL11.GL_RGBA16, f[2]);
        assertEquals(0, f[3], "undeclared buffer left as 0 (sentinel for 'use default')");
    }

    @Test
    @DisplayName("gaux1..4 alias colortex4..7")
    void gauxAliasing() {
        String src =
            "const int gaux1Format = R8;\n" +
            "const int gaux2Format = RGB10_A2;\n" +
            "const int gaux3Format = RGB16;\n" +
            "const int gaux4Format = RGB16;\n";
        int[] f = ShaderColortexFormats.parseInto(src, new int[8]);
        assertEquals(GL30.GL_R8, f[4]);
        assertEquals(GL11.GL_RGB10_A2, f[5]);
        assertEquals(GL11.GL_RGB16, f[6]);
        assertEquals(GL11.GL_RGB16, f[7]);
    }

    @Test
    @DisplayName("accumulates across multiple sources; whitespace-tolerant")
    void accumulateAcrossSources() {
        int[] f = new int[8];
        ShaderColortexFormats.parseInto("const  int   colortex5Format=RGBA32F ;", f);
        ShaderColortexFormats.parseInto("const int colortex0Format = RGBA16F;", f);
        assertEquals(GL30.GL_RGBA32F, f[5]);
        assertEquals(GL30.GL_RGBA16F, f[0]);
    }

    @Test
    @DisplayName("indices beyond the target array are ignored, not thrown")
    void outOfRangeIgnored() {
        int[] f = new int[8];
        // colortex9 exceeds our 0..7 aux range — must be skipped silently.
        ShaderColortexFormats.parseInto("const int colortex9Format = RGB16F;", f);
        for (int v : f) assertEquals(0, v);
    }

    @Test
    @DisplayName("upload format/type derive from internal format")
    void uploadParams() {
        assertEquals(GL11.GL_FLOAT, ShaderColortexFormats.uploadType(GL30.GL_RGBA16F));
        assertEquals(GL11.GL_UNSIGNED_BYTE, ShaderColortexFormats.uploadType(GL11.GL_RGBA8));
        assertEquals(GL11.GL_FLOAT, ShaderColortexFormats.uploadType(GL30.GL_R11F_G11F_B10F));
        assertEquals(GL11.GL_RED, ShaderColortexFormats.uploadFormat(GL30.GL_R8));
        assertEquals(GL11.GL_RGB, ShaderColortexFormats.uploadFormat(GL11.GL_RGB8));
        assertEquals(GL11.GL_RGBA, ShaderColortexFormats.uploadFormat(GL11.GL_RGBA8));
    }
}
