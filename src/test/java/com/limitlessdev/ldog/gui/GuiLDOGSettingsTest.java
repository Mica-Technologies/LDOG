package com.limitlessdev.ldog.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GuiLDOGSettings utility methods (value cycling, label formatting).
 * These are pure logic tests that don't require a Minecraft environment.
 */
class GuiLDOGSettingsTest {

    // ---- Value cycling tests ----

    @Test
    @DisplayName("cycleValue(int[]) cycles through array and wraps around")
    void cycleValueIntWrapsAround() throws Exception {
        Method cycleValue = GuiLDOGSettings.class.getDeclaredMethod("cycleValue", int[].class, int.class);
        cycleValue.setAccessible(true);

        int[] values = {0, 32, 64, 128};
        assertEquals(32, cycleValue.invoke(null, values, 0));
        assertEquals(64, cycleValue.invoke(null, values, 32));
        assertEquals(128, cycleValue.invoke(null, values, 64));
        assertEquals(0, cycleValue.invoke(null, values, 128)); // wraps
    }

    @Test
    @DisplayName("cycleValue(int[]) returns first element for unknown value")
    void cycleValueIntUnknownReturnFirst() throws Exception {
        Method cycleValue = GuiLDOGSettings.class.getDeclaredMethod("cycleValue", int[].class, int.class);
        cycleValue.setAccessible(true);

        int[] values = {0, 32, 64};
        assertEquals(0, cycleValue.invoke(null, values, 999));
    }

    @Test
    @DisplayName("cycleValue(double[]) cycles through array and wraps around")
    void cycleValueDoubleWrapsAround() throws Exception {
        Method cycleValue = GuiLDOGSettings.class.getDeclaredMethod("cycleValue", double[].class, double.class);
        cycleValue.setAccessible(true);

        double[] values = {0.0, 0.5, 1.0};
        assertEquals(0.5, (double) cycleValue.invoke(null, values, 0.0), 0.01);
        assertEquals(1.0, (double) cycleValue.invoke(null, values, 0.5), 0.01);
        assertEquals(0.0, (double) cycleValue.invoke(null, values, 1.0), 0.01); // wraps
    }

    @Test
    @DisplayName("cycleValue(double[]) handles floating point comparison tolerance")
    void cycleValueDoubleFloatingPointTolerance() throws Exception {
        Method cycleValue = GuiLDOGSettings.class.getDeclaredMethod("cycleValue", double[].class, double.class);
        cycleValue.setAccessible(true);

        double[] values = {0.0, 0.4, 0.8};
        // Value slightly off due to floating point should still match
        assertEquals(0.8, (double) cycleValue.invoke(null, values, 0.400000001), 0.01);
    }

    // ---- Label formatting tests ----

    @Test
    @DisplayName("toggleLabel shows ON/OFF correctly")
    void toggleLabelFormatting() throws Exception {
        Method toggleLabel = GuiLDOGSettings.class.getDeclaredMethod("toggleLabel", String.class, boolean.class);
        toggleLabel.setAccessible(true);

        String onLabel = (String) toggleLabel.invoke(null, "Test", true);
        String offLabel = (String) toggleLabel.invoke(null, "Test", false);

        assertTrue(onLabel.contains("Test"));
        assertTrue(onLabel.contains("ON"));
        assertTrue(offLabel.contains("Test"));
        assertTrue(offLabel.contains("OFF"));
    }

    @Test
    @DisplayName("distLabel shows Vanilla for 0, number otherwise")
    void distLabelFormatting() throws Exception {
        Method distLabel = GuiLDOGSettings.class.getDeclaredMethod("distLabel", String.class, int.class);
        distLabel.setAccessible(true);

        String vanillaLabel = (String) distLabel.invoke(null, "Test", 0);
        String customLabel = (String) distLabel.invoke(null, "Test", 64);

        assertTrue(vanillaLabel.contains("Vanilla"));
        assertTrue(customLabel.contains("64"));
    }

    @Test
    @DisplayName("afkTimeoutLabel formats seconds and minutes correctly")
    void afkTimeoutLabelFormatting() throws Exception {
        Method afkLabel = GuiLDOGSettings.class.getDeclaredMethod("afkTimeoutLabel", int.class);
        afkLabel.setAccessible(true);

        String disabled = (String) afkLabel.invoke(null, 0);
        String seconds = (String) afkLabel.invoke(null, 30);
        String minutes = (String) afkLabel.invoke(null, 300);

        assertTrue(disabled.contains("Disabled"));
        assertTrue(seconds.contains("30s"));
        assertTrue(minutes.contains("5m"));
    }

    @Test
    @DisplayName("opacityLabel formats percentage correctly")
    void opacityLabelFormatting() throws Exception {
        Method opacityLabel = GuiLDOGSettings.class.getDeclaredMethod("opacityLabel", String.class, double.class);
        opacityLabel.setAccessible(true);

        String label = (String) opacityLabel.invoke(null, "Water", 0.4);
        assertTrue(label.contains("40%"));

        String fullLabel = (String) opacityLabel.invoke(null, "Water", 1.0);
        assertTrue(fullLabel.contains("100%"));
    }

    @Test
    @DisplayName("featureLabel shows OptiFine when LDOG doesn't handle")
    void featureLabelOptiFineDeferred() throws Exception {
        Method featureLabel = GuiLDOGSettings.class.getDeclaredMethod("featureLabel", String.class, boolean.class, boolean.class);
        featureLabel.setAccessible(true);

        String optifineLabel = (String) featureLabel.invoke(null, "CTM", true, false);
        assertTrue(optifineLabel.contains("OptiFine"));

        String onLabel = (String) featureLabel.invoke(null, "CTM", true, true);
        assertTrue(onLabel.contains("ON"));

        String offLabel = (String) featureLabel.invoke(null, "CTM", false, true);
        assertTrue(offLabel.contains("OFF"));
    }
}
