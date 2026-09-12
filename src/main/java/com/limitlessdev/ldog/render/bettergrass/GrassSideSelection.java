package com.limitlessdev.ldog.render.bettergrass;

/**
 * Decides, for the quads a grass/mycelium model emitted on one horizontal
 * face, which one is the opaque base side (to be re-textured with the top
 * sprite) and which are the tinted overlay quads (to be dropped, because the
 * whole face becomes the tinted top texture).
 *
 * <p>Pure classification so it can be unit-tested without a GL context.
 *
 * <p><b>Why not match by sprite name.</b> The original implementation looked
 * for exactly {@code minecraft:blocks/grass_side}. Resource packs re-texture
 * the grass model freely: Stratum 256x ships its own
 * {@code models/block/grass_normal.json} with {@code "side": "blocks/dirt"}
 * and puts the entire grass fringe in the tinted overlay layer. The name match
 * then found nothing, so Better Grass silently did nothing in Fast, Fancy
 * <em>and</em> Off — indistinguishable from the feature being broken.
 *
 * <p>The structure is what is stable across packs, because it comes from the
 * model format rather than from texture names: the base side face is untinted
 * ({@code tintindex} absent, i.e. -1) and the overlay faces carry
 * {@code "tintindex": 0} so the biome colouriser can tint them. So: the first
 * untinted quad is the base, and every tinted quad on the same face is an
 * overlay. The known overlay sprite name is still honoured when present, for
 * packs that (wrongly) leave the overlay untinted.
 */
public final class GrassSideSelection {

    /** Leave the quad exactly as the model produced it. */
    public static final int KEEP = 0;
    /** Re-texture this quad with the top sprite and tint it with the biome colour. */
    public static final int RETEXTURE = 1;
    /** Drop this quad; the re-textured base already covers the face. */
    public static final int DROP = 2;

    private GrassSideSelection() {}

    /**
     * @param spriteNames       icon name of each quad on this face, in model order
     * @param tintIndexes       tint index of each quad ({@code -1} = untinted)
     * @param overlaySpriteName the pack's overlay sprite name, or {@code null}
     *                          for models that have no overlay layer (mycelium)
     * @return one of {@link #KEEP} / {@link #RETEXTURE} / {@link #DROP} per quad.
     *         All {@code KEEP} means "nothing recognisable here, leave the face
     *         alone" — callers should then return the original list untouched.
     */
    public static int[] classify(String[] spriteNames, int[] tintIndexes, String overlaySpriteName) {
        int n = spriteNames.length;
        int[] out = new int[n];
        if (n == 0 || tintIndexes.length != n) return out;

        int base = -1;
        for (int i = 0; i < n; i++) {
            boolean isNamedOverlay = overlaySpriteName != null && overlaySpriteName.equals(spriteNames[i]);
            if (!isNamedOverlay && tintIndexes[i] < 0) {
                base = i;
                break;
            }
        }
        // A model whose every side quad is tinted (no plain base layer) gives us
        // nothing safe to re-texture — the tinted quads may be the grass fringe
        // itself. Leave the face alone rather than guess.
        if (base < 0) return out;

        out[base] = RETEXTURE;
        for (int i = 0; i < n; i++) {
            if (i == base) continue;
            boolean isNamedOverlay = overlaySpriteName != null && overlaySpriteName.equals(spriteNames[i]);
            if (isNamedOverlay || tintIndexes[i] >= 0) {
                out[i] = DROP;
            }
        }
        return out;
    }
}
