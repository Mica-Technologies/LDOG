package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import java.nio.FloatBuffer;

/**
 * Per-frame collection + feed of the standard OF / Iris shader-pack
 * uniforms. Computed once per frame, then pushed to each composite stage
 * during execution.
 *
 * <p>This is the LDOG-original equivalent of OptiFine's
 * {@code WorldRenderingPipeline} uniform feed and Iris's
 * {@code CommonUniforms}. The naming convention matches what shader packs
 * declare ({@code uniform vec3 cameraPosition;} etc.), so a typical 1.12.2
 * pack's composite stage compiles and links against our feed without
 * source modification.
 *
 * <p>Scope is the composite-chain subset of uniforms. Gbuffer-only uniforms
 * (per-vertex / per-fragment from MC's draw calls) are not provided — the
 * gbuffer phase isn't implemented yet. Packs that only check uniforms at
 * the composite stage will see correct values for the common cases.
 */
public final class ShaderPackUniforms {

    private static final FloatBuffer MAT_BUF = BufferUtils.createFloatBuffer(16);

    // World-render matrices, captured during renderWorldPass when GL still holds
    // the camera transform. The composite chain runs LATER (after MC switched to
    // the GUI ortho matrix), so without this it would feed identity/ortho for
    // gbufferModelView/Projection — breaking any deferred pack that reconstructs
    // world position from depth (the "black/invisible world" symptom).
    private static final FloatBuffer WORLD_MV = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer WORLD_PROJ = BufferUtils.createFloatBuffer(16);
    private static boolean hasWorldMatrices;

