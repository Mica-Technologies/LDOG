package com.limitlessdev.ldog.gui;

import com.limitlessdev.ldog.Tags;
import com.limitlessdev.ldog.compat.OptiFineCompat;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import java.io.IOException;

/**
 * LDOG settings GUI accessible from Minecraft's main options screen.
 * Provides in-game toggles for all LDOG features without needing
 * to edit config files manually.
 */
public class GuiLDOGSettings extends GuiScreen {

    private final GuiScreen parentScreen;

    // Button IDs
    private static final int BTN_DONE = 200;

    // Performance toggles
    private static final int BTN_RENDER_OPTS = 10;
    private static final int BTN_ENTITY_DIST = 11;
    private static final int BTN_TE_DIST = 12;
    private static final int BTN_PARTICLE_CULL = 13;

    // FPS management
    private static final int BTN_FPS_REDUCER = 20;
    private static final int BTN_UNFOCUSED_FPS = 21;
    private static final int BTN_AFK_TIMEOUT = 22;
    private static final int BTN_AFK_FPS = 23;

    // Visual
    private static final int BTN_CLEAR_WATER = 30;
    private static final int BTN_WATER_OPACITY = 31;

    // Future features
    private static final int BTN_CTM = 40;
    private static final int BTN_EMISSIVE = 41;
    private static final int BTN_DYNAMIC_LIGHTS = 42;
    private static final int BTN_CUSTOM_SKY = 43;
    private static final int BTN_HD_TEXTURES = 44;
    private static final int BTN_SHADERS = 45;

