package com.limitlessdev.ldog.render.shaderpack;

import java.io.InputStream;

/**
 * A {@link ShaderPack} bundled inside the LDOG jar, read from classpath
 * resources under {@code /assets/ldog/shaderpacks/<dir>/shaders/}.
 *
 * <p>See {@link BuiltinShaderPacks} for the registry of shipped packs.
 */
public final class BuiltinShaderPack extends ShaderPack {

    /** Absolute classpath root including the trailing {@code shaders/}. */
    private final String root;

    public BuiltinShaderPack(String displayName, String resourceDir) {
        super(displayName);
        this.root = "/assets/ldog/shaderpacks/" + resourceDir + "/shaders/";
    }

    @Override
    public InputStream openShaderResource(String relativePath) {
        return BuiltinShaderPack.class.getResourceAsStream(root + relativePath);
    }

    @Override
    public boolean hasShaderResource(String relativePath) {
        return BuiltinShaderPack.class.getResource(root + relativePath) != null;
    }

    @Override
    public void close() {
        // Classpath-backed — nothing to release.
    }
}
