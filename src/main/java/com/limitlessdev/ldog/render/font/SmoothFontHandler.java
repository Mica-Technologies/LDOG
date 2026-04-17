package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.compat.OptiFineCompat;
import com.limitlessdev.ldog.config.LDOGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

/**
 * LDOG's Smooth Font replacement (Phase C3, easy path: HD texture swap).
 *
 * <p>On each resource reload, probes the active resource pack for an HD ASCII
 * font PNG in the OptiFine or MCPatcher paths; if found, registers it with
 * {@link TextureManager} under a stable LDOG location and remembers the ID so
 * the FontRenderer mixin can substitute it at bind time. Also parses an
 * {@code ascii.properties} width table if one is present, which MixinFontRenderer
 * layers on top of vanilla's auto-computed widths.
 *
 * <p>Path priority (first match wins):
 * <ol>
 *   <li>{@code assets/minecraft/optifine/font/ascii.png}</li>
 *   <li>{@code assets/minecraft/mcpatcher/font/ascii.png}</li>
 * </ol>
 * Vanilla's own {@code textures/font/ascii.png} always stays loaded; if no HD
 * variant is present, FontRenderer uses it unchanged.
 *
 * <p>Properties lookup (first match wins):
 * <ol>
 *   <li>{@code assets/minecraft/optifine/font/ascii.properties}</li>
 *   <li>{@code assets/minecraft/mcpatcher/font/ascii.properties}</li>
 *   <li>{@code assets/minecraft/font/ascii.properties}</li>
 * </ol>
 */
public final class SmoothFontHandler implements IResourceManagerReloadListener {

    public static final SmoothFontHandler INSTANCE = new SmoothFontHandler();

    /** Stable ResourceLocation under which the HD texture is registered. */
    private static final ResourceLocation HD_FONT_LOCATION =
        new ResourceLocation("ldog", "textures/font/hd_ascii");

    private static final ResourceLocation[] HD_FONT_CANDIDATES = {
        new ResourceLocation("minecraft", "optifine/font/ascii.png"),
        new ResourceLocation("minecraft", "mcpatcher/font/ascii.png"),
    };

    private static final ResourceLocation[] WIDTH_PROPERTY_CANDIDATES = {
        new ResourceLocation("minecraft", "optifine/font/ascii.properties"),
        new ResourceLocation("minecraft", "mcpatcher/font/ascii.properties"),
        new ResourceLocation("minecraft", "font/ascii.properties"),
    };

    private boolean hdFontAvailable = false;
    private HDFontTexture hdFontTexture;
    /** True after the first bind-redirect hit is logged; avoids log spam. */
    private boolean loggedFirstBind = false;

    /** 256-entry width override table; -1 means "no override for this code point". */
    private final int[] widthOverrides = new int[256];
    private boolean hasAnyWidthOverrides = false;

    private SmoothFontHandler() {
        Arrays.fill(widthOverrides, -1);
    }

    public boolean isActive() {
        return LDOGConfig.enableSmoothFont && OptiFineCompat.shouldHandleSmoothFont();
    }

    public boolean hasHDFont() {
        return hdFontAvailable && LDOGConfig.useHDFontTexture && isActive();
    }

    /**
     * The ResourceLocation to bind in place of {@code textures/font/ascii.png}
     * when HD mode is on and a variant was discovered.
     */
    public ResourceLocation getHDFontLocation() {
        return HD_FONT_LOCATION;
    }

    /**
     * Returns the overridden width for a code point, or {@code -1} if no override
     * applies (caller should keep vanilla's auto-computed width).
     */
    public int getWidthOverride(int codePoint) {
        if (!isActive() || !LDOGConfig.useFontPropertyWidths || !hasAnyWidthOverrides) return -1;
        if (codePoint < 0 || codePoint >= widthOverrides.length) return -1;
        return widthOverrides[codePoint];
    }