    // Slider value arrays for cycling
    private static final int[] ENTITY_DIST_VALUES = {0, 32, 48, 64, 96, 128, 192, 256, 512};
    private static final int[] TE_DIST_VALUES = {0, 16, 32, 48, 64, 96, 128, 256};
    private static final int[] UNFOCUSED_FPS_VALUES = {1, 2, 5, 10, 15, 30, 60};
    private static final int[] AFK_TIMEOUT_VALUES = {0, 60, 120, 300, 600, 900, 1800, 3600};
    private static final int[] AFK_FPS_VALUES = {1, 2, 5, 10, 15, 30, 60};
    private static final double[] WATER_OPACITY_VALUES = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};

    public GuiLDOGSettings(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int leftCol = this.width / 2 - 155;
        int rightCol = this.width / 2 + 5;
        int btnW = 150;
        int btnH = 20;
        int y = 32;

        // ---- Row: Performance ----
        y += 24;
        addButton(new GuiButton(BTN_RENDER_OPTS, leftCol, y, btnW, btnH,
            toggleLabel("Render Optimizations", LDOGConfig.enableRenderOptimizations)));
        addButton(new GuiButton(BTN_PARTICLE_CULL, rightCol, y, btnW, btnH,
            toggleLabel("Particle Culling", LDOGConfig.enableParticleCulling)));

        y += 24;
        addButton(new GuiButton(BTN_ENTITY_DIST, leftCol, y, btnW, btnH,
            distLabel("Entity Render Dist", LDOGConfig.entityRenderDistance)));
        addButton(new GuiButton(BTN_TE_DIST, rightCol, y, btnW, btnH,
            distLabel("TileEntity Render Dist", LDOGConfig.tileEntityRenderDistance)));

        // ---- Row: FPS Management ----
        y += 32;
        addButton(new GuiButton(BTN_FPS_REDUCER, leftCol, y, btnW, btnH,
            toggleLabel("FPS Reducer", LDOGConfig.enableFpsReducer)));
        addButton(new GuiButton(BTN_UNFOCUSED_FPS, rightCol, y, btnW, btnH,
            valLabel("Unfocused FPS", LDOGConfig.unfocusedFpsLimit)));

        y += 24;
        addButton(new GuiButton(BTN_AFK_TIMEOUT, leftCol, y, btnW, btnH,
            afkTimeoutLabel(LDOGConfig.afkTimeoutSeconds)));
        addButton(new GuiButton(BTN_AFK_FPS, rightCol, y, btnW, btnH,
            valLabel("AFK FPS", LDOGConfig.afkFpsLimit)));

        // ---- Row: Visual ----
        y += 32;
        addButton(new GuiButton(BTN_CLEAR_WATER, leftCol, y, btnW, btnH,
            toggleLabel("Clear Water", LDOGConfig.enableClearWater)));
        addButton(new GuiButton(BTN_WATER_OPACITY, rightCol, y, btnW, btnH,
            opacityLabel("Water Opacity", LDOGConfig.waterOpacity)));

        // ---- Row: Feature Toggles (future) ----
        y += 32;
        addButton(makeFeatureButton(BTN_CTM, leftCol, y, btnW, btnH,
            "Connected Textures", LDOGConfig.enableConnectedTextures, OptiFineCompat.shouldHandleCTM()));
        addButton(makeFeatureButton(BTN_EMISSIVE, rightCol, y, btnW, btnH,
            "Emissive Textures", LDOGConfig.enableEmissiveTextures, OptiFineCompat.shouldHandleEmissive()));

        y += 24;
        addButton(makeFeatureButton(BTN_DYNAMIC_LIGHTS, leftCol, y, btnW, btnH,
            "Dynamic Lights", LDOGConfig.enableDynamicLights, OptiFineCompat.shouldHandleDynamicLights()));
        addButton(makeFeatureButton(BTN_CUSTOM_SKY, rightCol, y, btnW, btnH,
            "Custom Sky", LDOGConfig.enableCustomSky, OptiFineCompat.shouldHandleCustomSky()));

        y += 24;
        addButton(makeFeatureButton(BTN_HD_TEXTURES, leftCol, y, btnW, btnH,
            "HD Textures", LDOGConfig.enableHDTextures, OptiFineCompat.shouldHandleHDTextures()));
        addButton(makeFeatureButton(BTN_SHADERS, rightCol, y, btnW, btnH,
            "Shaders", LDOGConfig.enableShaders, OptiFineCompat.shouldHandleShaders()));

        // ---- Done button ----
        addButton(new GuiButton(BTN_DONE, this.width / 2 - 100, this.height - 27, 200, btnH,
            I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;

        switch (button.id) {
            case BTN_DONE:
                saveAndClose();
                break;

            // Performance toggles
            case BTN_RENDER_OPTS:
                LDOGConfig.enableRenderOptimizations = !LDOGConfig.enableRenderOptimizations;
                button.displayString = toggleLabel("Render Optimizations", LDOGConfig.enableRenderOptimizations);
                break;
            case BTN_PARTICLE_CULL:
                LDOGConfig.enableParticleCulling = !LDOGConfig.enableParticleCulling;
                button.displayString = toggleLabel("Particle Culling", LDOGConfig.enableParticleCulling);
                break;
            case BTN_ENTITY_DIST:
                LDOGConfig.entityRenderDistance = cycleValue(ENTITY_DIST_VALUES, LDOGConfig.entityRenderDistance);
                button.displayString = distLabel("Entity Render Dist", LDOGConfig.entityRenderDistance);
                break;
            case BTN_TE_DIST:
                LDOGConfig.tileEntityRenderDistance = cycleValue(TE_DIST_VALUES, LDOGConfig.tileEntityRenderDistance);
                button.displayString = distLabel("TileEntity Render Dist", LDOGConfig.tileEntityRenderDistance);
                break;

            // FPS management
            case BTN_FPS_REDUCER:
                LDOGConfig.enableFpsReducer = !LDOGConfig.enableFpsReducer;
                button.displayString = toggleLabel("FPS Reducer", LDOGConfig.enableFpsReducer);
                break;
            case BTN_UNFOCUSED_FPS:
                LDOGConfig.unfocusedFpsLimit = cycleValue(UNFOCUSED_FPS_VALUES, LDOGConfig.unfocusedFpsLimit);
                button.displayString = valLabel("Unfocused FPS", LDOGConfig.unfocusedFpsLimit);
                break;
            case BTN_AFK_TIMEOUT:
                LDOGConfig.afkTimeoutSeconds = cycleValue(AFK_TIMEOUT_VALUES, LDOGConfig.afkTimeoutSeconds);
                button.displayString = afkTimeoutLabel(LDOGConfig.afkTimeoutSeconds);
                break;
            case BTN_AFK_FPS:
                LDOGConfig.afkFpsLimit = cycleValue(AFK_FPS_VALUES, LDOGConfig.afkFpsLimit);
                button.displayString = valLabel("AFK FPS", LDOGConfig.afkFpsLimit);
                break;

            // Visual
            case BTN_CLEAR_WATER:
                LDOGConfig.enableClearWater = !LDOGConfig.enableClearWater;
                button.displayString = toggleLabel("Clear Water", LDOGConfig.enableClearWater);
                break;
            case BTN_WATER_OPACITY:
                LDOGConfig.waterOpacity = cycleValue(WATER_OPACITY_VALUES, LDOGConfig.waterOpacity);
                button.displayString = opacityLabel("Water Opacity", LDOGConfig.waterOpacity);
                break;

            // Future features
            case BTN_CTM:
                LDOGConfig.enableConnectedTextures = !LDOGConfig.enableConnectedTextures;
                button.displayString = featureLabel("Connected Textures", LDOGConfig.enableConnectedTextures, OptiFineCompat.shouldHandleCTM());
                break;
            case BTN_EMISSIVE:
                LDOGConfig.enableEmissiveTextures = !LDOGConfig.enableEmissiveTextures;
                button.displayString = featureLabel("Emissive Textures", LDOGConfig.enableEmissiveTextures, OptiFineCompat.shouldHandleEmissive());
                break;
            case BTN_DYNAMIC_LIGHTS:
                LDOGConfig.enableDynamicLights = !LDOGConfig.enableDynamicLights;
                button.displayString = featureLabel("Dynamic Lights", LDOGConfig.enableDynamicLights, OptiFineCompat.shouldHandleDynamicLights());
                break;
            case BTN_CUSTOM_SKY:
                LDOGConfig.enableCustomSky = !LDOGConfig.enableCustomSky;
                button.displayString = featureLabel("Custom Sky", LDOGConfig.enableCustomSky, OptiFineCompat.shouldHandleCustomSky());
                break;
            case BTN_HD_TEXTURES:
                LDOGConfig.enableHDTextures = !LDOGConfig.enableHDTextures;
                button.displayString = featureLabel("HD Textures", LDOGConfig.enableHDTextures, OptiFineCompat.shouldHandleHDTextures());
                break;
            case BTN_SHADERS:
                LDOGConfig.enableShaders = !LDOGConfig.enableShaders;
                button.displayString = featureLabel("Shaders", LDOGConfig.enableShaders, OptiFineCompat.shouldHandleShaders());
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(this.fontRenderer, "LDOG Settings", this.width / 2, 15, 0xFFFFFF);

        // Section headers
        int leftCol = this.width / 2 - 155;
        int y = 32;
        this.drawString(this.fontRenderer, "\u00a7e Performance", leftCol, y + 6, 0xFFFFFF);

        y += 24 + 24 + 8;
        this.drawString(this.fontRenderer, "\u00a7e FPS Management", leftCol, y + 6, 0xFFFFFF);

        y += 24 + 24 + 8;
        this.drawString(this.fontRenderer, "\u00a7e Visual", leftCol, y + 6, 0xFFFFFF);

        y += 24 + 8;
        String featureHeader = OptiFineCompat.isOptiFineLoaded()
            ? "\u00a7e Features \u00a77(OptiFine detected - overlapping features deferred)"
            : "\u00a7e Features \u00a77(not yet implemented)";
        this.drawString(this.fontRenderer, featureHeader, leftCol, y + 6, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void saveAndClose() {
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        this.mc.displayGuiScreen(this.parentScreen);
    }

    // ---- Label helpers ----

    private static String toggleLabel(String name, boolean value) {
        return name + ": " + (value ? "\u00a7aON" : "\u00a7cOFF");
    }

    private static String distLabel(String name, int value) {
        return name + ": " + (value == 0 ? "\u00a77Vanilla" : "\u00a7a" + value);
    }

    private static String valLabel(String name, int value) {
        return name + ": \u00a7a" + value;
    }

    private static String opacityLabel(String name, double value) {
        return name + ": \u00a7a" + String.format("%.0f%%", value * 100);
    }

    private static String afkTimeoutLabel(int seconds) {
        if (seconds == 0) return "AFK Timeout: \u00a7cDisabled";
        if (seconds < 60) return "AFK Timeout: \u00a7a" + seconds + "s";
        return "AFK Timeout: \u00a7a" + (seconds / 60) + "m";
    }

    private static String featureLabel(String name, boolean enabled, boolean ldogHandles) {
        if (!ldogHandles) return name + ": \u00a77OptiFine";
        return name + ": " + (enabled ? "\u00a7aON" : "\u00a7cOFF");
    }

    private static GuiButton makeFeatureButton(int id, int x, int y, int w, int h,
                                                String name, boolean enabled, boolean ldogHandles) {
        GuiButton btn = new GuiButton(id, x, y, w, h, featureLabel(name, enabled, ldogHandles));
        if (!ldogHandles) {
            btn.enabled = false;
        }
        return btn;
    }

    // ---- Value cycling helpers ----

    private static int cycleValue(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static double cycleValue(double[] values, double current) {
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.01) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }
}
