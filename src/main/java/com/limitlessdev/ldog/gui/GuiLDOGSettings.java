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
 * LDOG settings GUI with a scrollable list of settings.
 * Uses GuiLDOGSettingsList (extends GuiListExtended) for the scrollable
 * content area, with a fixed Done button at the bottom.
 */
public class GuiLDOGSettings extends GuiScreen {

    private final GuiScreen parentScreen;
    private GuiLDOGSettingsList settingsList;

    private static final int BTN_DONE = 200;
    private static final int BTN_RENDER_OPTS = 10;
    private static final int BTN_ENTITY_DIST = 11;
    private static final int BTN_TE_DIST = 12;
    private static final int BTN_PARTICLE_CULL = 13;
    private static final int BTN_FPS_REDUCER = 20;
    private static final int BTN_UNFOCUSED_FPS = 21;
    private static final int BTN_AFK_TIMEOUT = 22;
    private static final int BTN_AFK_FPS = 23;
    private static final int BTN_CLEAR_WATER = 30;
    private static final int BTN_WATER_OPACITY = 31;
    private static final int BTN_CTM = 40;
    private static final int BTN_EMISSIVE = 41;
    private static final int BTN_DYNAMIC_LIGHTS = 42;
    private static final int BTN_CUSTOM_SKY = 43;
    private static final int BTN_HD_TEXTURES = 44;
    private static final int BTN_SHADERS = 45;

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

        int w = 150;
        int h = 20;

        // Create scrollable list (area between title and Done button)
        settingsList = new GuiLDOGSettingsList(this.mc, this.width, this.height, 28, this.height - 32);

        // -- Performance --
        settingsList.addHeaderRow("Performance");
        settingsList.addButtonRow(
            new GuiButton(BTN_RENDER_OPTS, 0, 0, w, h,
                toggleLabel("Render Opts", LDOGConfig.enableRenderOptimizations)),
            new GuiButton(BTN_PARTICLE_CULL, 0, 0, w, h,
                toggleLabel("Particle Culling", LDOGConfig.enableParticleCulling)));
        settingsList.addButtonRow(
            new GuiButton(BTN_ENTITY_DIST, 0, 0, w, h,
                distLabel("Entity Dist", LDOGConfig.entityRenderDistance)),
            new GuiButton(BTN_TE_DIST, 0, 0, w, h,
                distLabel("TileEntity Dist", LDOGConfig.tileEntityRenderDistance)));

        // -- FPS Management --
        settingsList.addHeaderRow("FPS Management");
        settingsList.addButtonRow(
            new GuiButton(BTN_FPS_REDUCER, 0, 0, w, h,
                toggleLabel("FPS Reducer", LDOGConfig.enableFpsReducer)),
            new GuiButton(BTN_UNFOCUSED_FPS, 0, 0, w, h,
                valLabel("Unfocused FPS", LDOGConfig.unfocusedFpsLimit)));
        settingsList.addButtonRow(
            new GuiButton(BTN_AFK_TIMEOUT, 0, 0, w, h,
                afkTimeoutLabel(LDOGConfig.afkTimeoutSeconds)),
            new GuiButton(BTN_AFK_FPS, 0, 0, w, h,
                valLabel("AFK FPS", LDOGConfig.afkFpsLimit)));

        // -- Visual --
        settingsList.addHeaderRow("Visual");
        settingsList.addButtonRow(
            new GuiButton(BTN_CLEAR_WATER, 0, 0, w, h,
                toggleLabel("Clear Water", LDOGConfig.enableClearWater)),
            new GuiButton(BTN_WATER_OPACITY, 0, 0, w, h,
                opacityLabel("Water Opacity", LDOGConfig.waterOpacity)));

        // -- Features --
        String featureNote = OptiFineCompat.isOptiFineLoaded()
            ? "Features (OptiFine handles these)"
            : "Features (coming soon)";
        settingsList.addHeaderRow(featureNote);
        settingsList.addButtonRow(
            makeFeatureButton(BTN_CTM, w, h, "Connected Textures",
                LDOGConfig.enableConnectedTextures, OptiFineCompat.shouldHandleCTM()),
            makeFeatureButton(BTN_EMISSIVE, w, h, "Emissive Textures",
                LDOGConfig.enableEmissiveTextures, OptiFineCompat.shouldHandleEmissive()));
        settingsList.addButtonRow(
            makeFeatureButton(BTN_DYNAMIC_LIGHTS, w, h, "Dynamic Lights",
                LDOGConfig.enableDynamicLights, OptiFineCompat.shouldHandleDynamicLights()),
            makeFeatureButton(BTN_CUSTOM_SKY, w, h, "Custom Sky",
                LDOGConfig.enableCustomSky, OptiFineCompat.shouldHandleCustomSky()));
        settingsList.addButtonRow(
            makeFeatureButton(BTN_HD_TEXTURES, w, h, "HD Textures",
                LDOGConfig.enableHDTextures, OptiFineCompat.shouldHandleHDTextures()),
            makeFeatureButton(BTN_SHADERS, w, h, "Shaders",
                LDOGConfig.enableShaders, OptiFineCompat.shouldHandleShaders()));

