package com.limitlessdev.ldog.render.sky;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Manages and renders custom sky layers from resource packs.
 *
 * Scans optifine/sky/world0/ (and mcpatcher/sky/world0/) for .properties
 * files at TextureStitchEvent.Pre. Each layer defines a texture, fade
 * timing, blend mode, and optional rotation.
 *
 * Layers are rendered after vanilla sky (injected at RETURN of
 * RenderGlobal.renderSky via MixinRenderGlobal) as textured sky domes
 * with time-based alpha fading.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class CustomSkyRenderer {

    private static final List<CustomSkyLayer> layers = new ArrayList<>();
    private static final float SKY_RADIUS = 100.0f;

    @SubscribeEvent
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (!LDOGConfig.enableCustomSky) return;
        layers.clear();
        loadSkyLayers();
    }

    private static void loadSkyLayers() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() == null) return;

        for (String basePath : new String[]{"optifine/sky/world0", "mcpatcher/sky/world0"}) {
            boolean found = false;

            // Try sky1.properties through sky64.properties
            for (int i = 1; i <= 64; i++) {
                ResourceLocation propsLoc = new ResourceLocation("minecraft",
                    basePath + "/sky" + i + ".properties");
                try {
                    IResource resource = mc.getResourceManager().getResource(propsLoc);
                    Properties props = new Properties();
                    props.load(resource.getInputStream());
                    resource.close();

                    CustomSkyLayer layer = parseLayer(props, basePath);
                    if (layer != null) {
                        layers.add(layer);
                        found = true;
                    }
                } catch (IOException ignored) {
                    // No more sky files at this index
                }
            }

            if (found) {
                LDOGMod.LOGGER.info("LDOG: Loaded {} custom sky layers from {}", layers.size(), basePath);
                break;
            }
        }
    }

    private static CustomSkyLayer parseLayer(Properties props, String basePath) {
        String source = props.getProperty("source");
        if (source == null || source.isEmpty()) return null;

        // Resolve texture path:
        // "skybox:stars.png" → ResourceLocation("skybox", "stars.png")
        // "./texture.png" → relative to properties file directory
        // "mcpatcher/sky/world0/stars.png" → absolute within minecraft namespace
        // "stars.png" → relative to properties file directory
        ResourceLocation texture;
        if (source.contains(":")) {
            texture = new ResourceLocation(source);
        } else if (source.startsWith("./")) {
            texture = new ResourceLocation("minecraft", basePath + "/" + source.substring(2));
        } else if (source.contains("/")) {
            // Absolute path within minecraft namespace (e.g., mcpatcher/sky/world0/stars.png)
            texture = new ResourceLocation("minecraft", source);
        } else {
            // Simple filename, relative to properties file directory
            texture = new ResourceLocation("minecraft", basePath + "/" + source);
        }

        // Parse fade times. If startFadeOut is missing, default to endFadeOut (instant fade-out).
        int endFadeOut = parseTime(props, "endFadeOut", 12000);
        int startFadeOut = parseTime(props, "startFadeOut", endFadeOut);
        int startFadeIn = parseTime(props, "startFadeIn", 0);
        int endFadeIn = parseTime(props, "endFadeIn", startFadeIn);
        String blend = props.getProperty("blend", "add").trim().toLowerCase();
        // Normalize blend mode names
        if ("alpha".equals(blend)) blend = "overlay";
        boolean rotate = Boolean.parseBoolean(props.getProperty("rotate", "true"));
        float speed = parseFloat(props, "speed", 1.0f);

        float axisX = 0, axisY = 0, axisZ = 1;
        String axis = props.getProperty("axis");
        if (axis != null) {
            String[] parts = axis.trim().split("\\s+");
            if (parts.length >= 3) {
                try {
                    axisX = Float.parseFloat(parts[0]);
                    axisY = Float.parseFloat(parts[1]);
                    axisZ = Float.parseFloat(parts[2]);
                } catch (NumberFormatException ignored) {}
            }
        }

        return new CustomSkyLayer(texture, startFadeIn, endFadeIn, startFadeOut, endFadeOut,
            blend, rotate, speed, axisX, axisY, axisZ);
    }

    /**
     * Render all custom sky layers. Called from MixinRenderGlobal after vanilla sky.
     */
    public static void renderCustomSky(float partialTicks) {
        if (layers.isEmpty()) return;

        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) return;
        if (world.provider.getDimension() != 0) return; // Only overworld

        long worldTime = world.getWorldTime();

        for (CustomSkyLayer layer : layers) {
            float alpha = layer.getAlpha(worldTime);
            if (alpha <= 0.0f) continue;

            renderLayer(layer, alpha, partialTicks, world);
        }
    }

    private static void renderLayer(CustomSkyLayer layer, float alpha,
                                     float partialTicks, WorldClient world) {
        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);

        // Set blend mode
        switch (layer.blend) {
            case "add":
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                break;
            case "multiply":
                GlStateManager.blendFunc(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA);
                break;
            default: // "overlay" or default
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                break;
        }

        // Apply rotation
        if (layer.rotate) {
            float celestialAngle = world.getCelestialAngle(partialTicks);
            float rotation = celestialAngle * 360.0f * layer.speed;
            GlStateManager.rotate(rotation, layer.axisX, layer.axisY, layer.axisZ);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        mc.getTextureManager().bindTexture(layer.texture);

        // Render textured sky dome (hemisphere of quads)
        renderSkyDome();

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * Renders a textured hemisphere (upper half of a sphere) covering the sky.
     * Uses latitude/longitude bands with 16 segments each.
     */
    private static void renderSkyDome() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        int segments = 16;

        for (int lat = 0; lat < segments / 2; lat++) {
            float theta0 = (float) (lat * Math.PI / segments);       // 0 to π/2
            float theta1 = (float) ((lat + 1) * Math.PI / segments); // next latitude

            float y0 = (float) Math.cos(theta0) * SKY_RADIUS;
            float r0 = (float) Math.sin(theta0) * SKY_RADIUS;
            float y1 = (float) Math.cos(theta1) * SKY_RADIUS;
            float r1 = (float) Math.sin(theta1) * SKY_RADIUS;

            float v0 = (float) lat / (segments / 2.0f);
            float v1 = (float) (lat + 1) / (segments / 2.0f);

            buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_TEX);

            for (int lon = 0; lon <= segments; lon++) {
                float phi = (float) (lon * 2.0 * Math.PI / segments);
                float u = (float) lon / segments;

                float x0 = (float) Math.cos(phi) * r0;
                float z0 = (float) Math.sin(phi) * r0;
                float x1 = (float) Math.cos(phi) * r1;
                float z1 = (float) Math.sin(phi) * r1;

                buffer.pos(x0, y0, z0).tex(u, v0).endVertex();
                buffer.pos(x1, y1, z1).tex(u, v1).endVertex();
            }

            tessellator.draw();
        }
    }

    /**
     * Parse a time value from properties. Supports both raw tick values (0-24000)
     * and HH:MM format (e.g., "19:20", "4:40").
     *
     * MC time mapping: 6:00 = 0 ticks, 12:00 = 6000, 18:00 = 12000, 0:00 = 18000.
     */
    private static int parseTime(Properties props, String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        val = val.trim();

        if (val.contains(":")) {
            // HH:MM format → convert to MC ticks
            String[] parts = val.split(":");
            try {
                int hours = Integer.parseInt(parts[0]);
                int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                // MC time: 6:00 = tick 0, wraps at 24000
                int ticks = ((hours - 6) * 1000 + minutes * 1000 / 60 + 24000) % 24000;
                return ticks;
            } catch (NumberFormatException e) { return defaultValue; }
        }

        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static int parseInt(Properties props, String key, int defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static float parseFloat(Properties props, String key, float defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try { return Float.parseFloat(val.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }
}
