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
    private ShaderPackRuntime runtime;
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

    /**
     * Names suitable for cycling in the GUI: "(none)" first, then LDOG's
     * bundled built-in packs, then the packs discovered in {@code shaderpacks/}.
     */
    public synchronized List<String> getPackNames() {
        List<String> builtins = BuiltinShaderPacks.displayNames();
        List<String> out = new ArrayList<>(discoveredPackNames.size() + builtins.size() + 1);
        out.add("(none)");
        out.addAll(builtins);
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
     * The compiled runtime for the active pack, or null when no pack is
     * active. {@link com.limitlessdev.ldog.render.pipeline.passes.ShaderPackCompositePass}
     * reads this each frame to decide whether to run the composite chain.
     */
    public synchronized ShaderPackRuntime getRuntime() {
        return runtime;
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

        ShaderPack created;
        try {
            if (BuiltinShaderPacks.isBuiltin(name)) {
                String dir = BuiltinShaderPacks.resourceDir(name);
                if (dir == null) {
                    LDOGMod.LOGGER.warn("LDOG: Unknown built-in shader pack '{}'; deactivating", name);
                    deactivate();
                    return;
                }
                created = new BuiltinShaderPack(name, dir);
            } else {
                ensurePacksDir();
                if (packsDir == null) return;
                File target = new File(packsDir, name);
                if (!target.exists()) {
                    LDOGMod.LOGGER.warn("LDOG: Shader pack '{}' not found; deactivating", name);
                    deactivate();
                    return;
                }
                if (target.isDirectory()) {
                    created = new DirectoryShaderPack(name, new File(target, "shaders"));
                } else {
                    created = new ZipShaderPack(name, target, detectZipPrefix(target));
                }
            }
            // Probe the current dimension's worldN/ folder first (OF/Iris layout).
            created.worldDir = currentWorldDir();
            loadProperties(created);
        } catch (IOException e) {
            LDOGMod.LOGGER.error("LDOG: Failed to load shader pack '{}': {}", name, e.toString());
            return;
        }

        if (active != null) active.close();
        if (runtime != null) { runtime.dispose(); runtime = null; }
        active = created;

        if (!active.hasAnyProgram()) {
            LDOGMod.LOGGER.warn(
                "LDOG: Activated shader pack '{}' but found no programs LDOG can load (dim folder '{}'). "
                + "Modern GLSL 330+/Iris packs for MC 1.16+ can't run on 1.12.2's GL 2.1 — "
                + "use a 1.12.2-format pack or an [LDOG] built-in pack.",
                name, active.worldDir);
        } else {
            LDOGMod.LOGGER.info("LDOG: Activated shader pack '{}' (dim folder '{}')", name, active.worldDir);
        }
        // Compile the composite + final stages (and any gbuffer programs the
        // dispatcher can use).
        runtime = new ShaderPackRuntime(active);
        if (runtime.isEmpty()) {
            // Nothing compiled at all — release so the passes short-circuit.
            runtime = null;
        } else {
            if (!LDOGConfig.enablePostProcessPipeline) {
                // The composite chain + deferred gbuffer path both run inside the
                // post-process pipeline; with it off, a pack compiles but renders
                // nothing. Enable it so activating a pack actually does something.
                LDOGConfig.enablePostProcessPipeline = true;
                LDOGMod.LOGGER.info(
                    "LDOG: Auto-enabled the post-process pipeline (required for shader packs to render)");
            }
            // Built-in LDOG packs are authored to be correct with the gbuffer
            // path on, so enable it automatically — they "just work" on select
            // without the user toggling Pack Gbuffers. External packs keep the
            // user's explicit toggle (their gbuffer behaviour is less certain).
            if (BuiltinShaderPacks.isBuiltin(active.name) && runtime.hasGbuffers()
                    && !LDOGConfig.enableShaderGbuffers) {
                LDOGConfig.enableShaderGbuffers = true;
                LDOGMod.LOGGER.info("LDOG: Auto-enabled Pack Gbuffers for built-in pack '{}'", active.name);
            }
        }
        // Clean state after the switch (also covers replacing one pack with another).
        onPackChanged();
    }

    /** Per-dimension shaders subfolder for the current world (default world0). */
    private static String currentWorldDir() {
        try {
            if (Minecraft.getMinecraft().world != null) {
                return "world" + Minecraft.getMinecraft().world.provider.getDimension();
            }
        } catch (Throwable ignored) {
            // Fall through to the overworld default.
        }
        return "world0";
    }

    private void deactivate() {
        boolean had = active != null || runtime != null;
        if (runtime != null) {
            runtime.dispose();
            runtime = null;
        }
        if (active != null) {
            String name = active.name;
            active.close();
            active = null;
            LDOGMod.LOGGER.info("LDOG: Deactivated shader pack '{}'", name);
        }
        if (had) onPackChanged();
    }

    /**
     * Clean GL state after a pack switch so nothing stale carries across:
     * dispose the deferred G-buffer + shadow targets and force a clean
     * post-process pipeline re-init (the temporal upscaler's history + the
     * scene targets otherwise persist a black frame across the switch — see
     * the "blank screen after toggling packs" bug). Only when a GL context
     * exists (in-game) — on init/menu the resources aren't allocated yet and
     * the dispose paths are no-ops anyway.
     */
    private void onPackChanged() {
        if (Minecraft.getMinecraft().world == null) return;
        try {
            ShaderPackGbufferManager.dispose();
            ShadowMapManager.dispose();
            com.limitlessdev.ldog.render.pipeline.PostProcessPipeline.INSTANCE.reset();
        } catch (Throwable t) {
            LDOGMod.LOGGER.warn("LDOG: pipeline reset on pack switch failed: {}", t.toString());
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
