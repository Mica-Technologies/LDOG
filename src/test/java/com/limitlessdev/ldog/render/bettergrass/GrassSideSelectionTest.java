package com.limitlessdev.ldog.render.bettergrass;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrassSideSelectionTest {

    private static final String OVERLAY = "minecraft:blocks/grass_side_overlay";

    @Test
    @DisplayName("vanilla grass side: untinted grass_side is re-textured, tinted overlay dropped")
    void vanillaGrass() {
        int[] plan = GrassSideSelection.classify(
            new String[]{"minecraft:blocks/grass_side", OVERLAY},
            new int[]{-1, 0},
            OVERLAY);
        assertArrayEquals(new int[]{GrassSideSelection.RETEXTURE, GrassSideSelection.DROP}, plan);
    }

    @Test
    @DisplayName("Stratum-style pack: side texture is plain dirt, still re-textured")
    void repaintedSideTexture() {
        // Stratum 256x ships its own grass_normal.json with "side": "blocks/dirt".
        // The old name match against grass_side found nothing here — the bug.
        int[] plan = GrassSideSelection.classify(
            new String[]{"minecraft:blocks/dirt", OVERLAY},
            new int[]{-1, 0},
            OVERLAY);
        assertArrayEquals(new int[]{GrassSideSelection.RETEXTURE, GrassSideSelection.DROP}, plan);
    }

    @Test
    @DisplayName("mycelium: no overlay layer, the lone untinted side quad is re-textured")
    void myceliumHasNoOverlay() {
        int[] plan = GrassSideSelection.classify(
            new String[]{"minecraft:blocks/mycelium_side"},
            new int[]{-1},
            null);
        assertArrayEquals(new int[]{GrassSideSelection.RETEXTURE}, plan);
    }

    @Test
    @DisplayName("an untinted overlay is still recognised by name, and the base is found past it")
    void untintedOverlayByName() {
        int[] plan = GrassSideSelection.classify(
            new String[]{OVERLAY, "minecraft:blocks/dirt"},
            new int[]{-1, -1},
            OVERLAY);
        assertArrayEquals(new int[]{GrassSideSelection.DROP, GrassSideSelection.RETEXTURE}, plan);
    }

    @Test
    @DisplayName("every quad tinted: leave the face alone rather than guess")
    void allTintedIsLeftAlone() {
        int[] plan = GrassSideSelection.classify(
            new String[]{"pack:blocks/fancy_grass_a", "pack:blocks/fancy_grass_b"},
            new int[]{0, 0},
            OVERLAY);
        assertArrayEquals(new int[]{GrassSideSelection.KEEP, GrassSideSelection.KEEP}, plan);
    }

    @Test
    @DisplayName("extra decorative quads on the face are kept")
    void extraQuadsKept() {
        int[] plan = GrassSideSelection.classify(
            new String[]{"minecraft:blocks/grass_side", OVERLAY, "othermod:blocks/moss"},
            new int[]{-1, 0, -1},
            OVERLAY);
        assertArrayEquals(
            new int[]{GrassSideSelection.RETEXTURE, GrassSideSelection.DROP, GrassSideSelection.KEEP},
            plan);
    }

    @Test
    @DisplayName("empty and malformed input is safe")
    void degenerateInput() {
        assertArrayEquals(new int[0], GrassSideSelection.classify(new String[0], new int[0], OVERLAY));
        // Mismatched lengths must not throw; nothing is classified.
        assertArrayEquals(new int[]{GrassSideSelection.KEEP},
            GrassSideSelection.classify(new String[]{"a"}, new int[0], OVERLAY));
    }
}
