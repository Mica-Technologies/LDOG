package com.limitlessdev.ldog.asm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BorderlessFullscreenConfig#isEnabled(Path)} — the hand-rolled
 * .cfg read that runs in the FML core-plugin phase, before Forge's ConfigManager
 * exists. Its answer decides whether the LWJGL Display is created undecorated,
 * so a false positive costs the user a window border they didn't ask for and a
 * parse crash costs them the whole launch.
 */
class BorderlessFullscreenConfigTest {

    @TempDir
    Path mcDir;

    /** Write {@code <mcDir>/config/ldog.cfg} with the given content. */
    private void writeConfig(String content) throws IOException {
        Path configDir = Files.createDirectories(mcDir.resolve("config"));
        Files.write(configDir.resolve("ldog.cfg"), content.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Missing / unreadable file ----

    @Test
    @DisplayName("Missing config directory returns false (first launch)")
    void missingDirectory() {
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Config directory present but ldog.cfg missing returns false")
    void missingFile() throws IOException {
        Files.createDirectories(mcDir.resolve("config"));
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("A directory where ldog.cfg should be returns false, not a crash")
    void configPathIsDirectory() throws IOException {
        Files.createDirectories(mcDir.resolve("config").resolve("ldog.cfg"));
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Empty file returns false")
    void emptyFile() throws IOException {
        writeConfig("");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    // ---- Value parsing ----

    @Test
    @DisplayName("B:borderlessFullscreen=true is read as enabled")
    void trueIsEnabled() throws IOException {
        writeConfig("B:borderlessFullscreen=true\n");
        assertTrue(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("B:borderlessFullscreen=false is read as disabled")
    void falseIsDisabled() throws IOException {
        writeConfig("B:borderlessFullscreen=false\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Value matching is case-insensitive (Forge writes lowercase, users may not)")
    void valueIsCaseInsensitive() throws IOException {
        writeConfig("B:borderlessFullscreen=TRUE\n");
        assertTrue(BorderlessFullscreenConfig.isEnabled(mcDir));

        writeConfig("b:BORDERLESSFULLSCREEN=True\n");
        assertTrue(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Whitespace around the key, '=' and value is tolerated")
    void whitespaceTolerated() throws IOException {
        writeConfig("   B:borderlessFullscreen  =  true   \n");
        assertTrue(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    // ---- Category indentation: how Forge actually writes the file ----

    @Test
    @DisplayName("Reads the key nested inside a Forge config category block")
    void insideCategoryBlock() throws IOException {
        writeConfig(
            "# Configuration file\n"
                + "\n"
                + "display {\n"
                + "    # Run the game in borderless windowed fullscreen.\n"
                + "    # Requires a game restart.\n"
                + "    B:borderlessFullscreen=true\n"
                + "}\n");
        assertTrue(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Tab-indented category bodies parse the same as space-indented")
    void tabIndented() throws IOException {
        writeConfig("display {\n\t\tB:borderlessFullscreen=true\n}\n");
        assertTrue(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("A disabled key nested in a category is still read as disabled")
    void disabledInsideCategoryBlock() throws IOException {
        writeConfig("display {\n    B:borderlessFullscreen=false\n}\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    // ---- Malformed / near-miss lines ----

    @Test
    @DisplayName("Commented-out key is ignored")
    void commentedKeyIgnored() throws IOException {
        writeConfig("# B:borderlessFullscreen=true\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Wrong type prefix is ignored")
    void wrongTypePrefixIgnored() throws IOException {
        writeConfig("S:borderlessFullscreen=true\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Non-boolean value is ignored rather than parsed as true")
    void nonBooleanValueIgnored() throws IOException {
        writeConfig("B:borderlessFullscreen=yes\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));

        writeConfig("B:borderlessFullscreen=1\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Trailing junk after the value is ignored")
    void trailingJunkIgnored() throws IOException {
        writeConfig("B:borderlessFullscreen=true # inline comment\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("A key that merely contains the name does not match")
    void similarKeyDoesNotMatch() throws IOException {
        writeConfig("B:borderlessFullscreenOnStartup=true\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Missing value is ignored")
    void missingValueIgnored() throws IOException {
        writeConfig("B:borderlessFullscreen=\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    // ---- Scanning behaviour across a realistic file ----

    @Test
    @DisplayName("Finds the key among many unrelated lines")
    void findsKeyInRealisticFile() throws IOException {
        writeConfig(
            "# Configuration file\n"
                + "\n"
                + "general {\n"
                + "    I:entityRenderDistance=64\n"
                + "    B:enableClearWater=true\n"
                + "    D:waterOpacity=0.4\n"
                + "}\n"
                + "\n"
                + "display {\n"
                + "    B:borderlessFullscreen=true\n"
                + "    B:enableFXAA=false\n"
                + "}\n");
        assertTrue(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("First matching line wins when the key is duplicated")
    void firstMatchWins() throws IOException {
        writeConfig("B:borderlessFullscreen=false\nB:borderlessFullscreen=true\n");
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }

    @Test
    @DisplayName("Binary garbage does not throw — startup must never crash here")
    void binaryGarbageIsSafe() throws IOException {
        Path configDir = Files.createDirectories(mcDir.resolve("config"));
        byte[] garbage = new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0xC3,
            (byte) 0x28, (byte) 0xA0, (byte) 0xA1, (byte) 0x0A};
        Files.write(configDir.resolve("ldog.cfg"), garbage);
        assertFalse(BorderlessFullscreenConfig.isEnabled(mcDir));
    }
}
