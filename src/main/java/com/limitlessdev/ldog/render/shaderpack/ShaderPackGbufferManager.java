package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-object draw-call dispatcher for an active shader pack's {@code gbuffers_*}
 * programs. Mixins wrapping each MC draw-call type call {@link #begin} before
 * the draw and {@link #end} after; in between, the matching pack program is
 * bound and fed the standard uniform set so the pack shades that geometry.
 *
 * <h3>How it works on MC 1.12.2's legacy pipeline</h3>
 *
 * <p>Vanilla 1.12.2 renders the world with the fixed-function pipeline (no GLSL
 * program bound — {@code GL_CURRENT_PROGRAM} is 0). Both immediate-mode draws
 * (sky, clouds, weather, hand) and the chunk VBO draws (terrain) feed the
 * classic built-in attributes {@code gl_Vertex}, {@code gl_Color},
 * {@code gl_MultiTexCoord0/1} and the {@code gl_ModelViewMatrix} /
 * {@code gl_ProjectionMatrix} matrices. A {@code #version 120} gbuffer shader
 * that uses {@code ftransform()} and those built-ins therefore transforms +
 * textures the geometry correctly without any custom vertex format. Binding the
 * pack program around the draw is enough to take over shading.
 *
 * <p>The block texture stays bound on unit 0 and the lightmap on unit 1 — the
 * units MC's world render already uses — so the {@code texture}/{@code lightmap}
 * samplers resolve to live data. {@code normals}/{@code specular} point at a
 * shared black texture (packs degrade to flat normals / no specular).
 *
 * <h3>v1 limitations (the remaining OF-parity gap)</h3>
 *
 * <ul>
 *   <li>Single render target — output goes to {@code colortex0} (the bound
 *       scene framebuffer). Packs writing extra G-buffer channels via
 *       {@code DRAWBUFFERS} / {@code gl_FragData[1..]} have those writes
 *       dropped, so deferred-lighting composites won't get normal/specular
 *       data yet. MRT attachments are the next step.</li>
 *   <li>No shadow pass — {@code shadowtex} samplers read black; the {@code shadow}
 *       programs are compiled but not yet driven by a sun-POV depth render.</li>
 *   <li>Custom vertex attributes ({@code mc_Entity}, {@code mc_midTexCoord},
 *       {@code at_tangent}) are absent, so block-id-aware and parallax effects
 *       degrade gracefully to their zero default.</li>
 * </ul>
 *
 * <p>Gated behind {@link LDOGConfig#enableShaderGbuffers} (opt-in, default off)
 * so the shipped composite-only path is never disturbed.
 */
public final class ShaderPackGbufferManager {

    private static final int GL_CURRENT_PROGRAM = 0x8B8D;

    /** Saved {@code GL_CURRENT_PROGRAM} ids to restore on {@link #end}. */
    private static final Deque<Integer> PROGRAM_STACK = new ArrayDeque<>();

    private static final ShaderPackUniforms UNIFORMS = new ShaderPackUniforms();
    private static boolean snapshottedThisFrame;

    /** 1x1 black texture for unused samplers (normals/specular/shadow). */
    private static int blackTex;

    private static boolean loggedFirstBind;

    private ShaderPackGbufferManager() {}

    /** True when the gbuffer dispatcher should attempt to take over draws. */
    public static boolean isActive() {
        if (!LDOGConfig.enableShaders || !LDOGConfig.enableShaderGbuffers) return false;
        ShaderPackRuntime rt = ShaderPackManager.INSTANCE.getRuntime();
        return rt != null && rt.hasGbuffers();
    }

    /**
     * Reset the per-frame uniform snapshot latch. Called once at the start of
     * each world render (renderSky HEAD) so the next {@link #begin} re-snapshots
     * the camera matrices / time / weather for this frame.
     */
    public static void beginFrame() {
        snapshottedThisFrame = false;
    }

    /**
     * Bind the pack program for {@code category} (resolving its fallback chain)
     * and feed it the standard uniforms. No-op — and pushes nothing — when the
     * dispatcher is inactive or the pack ships none of the category's programs,
     * so the paired {@link #end} stays balanced.
     */
    public static void begin(GbufferProgram category) {
        if (!isActive()) return;
        ShaderPackRuntime rt = ShaderPackManager.INSTANCE.getRuntime();
        if (rt == null) return;
        ShaderProgram program = rt.resolveGbuffer(category);
        if (program == null) return;

        ensureFrameSnapshot();
        ensureBlackTex();

        // Save whatever program was bound (0 during vanilla world render) so we
        // restore exactly that on end().
        int prev = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        PROGRAM_STACK.push(prev);

        program.bind();
        feedSamplers(program);
        UNIFORMS.feedTo(program);

        if (!loggedFirstBind) {
            loggedFirstBind = true;
            LDOGMod.LOGGER.info("LDOG: Shader pack gbuffer dispatch ACTIVE — first bind for {} ({})",
                category, rt.packName());
        }
    }

    /** Restore the program bound before the matching {@link #begin}. */
    public static void end() {
        if (PROGRAM_STACK.isEmpty()) return;
        int prev = PROGRAM_STACK.pop();
        GL20.glUseProgram(prev);
    }

    /** Free GL resources. Safe to call when nothing was allocated. */
    public static void dispose() {
        if (blackTex != 0) {
            GL11.glDeleteTextures(blackTex);
            blackTex = 0;
        }
        PROGRAM_STACK.clear();
        snapshottedThisFrame = false;
        loggedFirstBind = false;
    }

    private static void ensureFrameSnapshot() {
        if (snapshottedThisFrame) return;
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        // Roll last frame's matrices into the "previous" slot BEFORE snapshotting
        // this frame, so gbufferPreviousModelView/Projection hold a real one-frame
        // delta for motion-aware pack effects.
        UNIFORMS.rotatePrev();
        // Snapshot reads the live GL_MODELVIEW / GL_PROJECTION — which, at this
        // point in the world render, ARE the camera matrices we want for
        // gbufferModelView / gbufferProjection.
        UNIFORMS.snapshot(w, h, mc.getRenderPartialTicks());
        snapshottedThisFrame = true;
    }

    /**
     * Point the pack's well-known samplers at the right texture units. MC keeps
     * the block atlas on unit 0 and the lightmap on unit 1 during world render,
     * so those resolve to live data. Everything else gets the safe-zero black
     * texture bound on unit 2.
     */
    private static void feedSamplers(ShaderProgram program) {
        // Bind black on unit 2 for normals/specular/shadow samplers, then
        // restore the active unit to 0 (what MC's draw path expects).
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blackTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        // Color / albedo samplers (various pack naming conventions all alias 0).
        program.setUniform1i("texture", 0);
        program.setUniform1i("tex", 0);
        program.setUniform1i("gtexture", 0);
        program.setUniform1i("gcolor", 0);
        program.setUniform1i("colortex0", 0);
        // Lightmap on unit 1.
        program.setUniform1i("lightmap", 1);
        // Unused channels → black on unit 2.
        program.setUniform1i("normals", 2);
        program.setUniform1i("specular", 2);
        program.setUniform1i("shadow", 2);
        program.setUniform1i("shadowtex0", 2);
        program.setUniform1i("shadowtex1", 2);
        program.setUniform1i("shadowcolor0", 2);
        program.setUniform1i("shadowcolor1", 2);
        program.setUniform1i("noisetex", 2);
    }

    private static void ensureBlackTex() {
        if (blackTex != 0) return;
        blackTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, blackTex);
        java.nio.ByteBuffer zero = org.lwjgl.BufferUtils.createByteBuffer(4);
        zero.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zero);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
