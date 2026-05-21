package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.RenderTargetManager;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackManager;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackRuntime;
import com.limitlessdev.ldog.render.shaderpack.ShaderPackUniforms;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

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
        if (runtime == null || runtime.isEmpty()) return;
        if (!ctx.bindingActive()) return;

        int mainW = ctx.mainWidth();
        int mainH = ctx.mainHeight();
        if (mainW <= 0 || mainH <= 0) return;

        ensureBuffers(mainW, mainH);
        if (fboA == 0 || fboB == 0 || blackTex == 0 || sceneCopyTex == 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_VIEWPORT_BIT | GL11.GL_TEXTURE_BIT);

        // Capture main FB into colortex0 (sceneCopyTex). Composite stages
        // can then sample the scene without violating the same-attachment
        // read/write rule.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, ctx.mainFbo());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneCopyTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, mainW, mainH);

        // Snapshot per-frame uniforms once. All stages this frame see the
        // same numbers so cross-stage temporal effects stay consistent.
        uniforms.snapshot(mainW, mainH, 0.0f);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();

        // Walk composite stages, ping-ponging input/output. First stage
        // reads sceneCopyTex as colortex0; subsequent stages read whichever
        // ping was just written.
        int currentInputTex = sceneCopyTex;
        int currentOutputFbo = fboA;
        int currentOutputTex = texA;

        int depthTex = RenderTargetManager.INSTANCE.getSceneDepthTexture();
        for (ShaderPackRuntime.Stage stage : runtime.composites()) {
            runStage(stage.program, currentInputTex, depthTex,
                currentOutputFbo, mainW, mainH);
            // Swap: this stage's output becomes next stage's input.
            currentInputTex = currentOutputTex;
            if (currentOutputFbo == fboA) {
                currentOutputFbo = fboB;
                currentOutputTex = texB;
            } else {
                currentOutputFbo = fboA;
                currentOutputTex = texA;
            }
        }

        // Final stage: draw to the main FB instead of a ping target.
        ShaderPackRuntime.Stage finalStage = runtime.finalStage();
        if (finalStage != null) {
            runStage(finalStage.program, currentInputTex, depthTex,
                ctx.mainFbo(), mainW, mainH);
        } else {
            // No final.fsh — blit the last composite output back to the main
            // FB so the post-process work is actually visible.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                currentInputTex == texA ? fboA : (currentInputTex == texB ? fboB : 0));
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, ctx.mainFbo());
            GL30.glBlitFramebuffer(0, 0, mainW, mainH, 0, 0, mainW, mainH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        }

        // Restore main FB binding so subsequent passes find it bound.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());

        ShaderProgram.unbind();
        // Unbind all texture units we touched (0..8).
        for (int i = 8; i >= 0; i--) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        // Cache prev-frame matrices for the next snapshot.
        uniforms.rotatePrev();

        GL11.glPopAttrib();

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
                          int targetFbo, int w, int h) {
        // colortex0 (unit 0) — the current scene/composite-chain input.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colortex0);
        // depthtex0 (unit 1) — the world depth texture.
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthtex0);
        // colortex1..7 (units 2..8) — bound to the safe-zero 1x1 black
        // texture so shader-pack samples return (0,0,0,0) instead of
        // hitting a GL error on uninitialized slots.
        for (int i = 1; i <= 7; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + (i + 1));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, blackTex);
        }

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
        // Snapshot's standard uniforms (cameraPosition, sunPosition, ...).
        uniforms.feedTo(program);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
        GlStateManager.viewport(0, 0, w, h);

        // Triangle overdrawing the screen (cheaper than a quad).
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();
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
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
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
