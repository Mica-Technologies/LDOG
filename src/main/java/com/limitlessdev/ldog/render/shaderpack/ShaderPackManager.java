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
 * Shader pack discovery + activation.
 *
 * <p>Scans {@code <.minecraft>/shaderpacks/} (created on first use), lists
 * directory-style packs and {@code .zip} packs alongside LDOG's bundled
 * built-ins, lets the user activate one via the GUI, and owns the compiled
 * {@link ShaderPackRuntime} the rendering pipeline consumes.
 *
 * <p>Activation resolves the pack against the current dimension's
 * {@code worldN/} folder (OF/Iris layout); {@link #onDimensionChanged(int)}
 * re-runs it when the player changes dimension.
 *
 * <p>Downstream: {@link ShaderPackRuntime} compiles the gbuffer / deferred /
 * composite / final / shadow programs,
 * {@link ShaderPackGbufferManager} dispatches per-object draws and owns the
 * frame's uniform snapshot,
 * {@link com.limitlessdev.ldog.render.pipeline.passes.ShaderPackCompositePass}
 * runs the fullscreen chain, and {@link ShadowMapManager} renders the shadow map.
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
     * Activate the pack matching {@code name} for the dimension the client is
     * currently in. Pass {@code "(none)"} or null to deactivate.
     */
    public synchronized void activate(String name) {
        activate(name, currentWorldDir());
    }

    /**
     * Activate the pack matching {@code name}, resolving its per-dimension
     * {@code worldN/} folder against {@code worldDir}. Closes the
     * previously-active pack first.
     *
     * <p>The explicit {@code worldDir} exists for the dimension-change path:
     * {@code WorldEvent.Load} fires from the WorldClient constructor, while
     * {@code Minecraft.world} still points at the world being left — so the
     * caller passes the incoming dimension rather than letting us read a stale
     * one off the client.
     */
    public synchronized void activate(String name, String worldDir) {
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
            // Probe the dimension's worldN/ folder first (OF/Iris layout).
            created.worldDir = worldDir == null ? currentWorldDir() : worldDir;
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
            boolean configDirty = false;
            if (!LDOGConfig.enablePostProcessPipeline) {
                // The composite chain + deferred gbuffer path both run inside the
                // post-process pipeline; with it off, a pack compiles but renders
                // nothing. Enable it so activating a pack actually does something.
                LDOGConfig.enablePostProcessPipeline = true;
                configDirty = true;
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
                configDirty = true;
                LDOGMod.LOGGER.info("LDOG: Auto-enabled Pack Gbuffers for built-in pack '{}'", active.name);
            }
            // Those two flags were flipped in memory only. Without a sync the
            // change is lost on restart (and the config GUI keeps showing the
            // old value), so the pack silently stops rendering next session.
            if (configDirty) persistConfig();
        }
        // Clean state after the switch (also covers replacing one pack with another).
        onPackChanged();
    }

    /**
     * Write LDOGConfig's in-memory state back to the config file. Mirrors what
     * the settings GUI does on save.
     */
    private static void persistConfig() {
        try {
            net.minecraftforge.common.config.ConfigManager.sync(
                com.limitlessdev.ldog.Tags.MODID,
                net.minecraftforge.common.config.Config.Type.INSTANCE);
        } catch (Throwable t) {
            LDOGMod.LOGGER.warn("LDOG: Failed to persist shader config changes: {}", t.toString());
        }
    }

    /**
     * Re-resolve the active pack against a newly-entered dimension. The
     * {@code worldN/} folder a pack is loaded from is fixed at activation time,
     * so without this an Overworld→Nether trip keeps running {@code world0}'s
     * programs (and a pack activated from the main menu is stuck on the
     * {@code world0} default forever).
     *
     * <p>No-op when nothing is active or the folder wouldn't change.
     */
    public synchronized void onDimensionChanged(int dimension) {
        if (!LDOGConfig.enableShaders || active == null) return;
        String want = "world" + dimension;
        if (want.equals(active.worldDir)) return;
        String name = active.name;
        LDOGMod.LOGGER.info("LDOG: Dimension folder {} -> {}; reloading shader pack '{}'",
            active.worldDir, want, name);
        activate(name, want);
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
