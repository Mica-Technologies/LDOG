package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackGbufferManager;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackManager;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackRuntime;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackUniforms;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

/**
 * Runs the active shader pack's composite chain — {@code composite.{vsh,fsh}}
 * through {@code composite15}, followed by {@code final.{vsh,fsh}} when
 * present.
 *
 * <p>For each stage in order:
 *
 * <ol>
 *   <li>Bind the input buffer ({@code colortex0}) and depth ({@code depthtex0})
 *       on texture units 0 and 1. {@code colortex1..7} bind to a 1x1 black
 *       texture on units 2..8 so packs that reference them get safe zeros
 *       instead of a GL error.</li>
 *   <li>Feed the standard OF / Iris uniform set via {@link ShaderPackUniforms}.</li>
 *   <li>Draw a fullscreen triangle to the next ping-pong target.</li>
 *   <li>Swap input/output so the next stage reads what this one wrote.</li>
 * </ol>
 *
 * <p>The terminal {@code final} stage (when present) draws to the actual
 * main framebuffer instead of a ping-pong target. When the pack ships no
 * final stage, the last composite's output is blitted to the main FB so
 * the on-screen result reflects the chain.
 *
 * <p>Scope is composite-stage only. Gbuffer programs (per-object draw
 * shaders) aren't compiled or hooked yet — that's the next phase of shader
 * pack support and a much bigger lift. Most pack visual identity DOES live
 * in composite, so even this restricted runner produces visible output
 * for the typical pack.
 */
