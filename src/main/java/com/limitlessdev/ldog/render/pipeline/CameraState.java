package com.limitlessdev.ldog.render.pipeline;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import java.nio.FloatBuffer;

/**
 * Per-frame view+projection matrix state for Phase 9c.2 temporal reprojection.
 *
 * Captures the current world-pass projection × modelview on request, shifts
 * the previous-frame matrix into its own slot, and also pre-computes the
 * inverse of the current combined matrix so TAA's shader can reconstruct
 * world-space position from depth.
 *
 * Not thread-safe — MC's render loop is single-threaded on the client
 * thread, which is the only caller.
 *
 * Matrices are captured POST-jitter, on purpose. History textures store each
 * frame with that frame's jitter baked in, so cur/prev must describe what was
 * actually rendered — capturing the un-jittered "logical" camera makes the
 * reprojection unproject to the wrong world position and land on the wrong
 * history texel, a fractional offset that swings with the Halton cycle (the
 * "swimming" artifact). {@code MixinEntityRendererJitter} therefore calls
 * {@link #captureCurrentMatrices()} right after applying the jitter to the
 * terrain projection. See that mixin for the full rationale.
 */
public final class CameraState {

    private static final Matrix4f scratchProj = new Matrix4f();
    private static final Matrix4f scratchView = new Matrix4f();
    private static final Matrix4f curViewProj = new Matrix4f();
    private static final Matrix4f curInvViewProj = new Matrix4f();
    private static final Matrix4f prevViewProj = new Matrix4f();
    private static boolean hasPrev = false;
    private static final FloatBuffer scratchBuf = BufferUtils.createFloatBuffer(16);

    private CameraState() {}

    /**
     * Read current GL_PROJECTION × GL_MODELVIEW, rotate previous → cur,
     * compute the new inverse. Must run on the GL thread while the terrain
     * camera matrices are bound.
     */
    public static void captureCurrentMatrices() {
        // Save previous cur before overwriting.
        if (hasPrev) {
            prevViewProj.load(curViewProj);
        }

        scratchBuf.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, scratchBuf);
        scratchBuf.rewind();
        scratchProj.load(scratchBuf);

        scratchBuf.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, scratchBuf);
        scratchBuf.rewind();
        scratchView.load(scratchBuf);

        Matrix4f.mul(scratchProj, scratchView, curViewProj);
        Matrix4f.invert(curViewProj, curInvViewProj);

        // First frame: prev := cur so the initial MV is zero (no reprojection
        // garbage before we have two real frames of history).
        if (!hasPrev) {
            prevViewProj.load(curViewProj);
            hasPrev = true;
        }
    }

    /** Drop captured state — call on world change, resource reload, etc. */
    public static void reset() {
        hasPrev = false;
    }

    public static boolean isReady() {
        return hasPrev;
    }

    /** Fills the buffer with the current-frame inverse viewProj (column-major, 16 floats). */
    public static void writeCurInvViewProj(FloatBuffer dst) {
        dst.clear();
        curInvViewProj.store(dst);
        dst.rewind();
    }

    /** Fills the buffer with the previous-frame viewProj (column-major, 16 floats). */
    public static void writePrevViewProj(FloatBuffer dst) {
        dst.clear();
        prevViewProj.store(dst);
        dst.rewind();
    }
}
