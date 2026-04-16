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
    private static final int BTN_ENTITY_LOD = 14;
    private static final int BTN_FPS_REDUCER = 20;
    private static final int BTN_UNFOCUSED_FPS = 21;
    private static final int BTN_AFK_TIMEOUT = 22;
    private static final int BTN_AFK_FPS = 23;
    private static final int BTN_CLEAR_WATER = 30;
    private static final int BTN_WATER_OPACITY = 31;
    private static final int BTN_WATER_TINT = 32;
    private static final int BTN_WATER_RED = 33;
    private static final int BTN_WATER_GREEN = 34;
    private static final int BTN_WATER_BLUE = 35;
    private static final int BTN_WATER_PRESET = 36;

    // Water presets: {name, opacity, red, green, blue}
    private static final Object[][] WATER_PRESETS = {
        {"Clear",          0.2, 0.8, 0.9, 1.0},
        {"Tropical",       0.3, 0.4, 0.9, 1.0},
        {"Default",        0.4, 0.7, 0.85, 0.85},
        {"Arctic",         0.3, 0.7, 0.8, 1.0},
        {"Murky",          1.2, 0.7, 0.65, 0.5},
        {"Swamp",          2.0, 0.4, 0.6, 0.3},
        {"Muddy",          5.0, 0.8, 0.5, 0.3},
        {"Vanilla",        1.0, 1.0, 1.0, 1.0},
    };
    private int currentPresetIndex = -1; // -1 = custom (no preset matched)
    private boolean waterSettingsChanged = false; // triggers chunk rebuild on close
    private static final int BTN_CTM = 40;
    private static final int BTN_EMISSIVE = 41;
    private static final int BTN_DYNAMIC_LIGHTS = 42;
    private static final int BTN_DYN_LIGHT_INTERVAL = 46;
    private static final int BTN_CUSTOM_SKY = 43;
    private static final int BTN_HD_TEXTURES = 44;
    private static final int BTN_SHADERS = 45;
    private static final int BTN_LIGHT_TEMP = 50;
    private static final int BTN_LIGHT_TEMP_PRESET = 51;

    private static final int[] ENTITY_DIST_VALUES = {0, 32, 48, 64, 96, 128, 192, 256, 512};
    private static final int[] TE_DIST_VALUES = {0, 16, 32, 48, 64, 96, 128, 256};
    private static final int[] UNFOCUSED_FPS_VALUES = {1, 2, 5, 10, 15, 30, 60};
    private static final int[] AFK_TIMEOUT_VALUES = {0, 60, 120, 300, 600, 900, 1800, 3600};
    private static final int[] AFK_FPS_VALUES = {1, 2, 5, 10, 15, 30, 60};
    private static final double[] WATER_OPACITY_VALUES = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.2, 1.5, 2.0, 3.0, 5.0, 7.0, 10.0};
    private static final double[] TINT_VALUES = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0};
    private static final int[] DYN_LIGHT_INTERVAL_VALUES = {0, 1, 2, 5, 10, 20};
    private static final String[] LIGHT_TEMP_PRESETS = {
        "neutral", "warm_torches", "cinematic", "candlelight", "moonlit",
        "dark_nights", "horror", "bright_caves", "vivid",
        "fluorescent", "purple_haze", "neon_blue", "red_alert"
    };

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
        settingsList.addButtonRow(
            new GuiButton(BTN_ENTITY_LOD, 0, 0, w, h,
                toggleLabel("Entity LOD", LDOGConfig.enableEntityLOD)),
            null);

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
        currentPresetIndex = detectCurrentPreset();
        settingsList.addHeaderRow("Visual");
        settingsList.addButtonRow(
            new GuiButton(BTN_CLEAR_WATER, 0, 0, w, h,
                toggleLabel("Clear Water", LDOGConfig.enableClearWater)),
            new GuiButton(BTN_WATER_PRESET, 0, 0, w, h,
                presetLabel()));
        settingsList.addButtonRow(
            new GuiButton(BTN_WATER_OPACITY, 0, 0, w, h,
                opacityLabel("Water Opacity", LDOGConfig.waterOpacity)),
            new GuiButton(BTN_WATER_TINT, 0, 0, w, h,
                toggleLabel("Water Tint", LDOGConfig.enableWaterTint)));
        settingsList.addButtonRow(
            new GuiButton(BTN_WATER_RED, 0, 0, w, h,
                tintLabel("Red", LDOGConfig.waterTintRed, "\u00a7c")),
            new GuiButton(BTN_WATER_GREEN, 0, 0, w, h,
                tintLabel("Green", LDOGConfig.waterTintGreen, "\u00a7a")));
        settingsList.addButtonRow(
            new GuiButton(BTN_WATER_BLUE, 0, 0, w, h,
                tintLabel("Blue", LDOGConfig.waterTintBlue, "\u00a79")),
            null);

        // -- Lighting --
        settingsList.addHeaderRow("Lighting");
        settingsList.addButtonRow(
            makeFeatureButton(BTN_DYNAMIC_LIGHTS, w, h, "Dynamic Lights",
                LDOGConfig.enableDynamicLights, OptiFineCompat.shouldHandleDynamicLights()),
            new GuiButton(BTN_DYN_LIGHT_INTERVAL, 0, 0, w, h,
                dynLightIntervalLabel(LDOGConfig.dynamicLightsUpdateInterval)));
        settingsList.addButtonRow(
            new GuiButton(BTN_LIGHT_TEMP, 0, 0, w, h,
                toggleLabel("Light Temperature", LDOGConfig.enableLightTemperature)),
            new GuiButton(BTN_LIGHT_TEMP_PRESET, 0, 0, w, h,
                lightTempPresetLabel()));

        // -- Features --
        String featureNote = OptiFineCompat.isOptiFineLoaded()
            ? "Features (OptiFine handles these)"
            : "Features";
        settingsList.addHeaderRow(featureNote);
        settingsList.addButtonRow(
            makeFeatureButton(BTN_CTM, w, h, "Connected Textures",
                LDOGConfig.enableConnectedTextures, OptiFineCompat.shouldHandleCTM()),
            makeFeatureButton(BTN_EMISSIVE, w, h, "Emissive Textures",
                LDOGConfig.enableEmissiveTextures, OptiFineCompat.shouldHandleEmissive()));
        settingsList.addButtonRow(
            makeFeatureButton(BTN_CUSTOM_SKY, w, h, "Custom Sky",
                LDOGConfig.enableCustomSky, OptiFineCompat.shouldHandleCustomSky()),
            null);
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
            case BTN_ENTITY_LOD:
                LDOGConfig.enableEntityLOD = !LDOGConfig.enableEntityLOD;
                button.displayString = toggleLabel("Entity LOD", LDOGConfig.enableEntityLOD);
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
                waterSettingsChanged = true;
                break;
            case BTN_WATER_OPACITY:
                LDOGConfig.waterOpacity = cycleValue(WATER_OPACITY_VALUES, LDOGConfig.waterOpacity);
                button.displayString = opacityLabel("Water Opacity", LDOGConfig.waterOpacity);
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_TINT:
                LDOGConfig.enableWaterTint = !LDOGConfig.enableWaterTint;
                button.displayString = toggleLabel("Water Tint", LDOGConfig.enableWaterTint);
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_RED:
                LDOGConfig.waterTintRed = cycleValue(TINT_VALUES, LDOGConfig.waterTintRed);
                button.displayString = tintLabel("Red", LDOGConfig.waterTintRed, "\u00a7c");
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_GREEN:
                LDOGConfig.waterTintGreen = cycleValue(TINT_VALUES, LDOGConfig.waterTintGreen);
                button.displayString = tintLabel("Green", LDOGConfig.waterTintGreen, "\u00a7a");
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_BLUE:
                LDOGConfig.waterTintBlue = cycleValue(TINT_VALUES, LDOGConfig.waterTintBlue);
                button.displayString = tintLabel("Blue", LDOGConfig.waterTintBlue, "\u00a79");
                currentPresetIndex = -1;
                waterSettingsChanged = true;
                break;
            case BTN_WATER_PRESET:
                applyNextPreset();
                refreshWaterButtons();
                waterSettingsChanged = true;
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
            case BTN_DYN_LIGHT_INTERVAL:
                LDOGConfig.dynamicLightsUpdateInterval = cycleValue(DYN_LIGHT_INTERVAL_VALUES, LDOGConfig.dynamicLightsUpdateInterval);
                button.displayString = dynLightIntervalLabel(LDOGConfig.dynamicLightsUpdateInterval);
                break;
            case BTN_LIGHT_TEMP:
                LDOGConfig.enableLightTemperature = !LDOGConfig.enableLightTemperature;
                button.displayString = toggleLabel("Light Temperature", LDOGConfig.enableLightTemperature);
                break;
            case BTN_LIGHT_TEMP_PRESET:
                cycleLightTempPreset();
                button.displayString = lightTempPresetLabel();
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
        if (waterSettingsChanged && this.mc.renderGlobal != null) {
            // Water opacity/tint is baked into chunk vertex data — must rebuild.
            this.mc.renderGlobal.loadRenderers();
        }
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

    static String tintLabel(String channel, double value, String colorCode) {
        return channel + ": " + colorCode + String.format("%.1f", value);
    }

    static String dynLightIntervalLabel(int value) {
        if (value == 0) return "Speed: \u00a7aSmooth (per frame)";
        if (value == 1) return "Speed: \u00a7aFast (every tick)";
        return "Speed: \u00a7e" + value + " ticks";
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

    // ---- Water preset helpers ----

    private String presetLabel() {
        if (currentPresetIndex < 0) return "Preset: \u00a77Custom";
        return "Preset: \u00a7a" + WATER_PRESETS[currentPresetIndex][0];
    }

    private int detectCurrentPreset() {
        for (int i = 0; i < WATER_PRESETS.length; i++) {
            double opacity = (Double) WATER_PRESETS[i][1];
            double red     = (Double) WATER_PRESETS[i][2];
            double green   = (Double) WATER_PRESETS[i][3];
            double blue    = (Double) WATER_PRESETS[i][4];
            if (Math.abs(LDOGConfig.waterOpacity   - opacity) < 0.01 &&
                Math.abs(LDOGConfig.waterTintRed   - red)    < 0.01 &&
                Math.abs(LDOGConfig.waterTintGreen - green)  < 0.01 &&
                Math.abs(LDOGConfig.waterTintBlue  - blue)   < 0.01) {
                return i;
            }
        }
        return -1;
    }

    private void applyNextPreset() {
        currentPresetIndex = (currentPresetIndex + 1) % WATER_PRESETS.length;
        Object[] preset = WATER_PRESETS[currentPresetIndex];
        LDOGConfig.waterOpacity   = (Double) preset[1];
        LDOGConfig.waterTintRed   = (Double) preset[2];
        LDOGConfig.waterTintGreen = (Double) preset[3];
        LDOGConfig.waterTintBlue  = (Double) preset[4];
        // All presets except Vanilla enable clear water + tint
        boolean isVanilla = "Vanilla".equals(preset[0]);
        LDOGConfig.enableClearWater = !isVanilla;
        LDOGConfig.enableWaterTint  = !isVanilla;
    }

    private void refreshWaterButtons() {
        for (int i = 0; i < settingsList.getSize(); i++) {
            net.minecraft.client.gui.GuiListExtended.IGuiListEntry entry = settingsList.getListEntry(i);
            if (!(entry instanceof GuiLDOGSettingsList.ButtonRowEntry)) continue;
            GuiLDOGSettingsList.ButtonRowEntry row = (GuiLDOGSettingsList.ButtonRowEntry) entry;
            refreshButton(row.getLeftButton());
            refreshButton(row.getRightButton());
        }
    }

    // ---- Light temperature preset helpers ----

    private String lightTempPresetLabel() {
        String preset = LDOGConfig.lightTemperaturePreset;
        String display = preset.substring(0, 1).toUpperCase() + preset.substring(1).replace('_', ' ');
        return "Preset: \u00a7a" + display;
    }

    private void cycleLightTempPreset() {
        String current = LDOGConfig.lightTemperaturePreset;
        int idx = 0;
        for (int i = 0; i < LIGHT_TEMP_PRESETS.length; i++) {
            if (LIGHT_TEMP_PRESETS[i].equalsIgnoreCase(current)) {
                idx = (i + 1) % LIGHT_TEMP_PRESETS.length;
                break;
            }
        }
        LDOGConfig.lightTemperaturePreset = LIGHT_TEMP_PRESETS[idx];
    }

    private void refreshButton(GuiButton btn) {
        if (btn == null) return;
        switch (btn.id) {
            case BTN_CLEAR_WATER:
                btn.displayString = toggleLabel("Clear Water", LDOGConfig.enableClearWater); break;
            case BTN_WATER_PRESET:
                btn.displayString = presetLabel(); break;
            case BTN_WATER_OPACITY:
                btn.displayString = opacityLabel("Water Opacity", LDOGConfig.waterOpacity); break;
            case BTN_WATER_TINT:
                btn.displayString = toggleLabel("Water Tint", LDOGConfig.enableWaterTint); break;
            case BTN_WATER_RED:
                btn.displayString = tintLabel("Red", LDOGConfig.waterTintRed, "\u00a7c"); break;
            case BTN_WATER_GREEN:
                btn.displayString = tintLabel("Green", LDOGConfig.waterTintGreen, "\u00a7a"); break;
            case BTN_WATER_BLUE:
                btn.displayString = tintLabel("Blue", LDOGConfig.waterTintBlue, "\u00a79"); break;
        }
    }
}