public final class ShaderPackCompositePass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 texcoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    texcoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    /** Two ping-pong color targets at main-FB resolution. */
    private int fboA, fboB;
    private int texA, texB;
    private int width, height;

    /** 1x1 black RGBA texture, bound to unused colortex slots. */
    private int blackTex;

    /** The {@code colortex0} input texture — a copy of the main FB. */
    private int sceneCopyTex;
    private int sceneCopyW, sceneCopyH;

    // --- multi-write (colortex-flipping) state, deferred path only ---
    /** Per-colortex front (read) + back (scratch) textures, indices 0..mrtN. */
    private int[] mrtCur, mrtAlt;
    private int mrtFbo;       // dynamic MRT target (attachments re-pointed per stage)
    private int mrtCopyFbo;   // single-attachment helper for the initial gbuffer copy
    private int mrtN = -1;    // highest colortex index managed
    private int mrtW, mrtH;

    private final ShaderPackUniforms uniforms = new ShaderPackUniforms();
    private boolean loggedFirstRun;
    private String loggedActivePack;

    @Override public String id() { return "shader_pack_composite"; }

    @Override
    public void init(int width, int height) {
        // Lazy allocation — runtime resources only set up when a pack is
        // actually active. See ensureBuffers().
    }

    @Override
    public void resize(int width, int height) {
        // Lazy reallocation on next execute.
    }

    @Override
    public void execute(PostProcessContext ctx) {
        ShaderPackRuntime runtime = ShaderPackManager.INSTANCE.getRuntime();
        if (runtime == null || !runtime.hasCompositeChain()) return;
        if (!ctx.bindingActive()) return;

        int mainW = ctx.mainWidth();
        int mainH = ctx.mainHeight();
        if (mainW <= 0 || mainH <= 0) return;

        ensureBuffers(mainW, mainH);
        if (fboA == 0 || fboB == 0 || blackTex == 0 || sceneCopyTex == 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        // In the deferred (MRT) path, colortex0 is the pipeline's scene colour
        // texture — already holding the gbuffer-shaded world, and distinct from
        // our ping/main output FBOs, so no copy is needed. Otherwise capture the
        // main FB into sceneCopyTex so stages can sample the scene without
        // violating the same-attachment read/write rule.
        boolean deferred = ShaderPackGbufferManager.isDeferredActive();
        int colortex0Input;
        if (deferred) {
            colortex0Input = RenderTargetManager.INSTANCE.getSceneColorTexture();
        } else {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneCopyTex);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, mainW, mainH);
            colortex0Input = sceneCopyTex;
        }

        // Snapshot per-frame uniforms once. All stages this frame see the
        // same numbers so cross-stage temporal effects stay consistent.
        uniforms.snapshot(mainW, mainH, 0.0f);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        int depthTex = RenderTargetManager.INSTANCE.getSceneDepthTexture();

        // Multi-write path: when a deferred pack's composite chain writes to
        // colortex beyond 0 (bloom tiles, lighting accumulation), run the full
        // colortex-flipping model so later stages read the updated buffers.
        boolean handled = false;
        if (deferred && usesMultiWrite(runtime)) {
            try {
                handled = runMultiWriteChain(runtime, ctx, depthTex, mainW, mainH);
            } catch (Throwable t) {
                LDOGMod.LOGGER.error("LDOG: Multi-write composite failed; falling back to single-target", t);
                handled = false;
            }
        }

        if (!handled) {
            // Single-target ping-pong: first stage reads colortex0Input,
            // subsequent stages read whichever ping was just written.
            int currentInputTex = colortex0Input;
            int currentOutputFbo = fboA;
            int currentOutputTex = texA;
            java.util.List<ShaderPackRuntime.Stage> chain =
                new java.util.ArrayList<>(runtime.deferred());
            chain.addAll(runtime.composites());
            for (ShaderPackRuntime.Stage stage : chain) {
                runStage(stage.program, currentInputTex, depthTex,
                    currentOutputFbo, mainW, mainH, deferred);
                currentInputTex = currentOutputTex;
                if (currentOutputFbo == fboA) {
                    currentOutputFbo = fboB; currentOutputTex = texB;
                } else {
                    currentOutputFbo = fboA; currentOutputTex = texA;
                }
            }
            ShaderPackRuntime.Stage finalStage = runtime.finalStage();
            if (finalStage != null) {
                runStage(finalStage.program, currentInputTex, depthTex,
                    ctx.mainFbo(), mainW, mainH, deferred);
            } else {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    currentInputTex == texA ? fboA : (currentInputTex == texB ? fboB : 0));
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
                GL30.glBlitFramebuffer(0, 0, mainW, mainH, 0, 0, mainW, mainH,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            }
        }

        // Restore main FB binding so subsequent passes find it bound.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());

        ShaderProgram.unbind();
        // Unbind all texture units we touched (0..10, incl. shadow + noise).
        for (int i = 10; i >= 0; i--) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        // Cache prev-frame matrices for the next snapshot.
        uniforms.rotatePrev();

        GL11.glPopAttrib();

        com.limitlessdev.ldog.render.pipeline.PipelineGlProbe.drain(
            ShaderPackGbufferManager.isDeferredActive() && usesMultiWrite(runtime)
                ? "composite (multi-write)" : "composite");

        if (!loggedFirstRun || !runtime.packName().equals(loggedActivePack)) {
            loggedFirstRun = true;
            loggedActivePack = runtime.packName();
            LDOGMod.LOGGER.info(
                "LDOG: Shader pack runner live — '{}' ({} composite stage(s){})",
                runtime.packName(), runtime.composites().size(),
                runtime.finalStage() != null ? " + final" : "");
        }
    }

    /** Bind all the standard inputs for one stage and draw a fullscreen quad. */
    private void runStage(ShaderProgram program, int colortex0, int depthtex0,
                          int targetFbo, int w, int h, boolean deferred) {
        // colortex0 (unit 0) — the current scene/composite-chain input.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colortex0);
        // depthtex0 (unit 1) — the world depth texture.
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthtex0);
        // colortex1..7 (units 2..8) — in the deferred path, bind the real aux
        // G-buffer attachments the pack wrote (normals/material/etc.); fall back
        // to the safe-zero 1x1 black texture for slots the pack didn't allocate.
        // Note: colortex0 (the chain input) is intentionally NOT overwritten by
        // the deferred aux bind so ping-pong chaining still works.
        for (int i = 1; i <= 7; i++) {
            int tex = deferred ? ShaderPackGbufferManager.colortex(i) : 0;
            if (tex == 0) tex = blackTex;
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + (i + 1));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        }
        // noisetex on unit 10 (above colortex 0..8 and the shadow unit 9).
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 10);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,
            com.limitlessdev.ldog.render.shaderpack.ShaderNoiseTexture.get());

        program.bind();
        // OF convention names — these always exist as samplers in composite
        // shaders. Set them to point at our matching texture units.
        program.setUniform1i("colortex0", 0);
        program.setUniform1i("depthtex0", 1);
        program.setUniform1i("colortex1", 2);
        program.setUniform1i("colortex2", 3);
        program.setUniform1i("colortex3", 4);
        program.setUniform1i("colortex4", 5);
        program.setUniform1i("colortex5", 6);
        program.setUniform1i("colortex6", 7);
        program.setUniform1i("colortex7", 8);
        program.setUniform1i("depthtex1", 1);  // alias to depthtex0 (no shadows)
        program.setUniform1i("depthtex2", 1);
        program.setUniform1i("noisetex", 10);
        program.setUniform1i("noiseTextureResolution",
            com.limitlessdev.ldog.render.shaderpack.ShaderNoiseTexture.RESOLUTION);
        // Snapshot's standard uniforms (cameraPosition, sunPosition, ...).
        uniforms.feedTo(program);
        // Real shadow map (when the shadow pass ran this frame) on unit 9 +
        // shadowProjection/ModelView uniforms; otherwise the depthtex1/2 alias
        // above leaves shadow samplers reading depth (fully lit).
        com.limitlessdev.ldog.render.shaderpack.ShadowMapManager.feed(program, 9);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
        GlStateManager.viewport(0, 0, w, h);

        drawFullscreen();
    }

    private static void drawFullscreen() {
        // Triangle overdrawing the screen (cheaper than a quad).
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();
    }

    // ===== Multi-write (colortex-flipping) composite path =====

    private static final IntBuffer MRT_DRAW_BUF = BufferUtils.createIntBuffer(8);

    /** True if any composite stage writes to a colortex beyond 0 that we allocated. */
    private boolean usesMultiWrite(ShaderPackRuntime runtime) {
        int aux = ShaderPackGbufferManager.auxColortexCount();
        if (aux < 1) return false;
        // Any deferred stage means the pack does deferred lighting into aux
        // buffers — always use the multi-write path so those land + persist.
        if (!runtime.deferred().isEmpty()) return true;
        for (ShaderPackRuntime.Stage s : runtime.composites()) {
            for (int t : s.drawBuffers) if (t >= 1 && t <= aux) return true;
        }
        return false;
    }

    /**
     * Run the composite chain with the OptiFine colortex-flipping model: each
     * stage reads the "current" colortex set and writes its DRAWBUFFERS targets
     * to scratch copies, which then flip in. Buffers a stage doesn't write keep
     * their value. Returns false if the buffers couldn't be set up.
     */
    private boolean runMultiWriteChain(ShaderPackRuntime runtime, PostProcessContext ctx,
                                       int depthTex, int mainW, int mainH) {
        int n = Math.min(7, ShaderPackGbufferManager.auxColortexCount());
        int w = RenderTargetManager.INSTANCE.getScaledWidth();
        int h = RenderTargetManager.INSTANCE.getScaledHeight();
        if (w <= 0 || h <= 0 || !ensureMrt(runtime, n, w, h)) return false;

        copyGbufferToCur(n, w, h);

        // Deferred lighting passes first (gbuffers -> deferred -> composite).
        for (ShaderPackRuntime.Stage stage : runtime.deferred()) {
            runStageMrt(stage.program, stage.drawBuffers, n, w, h, depthTex);
        }
        for (ShaderPackRuntime.Stage stage : runtime.composites()) {
            runStageMrt(stage.program, stage.drawBuffers, n, w, h, depthTex);
        }

        // Final stage draws colortex0 (and any aux it reads) to the main FB.
        ShaderPackRuntime.Stage fin = runtime.finalStage();
        if (fin != null) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
            bindMrtInputs(fin.program, n, depthTex);
            GlStateManager.viewport(0, 0, mainW, mainH);
            drawFullscreen();
        } else {
            // Blit the final colortex0 to the main FB.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mrtCopyFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, mrtCur[0], 0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mrtCopyFbo);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, mainW, mainH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        }
        return true;
    }

    /** Copy the gbuffer colortex (scene + aux) into our writable "current" set. */
    private void copyGbufferToCur(int n, int w, int h) {
        int sceneFbo = RenderTargetManager.INSTANCE.getSceneFbo();
        int gbufFbo = ShaderPackGbufferManager.gbufferFbo();
        for (int i = 0; i <= n; i++) {
            int srcFbo = (i == 0) ? sceneFbo : gbufFbo;
            int srcAttach = GL30.GL_COLOR_ATTACHMENT0 + i;
            if (srcFbo == 0) continue;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
            GL11.glReadBuffer(srcAttach);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mrtCopyFbo);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, mrtCur[i], 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }
    }

    /** One composite stage: write its DRAWBUFFERS targets to scratch, then flip. */
    private void runStageMrt(ShaderProgram program, int[] drawBuffers, int n, int w, int h, int depthTex) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mrtFbo);
        // Detach every color attachment left over from the previous stage FIRST.
        // After a stage flips, the texture it wrote (still attached here) becomes
        // the NEXT stage's mrtCur[t] and is bound as a sampler — a texture that is
        // simultaneously attached to the bound FBO and sampled is a feedback loop
        // and raises GL_INVALID_OPERATION on the draw, even when it isn't in the
        // active draw-buffer list. Clearing attachments makes each stage attach
        // only the scratch buffers it actually writes.
        for (int t = 0; t <= n; t++) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + t,
                GL11.GL_TEXTURE_2D, 0, 0);
        }
        MRT_DRAW_BUF.clear();
        int count = 0;
        for (int t : drawBuffers) {
            if (t < 0 || t > n) continue;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + t,
                GL11.GL_TEXTURE_2D, mrtAlt[t], 0);
            MRT_DRAW_BUF.put(GL30.GL_COLOR_ATTACHMENT0 + t);
            count++;
        }
        if (count == 0) {  // nothing valid to write — default to colortex0
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, mrtAlt[0], 0);
            MRT_DRAW_BUF.put(GL30.GL_COLOR_ATTACHMENT0);
        }
        MRT_DRAW_BUF.flip();
        GL20.glDrawBuffers(MRT_DRAW_BUF);

        bindMrtInputs(program, n, depthTex);
        GlStateManager.viewport(0, 0, w, h);
        drawFullscreen();

        // Flip written buffers in.
        if (count == 0) { int tmp = mrtCur[0]; mrtCur[0] = mrtAlt[0]; mrtAlt[0] = tmp; }
        else for (int t : drawBuffers) {
            if (t < 0 || t > n) continue;
            int tmp = mrtCur[t]; mrtCur[t] = mrtAlt[t]; mrtAlt[t] = tmp;
        }
    }

    /** Bind the current colortex set + depth + shadow + noise + uniforms for an MRT stage. */
    private void bindMrtInputs(ShaderProgram program, int n, int depthTex) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mrtCur[0]);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
        for (int i = 1; i <= 7; i++) {
            int tex = (i <= n) ? mrtCur[i] : blackTex;
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + (i + 1));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 10);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,
            com.limitlessdev.ldog.render.shaderpack.ShaderNoiseTexture.get());

        program.bind();
        program.setUniform1i("colortex0", 0);
        program.setUniform1i("depthtex0", 1);
        program.setUniform1i("colortex1", 2);
        program.setUniform1i("colortex2", 3);
        program.setUniform1i("colortex3", 4);
        program.setUniform1i("colortex4", 5);
        program.setUniform1i("colortex5", 6);
        program.setUniform1i("colortex6", 7);
        program.setUniform1i("colortex7", 8);
        program.setUniform1i("depthtex1", 1);
        program.setUniform1i("depthtex2", 1);
        program.setUniform1i("noisetex", 10);
        program.setUniform1i("noiseTextureResolution",
            com.limitlessdev.ldog.render.shaderpack.ShaderNoiseTexture.RESOLUTION);
        uniforms.feedTo(program);
        com.limitlessdev.ldog.render.shaderpack.ShadowMapManager.feed(program, 9);
    }

    private boolean ensureMrt(ShaderPackRuntime runtime, int n, int w, int h) {
        if (mrtFbo != 0 && mrtN == n && mrtW == w && mrtH == h) return true;
        disposeMrt();
        mrtN = n; mrtW = w; mrtH = h;
        mrtCur = new int[n + 1];
        mrtAlt = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            // Match the pack's declared format per colortex so the flipping
            // scratch keeps HDR/16-bit precision (e.g. BSL's R11F_G11F_B10F
            // colortex0) instead of re-clipping through RGBA8 each stage.
            int fmt = runtime.colortexFormat(i);
            mrtCur[i] = allocColorTex(w, h, fmt);
            mrtAlt[i] = allocColorTex(w, h, fmt);
        }
        mrtFbo = GL30.glGenFramebuffers();
        mrtCopyFbo = GL30.glGenFramebuffers();
        return true;
    }

    private void disposeMrt() {
        if (mrtCur != null) for (int t : mrtCur) if (t != 0) GL11.glDeleteTextures(t);
        if (mrtAlt != null) for (int t : mrtAlt) if (t != 0) GL11.glDeleteTextures(t);
        mrtCur = mrtAlt = null;
        if (mrtFbo != 0) { GL30.glDeleteFramebuffers(mrtFbo); mrtFbo = 0; }
        if (mrtCopyFbo != 0) { GL30.glDeleteFramebuffers(mrtCopyFbo); mrtCopyFbo = 0; }
        mrtN = -1; mrtW = mrtH = 0;
    }

    private void ensureBuffers(int w, int h) {
        if (fboA != 0 && width == w && height == h && blackTex != 0 && sceneCopyTex != 0) return;

        disposeFramebuffers();

        // Two ping-pong color targets matching main-FB dims.
        texA = allocColorTex(w, h);
        texB = allocColorTex(w, h);
        fboA = wrapFbo(texA);
        fboB = wrapFbo(texB);
        sceneCopyTex = allocColorTex(w, h);
        sceneCopyW = w; sceneCopyH = h;

        if (blackTex == 0) blackTex = allocBlackTex();
        width = w; height = h;
    }

    private static int allocColorTex(int w, int h) {
        return allocColorTex(w, h, GL11.GL_RGBA8);
    }

    private static int allocColorTex(int w, int h, int internalFormat) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0,
            com.limitlessdev.ldog.render.shaderpack.ShaderColortexFormats.uploadFormat(internalFormat),
            com.limitlessdev.ldog.render.shaderpack.ShaderColortexFormats.uploadType(internalFormat),
            (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int wrapFbo(int colorTex) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, colorTex, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LDOGMod.LOGGER.error("LDOG: Shader pack composite FBO incomplete (status=0x{})",
                Integer.toHexString(status));
            GL30.glDeleteFramebuffers(fbo);
            return 0;
        }
        return fbo;
    }

    private static int allocBlackTex() {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        java.nio.ByteBuffer zero = org.lwjgl.BufferUtils.createByteBuffer(4);
        zero.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zero);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    @Override
    public void dispose() {
        disposeFramebuffers();
        disposeMrt();
        if (blackTex != 0) { GL11.glDeleteTextures(blackTex); blackTex = 0; }
    }

    private void disposeFramebuffers() {
        if (fboA != 0) { GL30.glDeleteFramebuffers(fboA); fboA = 0; }
        if (fboB != 0) { GL30.glDeleteFramebuffers(fboB); fboB = 0; }
        if (texA != 0) { GL11.glDeleteTextures(texA);     texA = 0; }
        if (texB != 0) { GL11.glDeleteTextures(texB);     texB = 0; }
        if (sceneCopyTex != 0) { GL11.glDeleteTextures(sceneCopyTex); sceneCopyTex = 0; }
        sceneCopyW = sceneCopyH = 0;
        width = height = 0;
    }

    @Override public boolean isEnabled() { return true; }
}
