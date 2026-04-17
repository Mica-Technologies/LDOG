package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.LDOGMod;
import com.limitlessdev.ldog.compat.OptiFineCompat;
import com.limitlessdev.ldog.config.LDOGConfig;
import com.limitlessdev.ldog.mixin.FontRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
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
 * Coordinates LDOG's font replacement pipeline. Two sources of glyph pixels
 * are supported, in priority order:
 *
 * <ol>
 *   <li><b>TTF</b> — runtime AWT rasterization from a system TrueType font
 *       ({@link TTFFontRasterizer}). Pixel-perfect antialiased glyphs at the
 *       chosen cell size; this is what Smooth Font's default mode gives you.
 *       Takes priority when {@link LDOGConfig#useTTFFont} is on.</li>
 *   <li><b>HD</b> — pre-rasterized PNG from a resource pack, at
 *       {@code optifine/font/ascii.png} or {@code mcpatcher/font/ascii.png}
 *       ({@link HDFontTexture}). Used when TTF is off and
 *       {@link LDOGConfig#useHDFontTexture} is on.</li>
 * </ol>
 *
 * <p>Either source is uploaded under the same stable
 * {@code ldog:textures/font/hd_ascii} location, so {@link MixinFontRenderer}'s
 * redirect is unchanged regardless of which source is active.
 *
 * <p>Width overrides follow the active source:
 * <ul>
 *   <li>TTF: widths come straight from AWT {@code FontMetrics.charWidth(ch)},
 *       converted to MC's logical 8-per-cell scale and patched into
 *       {@code FontRenderer.charWidth[]} via the accessor.</li>
 *   <li>HD: widths come from a pack-provided {@code ascii.properties} file if
 *       present and {@link LDOGConfig#useFontPropertyWidths} is on.</li>
 * </ul>
 */
public final class SmoothFontHandler implements IResourceManagerReloadListener {

    public static final SmoothFontHandler INSTANCE = new SmoothFontHandler();

    /** Stable ResourceLocation under which whichever source is active lives. */
    private static final ResourceLocation ACTIVE_FONT_LOCATION =
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

    /** One of: "none", "ttf", "hd" — tracks which source populated the atlas. */
    private String activeSource = "none";
    private HDFontTexture hdFontTexture;
    private TTFFontTexture ttfFontTexture;
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

    /** True when any LDOG font source (TTF or HD) is loaded and should be bound. */
    public boolean hasCustomFont() {
        return !"none".equals(activeSource) && isActive();
    }

    public ResourceLocation getCustomFontLocation() {
        return ACTIVE_FONT_LOCATION;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        LDOGMod.LOGGER.info(
            "LDOG: SmoothFont reload (enable={}, ttf={}, hd={}, aa={}, packWidths={}, optiFine={})",
            LDOGConfig.enableSmoothFont, LDOGConfig.useTTFFont, LDOGConfig.useHDFontTexture,
            LDOGConfig.fontAntialiasing, LDOGConfig.useFontPropertyWidths,
            !OptiFineCompat.shouldHandleSmoothFont());
        // Pick up any user-dropped .ttf/.otf files that appeared since last reload
        // before we (maybe) try to rasterize from one.
        TTFFontCatalog.rescan();
        reloadActiveFont(resourceManager);
        reloadWidthOverrides(resourceManager);
        applyWidthOverridesToFontRenderer();
        loggedFirstBind = false;
    }

    /**
     * Selects the active font source. TTF wins if enabled; otherwise falls back
     * to the HD pack-PNG swap; otherwise leaves vanilla untouched.
     */
    private void reloadActiveFont(IResourceManager resourceManager) {
        activeSource = "none";
        hdFontTexture = null;
        ttfFontTexture = null;

        if (!isActive()) {
            LDOGMod.LOGGER.info("LDOG: Smooth font disabled (enable={}, optiFine={})",
                LDOGConfig.enableSmoothFont, !OptiFineCompat.shouldHandleSmoothFont());
            return;
        }

        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        if (tm == null) return;

        if (LDOGConfig.useTTFFont) {
            if (tryLoadTTFFont(tm)) return;
            LDOGMod.LOGGER.warn("LDOG: TTF font load failed; falling through to HD path");
        }

        if (LDOGConfig.useHDFontTexture) {
            tryLoadHDFont(tm, resourceManager);
        } else {
            LDOGMod.LOGGER.info("LDOG: HD font disabled (useHDFontTexture=false)");
        }
    }

    private boolean tryLoadTTFFont(TextureManager tm) {
        TTFFontRasterizer.Result result;
        try {
            result = TTFFontRasterizer.rasterize(
                LDOGConfig.ttfFontFamily,
                LDOGConfig.ttfBold, LDOGConfig.ttfItalic,
                LDOGConfig.ttfFontSize, LDOGConfig.ttfCellSize);
        } catch (Throwable t) {
            LDOGMod.LOGGER.warn("LDOG: TTF rasterization failed: {}", t.toString());
            return false;
        }
        // Evict any stale HD registration so the location points at the TTF atlas.
        ITextureObject prev = tm.getTexture(ACTIVE_FONT_LOCATION);
        if (prev != null) TextureUtil.deleteTexture(prev.getGlTextureId());
        ttfFontTexture = new TTFFontTexture(ACTIVE_FONT_LOCATION, result);
        boolean ok = tm.loadTexture(ACTIVE_FONT_LOCATION, ttfFontTexture);
        if (!ok) {
            LDOGMod.LOGGER.warn("LDOG: TextureManager rejected TTF atlas");
            ttfFontTexture = null;
            return false;
        }
        activeSource = "ttf";
        LDOGMod.LOGGER.info("LDOG: TTF font active ({} {}{}{}pt @ {}px cells → {})",
            LDOGConfig.ttfFontFamily,
            LDOGConfig.ttfBold ? "bold " : "",
            LDOGConfig.ttfItalic ? "italic " : "",
            LDOGConfig.ttfFontSize, LDOGConfig.ttfCellSize, ACTIVE_FONT_LOCATION);
        return true;
    }

    private void tryLoadHDFont(TextureManager tm, IResourceManager rm) {
        ResourceLocation hdSource = null;
        for (ResourceLocation candidate : HD_FONT_CANDIDATES) {
            boolean present = resourceExists(rm, candidate);
            LDOGMod.LOGGER.info("LDOG: HD font probe {} -> {}", candidate, present ? "FOUND" : "absent");
            if (present && hdSource == null) hdSource = candidate;
        }
        if (hdSource == null) {
            LDOGMod.LOGGER.info("LDOG: No HD font texture in active resource pack — using vanilla");
            return;
        }
        ITextureObject prev = tm.getTexture(ACTIVE_FONT_LOCATION);
        if (prev != null) TextureUtil.deleteTexture(prev.getGlTextureId());
        hdFontTexture = new HDFontTexture(hdSource);
        boolean ok = tm.loadTexture(ACTIVE_FONT_LOCATION, hdFontTexture);
        if (ok) {
            activeSource = "hd";
            LDOGMod.LOGGER.info("LDOG: HD font active ({} → {})", hdSource, ACTIVE_FONT_LOCATION);
        } else {
            LDOGMod.LOGGER.warn("LDOG: Failed to register HD font {}", hdSource);
            hdFontTexture = null;
        }
    }

    /**
     * Populates {@link #widthOverrides} from whichever source is active:
     * TTF takes its widths from rasterization; HD reads ascii.properties (gated
     * on {@link LDOGConfig#useFontPropertyWidths}).
     */
    private void reloadWidthOverrides(IResourceManager resourceManager) {
        Arrays.fill(widthOverrides, -1);
        hasAnyWidthOverrides = false;

        if (!isActive()) return;

        if ("ttf".equals(activeSource) && ttfFontTexture != null) {
            int[] ttfWidths = ttfFontTexture.getLogicalWidths();
            int count = 0;
            for (int i = 0; i < Math.min(ttfWidths.length, widthOverrides.length); i++) {
                if (ttfWidths[i] >= 0) {
                    widthOverrides[i] = ttfWidths[i];
                    count++;
                }
            }
            hasAnyWidthOverrides = count > 0;
            LDOGMod.LOGGER.info("LDOG: Loaded {} font widths from TTF metrics", count);
            return;
        }

        if (!LDOGConfig.useFontPropertyWidths) return;
        for (ResourceLocation candidate : WIDTH_PROPERTY_CANDIDATES) {
            if (tryLoadProperties(resourceManager, candidate)) {
                LDOGMod.LOGGER.info("LDOG: Loaded font width overrides from {}", candidate);
                return;
            }
        }
        LDOGMod.LOGGER.debug("LDOG: No font width properties in resource pack");
    }

    /**
     * Writes any loaded width overrides directly into the live FontRenderer's
     * {@code charWidth[]}. Called after {@link #reloadWidthOverrides} so our
     * values land <em>after</em> FontRenderer's own {@code readFontTexture}
     * (which MC already triggered earlier in the same reload pass).
     */
    private void applyWidthOverridesToFontRenderer() {
        if (!hasAnyWidthOverrides || !isActive()) return;
        // TTF widths are authoritative for their source; HD widths only apply when
        // the user opted in via useFontPropertyWidths.
        if ("hd".equals(activeSource) && !LDOGConfig.useFontPropertyWidths) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        FontRenderer fr = mc.fontRenderer;
        if (fr == null) return;
        int[] charWidth = ((FontRendererInvoker) fr).ldog$getCharWidth();
        int applied = 0;
        for (int i = 0; i < widthOverrides.length && i < charWidth.length; i++) {
            int override = widthOverrides[i];
            if (override >= 0) {
                charWidth[i] = override;
                applied++;
            }
        }
        LDOGMod.LOGGER.info("LDOG: Applied {} font width overrides to FontRenderer (source={})",
            applied, activeSource);
    }

    /**
     * Called by the MixinFontRenderer redirect on its first hit after each
     * reload. Re-applies the current filter to the already-bound texture as a
     * defensive one-shot against filter state being clobbered during startup.
     */
    public void onFirstBindAfterReload() {
        if (loggedFirstBind) return;
        loggedFirstBind = true;
        if (hasCustomFont()) {
            refreshAntialiasFilter();
            LDOGMod.LOGGER.info("LDOG: FontRenderer mixin live — binding {} font ({})",
                activeSource, ACTIVE_FONT_LOCATION);
        } else {
            LDOGMod.LOGGER.info("LDOG: FontRenderer mixin live — no custom font active");
        }
    }

    private boolean tryLoadProperties(IResourceManager resourceManager, ResourceLocation loc) {
        IResource resource = null;
        try {
            resource = resourceManager.getResource(loc);
            Properties props = new Properties();
            props.load(resource.getInputStream());
            int count = 0;
            for (String name : props.stringPropertyNames()) {
                if (!name.startsWith("width.")) continue;
                try {
                    int codePoint = Integer.parseInt(name.substring(6).trim());
                    int rawWidth = Integer.parseInt(props.getProperty(name).trim());
                    if (codePoint >= 0 && codePoint < widthOverrides.length && rawWidth >= 0) {
                        widthOverrides[codePoint] = rawWidth + 1;
                        count++;
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed entries.
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
     * Invoked from the GUI after the font AA mode changes so the user doesn't
     * have to wait for a full resource reload to see the filter flip.
     */
    public void refreshAntialiasFilter() {
        if (ttfFontTexture != null) ttfFontTexture.refreshFilter();
        if (hdFontTexture != null) hdFontTexture.refreshFilter();
    }

    /**
     * Returns true if the current active texture needs to be rebuilt to switch
     * to {@code target} (i.e. the change involves adding or dropping a mip chain).
     */
    public boolean needsReloadToSwitchTo(FontAAMode target) {
        if (ttfFontTexture != null) return ttfFontTexture.needsReloadToSwitchTo(target);
        if (hdFontTexture != null) return hdFontTexture.needsReloadToSwitchTo(target);
        return false;
    }
}