    /** Called on every resource reload (pack swap, F3+T, or settings save). */
    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        LDOGMod.LOGGER.info(
            "LDOG: SmoothFont reload (enableSmoothFont={}, useHDFontTexture={}, antialiasedFont={}, useFontPropertyWidths={}, optiFine={})",
            LDOGConfig.enableSmoothFont, LDOGConfig.useHDFontTexture,
            LDOGConfig.antialiasedFont, LDOGConfig.useFontPropertyWidths,
            !OptiFineCompat.shouldHandleSmoothFont());
        reloadHDFontTexture(resourceManager);
        reloadWidthOverrides(resourceManager);
        loggedFirstBind = false;
    }

    /**
     * Called once by the MixinFontRenderer redirect on its first hit after a reload.
     * Confirms in the log that the mixin wired in correctly and which path is live.
     */
    public void noteFirstBindIfNeeded() {
        if (loggedFirstBind) return;
        loggedFirstBind = true;
        if (hasHDFont()) {
            LDOGMod.LOGGER.info("LDOG: FontRenderer mixin live — binding HD font ({})", HD_FONT_LOCATION);
        } else {
            LDOGMod.LOGGER.info("LDOG: FontRenderer mixin live — HD font not active (hdFontAvailable={}, enabled={}, useHD={})",
                hdFontAvailable, isActive(), LDOGConfig.useHDFontTexture);
        }
    }

    private void reloadHDFontTexture(IResourceManager resourceManager) {
        hdFontAvailable = false;
        hdFontTexture = null;

        if (!isActive() || !LDOGConfig.useHDFontTexture) {
            LDOGMod.LOGGER.info("LDOG: HD font disabled (active={}, useHDFontTexture={})",
                isActive(), LDOGConfig.useHDFontTexture);
            return;
        }

        ResourceLocation hdSource = null;
        for (ResourceLocation candidate : HD_FONT_CANDIDATES) {
            boolean present = resourceExists(resourceManager, candidate);
            LDOGMod.LOGGER.info("LDOG: HD font probe {} -> {}", candidate, present ? "FOUND" : "absent");
            if (present && hdSource == null) {
                hdSource = candidate;
            }
        }

        if (hdSource == null) {
            LDOGMod.LOGGER.info("LDOG: No HD font texture in active resource pack — using vanilla");
            return;
        }

        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        if (tm == null) return;

        hdFontTexture = new HDFontTexture(hdSource);
        boolean ok = tm.loadTexture(HD_FONT_LOCATION, hdFontTexture);
        if (ok) {
            hdFontAvailable = true;
            LDOGMod.LOGGER.info("LDOG: HD font active ({} → {})", hdSource, HD_FONT_LOCATION);
        } else {
            LDOGMod.LOGGER.warn("LDOG: Failed to register HD font {}", hdSource);
        }
    }

    private void reloadWidthOverrides(IResourceManager resourceManager) {
        Arrays.fill(widthOverrides, -1);
        hasAnyWidthOverrides = false;

        if (!isActive() || !LDOGConfig.useFontPropertyWidths) return;

        for (ResourceLocation candidate : WIDTH_PROPERTY_CANDIDATES) {
            if (tryLoadProperties(resourceManager, candidate)) {
                LDOGMod.LOGGER.info("LDOG: Loaded font width overrides from {}", candidate);
                return;
            }
        }
        LDOGMod.LOGGER.debug("LDOG: No font width properties in resource pack");
    }

    private boolean tryLoadProperties(IResourceManager resourceManager, ResourceLocation loc) {
        IResource resource = null;
        try {
            resource = resourceManager.getResource(loc);
            Properties props = new Properties();
            props.load(resource.getInputStream());
            int count = 0;
            for (String name : props.stringPropertyNames()) {
                // Format: "width.N=W" where N is the code point (0-255) and W is the width.
                if (!name.startsWith("width.")) continue;
                try {
                    int codePoint = Integer.parseInt(name.substring(6).trim());
                    int width = Integer.parseInt(props.getProperty(name).trim());
                    if (codePoint >= 0 && codePoint < widthOverrides.length && width >= 0) {
                        widthOverrides[codePoint] = width;
                        count++;
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed entries; other keys in .properties are allowed.
                }
            }
            hasAnyWidthOverrides = count > 0;
            return hasAnyWidthOverrides;
        } catch (IOException e) {
            return false;
        } finally {
            IOUtils.closeQuietly((Closeable) resource);
        }
    }

    private static boolean resourceExists(IResourceManager rm, ResourceLocation loc) {
        try {
            IResource r = rm.getResource(loc);
            IOUtils.closeQuietly((Closeable) r);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Invoked from the GUI after an antialias toggle flip so the user doesn't
     * have to wait for a full resource reload to see the filter change.
     */
    public void refreshAntialiasFilter() {
        if (hdFontTexture != null) {
            hdFontTexture.refreshFilter();
        }
    }
}
