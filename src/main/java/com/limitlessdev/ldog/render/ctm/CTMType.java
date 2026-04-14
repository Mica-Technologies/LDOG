package com.limitlessdev.ldog.render.ctm;

/**
 * Connected texture methods supported by LDOG.
 * Matches the OptiFine CTM method names from .properties files.
 */
public enum CTMType {
    /** Full 47-tile connected textures (like glass panes) */
    CTM("ctm", 47),
    /** Horizontal-only connections (like bookshelves) */
    HORIZONTAL("horizontal", 4),
    /** Vertical-only connections */
    VERTICAL("vertical", 4),
    /** Top-connected (e.g., sandstone variants) */
    TOP("top", 1),
    /** Random variants from a set of tiles */
    RANDOM("random", -1),
    /** Fixed tile index */
    FIXED("fixed", 1);

    private final String name;
    private final int tileCount;

    CTMType(String name, int tileCount) {
        this.name = name;
        this.tileCount = tileCount;
    }

    public String getName() { return name; }
    public int getTileCount() { return tileCount; }

    public static CTMType fromString(String s) {
        for (CTMType type : values()) {
            if (type.name.equalsIgnoreCase(s)) return type;
        }
        return null;
    }
}
