package com.limitlessdev.ldog.render.ctm;

import com.limitlessdev.ldog.LDOGMod;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Parses an OptiFine/MCPatcher CTM .properties file.
 *
 * Supported properties:
 *   method      - ctm, horizontal, vertical, top, random, fixed (default: ctm)
 *   matchBlocks - space-separated block IDs or registry names
 *   matchTiles  - space-separated texture/tile names
 *   tiles       - tile indices/ranges (e.g. "0-46", "0 1 2 3")
 *   connect     - block, tile, material, state (default: block)
 *   faces       - space-separated face names: top, bottom, north, south, east, west, sides, all
 *   weight      - integer priority (higher = applied first, default: 0)
 */
public class CTMProperties {

    private final ResourceLocation source;
    private CTMType method = CTMType.CTM;
    private final List<String> matchBlocks = new ArrayList<>();
    private final List<String> matchTiles = new ArrayList<>();
    private String tiles = "";
    private String connect = "block";
    private final Set<String> faces = new LinkedHashSet<>();
    private int weight = 0;

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

        // Match blocks (numeric IDs or registry names)
        String blocks = p.getProperty("matchBlocks", "");
        if (!blocks.isEmpty()) {
            for (String block : blocks.split("\\s+")) {
                String trimmed = block.trim();
                if (!trimmed.isEmpty()) {
                    props.matchBlocks.add(trimmed);
                }
            }
        }

        // Match tiles (texture names)
        String tileMatches = p.getProperty("matchTiles", "");
        if (!tileMatches.isEmpty()) {
            for (String tile : tileMatches.split("\\s+")) {
                String trimmed = tile.trim();
                if (!trimmed.isEmpty()) {
                    props.matchTiles.add(trimmed);
                }
            }
        }

        // Tiles
        props.tiles = p.getProperty("tiles", "");

        // Connect mode
        String connectStr = p.getProperty("connect", "block");
        if ("block".equals(connectStr) || "tile".equals(connectStr)
            || "material".equals(connectStr) || "state".equals(connectStr)) {
            props.connect = connectStr;
        }

        // Faces restriction
        String facesStr = p.getProperty("faces", "");
        if (!facesStr.isEmpty()) {
            for (String face : facesStr.split("\\s+")) {
                String trimmed = face.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    if ("sides".equals(trimmed)) {
                        props.faces.addAll(Arrays.asList("north", "south", "east", "west"));
                    } else if ("all".equals(trimmed)) {
                        props.faces.addAll(Arrays.asList("top", "bottom", "north", "south", "east", "west"));
                    } else {
                        props.faces.add(trimmed);
                    }
                }
            }
        }

        // Weight (priority)
        String weightStr = p.getProperty("weight", "0");
        try {
            props.weight = Integer.parseInt(weightStr.trim());
        } catch (NumberFormatException ignored) {}

        return props;
    }

    public CTMType getMethod() { return method; }
    public List<String> getMatchBlocks() { return matchBlocks; }
    public List<String> getMatchTiles() { return matchTiles; }
    public String getTiles() { return tiles; }
    public ResourceLocation getSource() { return source; }
    public String getConnect() { return connect; }
    public Set<String> getFaces() { return faces; }
    public int getWeight() { return weight; }

    /**
     * Check if CTM should apply to the given face.
     * Empty faces set means all faces are allowed.
     */
    public boolean appliesToFace(String faceName) {
        if (faces.isEmpty()) return true;
        return faces.contains(faceName.toLowerCase(Locale.ROOT));
    }

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
