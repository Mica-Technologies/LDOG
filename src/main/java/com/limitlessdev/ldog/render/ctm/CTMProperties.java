package com.limitlessdev.ldog.render.ctm;

import com.limitlessdev.ldog.LDOGMod;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Parses an OptiFine-format CTM .properties file.
 *
 * Example properties file:
 *   method=ctm
 *   matchBlocks=glass
 *   tiles=0-46
 */
public class CTMProperties {

    private final ResourceLocation source;
    private CTMType method = CTMType.CTM;
    private final List<String> matchBlocks = new ArrayList<>();
    private String tiles = "";
    private int tileStartIndex = 0;

    private CTMProperties(ResourceLocation source) {
        this.source = source;
    }

    public static CTMProperties parse(ResourceLocation location, InputStream stream) {
        CTMProperties props = new CTMProperties(location);
        Properties p = new Properties();

        try {
            p.load(stream);
        } catch (IOException e) {
            LDOGMod.LOGGER.warn("LDOG: Failed to parse CTM properties {}", location, e);
            return null;
        }

        // Method
        String methodStr = p.getProperty("method", "ctm");
        CTMType type = CTMType.fromString(methodStr);
        if (type != null) {
            props.method = type;
        }

        // Match blocks
        String blocks = p.getProperty("matchBlocks", "");
        if (!blocks.isEmpty()) {
            for (String block : blocks.split("\\s+")) {
                props.matchBlocks.add(block.trim());
            }
        }

        // Tiles
        props.tiles = p.getProperty("tiles", "");

        return props;
    }

    public CTMType getMethod() { return method; }
    public List<String> getMatchBlocks() { return matchBlocks; }
    public String getTiles() { return tiles; }
    public ResourceLocation getSource() { return source; }

    /**
     * Parse the tiles property to get a list of tile indices.
     * Supports formats: "0-46", "0 1 2 3", "0-5 10 15-20"
     */
    public List<Integer> parseTileIndices() {
        List<Integer> indices = new ArrayList<>();
        if (tiles.isEmpty()) return indices;

        for (String part : tiles.split("\\s+")) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0]);
                        int end = Integer.parseInt(range[1]);
                        for (int i = start; i <= end; i++) {
                            indices.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                try {
                    indices.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {}
            }
        }
        return indices;
    }
}
