package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;

import java.nio.FloatBuffer;

/**
 * Sun/moon-POV shadow map for the active shader pack. Renders a depth-only,
 * camera-relative pass of the terrain from the light's point of view into
 * {@code shadowtex0/1}, and supplies the {@code shadowModelView}/
 * {@code shadowProjection} (+inverse) uniforms composites use to sample it.
 *
 * <h3>How the geometry replay works</h3>
 *
 * <p>MC renders terrain camera-relative (chunk VBOs are drawn with a per-chunk
 * {@code glTranslate(chunkPos - cameraPos)} on top of the camera view matrix).
 * To capture the same geometry from the light, we just swap the GL matrices:
 * an orthographic projection + a {@code gluLookAt} from the light direction
 * toward the camera origin, then re-invoke {@link net.minecraft.client.renderer.RenderGlobal#renderBlockLayer}
 * for the opaque + cutout layers. We read the resulting GL matrices back for
 * the uniforms, so no hand-rolled matrix math is needed.
 *
 * <h3>v1 limitations</h3>
 *
 * <ul>
 *   <li>Uses the <em>camera</em> chunk-visibility list, so geometry behind the
 *       camera may not cast (OptiFine prepares a separate shadow frustum).</li>
 *   <li>Terrain only — no entity/TESR shadow casters yet.</li>
 *   <li>Fixed-function depth (no {@code shadow.vsh/fsh} program), so colored /
 *       alpha-blended shadows aren't produced.</li>
 * </ul>
 *
 * <p>Gated behind {@link LDOGConfig#enableShaderShadows} (opt-in, default off).
 */
public final class ShadowMapManager {

    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;

    private static int shadowFbo;
    private static int shadowDepthTex;
    private static int resolution;
    private static boolean renderedThisFrame;

    private static final FloatBuffer PROJ_BUF = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MV_BUF = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer INV_BUF = BufferUtils.createFloatBuffer(16);
    private static final Matrix4f SCRATCH = new Matrix4f();
    private static final int[] VIEWPORT = new int[4];

    /** Suppresses the per-object gbuffer dispatch while the shadow pass runs. */
    private static boolean shadowPass;

    /** True once this frame's shadow matrices have been computed (depth render optional). */
    private static boolean hasMatrices;

    private ShadowMapManager() {}

    public static boolean isShadowPass() { return shadowPass; }

    /** True when shadows should run: opt-in flag + deferred path available. */
    public static boolean isEnabled() {
        return LDOGConfig.enableShaderShadows && ShaderPackGbufferManager.isDeferredActive();
    }

    /** True when a shadow map was produced this frame (composites can sample it). */
    public static boolean isReady() {
        return renderedThisFrame && shadowDepthTex != 0;
    }

    public static int depthTexture() { return shadowDepthTex; }

    public static void beginFrame() { renderedThisFrame = false; hasMatrices = false; }

