package com.limitlessdev.ldog.render.shaderpack;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Phase 8 stretch — shader pack discovery + activation glue.
 *
 * <p>Scans {@code <.minecraft>/shaderpacks/} (created on first use), lists
 * directory-style packs and {@code .zip} packs, lets the user activate one
 * via the GUI, and exposes the currently-active pack for the rendering
 * pipeline to consume. The actual gbuffer/composite/final compilation +
 * dispatch is the next layer ({@code ShaderPackPipeline}, not yet written)
 * — this scaffold ships the discovery + selection model so the user-facing
 * GUI can list packs and the pipeline can be wired incrementally.
 *
 * <h3>What's intentionally NOT in v1</h3>
 *
 * <ul>
 *   <li>No actual gbuffer hooking — vanilla rendering continues unmodified.</li>
 *   <li>No composite/final chain compilation — files are discoverable but
 *       not compiled to GL programs.</li>
 *   <li>No per-program uniform feed (cameraPosition, projectionMatrix,
 *       sunPosition, etc.). The full uniform table is documented at OF /
 *       Iris but implementing the feed is a separate pass.</li>
 *   <li>No shadow pass — needs a depth-only second world-render path.</li>
 * </ul>
 *
 * These are all known unknowns in the master plan §13 expansion ideas.
 * Building them out without a working scaffold first leads to dead code,
 * so v1 ships the scaffold and the test plan calls out shader-pack work
 * as separately track-able.
 */
public final class ShaderPackManager {

    public static final ShaderPackManager INSTANCE = new ShaderPackManager();

    private final List<String> discoveredPackNames = new ArrayList<>();
    private ShaderPack active;
    private File packsDir;
    private boolean dirEnsured;

    private ShaderPackManager() {}

    /**
     * Rescans the shaderpacks folder and updates the discovered list. Cheap
     * enough to call any time the user opens the shader-pack settings.
     */
    public synchronized void rescan() {
        ensurePacksDir();
        discoveredPackNames.clear();
        if (packsDir == null || !packsDir.isDirectory()) return;

        File[] entries = packsDir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (f.isDirectory()) {
                // Accept directories that contain a `shaders/` subdir.
                if (new File(f, "shaders").isDirectory()) {
                    discoveredPackNames.add(f.getName());
                }
            } else if (f.isFile() && f.getName().toLowerCase().endsWith(".zip")) {
                discoveredPackNames.add(f.getName());
            }
        }
        Collections.sort(discoveredPackNames);
        LDOGMod.LOGGER.info("LDOG: Shader pack scan found {} pack(s) in {}",
            discoveredPackNames.size(), packsDir.getAbsolutePath());
    }

    /** Names suitable for cycling in the GUI. Always includes "(none)" first. */
    public synchronized List<String> getPackNames() {
        List<String> out = new ArrayList<>(discoveredPackNames.size() + 1);
        out.add("(none)");
        out.addAll(discoveredPackNames);
        return out;
    }

    public synchronized String getActiveName() {
        return active == null ? "(none)" : active.name;
    }

    public synchronized ShaderPack getActive() {
        return active;
    }

    /**
     * Activate the pack matching {@code name}. Pass {@code "(none)"} or null
     * to deactivate. Closes the previously-active pack first.
     */
    public synchronized void activate(String name) {
        if (name == null || name.equals("(none)") || name.isEmpty()) {
            deactivate();
            return;
        }
        ensurePacksDir();
        if (packsDir == null) return;

        File target = new File(packsDir, name);
        if (!target.exists()) {
            LDOGMod.LOGGER.warn("LDOG: Shader pack '{}' not found; deactivating", name);
            deactivate();
            return;
        }

        ShaderPack created;
        try {
            if (target.isDirectory()) {
                File shadersDir = new File(target, "shaders");
                created = new DirectoryShaderPack(name, shadersDir);
            } else {
                String prefix = detectZipPrefix(target);
                created = new ZipShaderPack(name, target, prefix);
            }
            loadProperties(created);
        } catch (IOException e) {
            LDOGMod.LOGGER.error("LDOG: Failed to load shader pack '{}': {}", name, e.toString());
            return;
        }

        if (active != null) active.close();
        active = created;

        if (!active.hasAnyProgram()) {
            LDOGMod.LOGGER.warn(
                "LDOG: Activated shader pack '{}' but no standard gbuffer programs found — pack may be incompatible",
                name);
        } else {
            LDOGMod.LOGGER.info("LDOG: Activated shader pack '{}'", name);
        }
    }

    private void deactivate() {
        if (active != null) {
            String name = active.name;
            active.close();
            active = null;
            LDOGMod.LOGGER.info("LDOG: Deactivated shader pack '{}'", name);
        }
    }

    private void ensurePacksDir() {
        if (dirEnsured) return;
        dirEnsured = true;
        File mcDir = Minecraft.getMinecraft().gameDir;
        if (mcDir == null) return;
        packsDir = new File(mcDir, "shaderpacks");
        if (!packsDir.exists()) {
            if (packsDir.mkdirs()) {
                LDOGMod.LOGGER.info("LDOG: Created shaderpacks directory at {}",
                    packsDir.getAbsolutePath());
            }
        }
    }

    /**
     * Detect whether a zip's shaders root is at "shaders/" or nested under
     * a single top-level folder ("PackName/shaders/"). Returns the prefix
     * including trailing slash, or empty string when shaders/ is at root.
     */
    private static String detectZipPrefix(File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            // First-pass: is there an entry literally named "shaders/"?
            if (zip.getEntry("shaders/shaders.properties") != null
                || zip.getEntry("shaders/") != null) return "";

            // Second-pass: find a single top-level dir containing "shaders/".
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                int slash = n.indexOf('/');
                if (slash <= 0) continue;
                String topDir = n.substring(0, slash + 1);
                if (zip.getEntry(topDir + "shaders/shaders.properties") != null
                    || zip.getEntry(topDir + "shaders/") != null) {
                    return topDir;
                }
            }
        }
        return "";
    }

    private static void loadProperties(ShaderPack pack) throws IOException {
        try (InputStream in = pack.openShaderResource("shaders.properties")) {
            if (in != null) pack.properties.load(in);
        }
    }

    /**
     * Apply the user's configured shader pack name. Called on mod init and
     * any time the user changes the selection in the GUI.
     */
    public void applyConfigSelection() {
        if (!LDOGConfig.enableShaders) {
            deactivate();
            return;
        }
        rescan();
        activate(LDOGConfig.shaderPackName);
    }
}
