package com.limitlessdev.ldog.render.color;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Handles custom colors from resource packs following OptiFine's format.
 *
 * Registered as an IResourceManagerReloadListener in ClientProxy.init(),
 * which runs after vanilla's GrassColorReloadListener and
 * FoliageColorReloadListener, so custom colormaps successfully override
 * the vanilla ones.
 *
 * Supports:
 * - Custom grass.png / foliage.png colormaps (256x256 temperature/humidity maps)
 * - Static color overrides from optifine/color.properties
 * - Redstone wire color by power level
 * - Particle colors (water splash, portal, lava drip, etc.)
 */
public class CustomColorHandler implements IResourceManagerReloadListener {

    public static final CustomColorHandler INSTANCE = new CustomColorHandler();

    // Custom redstone wire colors by power level (0-15), null if not overridden
    private static int[] redstoneColors = null;

    // Static color overrides from color.properties
    private static final Map<String, Integer> staticColors = new HashMap<>();

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        if (!LDOGConfig.enableCustomColors) return;

        redstoneColors = null;
        staticColors.clear();

        loadCustomColormaps(resourceManager);
        loadColorProperties(resourceManager);
    }

    private void loadCustomColormaps(IResourceManager resourceManager) {
        // Try optifine/colormap/ then mcpatcher/colormap/ paths
        for (String basePath : new String[]{"optifine/colormap", "mcpatcher/colormap"}) {
            boolean loaded = false;

            // Custom grass colormap
            ResourceLocation grassLoc = new ResourceLocation("minecraft",
                basePath + "/grass.png");
            try {
                int[] grassPixels = TextureUtil.readImageData(resourceManager, grassLoc);
                ColorizerGrass.setGrassBiomeColorizer(grassPixels);
                LDOGMod.LOGGER.info("LDOG: Loaded custom grass colormap from {}", basePath);
                loaded = true;
            } catch (IOException ignored) {}

            // Custom foliage colormap
            ResourceLocation foliageLoc = new ResourceLocation("minecraft",
                basePath + "/foliage.png");
            try {
                int[] foliagePixels = TextureUtil.readImageData(resourceManager, foliageLoc);
                ColorizerFoliage.setFoliageBiomeColorizer(foliagePixels);
                LDOGMod.LOGGER.info("LDOG: Loaded custom foliage colormap from {}", basePath);
                loaded = true;
            } catch (IOException ignored) {}

            if (loaded) break; // Don't check mcpatcher if optifine path worked
        }
    }

    private void loadColorProperties(IResourceManager resourceManager) {
        for (String basePath : new String[]{"optifine", "mcpatcher"}) {
            ResourceLocation propsLoc = new ResourceLocation("minecraft",
                basePath + "/color.properties");
            try {
                var resource = resourceManager.getResource(propsLoc);
                Properties props = new Properties();
                props.load(resource.getInputStream());
                resource.close();

                // Parse redstone wire colors
                parseRedstoneColors(props);

                // Parse static color overrides
                for (Map.Entry<Object, Object> entry : props.entrySet()) {
                    String key = entry.getKey().toString().trim();
                    String value = entry.getValue().toString().trim();

                    if (key.startsWith("particle.") || key.startsWith("fog.") ||
                        key.startsWith("sky.") || key.startsWith("underwater.")) {
                        try {
                            staticColors.put(key, parseColor(value));
                        } catch (NumberFormatException ignored) {}
                    }
                }

                LDOGMod.LOGGER.info("LDOG: Loaded color.properties from {} ({} entries)",
                    basePath, props.size());
                break;
            } catch (IOException ignored) {}
        }
    }

    private void parseRedstoneColors(Properties props) {
        // OptiFine format: redstone.color.0=0x000000, redstone.color.15=0xFF0000
        // Or single-line: redstone.colors=hex0 hex1 ... hex15
        boolean hasAny = false;
        int[] colors = new int[16];

        // Default vanilla redstone colors (red gradient)
        for (int i = 0; i < 16; i++) {
            float power = (float) i / 15.0f;
            int r = (int) (power * 255.0f);
            colors[i] = (r << 16);
        }

        for (int i = 0; i < 16; i++) {
            String key = "redstone.color." + i;
            String value = props.getProperty(key);
            if (value != null) {
                try {
                    colors[i] = parseColor(value.trim());
                    hasAny = true;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (hasAny) {
            redstoneColors = colors;
            LDOGMod.LOGGER.info("LDOG: Loaded custom redstone wire colors");
        }
    }

    /**
     * Get custom redstone wire color for a power level (0-15).
     * Returns -1 if no custom color is set.
     */
    public static int getRedstoneColor(int power) {
        if (redstoneColors == null || power < 0 || power > 15) return -1;
        return redstoneColors[power];
    }

    /**
     * Get a static color override by key (e.g., "particle.water", "fog.nether").
     * Returns -1 if no override exists.
     */
    public static int getStaticColor(String key) {
        Integer color = staticColors.get(key);
        return color != null ? color : -1;
    }

    private static int parseColor(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return Integer.parseInt(value.substring(2), 16);
        }
        if (value.startsWith("#")) {
            return Integer.parseInt(value.substring(1), 16);
        }
        return Integer.parseInt(value, 16);
    }
}
