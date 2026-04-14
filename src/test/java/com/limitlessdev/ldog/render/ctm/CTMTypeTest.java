package com.limitlessdev.ldog.render.ctm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CTMTypeTest {

    @Test
    @DisplayName("fromString parses all valid types")
    void fromStringParsesAll() {
        assertEquals(CTMType.CTM, CTMType.fromString("ctm"));
        assertEquals(CTMType.HORIZONTAL, CTMType.fromString("horizontal"));
        assertEquals(CTMType.VERTICAL, CTMType.fromString("vertical"));
        assertEquals(CTMType.TOP, CTMType.fromString("top"));
        assertEquals(CTMType.RANDOM, CTMType.fromString("random"));
        assertEquals(CTMType.FIXED, CTMType.fromString("fixed"));
    }

    @Test
    @DisplayName("fromString is case insensitive")
    void fromStringCaseInsensitive() {
        assertEquals(CTMType.CTM, CTMType.fromString("CTM"));
        assertEquals(CTMType.HORIZONTAL, CTMType.fromString("Horizontal"));
    }

    @Test
    @DisplayName("fromString returns null for unknown")
    void fromStringReturnsNullForUnknown() {
        assertNull(CTMType.fromString("invalid"));
        assertNull(CTMType.fromString(""));
    }

    @Test
    @DisplayName("Full CTM type has 47 tiles")
    void fullCTMHas47Tiles() {
        assertEquals(47, CTMType.CTM.getTileCount());
    }
}
