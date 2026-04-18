package com.limitlessdev.ldog.asm;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Early read of the borderless-fullscreen flag from the Forge-written LDOG
 * config file. Needed at core-plugin load time, which runs before
 * ConfigManager initializes — so this class parses the .cfg text directly
 * rather than going through Forge's config API.
 *
 * The flag must be known before the main Display is created, because LWJGL
 * 2.9.4's {@code org.lwjgl.opengl.Window.undecorated} system property is
 * only read at Display creation time. That's also why toggling this config
 * requires a game restart.
 */
public final class BorderlessFullscreenConfig {

    private static final Pattern PATTERN = Pattern.compile(
        "^\\s*B:borderlessFullscreen\\s*=\\s*(true|false)\\s*$",
        Pattern.CASE_INSENSITIVE);

    private BorderlessFullscreenConfig() {}

    /**
     * Returns the value of {@code borderlessFullscreen} in
     * {@code <mcDir>/config/ldog.cfg}, or {@code false} if the file or key
     * doesn't exist yet. First launch returns false — user toggles the
     * setting, Forge writes the config on save, and the next launch picks
     * it up.
     */
    public static boolean isEnabled(Path mcDir) {
        Path configFile = mcDir.resolve("config").resolve("ldog.cfg");
        if (!Files.isRegularFile(configFile)) return false;

        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = PATTERN.matcher(line);
                if (m.matches()) {
                    return Boolean.parseBoolean(m.group(1));
                }
            }
        } catch (IOException e) {
            // Best effort — if the file is mid-write or permission-denied,
            // fall back to disabled rather than crashing startup.
            System.err.println("[LDOG] Failed to read " + configFile + ": " + e.getMessage());
        }
        return false;
    }
}
