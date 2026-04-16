package com.limitlessdev.ldog.render.natural;

/**
 * Natural texture variation modes, matching OptiFine's natural.properties format.
 *
 * 1 = rotate only (0/90/180/270)
 * 2 = rotate + flip
 * 3 = flip only (horizontal)
 * 4 = fixed (no variation, placeholder)
 */
public enum NaturalTextureMode {
    ROTATE(1),
    ROTATE_FLIP(2),
    FLIP(3),
    FIXED(4);

    private final int id;

    NaturalTextureMode(int id) {
        this.id = id;
    }

    public static NaturalTextureMode fromId(int id) {
        switch (id) {
            case 1: return ROTATE;
            case 2: return ROTATE_FLIP;
            case 3: return FLIP;
            case 4: return FIXED;
            default: return ROTATE;
        }
    }

    public int getId() {
        return id;
    }
}
