package com.limitlessdev.ldog.compat;

/**
 * Catalog of features where LDOG and OptiFine overlap — Phase C4.
 *
 * Each entry knows:
 *   - A stable display name (for GUI / logging).
 *   - A list of <b>candidate field names</b> in OF's {@code optifine.Config}
 *     class. Field names drift across OF versions ({@code ofConnectedTextures}
 *     became {@code ofConnectedTextures} stayed but some others were renamed),
 *     so we keep an ordered fallback list and the bridge tries each in turn.
 *   - The expected field {@link FieldType} so the bridge knows whether to
 *     write a boolean or an int (some OF settings use integer codes for
 *     off / fast / fancy tri-states; others are pure booleans).
 *   - The integer code that means "OFF" for INT features (typically 1, since
 *     OF often uses 1=off, 2=default-style, 3=alternative-style — but this
 *     varies and is exposed as a per-feature constant).
 *
 * <b>Field name research notes:</b> OptiFine is closed-source and obfuscated
 * across versions, so the candidate lists below are best-effort guesses from
 * publicly-documented OF behavior + common field naming conventions. The
 * bridge logs which candidate (if any) resolved at startup so unmapped
 * features become visible without crashing.
 */
public enum OFFeature {

    CONNECTED_TEXTURES(
        "Connected Textures",
        FieldType.INT,
        1,
        new String[]{"ofConnectedTextures"}),

    EMISSIVE_TEXTURES(
        "Emissive Textures",
        FieldType.BOOLEAN,
        0,
        new String[]{"ofEmissiveTextures"}),

    DYNAMIC_LIGHTS(
        "Dynamic Lights",
        FieldType.INT,
        1,
        new String[]{"ofDynamicLights"}),

    CUSTOM_SKY(
        "Custom Sky",
        FieldType.BOOLEAN,
        0,
        new String[]{"ofCustomSky"}),

    HD_TEXTURES(
        "HD Textures",
        FieldType.BOOLEAN,
        0,
        new String[]{"ofCustomItems", "ofHdU"}),  // OF doesn't have a single "HD textures off"
                                                  // toggle — best-effort to ofCustomItems.

    SMOOTH_FONT(
        "Smooth Font",
        FieldType.BOOLEAN,
        0,
        new String[]{"ofCustomFonts", "ofSmoothFonts"}),

    SHADERS(
        "Shaders",
        FieldType.BOOLEAN,
        0,
        new String[]{"ofShaders"});  // OF shader pack handling — disabling
                                     // here is best-effort; OF's shader system
                                     // is deeply integrated and may need extra
                                     // unloading work that's out of scope here.

    private final String displayName;
    private final FieldType fieldType;
    private final int offValue;
    private final String[] candidateFieldNames;

    OFFeature(String displayName, FieldType fieldType, int offValue,
              String[] candidateFieldNames) {
        this.displayName = displayName;
        this.fieldType = fieldType;
        this.offValue = offValue;
        this.candidateFieldNames = candidateFieldNames;
    }

    public String displayName() { return displayName; }
    public FieldType fieldType() { return fieldType; }
    public int offValue() { return offValue; }
    public String[] candidateFieldNames() { return candidateFieldNames; }

    /** Type of the OF Config field. Determines how we write the OFF state. */
    public enum FieldType {
        BOOLEAN,
        INT
    }
}
