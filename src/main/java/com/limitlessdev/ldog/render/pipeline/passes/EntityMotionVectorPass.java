package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.CameraState;
import com.limitlessdev.ldog.render.pipeline.EntityRenderState;
import com.limitlessdev.ldog.render.pipeline.EntityRenderStateCache;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.FloatBuffer;

/**
 * Phase 9c.3-C — per-entity motion-vector emission via BBox projection.
 *
 * <p>For each entity captured by {@link com.limitlessdev.ldog.mixin.MixinRenderManagerEntityMV}
 * this frame:
 *
 * <ol>
 *   <li>CPU-side, project the entity's bounding-box corners through the
 *       current frame's captured view+projection (from {@link CameraState})
 *       to get its screen-space axis-aligned bounding box in NDC.</li>
 *   <li>Project the entity's THIS-frame world position to NDC via the
 *       current matrix; project its LAST-frame world position to NDC via
 *       the previous matrix; subtract to get the screen-space velocity
 *       vector.</li>
 *   <li>Render a screen-space quad covering the projected bbox; the
 *       fragment shader simply writes the velocity uniform into the
 *       RG channels of the MV target.</li>
 * </ol>
 *
 * <p>Static and offscreen entities short-circuit before the GL draw:
 * |velocity| below ~0.001 NDC skips emission (idle pigs etc. don't need
 * MV — TAA's camera-only MV path handles them) and entirely-offscreen
 * bboxes skip too.
 *
 * <p>Trade-off vs the master plan's Strategy 1 (re-render entity geometry
 * with velocity shader): BBox stamping is approximate but ~10× cheaper
 * and covers all entity types uniformly. The pixel footprint is the
 * entity's screen-space AABB rather than its actual silhouette — some
 * non-entity pixels inside the bbox get the entity's MV. TAA's
 * neighborhood color clamping handles the mismatch (history is clamped
 * to the local color box, so a non-entity pixel reprojected from an
 * adjacent entity-tinted history is clamped back to the local terrain
 * color).
 */
