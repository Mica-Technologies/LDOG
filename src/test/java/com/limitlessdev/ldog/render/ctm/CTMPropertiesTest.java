package com.limitlessdev.ldog.render.ctm;

import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CTM properties file parsing.
 */
class CTMPropertiesTest {

    private CTMProperties parse(String content) {
        return CTMProperties.parse(
            new ResourceLocation("test", "ctm/test.properties"),
            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    @DisplayName("Parse method type")
    void parseMethod() {
        CTMProperties props = parse("method=horizontal\nmatchBlocks=glass");
        assertNotNull(props);
        assertEquals(CTMType.HORIZONTAL, props.getMethod());
    }

    @Test
    @DisplayName("Default method is CTM")
    void defaultMethodIsCTM() {
        CTMProperties props = parse("matchBlocks=glass");
        assertNotNull(props);
        assertEquals(CTMType.CTM, props.getMethod());
    }

    @Test
    @DisplayName("Parse match blocks")
    void parseMatchBlocks() {
        CTMProperties props = parse("matchBlocks=glass stone dirt");
        assertNotNull(props);
        assertEquals(3, props.getMatchBlocks().size());
        assertTrue(props.getMatchBlocks().contains("glass"));
        assertTrue(props.getMatchBlocks().contains("stone"));
        assertTrue(props.getMatchBlocks().contains("dirt"));
    }

    @Test
    @DisplayName("Parse tile range")
    void parseTileRange() {
        CTMProperties props = parse("tiles=0-4");
        assertNotNull(props);
        List<Integer> indices = props.parseTileIndices();
        assertEquals(5, indices.size());
        assertEquals(0, indices.get(0));
        assertEquals(4, indices.get(4));
    }

    @Test
    @DisplayName("Parse tile mixed format")
    void parseTileMixed() {
        CTMProperties props = parse("tiles=0-2 5 10-12");
        assertNotNull(props);
        List<Integer> indices = props.parseTileIndices();
        assertEquals(7, indices.size()); // 0,1,2 + 5 + 10,11,12
        assertTrue(indices.contains(0));
        assertTrue(indices.contains(5));
        assertTrue(indices.contains(12));
    }

    @Test
    @DisplayName("Empty tiles returns empty list")
    void emptyTiles() {
        CTMProperties props = parse("matchBlocks=glass");
        assertNotNull(props);
        assertTrue(props.parseTileIndices().isEmpty());
    }
}
