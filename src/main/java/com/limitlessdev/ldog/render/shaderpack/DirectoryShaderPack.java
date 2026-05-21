package com.limitlessdev.ldog.render.shaderpack;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Shader pack backed by an unpacked directory under {@code shaderpacks/}.
 * Faster iteration during development since you can edit .vsh/.fsh files
 * in place and reload the pack.
 */
public final class DirectoryShaderPack extends ShaderPack {

    private final File shadersDir;

    public DirectoryShaderPack(String name, File shadersDir) {
        super(name);
        this.shadersDir = shadersDir;
    }

    @Override
    public InputStream openShaderResource(String relativePath) throws IOException {
        File f = new File(shadersDir, relativePath);
        if (!f.isFile()) return null;
        return new FileInputStream(f);
    }

    @Override
    public boolean hasShaderResource(String relativePath) {
        return new File(shadersDir, relativePath).isFile();
    }

    @Override
    public void close() {
        // Nothing to release — files are opened on demand.
    }
}