        // Done button (fixed at bottom, outside scrollable area)
        this.buttonList.add(new GuiButton(BTN_DONE,
            this.width / 2 - 100, this.height - 27, 200, h,
            I18n.format("gui.done")));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        settingsList.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        settingsList.mouseClicked(mouseX, mouseY, mouseButton);

        // Handle button clicks from the scrollable list
        handleListButtonClick(mouseX, mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        settingsList.mouseReleased(mouseX, mouseY, state);
    }

    private void handleListButtonClick(int mouseX, int mouseY) {
        // Find which button in the list was clicked and dispatch actionPerformed
        for (int i = 0; i < settingsList.getSize(); i++) {
            net.minecraft.client.gui.GuiListExtended.IGuiListEntry entry = settingsList.getListEntry(i);
            if (entry instanceof GuiLDOGSettingsList.ButtonRowEntry) {
                GuiLDOGSettingsList.ButtonRowEntry row = (GuiLDOGSettingsList.ButtonRowEntry) entry;
                GuiButton left = row.getLeftButton();
                GuiButton right = row.getRightButton();
                if (left != null && left.mousePressed(this.mc, mouseX, mouseY)) {
                    try { actionPerformed(left); } catch (IOException ignored) {}
                }
                if (right != null && right.mousePressed(this.mc, mouseX, mouseY)) {
                    try { actionPerformed(right); } catch (IOException ignored) {}
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) return;

        switch (button.id) {
            case BTN_DONE:
                saveAndClose();
                break;
            case BTN_RENDER_OPTS:
                LDOGConfig.enableRenderOptimizations = !LDOGConfig.enableRenderOptimizations;
                button.displayString = toggleLabel("Render Opts", LDOGConfig.enableRenderOptimizations);
                break;
            case BTN_PARTICLE_CULL:
                LDOGConfig.enableParticleCulling = !LDOGConfig.enableParticleCulling;
                button.displayString = toggleLabel("Particle Culling", LDOGConfig.enableParticleCulling);
                break;
            case BTN_ENTITY_DIST:
                LDOGConfig.entityRenderDistance = cycleValue(ENTITY_DIST_VALUES, LDOGConfig.entityRenderDistance);
                button.displayString = distLabel("Entity Dist", LDOGConfig.entityRenderDistance);
                break;
            case BTN_TE_DIST:
                LDOGConfig.tileEntityRenderDistance = cycleValue(TE_DIST_VALUES, LDOGConfig.tileEntityRenderDistance);
                button.displayString = distLabel("TileEntity Dist", LDOGConfig.tileEntityRenderDistance);
                break;
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
            case BTN_CLEAR_WATER:
                LDOGConfig.enableClearWater = !LDOGConfig.enableClearWater;
                button.displayString = toggleLabel("Clear Water", LDOGConfig.enableClearWater);
                break;
            case BTN_WATER_OPACITY:
                LDOGConfig.waterOpacity = cycleValue(WATER_OPACITY_VALUES, LDOGConfig.waterOpacity);
                button.displayString = opacityLabel("Water Opacity", LDOGConfig.waterOpacity);
                break;
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
        settingsList.drawScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRenderer, "LDOG Settings", this.width / 2, 8, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void saveAndClose() {
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        this.mc.displayGuiScreen(this.parentScreen);
    }

    // ---- Label helpers ----

    static String toggleLabel(String name, boolean value) {
        return name + ": " + (value ? "\u00a7aON" : "\u00a7cOFF");
    }

    static String distLabel(String name, int value) {
        return name + ": " + (value == 0 ? "\u00a77Vanilla" : "\u00a7a" + value);
    }

    static String valLabel(String name, int value) {
        return name + ": \u00a7a" + value;
    }

    static String opacityLabel(String name, double value) {
        return name + ": \u00a7a" + String.format("%.0f%%", value * 100);
    }

    static String afkTimeoutLabel(int seconds) {
        if (seconds == 0) return "AFK Timeout: \u00a7cDisabled";
        if (seconds < 60) return "AFK Timeout: \u00a7a" + seconds + "s";
        return "AFK Timeout: \u00a7a" + (seconds / 60) + "m";
    }

    static String featureLabel(String name, boolean enabled, boolean ldogHandles) {
        if (!ldogHandles) return name + ": \u00a77OptiFine";
        return name + ": " + (enabled ? "\u00a7aON" : "\u00a7cOFF");
    }

    private static GuiButton makeFeatureButton(int id, int w, int h,
                                                String name, boolean enabled, boolean ldogHandles) {
        GuiButton btn = new GuiButton(id, 0, 0, w, h, featureLabel(name, enabled, ldogHandles));
        if (!ldogHandles) {
            btn.enabled = false;
        }
        return btn;
    }

    static int cycleValue(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    static double cycleValue(double[] values, double current) {
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.01) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }
}