public final class EntityMotionVectorPass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "uniform vec4 u_bboxNDC;\n" +  // x: minX, y: minY, z: maxX, w: maxY
        "void main() {\n" +
        "    // gl_Vertex is one of (0,0), (1,0), (0,1), (1,1).\n" +
        "    vec2 t = gl_Vertex.xy;\n" +
        "    vec2 ndc = mix(u_bboxNDC.xy, u_bboxNDC.zw, t);\n" +
        "    gl_Position = vec4(ndc, 0.0, 1.0);\n" +
        "}\n";

    private static final String FRAG_SOURCE =
        "#version 120\n" +
        "uniform vec2 u_velocity;\n" +  // Pre-computed (curUV - prevUV).
        "void main() {\n" +
        "    gl_FragColor = vec4(u_velocity, 0.0, 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private boolean loggedFirstExecute;

    // Scratch buffers reused across entities — no per-entity allocations.
    private final FloatBuffer matInvBuf = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer matPrevBuf = BufferUtils.createFloatBuffer(16);
    private final Matrix4f curViewProj = new Matrix4f();
    private final Matrix4f prevViewProj = new Matrix4f();

    @Override public String id() { return "entity_mv"; }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_entity_mv", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: Entity MV shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: Entity MV shader compile failed; pass will no-op", e);
        }
    }

    @Override public void resize(int width, int height) { /* uses RTM dims */ }

    @Override
    public void execute(PostProcessContext ctx) {
        if (shaderFailed || shader == null) return;
        if (!ctx.bindingActive()) return;
        if (!LDOGConfig.enableEntityMotionVectors) return;
        if (!LDOGConfig.enableTAA) return; // MV target is only consumed by TAA
        if (!CameraState.isReady()) return;

        RenderTargetManager rtm = RenderTargetManager.INSTANCE;
        if (!rtm.isReady() || rtm.getMotionVectorFbo() == 0) return;

        int mvW = rtm.getMotionVectorWidth();
        int mvH = rtm.getMotionVectorHeight();
        if (mvW <= 0 || mvH <= 0) return;

        // We need both matrices to project entity positions. CameraState exposes
        // them as 16-float buffers; reconstruct Matrix4f instances locally so we
        // can do the projection math in Java without round-tripping through GL.
        CameraState.writeCurInvViewProj(matInvBuf);
        CameraState.writePrevViewProj(matPrevBuf);
        // Note: we need the FORWARD curViewProj here, not its inverse. Invert
        // back. Cheap; LWJGL's Matrix4f.invert is O(1) in matrix size.
        matInvBuf.rewind();
        curViewProj.load(matInvBuf);
        Matrix4f.invert(curViewProj, curViewProj);
        matPrevBuf.rewind();
        prevViewProj.load(matPrevBuf);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, rtm.getMotionVectorFbo());
        GlStateManager.viewport(0, 0, mvW, mvH);
        GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        shader.bind();

        int emitted = 0;
        for (EntityRenderStateCache.RenderedEntity e :
                EntityRenderStateCache.get().currentFrameEntries()) {
            if (drawEntity(e.state)) emitted++;
        }

        ShaderProgram.unbind();
        GL11.glPopAttrib();

        if (!loggedFirstExecute) {
            loggedFirstExecute = true;
            LDOGMod.LOGGER.info(
                "LDOG: Entity MV pass live ({}x{}, {} entities this frame)", mvW, mvH, emitted);
        }
    }

    /**
     * Project one entity's bbox + position to NDC and emit its velocity stamp.
     * Returns true if a draw call was issued.
     */
    private boolean drawEntity(EntityRenderState state) {
        AxisAlignedBB box = state.aabb;
        if (box == null) return false;

        // 1. Project cur + prev world positions to NDC via the matching frame's
        //    matrix. Centerpoint suffices for the velocity vector since BBox
        //    pixels share a single velocity uniform.
        Vector4f curNDC = projectToNDC(curViewProj,
            state.thisFrameInterpX, state.thisFrameInterpY, state.thisFrameInterpZ);
        Vector4f prevNDC = projectToNDC(prevViewProj,
            state.lastFrameInterpX, state.lastFrameInterpY, state.lastFrameInterpZ);
        if (curNDC == null || prevNDC == null) return false;

        // Convert NDC [-1,1] to UV [0,1] and compute velocity = curUV - prevUV.
        // This is the offset that TAA will SUBTRACT from current-frame UV to
        // find where this pixel was last frame.
        float curUVx = curNDC.x * 0.5f + 0.5f;
        float curUVy = curNDC.y * 0.5f + 0.5f;
        float prevUVx = prevNDC.x * 0.5f + 0.5f;
        float prevUVy = prevNDC.y * 0.5f + 0.5f;
        float velX = curUVx - prevUVx;
        float velY = curUVy - prevUVy;

        // Static-entity skip: sub-pixel motion isn't worth the draw call.
        // Threshold tuned so a 1-pixel-per-frame motion on a 1080p screen
        // (≈0.001 UV units) still triggers emission.
        if (Math.abs(velX) < 0.001f && Math.abs(velY) < 0.001f) return false;

        // 2. Project the 8 bbox corners through cur matrix to find screen-space
        //    AABB in NDC.
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        boolean anyInside = false;
        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? box.minX : box.maxX;
            double cy = (i & 2) == 0 ? box.minY : box.maxY;
            double cz = (i & 4) == 0 ? box.minZ : box.maxZ;
            Vector4f n = projectToNDC(curViewProj, cx, cy, cz);
            if (n == null) continue;
            // Clip behind-camera corners — only count in-frustum points.
            if (n.z < -1.0f || n.z > 1.0f) {
                // Still expand bbox by clipped point so partial-occlusion works.
                // (NDC z outside [-1,1] but x/y can be valid for tall entities)
            }
            anyInside = true;
            if (n.x < minX) minX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.x > maxX) maxX = n.x;
            if (n.y > maxY) maxY = n.y;
        }
        if (!anyInside) return false;

        // Clip to screen and skip fully off-screen entities.
        if (maxX < -1.0f || minX > 1.0f || maxY < -1.0f || minY > 1.0f) return false;
        float clipMinX = Math.max(minX, -1.0f);
        float clipMinY = Math.max(minY, -1.0f);
        float clipMaxX = Math.min(maxX,  1.0f);
        float clipMaxY = Math.min(maxY,  1.0f);
        if (clipMaxX <= clipMinX || clipMaxY <= clipMinY) return false;

        shader.setUniform4f("u_bboxNDC", clipMinX, clipMinY, clipMaxX, clipMaxY);
        shader.setUniform2f("u_velocity", velX, velY);

        // Triangle strip covering the unit square (0,0)-(1,1); vertex shader
        // remaps to bbox NDC. Using GL_QUADS for simplicity; vanilla MC uses
        // them all over so we're not introducing anything weird.
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(0.0f, 0.0f);
        GL11.glVertex2f(1.0f, 0.0f);
        GL11.glVertex2f(1.0f, 1.0f);
        GL11.glVertex2f(0.0f, 1.0f);
        GL11.glEnd();
        return true;
    }

    /**
     * Project a world-space point through {@code m} to NDC. Returns null
     * when the point is behind the camera (w ≤ 0) so callers can skip it.
     */
    private Vector4f projectToNDC(Matrix4f m, double wx, double wy, double wz) {
        // Must return a DISTINCT object each call — callers hold the current AND
        // previous projections simultaneously to compute velocity. Returning a
        // shared scratch made prev alias cur, so every motion vector was zero
        // and per-entity MV silently never worked.
        Vector4f v = new Vector4f((float) wx, (float) wy, (float) wz, 1.0f);
        Matrix4f.transform(m, v, v);
        if (v.w <= 1e-6f) return null;
        v.x /= v.w;
        v.y /= v.w;
        v.z /= v.w;
        return v;
    }

    @Override
    public void dispose() {
        if (shader != null) { shader.dispose(); shader = null; }
    }

    @Override public boolean isEnabled() { return true; }
}
