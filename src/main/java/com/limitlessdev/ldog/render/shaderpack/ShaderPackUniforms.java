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
 * <p>One instance is shared by every stage of a frame — see
 * {@link ShaderPackGbufferManager#uniforms()}. Gbuffer, shadow, deferred and
 * composite stages must agree on frameCounter / camera position / the gbuffer
 * matrices, so the snapshot is taken once (at the first stage that needs it)
 * and fed to each program in turn.
 *
 * <p>Scope is the frame-uniform subset. Per-vertex attributes packs may want
 * ({@code mc_Entity}, {@code at_tangent}) are not supplied.
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

    // Inputs the shaders.properties expression language can read that are not
    // otherwise pushed as uniforms (biome, moon phase, GUI state, held items).
    private int currentBiomeId;
    private float currentBiomeTemperature;
    private float currentBiomeRainfall;
    private int currentMoonPhase;
    private boolean currentHideGui;
    private int heldItemId = -1, heldItemId2 = -1;
    private int heldBlockLightValue, heldBlockLightValue2;
    private long lastSnapshotNanos;
    private float lastFrameDt = 1f / 60f;

    /** The active pack's custom uniform definitions, or null. */
    private ShaderCustomUniforms custom;

    public void setCustomUniforms(ShaderCustomUniforms custom) {
        this.custom = custom;
    }

    /**
     * Refresh all per-frame values from the live MC state. Call ONCE per frame
     * (via {@link ShaderPackGbufferManager#ensureFrameSnapshot(float)}) with the
     * frame's real partialTicks — interpolating the camera at a different
     * partialTicks than the one the gbuffer matrices were captured at leaves
     * cameraPosition and gbufferModelView describing slightly different moments,
     * which oscillates frame to frame.
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

        // sunAngle in OptiFine's convention: 0 = sunrise, 0.25 = noon,
        // 0.5 = sunset, 0.75 = midnight. MC's celestial angle is 0 at noon
        // and 0.75 at sunrise, so it is shifted by a quarter turn. Packs bake
        // this convention into their maths (BSL's shadowFade window is centred
        // on 0.25 and is exactly ZERO at raw-celestial noon) — feeding the raw
        // angle is what made every daytime scene render as if the sun had set.
        float celestial;
        if (world != null) {
            currentWorldTime = (int) (world.getWorldTime() % 24000L);
            celestial = world.getCelestialAngle(partialTicks);
            currentRainStrength = world.getRainStrength(partialTicks);
        } else {
            currentWorldTime = 0;
            celestial = 0.0F;
            currentRainStrength = 0.0F;
        }
        currentSunAngle = celestial < 0.75F ? celestial + 0.25F : celestial - 0.75F;

        // World-space sun direction, matching how vanilla draws the sun:
        // rotate(-90 about Y) · rotate(celestial·360 about X) · (0, 1, 0)
        // = (−sin θ, cos θ, 0). Transformed into VIEW space below, once the
        // camera matrix is loaded — OF's sunPosition/moonPosition/upPosition
        // are eye-space vectors (packs dot them against view-space normals).
        double sa = celestial * 2.0 * Math.PI;
        float wsx = (float) -Math.sin(sa) * 100.0F;
        float wsy = (float)  Math.cos(sa) * 100.0F;
        float wsz = 0.0F;

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

        // Celestial vectors into view space: rotation part of the camera
        // modelview only (direction vectors, w = 0). Shadow light = the body
        // above the horizon; OF switches at sunAngle 0.5.
        rotateInto(cur, wsx, wsy, wsz, sunPos);
        moonPos[0] = -sunPos[0]; moonPos[1] = -sunPos[1]; moonPos[2] = -sunPos[2];
        rotateInto(cur, 0.0F, 100.0F, 0.0F, upPos);
        boolean isNight = currentSunAngle > 0.5F;
        shadowLightPos[0] = isNight ? moonPos[0] : sunPos[0];
        shadowLightPos[1] = isNight ? moonPos[1] : sunPos[1];
        shadowLightPos[2] = isNight ? moonPos[2] : sunPos[2];

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

        // Biome / moon / GUI / held-item state for the custom-uniform expressions
        // (and the heldItemId family, which BSL declares directly).
        currentBiomeId = 0;
        currentBiomeTemperature = 0.5f;
        currentBiomeRainfall = 0.5f;
        currentMoonPhase = 0;
        currentHideGui = mc.gameSettings != null && mc.gameSettings.hideGUI;
        heldItemId = heldItemId2 = -1;
        heldBlockLightValue = heldBlockLightValue2 = 0;
        if (world != null && mc.player != null) {
            net.minecraft.util.math.BlockPos at = new net.minecraft.util.math.BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ);
            net.minecraft.world.biome.Biome biome = world.getBiome(at);
            if (biome != null) {
                currentBiomeId = net.minecraft.world.biome.Biome.getIdForBiome(biome);
                currentBiomeTemperature = biome.getDefaultTemperature();
                currentBiomeRainfall = biome.getRainfall();
            }
            currentMoonPhase = world.getMoonPhase();
            heldItemId = itemIdOf(mc.player.getHeldItemMainhand());
            heldItemId2 = itemIdOf(mc.player.getHeldItemOffhand());
            heldBlockLightValue = blockLightOf(mc.player.getHeldItemMainhand());
            heldBlockLightValue2 = blockLightOf(mc.player.getHeldItemOffhand());
        }

        // Frame delta for smooth(); clamp so a hitch cannot snap a 10 s fade.
        long now = System.nanoTime();
        if (lastSnapshotNanos != 0L) {
            lastFrameDt = (float) Math.min(0.25, (now - lastSnapshotNanos) / 1_000_000_000.0);
        }
        lastSnapshotNanos = now;

        // Advance frame counter + cache prev matrices for next frame's snapshot.
        frameCounter++;

        if (custom != null) custom.evaluateFrame(BUILTINS, lastFrameDt);
    }

    private static int itemIdOf(net.minecraft.item.ItemStack stack) {
        return stack == null || stack.isEmpty() ? -1 : net.minecraft.item.Item.getIdFromItem(stack.getItem());
    }

    private static int blockLightOf(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        net.minecraft.block.Block b = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
        if (b == null || b == net.minecraft.init.Blocks.AIR) return 0;
        try {
            return b.getDefaultState().getLightValue();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Resolver for the names a {@code shaders.properties} expression may use.
     * Mirrors what {@link #feedTo} pushes, plus the expression-only inputs
     * OptiFine exposes ({@code biome}, {@code moonPhase}, {@code hideGUI},
     * {@code BIOME_*} constants from the biome registry).
     */
    private final ShaderUniformExpression.Env BUILTINS = new ShaderUniformExpression.Env() {
        @Override public float[] get(String name) {
            switch (name) {
                case "sunAngle": return new float[]{ currentSunAngle };
                case "shadowAngle": return new float[]{ currentSunAngle > 0.5F ? currentSunAngle - 0.5F : currentSunAngle };
                case "moonPhase": return new float[]{ currentMoonPhase };
                case "worldTime": return new float[]{ currentWorldTime };
                case "worldDay": return new float[]{ Minecraft.getMinecraft().world == null ? 0f : (float) (Minecraft.getMinecraft().world.getWorldTime() / 24000L) };
                case "frameCounter": return new float[]{ frameCounter };
                case "frameTime": return new float[]{ lastFrameDt };
                case "frameTimeCounter": return new float[]{ (float) (((System.nanoTime() - startNanos) / 1_000_000_000.0) % 3600.0) };
                case "rainStrength": case "wetness": return new float[]{ currentRainStrength };
                case "blindness": return new float[]{ currentBlindness };
                case "nightVision": return new float[]{ currentNightVision };
                case "screenBrightness": return new float[]{ currentScreenBrightness };
                case "isEyeInWater": return new float[]{ eyeInWater() };
                case "eyeBrightness": case "eyeBrightnessSmooth": return new float[]{ eyeBrightnessBlock, eyeBrightnessSky };
                case "cameraPosition": return new float[]{ (float) currentCameraX, (float) currentCameraY, (float) currentCameraZ };
                case "previousCameraPosition": return new float[]{ (float) prevCameraX, (float) prevCameraY, (float) prevCameraZ };
                case "sunPosition": return sunPos.clone();
                case "moonPosition": return moonPos.clone();
                case "shadowLightPosition": return shadowLightPos.clone();
                case "upPosition": return upPos.clone();
                case "skyColor": return skyColor.clone();
                case "fogColor": return new float[]{ fogColor[0], fogColor[1], fogColor[2] };
                case "biome": return new float[]{ currentBiomeId };
                case "temperature": return new float[]{ currentBiomeTemperature };
                case "rainfall": return new float[]{ currentBiomeRainfall };
                case "hideGUI": return new float[]{ currentHideGui ? 1f : 0f };
                case "heldItemId": return new float[]{ heldItemId };
                case "heldItemId2": return new float[]{ heldItemId2 };
                case "heldBlockLightValue": return new float[]{ heldBlockLightValue };
                case "heldBlockLightValue2": return new float[]{ heldBlockLightValue2 };
                case "viewWidth": return new float[]{ currentViewWidth };
                case "viewHeight": return new float[]{ currentViewHeight };
                case "aspectRatio": return new float[]{ currentViewHeight > 0 ? (float) currentViewWidth / currentViewHeight : 1f };
                case "near": return new float[]{ currentNear };
                case "far": return new float[]{ currentFar };
                case "PI": return new float[]{ (float) Math.PI };
                default:
                    if (name.startsWith("BIOME_")) return new float[]{ biomeConstant(name) };
                    return null;
            }
        }

        @Override public float smooth(int id, float value, float fadeUp, float fadeDown) {
            return value; // unused: ShaderCustomUniforms owns the smoothing state
        }
    };

    private static final java.util.Map<String, Float> BIOME_CONSTANTS = new java.util.HashMap<>();

    /**
     * {@code BIOME_<REGISTRY_NAME>} -> numeric biome id, built from the Forge
     * registry on first use. Names a pack uses that this MC version lacks
     * (BIOME_GROVE, BIOME_SNOWY_PLAINS...) resolve to -1 so {@code in(biome, ...)}
     * never matches them -- the same outcome as OptiFine on 1.12.2.
     */
    private static float biomeConstant(String name) {
        if (BIOME_CONSTANTS.isEmpty()) {
            for (net.minecraft.world.biome.Biome b : net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES) {
                net.minecraft.util.ResourceLocation rl = b.getRegistryName();
                if (rl == null) continue;
                String key = "BIOME_" + rl.getPath().toUpperCase(java.util.Locale.ROOT);
                BIOME_CONSTANTS.put(key, (float) net.minecraft.world.biome.Biome.getIdForBiome(b));
            }
        }
        Float v = BIOME_CONSTANTS.get(name);
        return v == null ? -1f : v;
    }

    private static int eyeInWater() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.world == null) return 0;
        net.minecraft.block.material.Material m = mc.player.world.getBlockState(new net.minecraft.util.math.BlockPos(
            mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ)).getMaterial();
        if (m == net.minecraft.block.material.Material.WATER) return 1;
        if (m == net.minecraft.block.material.Material.LAVA) return 2;
        return 0;
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
        //
        // TYPE NOTE (applies to this block and the celestial one below): the
        // OF / Iris spec declares all of these as `uniform vec3`. GL matches
        // the setter's component count to the DECLARED type — pushing a vec3
        // through glUniform4f raises GL_INVALID_OPERATION and leaves the
        // uniform at (0,0,0), which reads to a pack as "camera at world
        // origin / sun straight through the ground". That mismatch was the
        // source of the one-shot 1282 errors the GL probe kept draining.
        program.setUniform3f("cameraPosition",
            (float) currentCameraX,
            (float) currentCameraY,
            (float) currentCameraZ);
        program.setUniform3f("previousCameraPosition",
            (float) prevCameraX,
            (float) prevCameraY,
            (float) prevCameraZ);

        // Celestial directions (vec3 — see the type note above).
        program.setUniform3f("sunPosition", sunPos[0], sunPos[1], sunPos[2]);
        program.setUniform3f("moonPosition", moonPos[0], moonPos[1], moonPos[2]);
        program.setUniform3f("upPosition",  upPos[0],   upPos[1],   upPos[2]);
        program.setUniform3f("shadowLightPosition",
            shadowLightPos[0], shadowLightPos[1], shadowLightPos[2]);

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
        // eyeBrightness / eyeBrightnessSmooth: lightmap units [0,240], and the
        // OF spec declares them `uniform ivec2` — they MUST be fed as integers
        // (glUniform2f against an ivec2 is a GL_INVALID_OPERATION that leaves
        // the value at zero, i.e. "player is in pitch darkness").
        int eyeBlock = (int) eyeBrightnessBlock;
        int eyeSky = (int) eyeBrightnessSky;
        program.setUniform2i("eyeBrightness", eyeBlock, eyeSky);
        program.setUniform2i("eyeBrightnessSmooth", eyeBlock, eyeSky);

        // Held-item family (OF spec: ints). Packs use these for hand light.
        program.setUniform1i("heldItemId", heldItemId);
        program.setUniform1i("heldItemId2", heldItemId2);
        program.setUniform1i("heldBlockLightValue", heldBlockLightValue);
        program.setUniform1i("heldBlockLightValue2", heldBlockLightValue2);
        program.setUniform1i("moonPhase", currentMoonPhase);
        program.setUniform1i("hideGUI", currentHideGui ? 1 : 0);

        // Pack-defined uniforms from shaders.properties (BSL's shadowFade,
        // timeAngle, biome flags ...). Evaluated once in snapshot().
        if (custom != null) custom.feedTo(program);
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

    /** out = upper-3×3(m) · (x, y, z) — a direction through the modelview. */
    private static void rotateInto(Matrix4f m, float x, float y, float z, float[] out) {
        // LWJGL Matrix4f is column-major: mAB = column A, row B.
        out[0] = m.m00 * x + m.m10 * y + m.m20 * z;
        out[1] = m.m01 * x + m.m11 * y + m.m21 * z;
        out[2] = m.m02 * x + m.m12 * y + m.m22 * z;
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
