package com.limitlessdev.ldog.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GuiLDOGSettings utility methods (value cycling, label formatting).
 * These are pure logic tests that don't require a Minecraft environment.
 */
class GuiLDOGSettingsTest {

    // ---- Value cycling tests ----

    @Test
    @DisplayName("cycleValue(int[]) cycles through array and wraps around")
    void cycleValueIntWrapsAround() {
        int[] values = {0, 32, 64, 128};
        assertEquals(32, GuiLDOGSettings.cycleValue(values, 0));
        assertEquals(64, GuiLDOGSettings.cycleValue(values, 32));
        assertEquals(128, GuiLDOGSettings.cycleValue(values, 64));
        assertEquals(0, GuiLDOGSettings.cycleValue(values, 128)); // wraps
    }

    @Test
    @DisplayName("cycleValue(int[]) returns first element for unknown value")
    void cycleValueIntUnknownReturnFirst() {
        int[] values = {0, 32, 64};
        assertEquals(0, GuiLDOGSettings.cycleValue(values, 999));
    }

    @Test
    @DisplayName("cycleValue(double[]) cycles through array and wraps around")
    void cycleValueDoubleWrapsAround() {
        double[] values = {0.0, 0.5, 1.0};
        assertEquals(0.5, GuiLDOGSettings.cycleValue(values, 0.0), 0.01);
        assertEquals(1.0, GuiLDOGSettings.cycleValue(values, 0.5), 0.01);
        assertEquals(0.0, GuiLDOGSettings.cycleValue(values, 1.0), 0.01); // wraps
    }

    @Test
    @DisplayName("cycleValue(double[]) handles floating point comparison tolerance")
    void cycleValueDoubleFloatingPointTolerance() {
        double[] values = {0.0, 0.4, 0.8};
        assertEquals(0.8, GuiLDOGSettings.cycleValue(values, 0.400000001), 0.01);
    }

    // ---- Label formatting tests ----

    @Test
    @DisplayName("toggleLabel shows ON/OFF correctly")
    void toggleLabelFormatting() {
        String onLabel = GuiLDOGSettings.toggleLabel("Test", true);
        String offLabel = GuiLDOGSettings.toggleLabel("Test", false);

        assertTrue(onLabel.contains("Test"));
        assertTrue(onLabel.contains("ON"));
        assertTrue(offLabel.contains("Test"));
        assertTrue(offLabel.contains("OFF"));
    }

    @Test
    @DisplayName("distLabel shows Vanilla for 0, number otherwise")
    void distLabelFormatting() {
        String vanillaLabel = GuiLDOGSettings.distLabel("Test", 0);
        String customLabel = GuiLDOGSettings.distLabel("Test", 64);

        assertTrue(vanillaLabel.contains("Vanilla"));
        assertTrue(customLabel.contains("64"));
    }

    @Test
    @DisplayName("afkTimeoutLabel formats seconds and minutes correctly")
    void afkTimeoutLabelFormatting() {
        String disabled = GuiLDOGSettings.afkTimeoutLabel(0);
        String seconds = GuiLDOGSettings.afkTimeoutLabel(30);
        String minutes = GuiLDOGSettings.afkTimeoutLabel(300);

        assertTrue(disabled.contains("Disabled"));
        assertTrue(seconds.contains("30s"));
        assertTrue(minutes.contains("5m"));
    }

    @Test
    @DisplayName("opacityLabel formats percentage correctly")
    void opacityLabelFormatting() {
        String label = GuiLDOGSettings.opacityLabel("Water", 0.4);
        assertTrue(label.contains("40%"));

        String fullLabel = GuiLDOGSettings.opacityLabel("Water", 1.0);
        assertTrue(fullLabel.contains("100%"));
    }

    @Test
    @DisplayName("featureLabel shows OptiFine when LDOG doesn't handle")
    void featureLabelOptiFineDeferred() {
        String optifineLabel = GuiLDOGSettings.featureLabel("CTM", true, false);
        assertTrue(optifineLabel.contains("OptiFine"));

        String onLabel = GuiLDOGSettings.featureLabel("CTM", true, true);
        assertTrue(onLabel.contains("ON"));

        String offLabel = GuiLDOGSettings.featureLabel("CTM", false, true);
        assertTrue(offLabel.contains("OFF"));
    }
}
