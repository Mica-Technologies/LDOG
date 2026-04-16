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
 * RenderGlobal.renderSky via MixinRenderGlobal) as textured spheres
 * with time-based alpha fading.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
public class CustomSkyRenderer {

    private static final List<CustomSkyLayer> layers = new ArrayList<>();
    private static final float SKY_RADIUS = 100.0f;
    private static boolean loggedOnce = false;
    private static boolean loaded = false;

    @SubscribeEvent
    public static void onTextureStitchPre(TextureStitchEvent.Pre event) {
        // Mark as needing reload — actual load happens lazily on first render
        // so resource packs are guaranteed to be available
        layers.clear();
        loaded = false;
        loggedOnce = false;
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
                        LDOGMod.LOGGER.info("LDOG: Loaded sky layer sky{} from {} (texture={}, fadeIn={}-{}, fadeOut={}-{})",
                            i, basePath, layer.texture, layer.startFadeIn, layer.endFadeIn,
                            layer.startFadeOut, layer.endFadeOut);
                    }
                } catch (IOException ignored) {
                    // File not found at this index, continue checking
                }
            }

            if (found) {
                LDOGMod.LOGGER.info("LDOG: Loaded {} custom sky layers total from {}", layers.size(), basePath);
                break;
            }
        }

        if (layers.isEmpty()) {
            LDOGMod.LOGGER.info("LDOG: No custom sky layers found in resource packs");
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
            // Absolute path within minecraft namespace
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
        // Lazy load on first render — guarantees resource packs are fully available
        if (!loaded) {
            loaded = true;
            loadSkyLayers();
        }

        if (layers.isEmpty()) return;

        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) return;
        if (world.provider.getDimension() != 0) return; // Only overworld

        long worldTime = world.getWorldTime();

        // One-time debug log with alpha values
        if (!loggedOnce) {
            LDOGMod.LOGGER.info("LDOG: Custom sky rendering with {} layers, worldTime={}",
                layers.size(), worldTime);
            for (int i = 0; i < layers.size(); i++) {
                CustomSkyLayer l = layers.get(i);
                LDOGMod.LOGGER.info("LDOG:   Layer {}: alpha={}, texture={}", i, l.getAlpha(worldTime), l.texture);
            }
            loggedOnce = true;
        }

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

        // Save and set GL state for sky rendering
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.depthMask(false);
        GlStateManager.disableFog();
        GlStateManager.disableLighting();

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

        // Apply rotation (celestial angle based)
        if (layer.rotate) {
            float celestialAngle = world.getCelestialAngle(partialTicks);
            float rotation = celestialAngle * 360.0f * layer.speed;
            GlStateManager.rotate(rotation, layer.axisX, layer.axisY, layer.axisZ);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        mc.getTextureManager().bindTexture(layer.texture);

        // Render textured full sphere covering the sky
        renderSkySphere();

        // Restore state
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableFog();
        GlStateManager.popMatrix();
    }

    /**
     * Renders a textured sphere covering the entire sky dome.
     * Uses latitude/longitude bands: 16 longitude segments, latitude from
     * zenith (top) down to -20 degrees below the horizon.
     *
     * The sphere is rendered inside-out (vertices wound so the inside face
     * is visible) since the camera is at the center.
     */
    private static void renderSkySphere() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        int lonSegments = 16;
        int latBands = 10; // from zenith to slightly below horizon

        for (int lat = 0; lat < latBands; lat++) {
            // Latitude from 0 (zenith) to ~110 degrees (below horizon)
            float theta0 = (float) (lat * Math.PI / latBands) * 0.6f;        // scale to cover 0-108°
            float theta1 = (float) ((lat + 1) * Math.PI / latBands) * 0.6f;

            float y0 = (float) Math.cos(theta0) * SKY_RADIUS;
            float r0 = (float) Math.sin(theta0) * SKY_RADIUS;
            float y1 = (float) Math.cos(theta1) * SKY_RADIUS;
            float r1 = (float) Math.sin(theta1) * SKY_RADIUS;

            float v0 = (float) lat / latBands;
            float v1 = (float) (lat + 1) / latBands;

            buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_TEX);

            for (int lon = 0; lon <= lonSegments; lon++) {
                float phi = (float) (lon * 2.0 * Math.PI / lonSegments);
                float u = (float) lon / lonSegments;

                float x0 = (float) Math.cos(phi) * r0;
                float z0 = (float) Math.sin(phi) * r0;
                float x1 = (float) Math.cos(phi) * r1;
                float z1 = (float) Math.sin(phi) * r1;

                // Reversed winding: lower latitude vertex first so inside face is visible
                buffer.pos(x1, y1, z1).tex(u, v1).endVertex();
                buffer.pos(x0, y0, z0).tex(u, v0).endVertex();
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
