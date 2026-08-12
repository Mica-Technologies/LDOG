package com.limitlessdev.ldog.render.pipeline;

import com.limitlessdev.ldog.LDOGMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal GLSL compile + link + bind wrapper for LDOG pipeline passes.
 *
 * Targets MC 1.12.2's OpenGL compatibility profile and LWJGL 2.9.4. The
 * vertex shader should read gl_Vertex / gl_MultiTexCoord0 (immediate-mode
 * compatibility inputs) so callers can draw a fullscreen quad via glBegin
 * / glVertex without needing a VAO.
 *
 * Creation throws a {@link ShaderCompileException} on compile/link failure
 * with the GL info log attached, so callers can log + fall back instead
 * of running a half-initialized program.
 */
public final class ShaderProgram {

    private final int programId;
    private final String name;
    private final Map<String, Integer> uniformCache = new HashMap<>();
    private boolean disposed;
    /**
     * Whether to warn when a uniform isn't found. True for LDOG's own passes
     * (a missing uniform there is a likely typo worth surfacing). Set false for
     * shader-pack programs, which legitimately declare only a small subset of the
     * standard OF/Iris uniform set — warning on the rest produces hundreds of
     * noise lines per pack with no signal.
     */
    private boolean warnMissingUniforms = true;

    public ShaderProgram(String name, String vertSource, String fragSource) throws ShaderCompileException {
        this.name = name;

        int vert = compile(GL20.GL_VERTEX_SHADER, vertSource, name + ":vert");
        int frag;
        try {
            frag = compile(GL20.GL_FRAGMENT_SHADER, fragSource, name + ":frag");
        } catch (ShaderCompileException e) {
            GL20.glDeleteShader(vert);
            throw e;
        }

        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vert);
        GL20.glAttachShader(prog, frag);
        // Bind OptiFine/Iris custom vertex attributes to SAFE generic locations
        // (11+) BEFORE linking. Otherwise the linker may auto-assign them to
        // low locations (1..10) that alias the fixed-function arrays MC enables
        // for terrain/entity VBOs (gl_Vertex=0, gl_Normal=2, gl_Color=3,
        // gl_MultiTexCoord0=8, gl_MultiTexCoord1=9), producing a GL_INVALID_
        // OPERATION on every draw — a per-chunk error flood that also tanks
        // performance. glBindAttribLocation is a no-op for names the shader
        // doesn't declare, so this is harmless for LDOG's own passes.
        GL20.glBindAttribLocation(prog, 11, "mc_Entity");
        GL20.glBindAttribLocation(prog, 12, "mc_midTexCoord");
        GL20.glBindAttribLocation(prog, 13, "at_tangent");
        GL20.glBindAttribLocation(prog, 14, "at_velocity");
        GL20.glBindAttribLocation(prog, 15, "at_midBlock");
        GL20.glLinkProgram(prog);

        int linked = GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS);
        if (linked == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(prog, 4096);
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            GL20.glDeleteProgram(prog);
            throw new ShaderCompileException("Link failed for " + name + ":\n" + log);
        }

        // Shaders can be detached + deleted once linked; the program retains them.
        GL20.glDetachShader(prog, vert);
        GL20.glDetachShader(prog, frag);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);

        this.programId = prog;
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public static void unbind() {
        GL20.glUseProgram(0);
    }

    public int programId() { return programId; }

    /**
     * Suppress "no active uniform" warnings for this program. Call for shader-pack
     * programs, which intentionally use only part of the standard uniform set.
     */
    public ShaderProgram quietMissingUniforms() {
        this.warnMissingUniforms = false;
        return this;
    }

    public void setUniform1i(String uniform, int value) {
        int loc = locate(uniform);
        if (loc >= 0) GL20.glUniform1i(loc, value);
    }

    public void setUniform1f(String uniform, float value) {
        int loc = locate(uniform);
        if (loc >= 0) GL20.glUniform1f(loc, value);
    }

    public void setUniform2f(String uniform, float x, float y) {
        int loc = locate(uniform);
        if (loc >= 0) GL20.glUniform2f(loc, x, y);
    }

    /**
     * Integer 2-vector. GL matches uniform setter type to the DECLARED GLSL
     * type — feeding an {@code ivec2} through glUniform2f raises
     * GL_INVALID_OPERATION and silently leaves the uniform at zero. The OF /
     * Iris spec declares {@code eyeBrightness} / {@code eyeBrightnessSmooth}
     * as {@code ivec2}, so they must come through here.
     */
    public void setUniform2i(String uniform, int x, int y) {
        int loc = locate(uniform);
        if (loc >= 0) GL20.glUniform2i(loc, x, y);
    }

    public void setUniform3f(String uniform, float x, float y, float z) {
        int loc = locate(uniform);
        if (loc >= 0) GL20.glUniform3f(loc, x, y, z);
    }

    public void setUniform4f(String uniform, float x, float y, float z, float w) {
        int loc = locate(uniform);
        if (loc >= 0) GL20.glUniform4f(loc, x, y, z, w);
    }

    /** Buffer must be positioned at 0 and contain 16 column-major floats. */
    public void setUniformMatrix4(String uniform, FloatBuffer mat4) {
        int loc = locate(uniform);
        if (loc >= 0) GL20.glUniformMatrix4(loc, false, mat4);
    }

    public void dispose() {
        if (disposed) return;
        GL20.glDeleteProgram(programId);
        uniformCache.clear();
        disposed = true;
    }

    private int locate(String uniform) {
        Integer cached = uniformCache.get(uniform);
        if (cached != null) return cached;
        int loc = GL20.glGetUniformLocation(programId, uniform);
        uniformCache.put(uniform, loc);
        if (loc < 0 && warnMissingUniforms) {
            // Uniforms the GLSL compiler optimized out show up as -1. Log once
            // per uniform so accidental typos surface without spamming. Pack
            // programs opt out (quietMissingUniforms) — they omit most uniforms
            // by design, so the warning is pure noise there.
            LDOGMod.LOGGER.warn("LDOG: Shader '{}' has no active uniform '{}'", name, uniform);
        }
        return loc;
    }

    private static int compile(int type, String source, String label) throws ShaderCompileException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        int status = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (status == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new ShaderCompileException("Compile failed for " + label + ":\n" + log);
        }
        return shader;
    }

    public static final class ShaderCompileException extends Exception {
        public ShaderCompileException(String message) {
            super(message);
        }
    }
}