    /** Capture the live GL camera matrices. Call mid-renderWorldPass. */
    public static void captureWorldMatrices() {
        WORLD_MV.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, WORLD_MV);
        WORLD_PROJ.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, WORLD_PROJ);
        hasWorldMatrices = true;
    }

    /** Drop the captured matrices (call at frame start). */
    public static void clearWorldMatrices() {
        hasWorldMatrices = false;
    }

    // Cached one-frame-old matrices for the "previous" uniforms.
    private final Matrix4f prevModelView = new Matrix4f();
    private final Matrix4f prevProjection = new Matrix4f();
    private boolean hasPrev;
    private int frameCounter;
    private long startNanos = System.nanoTime();

    // Last-snapshot values — captured once at start of execute() so all
    // stages in this frame see the same numbers.
    private float currentSunAngle;
    private float currentRainStrength;
    private int currentWorldTime;
    private double currentCameraX, currentCameraY, currentCameraZ;
    private float currentNear, currentFar;
    private int currentViewWidth, currentViewHeight;
    // Previous frame's camera position (OF previousCameraPosition) — motion-blur
    // and temporal packs difference it against cameraPosition. Seeded on the
    // first frame so frame 0 reports zero camera motion rather than a jump.
    private double prevCameraX, prevCameraY, prevCameraZ;
    private boolean hasPrevCamera;
    private final Matrix4f currentModelView = new Matrix4f();
    private final Matrix4f currentProjection = new Matrix4f();
    private final float[] sunPos = new float[3];
    private final float[] moonPos = new float[3];
    private final float[] upPos = new float[3];
    private final float[] shadowLightPos = new float[3];

    // Atmospheric + player-state uniforms commonly referenced by packs.
    private final float[] fogColor = new float[4];
    private final float[] skyColor = new float[3];
    private float currentNightVision;
    private float currentBlindness;
    private float currentScreenBrightness;
    private float eyeBrightnessBlock;
    private float eyeBrightnessSky;
    private static final java.nio.FloatBuffer FOG_BUF = BufferUtils.createFloatBuffer(16);

    /**
     * Refresh all per-frame values from the live MC state. Call once at the
     * start of {@link com.limitlessdev.ldog.render.pipeline.passes.ShaderPackCompositePass#execute}.
     */
    public void snapshot(int viewWidth, int viewHeight, float partialTicks) {
        currentViewWidth = viewWidth;
        currentViewHeight = viewHeight;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        World world = mc.world;

        // Interpolated camera world position.
        if (view != null) {
            currentCameraX = view.prevPosX + (view.posX - view.prevPosX) * partialTicks;
            currentCameraY = view.prevPosY + (view.posY - view.prevPosY) * partialTicks;
            currentCameraZ = view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks;
        }
        if (!hasPrevCamera) {
            prevCameraX = currentCameraX;
            prevCameraY = currentCameraY;
            prevCameraZ = currentCameraZ;
            hasPrevCamera = true;
        }

        currentNear = 0.05F;
        currentFar = mc.gameSettings.renderDistanceChunks * 16.0F;

        // Sun angle in OF's [0,1] domain (0 = noon, 0.5 = midnight).
        if (world != null) {
            currentWorldTime = (int) (world.getWorldTime() % 24000L);
            float celestial = world.getCelestialAngle(partialTicks);
            currentSunAngle = celestial;
            currentRainStrength = world.getRainStrength(partialTicks);
        } else {
            currentWorldTime = 0;
            currentSunAngle = 0.0F;
            currentRainStrength = 0.0F;
        }

        // Sun / moon direction in view space — OF convention is roughly:
        // sun rotates around the Z axis in world space; "up" is (0,1,0).
        // We don't have the live GL matrix here (composite runs without
        // GL_MODELVIEW), so we compute the celestial direction from the
        // sun angle alone. Good enough for time-of-day shader effects.
        double sa = currentSunAngle * 2.0 * Math.PI;
        float sx = (float) -Math.sin(sa);
        float sy = (float)  Math.cos(sa);
        sunPos[0] = sx * 100.0F;
        sunPos[1] = sy * 100.0F;
        sunPos[2] = 0.0F;
        moonPos[0] = -sunPos[0];
        moonPos[1] = -sunPos[1];
        moonPos[2] = 0.0F;
        upPos[0] = 0.0F; upPos[1] = 100.0F; upPos[2] = 0.0F;
        // Shadow light = whichever celestial body is above the horizon
        // (vanilla switches at angle 0.25..0.75 = night).
        boolean isNight = currentSunAngle > 0.25F && currentSunAngle < 0.75F;
        shadowLightPos[0] = isNight ? moonPos[0] : sunPos[0];
        shadowLightPos[1] = isNight ? moonPos[1] : sunPos[1];
        shadowLightPos[2] = 0.0F;

        // gbufferModelView + gbufferProjection. Prefer the matrices captured
        // during the world render (correct camera transform); fall back to the
        // live GL matrices only when no capture happened this frame (e.g. a
        // composite-only pack that never ran a gbuffer pass — it won't use these
        // for world-space reconstruction anyway).
        Matrix4f cur = currentModelView;
        Matrix4f proj = currentProjection;
        if (hasWorldMatrices) {
            WORLD_MV.position(0);
            cur.load(WORLD_MV);
            WORLD_PROJ.position(0);
            proj.load(WORLD_PROJ);
        } else {
            cur.setIdentity();
            proj.setIdentity();
            MAT_BUF.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MAT_BUF);
            MAT_BUF.rewind();
            cur.load(MAT_BUF);
            MAT_BUF.clear();
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, MAT_BUF);
            MAT_BUF.rewind();
            proj.load(MAT_BUF);
        }

        // Fog colour — read live GL fog state. Accurate during the world
        // render (gbuffer path); may be stale in the composite/GUI context.
        FOG_BUF.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, FOG_BUF);
        fogColor[0] = FOG_BUF.get(0);
        fogColor[1] = FOG_BUF.get(1);
        fogColor[2] = FOG_BUF.get(2);
        fogColor[3] = FOG_BUF.get(3);

        // Sky colour for the current view position / time.
        if (world != null && view != null) {
            net.minecraft.util.math.Vec3d sky = world.getSkyColor(view, partialTicks);
            skyColor[0] = (float) sky.x;
            skyColor[1] = (float) sky.y;
            skyColor[2] = (float) sky.z;
        }

        // Player potion / brightness state.
        currentScreenBrightness = mc.gameSettings != null ? mc.gameSettings.gammaSetting : 1.0f;
        currentNightVision = 0.0f;
        currentBlindness = 0.0f;
        eyeBrightnessBlock = 0.0f;
        eyeBrightnessSky = 240.0f;
        if (mc.player != null) {
            if (mc.player.isPotionActive(net.minecraft.init.MobEffects.NIGHT_VISION)) currentNightVision = 1.0f;
            if (mc.player.isPotionActive(net.minecraft.init.MobEffects.BLINDNESS)) currentBlindness = 1.0f;
            if (world != null) {
                net.minecraft.util.math.BlockPos eye = new net.minecraft.util.math.BlockPos(
                    mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
                // OF eyeBrightness is in lightmap units [0,240] (= light level * 16).
                eyeBrightnessBlock = world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, eye) * 16.0f;
                eyeBrightnessSky = world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, eye) * 16.0f;
            }
        }

        // Advance frame counter + cache prev matrices for next frame's snapshot.
        frameCounter++;
    }

    /**
     * Push the snapshot to every uniform the {@code program} declares. Uniforms
     * the shader doesn't declare are silently skipped — {@link ShaderProgram}
     * resolves missing uniforms to location -1 and the set-call is a no-op.
     */
    public void feedTo(ShaderProgram program) {
        program.bind();

        // Screen.
        program.setUniform1i("viewWidth", currentViewWidth);
        program.setUniform1i("viewHeight", currentViewHeight);
        program.setUniform2f("invMainSize",
            currentViewWidth > 0 ? 1.0f / currentViewWidth : 0,
            currentViewHeight > 0 ? 1.0f / currentViewHeight : 0);
        program.setUniform2f("mainSize", currentViewWidth, currentViewHeight);
        float aspect = currentViewHeight > 0
            ? (float) currentViewWidth / currentViewHeight : 1.0f;
        program.setUniform1f("aspectRatio", aspect);
        program.setUniform1f("near", currentNear);
        program.setUniform1f("far", currentFar);

        // Time / frame.
        program.setUniform1i("frameCounter", frameCounter);
        program.setUniform1f("frameTime", 1.0f / 60.0f);  // approximation
        // frameTimeCounter wraps at 3600s like OptiFine — packs that drive
        // periodic animation off it (and mod/fract the value) rely on the wrap.
        float seconds = (float) (((System.nanoTime() - startNanos) / 1_000_000_000.0) % 3600.0);
        program.setUniform1f("frameTimeCounter", seconds);

        // World.
        program.setUniform1i("worldTime", currentWorldTime);
        program.setUniform1f("rainStrength", currentRainStrength);
        program.setUniform1f("wetness", currentRainStrength);
        program.setUniform1f("sunAngle", currentSunAngle);
        program.setUniform1f("shadowAngle",
            currentSunAngle > 0.5F ? currentSunAngle - 0.5F : currentSunAngle);

        // Camera.
        // OF convention: cameraPosition is the player's world-space position.
        // GL precision means we feed it as floats; sub-meter precision lost
        // far from the origin is the same compromise OF makes.
        program.setUniform4f("cameraPosition",
            (float) currentCameraX,
            (float) currentCameraY,
            (float) currentCameraZ,
            0.0f);
        program.setUniform4f("previousCameraPosition",
            (float) prevCameraX,
            (float) prevCameraY,
            (float) prevCameraZ,
            0.0f);

        // Celestial directions.
        program.setUniform4f("sunPosition", sunPos[0], sunPos[1], sunPos[2], 0);
        program.setUniform4f("moonPosition", moonPos[0], moonPos[1], moonPos[2], 0);
        program.setUniform4f("upPosition",  upPos[0],   upPos[1],   upPos[2],  0);
        program.setUniform4f("shadowLightPosition",
            shadowLightPos[0], shadowLightPos[1], shadowLightPos[2], 0);

        // Matrices.
        writeMat4(MAT_BUF, currentModelView);
        program.setUniformMatrix4("gbufferModelView", MAT_BUF);
        invertInto(currentModelView, MAT_BUF);
        program.setUniformMatrix4("gbufferModelViewInverse", MAT_BUF);

        writeMat4(MAT_BUF, currentProjection);
        program.setUniformMatrix4("gbufferProjection", MAT_BUF);
        invertInto(currentProjection, MAT_BUF);
        program.setUniformMatrix4("gbufferProjectionInverse", MAT_BUF);

        if (hasPrev) {
            writeMat4(MAT_BUF, prevModelView);
            program.setUniformMatrix4("gbufferPreviousModelView", MAT_BUF);
            writeMat4(MAT_BUF, prevProjection);
            program.setUniformMatrix4("gbufferPreviousProjection", MAT_BUF);
        }

        // Eye-in-water state — 0 air / 1 water / 2 lava. Composite stages
        // often tint based on this. Quick MC api check.
        Minecraft mc = Minecraft.getMinecraft();
        int eyeIn = 0;
        if (mc.player != null) {
            net.minecraft.block.material.Material m =
                MathHelper.floor(mc.player.posY) >= 0
                    ? mc.player.world.getBlockState(new net.minecraft.util.math.BlockPos(
                        mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ))
                        .getMaterial()
                    : net.minecraft.block.material.Material.AIR;
            if (m == net.minecraft.block.material.Material.WATER) eyeIn = 1;
            else if (m == net.minecraft.block.material.Material.LAVA) eyeIn = 2;
        }
        program.setUniform1i("isEyeInWater", eyeIn);

        // Atmospheric + player state.
        program.setUniform3f("fogColor", fogColor[0], fogColor[1], fogColor[2]);
        program.setUniform3f("skyColor", skyColor[0], skyColor[1], skyColor[2]);
        program.setUniform1f("nightVision", currentNightVision);
        program.setUniform1f("blindness", currentBlindness);
        program.setUniform1f("screenBrightness", currentScreenBrightness);
        // eyeBrightness / eyeBrightnessSmooth: lightmap units [0,240].
        program.setUniform2f("eyeBrightness", eyeBrightnessBlock, eyeBrightnessSky);
        program.setUniform2f("eyeBrightnessSmooth", eyeBrightnessBlock, eyeBrightnessSky);
    }

    /** Rotate this frame's matrices into the "prev" slot for next frame. */
    public void rotatePrev() {
        prevModelView.load(currentModelView);
        prevProjection.load(currentProjection);
        prevCameraX = currentCameraX;
        prevCameraY = currentCameraY;
        prevCameraZ = currentCameraZ;
        hasPrev = true;
    }

    private static void writeMat4(FloatBuffer buf, Matrix4f m) {
        buf.clear();
        m.store(buf);
        buf.rewind();
    }

    private static final Matrix4f INV_SCRATCH = new Matrix4f();
    private static void invertInto(Matrix4f src, FloatBuffer dst) {
        INV_SCRATCH.load(src);
        Matrix4f.invert(INV_SCRATCH, INV_SCRATCH);
        writeMat4(dst, INV_SCRATCH);
    }
}
