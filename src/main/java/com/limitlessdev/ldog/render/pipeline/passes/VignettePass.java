package com.limitlessdev.ldog.render.pipeline.passes;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.render.pipeline.PostProcessContext;
import com.limitlessdev.ldog.render.pipeline.PostProcessPass;
import com.limitlessdev.ldog.render.pipeline.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Phase "future expansion" — radial vignette darkening of the main image.
 *
 * Design choice: uses multiplicative blending (GL_DST_COLOR × src) instead
 * of the copy-source-then-sample-and-write pattern that RCAS / FXAA use.
 * The vignette factor is independent of the existing pixel colour — we just
 * need to multiply each pixel by a scalar — so a simple fullscreen quad with
 * blend func GL_DST_COLOR/GL_ZERO does the job in one draw with no extra
 * texture allocation. Cheapest pipeline pass on the chain.
 *
 * Runs LAST in the pipeline (after FXAA) so the vignette is the final
 * thing applied to the image. Putting it earlier would let FXAA's edge
 * detection see the vignette gradient and waste samples on it.
 *
 * Aspect-corrected distance so the vignette is a true circle, not stretched
 * to the screen aspect (which would put more darkening at the side edges
 * than at the top / bottom on widescreen displays).
 */
public final class VignettePass implements PostProcessPass {

    private static final String VERT_SOURCE =
        "#version 120\n" +
        "varying vec2 v_texCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n" +
        "    v_texCoord = gl_Vertex.xy * 0.5 + 0.5;\n" +
        "}\n";

    /**
     * Outputs vec4(vignette, vignette, vignette, 1) where vignette goes
     * from 1.0 at the centre to (1.0 - intensity) at the corners. With
     * GL_DST_COLOR/GL_ZERO blending this multiplies the existing main-FB
     * pixel by the vignette scalar — clear in the middle, darkened at
     * the edges. The fixed inner-radius + softness gives a recognisable
     * cinematic falloff without needing extra user-tunable knobs (we keep
     * the GUI to one slider for v1).
     */
    private static final String FRAG_SOURCE =
        "#version 120\n" +
        "uniform float u_intensity;\n" +
        "uniform float u_aspect;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 centered = v_texCoord - 0.5;\n" +
        "    centered.x *= u_aspect;\n" +
        "    float dist = length(centered);\n" +
        "    // Inner radius 0.40 stays clear; smooth falloff out to 0.85.\n" +
        "    // At u_intensity = 1.0, corners go fully black; at 0.5 they\n" +
        "    // dim by 50%. At 0.0 the pass is a no-op (vignette = 1.0).\n" +
        "    float falloff = smoothstep(0.40, 0.85, dist);\n" +
        "    float vignette = 1.0 - falloff * u_intensity;\n" +
        "    gl_FragColor = vec4(vec3(vignette), 1.0);\n" +
        "}\n";

    private ShaderProgram shader;
    private boolean shaderFailed;
    private boolean loggedFirstExecute;

    @Override
    public String id() {
        return "vignette";
    }

    @Override
    public void init(int width, int height) {
        try {
            shader = new ShaderProgram("ldog_vignette", VERT_SOURCE, FRAG_SOURCE);
            LDOGMod.LOGGER.info("LDOG: Vignette shader compiled OK");
        } catch (ShaderProgram.ShaderCompileException e) {
            shaderFailed = true;
            LDOGMod.LOGGER.error("LDOG: Vignette shader compile failed; pass will no-op", e);
        }
    }

    @Override
    public void resize(int width, int height) {
        // No allocations to reshape — shader uses NDC + uniforms only.
    }

    @Override
    public void execute(PostProcessContext ctx) {
        if (shaderFailed || shader == null) return;
        if (!LDOGConfig.enableVignette) return;
        if (LDOGConfig.vignetteIntensity <= 0.0) return;  // intensity 0 is a no-op

        int w = ctx.mainWidth();
        int h = ctx.mainHeight();
        if (w <= 0 || h <= 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_VIEWPORT_BIT);

        // Bind main FB + viewport defensively in case prior pass left
        // something else bound. Vignette never needs the scene FBO.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ctx.mainFbo());
        GlStateManager.viewport(0, 0, w, h);

        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();

        // Multiplicative blend: out = src.rgb * dst.rgb (alpha unchanged).
        // Equivalent to: each existing pixel × the vignette scalar.
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);

        shader.bind();
        shader.setUniform1f("u_intensity", (float) LDOGConfig.vignetteIntensity);
        shader.setUniform1f("u_aspect", (float) w / (float) h);

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glVertex2f( 3.0f, -1.0f);
        GL11.glVertex2f(-1.0f,  3.0f);
        GL11.glEnd();

        ShaderProgram.unbind();
        GL11.glPopAttrib();

        if (!loggedFirstExecute) {
            loggedFirstExecute = true;
            LDOGMod.LOGGER.info("LDOG: Vignette pass live at {}x{}", w, h);
        }
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }

    @Override
    public boolean isEnabled() {
        // Gate inline in execute() — cheaper than excluding from the chain
        // since toggling involves a config flip, not a pipeline rebuild.
        return true;
    }
}
