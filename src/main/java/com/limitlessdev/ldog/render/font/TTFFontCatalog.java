package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.LDOGMod;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maintains the list of font families offered to the TTF rasterizer.
 *
 * <p>A small built-in list ships with LDOG (logical names that AWT always
 * resolves, plus common OS fonts). On top of that, any {@code .ttf} or
 * {@code .otf} file dropped into {@code config/ldog/fonts/} is loaded at
 * startup via {@link Font#createFont(int, File)} and registered with the
 * system-wide {@link GraphicsEnvironment}, so MC-side code can then
 * reference the custom font by its family name exactly like a built-in.
 *
 * <p>The directory is created on first init if missing. A rescan() hook
 * lets the user add or remove files while the game is running — a resource
 * reload (F3+T or opening the LDOG settings GUI) will pick up the changes.
 */
public final class TTFFontCatalog {

    private TTFFontCatalog() {}

    /**
     * Built-in family list always offered by the GUI. Logical names
     * (SansSerif/Serif/Monospaced) are guaranteed resolvable on any JVM;
     * the rest are common OS fonts that will fall back silently to the
     * system default when absent on a particular machine.
     */
    private static final String[] BUILT_IN_FAMILIES = {
        "SansSerif", "Serif", "Monospaced",
        "Arial", "Verdana", "Tahoma", "Segoe UI", "Helvetica",
        "Consolas", "Courier New"
    };

    private static File fontsDir;
    /** Insertion-order set so the GUI cycle is stable across rescans. */
    private static final Set<String> customFamilies = new LinkedHashSet<>();
    /** Tracks which files we've already registered so rescan doesn't double-load. */
    private static final Set<String> registeredFilePaths = new LinkedHashSet<>();

    /**
     * One-time init called from the client proxy. Takes the mod-config
     * directory from {@code FMLPreInitializationEvent.getModConfigurationDirectory()}.
     */
    public static synchronized void init(File modConfigDir) {
        fontsDir = new File(modConfigDir, "ldog/fonts");
        if (!fontsDir.exists()) {
            if (fontsDir.mkdirs()) {
                LDOGMod.LOGGER.info("LDOG: Created custom-font directory at {}", fontsDir);
            }
        }
        rescan();
    }

    /**
     * Re-scans the fonts directory and registers any new files. Called from
     * {@link SmoothFontHandler}'s reload listener so users can drop in a TTF
     * while the game is running and pick it up with F3+T.
     */
    public static synchronized void rescan() {
        if (fontsDir == null || !fontsDir.isDirectory()) return;

        File[] files = fontsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".ttf") || lower.endsWith(".otf");
        });
        if (files == null || files.length == 0) return;

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        int newlyLoaded = 0;
        for (File f : files) {
            String key = f.getAbsolutePath();
            if (registeredFilePaths.contains(key)) continue;
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, f);
                if (ge.registerFont(font)) {
                    String family = font.getFamily();
                    customFamilies.add(family);
                    registeredFilePaths.add(key);
                    newlyLoaded++;
                    LDOGMod.LOGGER.info("LDOG: Registered custom font family '{}' from {}",
                        family, f.getName());
                } else {
                    LDOGMod.LOGGER.warn("LDOG: registerFont() rejected {} (name collision with a system font?)",
                        f.getName());
                }
            } catch (FontFormatException e) {
                LDOGMod.LOGGER.warn("LDOG: Font {} is not a valid TTF/OTF: {}", f.getName(), e.getMessage());
            } catch (IOException e) {
                LDOGMod.LOGGER.warn("LDOG: Failed to read font {}: {}", f.getName(), e.getMessage());
            } catch (Throwable t) {
                LDOGMod.LOGGER.warn("LDOG: Unexpected error loading font {}: {}", f.getName(), t.toString());
            }
        }
        if (newlyLoaded > 0) {
            LDOGMod.LOGGER.info("LDOG: Custom font scan picked up {} new font(s)", newlyLoaded);
        }
    }

    /**
     * Returns every family the GUI should offer, built-ins followed by
     * user-supplied customs. Order is stable across rescans.
     */
    public static synchronized String[] getAllFamilies() {
        List<String> all = new ArrayList<>(BUILT_IN_FAMILIES.length + customFamilies.size());
        Collections.addAll(all, BUILT_IN_FAMILIES);
        all.addAll(customFamilies);
        return all.toArray(new String[0]);
    }

    public static synchronized File getFontsDir() {
        return fontsDir;
    }

    /** True if the given family name came from a user-dropped TTF/OTF file. */
    public static synchronized boolean isCustomFamily(String family) {
        return customFamilies.contains(family);
    }
}
