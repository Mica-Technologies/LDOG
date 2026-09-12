package com.limitlessdev.ldog.render.shaderpack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShaderSamplerAliasesTest {

    @Test
    @DisplayName("the legacy names map to OptiFine's documented colortex indices")
    void aliasTable() {
        assertEquals(0, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gcolor"));
        assertEquals(1, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gdepth"));
        assertEquals(2, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gnormal"));
        assertEquals(3, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("composite"));
        assertEquals(4, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gaux1"));
        assertEquals(5, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gaux2"));
        assertEquals(6, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gaux3"));
        assertEquals(7, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gaux4"));
        assertEquals(8, ShaderSamplerAliases.LEGACY_COLORTEX.size());
    }

    @Test
    @DisplayName("gdepth is colortex1, NOT the depth texture — that is gdepthtex")
    void gdepthIsNotDepth() {
        // The single easiest mistake in the table: the name reads like a depth
        // buffer but OptiFine defines it as the second colour attachment.
        assertEquals(1, (int) ShaderSamplerAliases.LEGACY_COLORTEX.get("gdepth"));
        assertFalse(ShaderSamplerAliases.LEGACY_COLORTEX.containsKey(ShaderSamplerAliases.LEGACY_DEPTH));
        assertEquals("gdepthtex", ShaderSamplerAliases.LEGACY_DEPTH);
    }

    @Test
    @DisplayName("every alias maps to a distinct attachment in range")
    void aliasesAreDistinctAndInRange() {
        Set<Integer> seen = new HashSet<>();
        for (Integer idx : ShaderSamplerAliases.LEGACY_COLORTEX.values()) {
            assertTrue(idx >= 0 && idx <= 7, "colortex index out of range: " + idx);
            assertTrue(seen.add(idx), "two aliases share colortex" + idx);
        }
    }

    @Test
    @DisplayName("the texture-unit formula matches the composite pass layout")
    void textureUnitLayout() {
        // The composite pass binds colortex0 on unit 0, depthtex0 on unit 1 and
        // colortex1..7 on units 2..8, so a legacy alias resolves the same way.
        for (java.util.Map.Entry<String, Integer> e : ShaderSamplerAliases.LEGACY_COLORTEX.entrySet()) {
            int idx = e.getValue();
            int unit = idx == 0 ? 0 : idx + 1;
            assertTrue(unit == 0 || (unit >= 2 && unit <= 8), e.getKey() + " -> unit " + unit);
            assertNotEquals(1, unit, e.getKey() + " must not land on the depth unit");
        }
    }

    @Test
    @DisplayName("the table is immutable so a caller cannot corrupt it")
    void immutable() {
        assertThrows(UnsupportedOperationException.class,
            () -> ShaderSamplerAliases.LEGACY_COLORTEX.put("gaux5", 8));
    }
}
