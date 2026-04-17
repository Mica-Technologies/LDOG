package com.limitlessdev.ldog.render.font;

import com.limitlessdev.ldog.config.LDOGConfig;
import org.lwjgl.opengl.GL11;

/**
 * Three-step quality scale for HD font texture filtering, mirroring the
 * progression most "smooth font" implementations expose:
 *
 * <ul>
 *   <li>{@link #OFF} — {@code GL_NEAREST}. Blocky, vanilla-look. No mipmaps.</li>
 *   <li>{@link #BILINEAR} — {@code GL_LINEAR}. Smooths edges at 1:1 but aliases
 *       at GUI scale because the HD atlas has no mip chain.</li>
 *   <li>{@link #TRILINEAR} — {@code GL_LINEAR_MIPMAP_LINEAR} with a full mipmap
 *       chain. Smoothest: the GPU picks a pre-downsampled level matching the
 *       screen footprint and blends between levels.</li>
 * </ul>
 *
 * <p>We deliberately don't expose a dedicated anisotropic option for fonts —
 * GUI text is always front-facing, so anisotropic sampling gives no win over
 * trilinear for this workload.
 */
public enum FontAAMode {
    OFF,
    BILINEAR,
    TRILINEAR;

    public static FontAAMode current() {
        return parse(LDOGConfig.fontAntialiasing);
    }

    public static FontAAMode parse(String raw) {
        if (raw == null) return TRILINEAR;
        switch (raw.trim().toLowerCase()) {
            case "off":      return OFF;
            case "bilinear": return BILINEAR;
            case "trilinear":
            default:         return TRILINEAR;
        }
    }

    public boolean needsMipmaps() {
        return this == TRILINEAR;
    }

    /** MIN_FILTER value, downgraded to plain LINEAR if the mip chain isn't built. */
    public int minFilter(boolean hasMipmaps) {
        switch (this) {
            case OFF:       return GL11.GL_NEAREST;
            case BILINEAR:  return GL11.GL_LINEAR;
            case TRILINEAR: return hasMipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR;
            default:        return GL11.GL_LINEAR;
        }
    }

    public int magFilter() {
        return (this == OFF) ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }

    /** True if this mode smooths edges at all (bilinear or better). */
    public boolean isSmooth() {
        return this != OFF;
    }
}