    /**
     * Render the shadow map. Saves + restores all GL matrix/FBO/viewport state
     * so the caller's render continues unaffected. Called mid-world-pass once
     * the opaque terrain + chunk list are ready (renderEntities HEAD).
     */
    public static void render(float partialTicks) {
        // Runs whenever a pack is driving — NOT only when shadows are enabled.
        // Composites compute a shadow lookup unconditionally; if we don't feed
        // valid shadowProjection/shadowModelView the coordinate degenerates to
        // NaN and the sun term collapses to zero (the "dark even at noon" bug).
        if (!ShaderPackGbufferManager.isDeferredActive()) return;
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        Entity view = mc.getRenderViewEntity();
        if (world == null || view == null || mc.renderGlobal == null) return;

        // --- light direction (matches ShaderPackUniforms' sun/moon) ---
        float sa = world.getCelestialAngle(partialTicks) * (float) (2.0 * Math.PI);
        float lx = (float) -Math.sin(sa);
        float ly = (float) Math.cos(sa);
        boolean night = ly < 0.0f;
        if (night) { lx = -lx; ly = -ly; } // moon is the caster at night
        float lz = 0.0f;
        float upx = 0f, upy = 1f, upz = 0f;
        if (Math.abs(ly) > 0.99f) { upx = 0f; upy = 0f; upz = 1f; }

        int d = LDOGConfig.shaderShadowDistance;
        float ex = lx * d, ey = ly * d, ez = lz * d;

        // Compute the shadow matrices on the GL stack and read them back. Done
        // every frame a pack is active (cheap) so composites always have valid
        // matrices, even when the depth render below is disabled.
        int prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(-d, d, -d, d, 0.05, d * 2.5);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GLU.gluLookAt(ex, ey, ez, 0f, 0f, 0f, upx, upy, upz);
        PROJ_BUF.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJ_BUF);
        MV_BUF.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MV_BUF);
        hasMatrices = true;

        // Render the actual depth map only when the shadow toggle is on.
        if (LDOGConfig.enableShaderShadows && ensure()) {
            saveViewport();
            int prevFbo = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glViewport(0, 0, resolution, resolution);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(2.5f, 4.0f);

            shadowPass = true;
            try {
                mc.renderGlobal.renderBlockLayer(BlockRenderLayer.SOLID, partialTicks, 2, view);
                mc.renderGlobal.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, partialTicks, 2, view);
            } catch (Throwable t) {
                LDOGMod.LOGGER.error("LDOG: Shadow terrain pass failed, disabling shadows for this session", t);
                LDOGConfig.enableShaderShadows = false;
            } finally {
                shadowPass = false;
            }

            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            // Rebind the previous framebuffer BEFORE popAttrib so the restored
            // draw/read-buffer state applies to that FBO, not the (color-less)
            // shadow FBO — restoring e.g. COLOR_ATTACHMENT0 against the shadow
            // FBO is a GL_INVALID_OPERATION.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL11.glPopAttrib();
            GL11.glViewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
            renderedThisFrame = true;
        }

        // Pop the shadow matrices, restoring the world camera matrices, then the
        // caller's original matrix mode.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(prevMatrixMode);
    }

    /**
     * Bind a depth texture to {@code unit} for the shadow samplers and feed the
     * shadow uniforms. ALWAYS binds a compare-mode depth texture (the real
     * shadow map when one was produced this frame, else a 1x1 far-depth dummy
     * = everything lit) — packs declare {@code shadowtex0/1} as
     * {@code sampler2DShadow}, so binding anything that isn't a compare-mode
     * depth texture there triggers a GL_INVALID_OPERATION on every draw.
     */
    public static void feed(ShaderProgram program, int unit) {
        int tex = isReady() ? shadowDepthTex : ensureDummy();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        program.setUniform1i("shadowtex0", unit);
        program.setUniform1i("shadowtex1", unit);
        program.setUniform1i("shadow", unit);
        program.setUniform1i("watershadow", unit);
        program.setUniform1f("shadowMapResolution", isReady() ? resolution : 1);

        // Feed the matrices whenever they were computed this frame (i.e. a pack
        // is active), even if the depth render was skipped — otherwise the
        // composite's shadow coordinate is degenerate and kills the sun term.
        if (hasMatrices) {
            PROJ_BUF.position(0);
            program.setUniformMatrix4("shadowProjection", PROJ_BUF);
            MV_BUF.position(0);
            program.setUniformMatrix4("shadowModelView", MV_BUF);
            invertInto(PROJ_BUF, INV_BUF);
            program.setUniformMatrix4("shadowProjectionInverse", INV_BUF);
            invertInto(MV_BUF, INV_BUF);
            program.setUniformMatrix4("shadowModelViewInverse", INV_BUF);
        }
    }

    /** 1x1 compare-mode depth texture (depth = 1.0 -> always lit) for shadows-off. */
    private static int dummyDepthTex;
    private static int ensureDummy() {
        if (dummyDepthTex != 0) return dummyDepthTex;
        dummyDepthTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, dummyDepthTex);
        java.nio.FloatBuffer one = BufferUtils.createFloatBuffer(1);
        one.put(1.0f).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, 1, 1, 0,
            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, one);
        applyDepthSamplerParams();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return dummyDepthTex;
    }

    /** NEAREST + clamp + hardware-compare (LEQUAL) so sampler2DShadow matches. */
    private static void applyDepthSamplerParams() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL14.GL_COMPARE_R_TO_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
    }

    public static void dispose() {
        if (shadowDepthTex != 0) { GL11.glDeleteTextures(shadowDepthTex); shadowDepthTex = 0; }
        if (shadowFbo != 0) { GL30.glDeleteFramebuffers(shadowFbo); shadowFbo = 0; }
        if (dummyDepthTex != 0) { GL11.glDeleteTextures(dummyDepthTex); dummyDepthTex = 0; }
        resolution = 0;
        renderedThisFrame = false;
    }

    private static boolean ensure() {
        int want = LDOGConfig.shaderShadowResolution;
        if (shadowFbo != 0 && resolution == want) return true;
        dispose();
        resolution = want;

        shadowDepthTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowDepthTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, want, want, 0,
            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        applyDepthSamplerParams();  // incl. hardware-compare so sampler2DShadow matches
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        shadowFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
            GL11.GL_TEXTURE_2D, shadowDepthTex, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: Shadow FBO incomplete (status=0x{})", Integer.toHexString(status));
            dispose();
            return false;
        }
        LDOGMod.LOGGER.info("LDOG: Shadow map ready ({}x{} depth)", want, want);
        return true;
    }

    private static final java.nio.IntBuffer VIEWPORT_BUF = BufferUtils.createIntBuffer(16);

    private static void saveViewport() {
        VIEWPORT_BUF.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUF);
        VIEWPORT_BUF.get(VIEWPORT);
    }

    private static void invertInto(FloatBuffer src, FloatBuffer dst) {
        src.position(0);
        SCRATCH.load(src);
        src.position(0);
        Matrix4f.invert(SCRATCH, SCRATCH);
        dst.clear();
        SCRATCH.store(dst);
        dst.position(0);
    }
}
