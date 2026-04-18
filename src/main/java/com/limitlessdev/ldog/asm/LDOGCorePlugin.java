package com.limitlessdev.ldog.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LDOG's FML core plugin. Its sole purpose is to register mixin configs that
 * must apply <em>before</em> vanilla classes are resolved by the classloader.
 *
 * <p>The {@link com.limitlessdev.ldog.LDOGMixinLoader} late loader is fine for
 * classes that are only touched once the player opens a GUI or enters a world
 * (GuiScreen subclasses, RenderChunk, etc.). But some vanilla classes get
 * pulled into the classloader during Forge/FML bootstrap — {@code FontRenderer}
 * is one of them — and by the time the late loader registers its configs the
 * class has already been defined, so mixin transforms silently fail with a
 * "loaded too early" error.
 *
 * <p>This plugin routes around that by implementing MixinBooter's
 * {@link IEarlyMixinLoader}, which injects its configs into the Mixin library
 * while the FML core-plugin phase is still running. The buildscript auto-adds
 * {@code -Dfml.coreMods.load=com.limitlessdev.ldog.asm.LDOGCorePlugin} in dev
 * and writes {@code FMLCorePlugin} into the jar manifest for production, so
 * the same class is wired up in both environments.
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("LDOG Core")
@IFMLLoadingPlugin.SortingIndex(1001)
public class LDOGCorePlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.ldog.vanilla.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // Phase 10: flip LWJGL's undecorated system property BEFORE Minecraft
        // creates its Display. This is the only place the flag can be set in
        // time — ConfigManager doesn't exist yet, so we read the .cfg file
        // directly. Toggling the config requires a game restart for this
        // reason; the GUI tooltip spells that out.
        Object mcLocation = data.get("mcLocation");
        if (mcLocation instanceof File) {
            File mcDir = (File) mcLocation;
            if (BorderlessFullscreenConfig.isEnabled(mcDir.toPath())) {
                System.setProperty("org.lwjgl.opengl.Window.undecorated", "true");
                // Logger isn't initialized yet in the core-plugin phase.
                System.out.println("[LDOG] Borderless windowed fullscreen enabled — Display will be created undecorated");
            }
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
