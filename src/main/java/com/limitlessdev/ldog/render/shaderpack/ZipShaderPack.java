package com.limitlessdev.ldog.render.shaderpack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shader pack backed by a .zip file under {@code shaderpacks/}. The
 * {@code shaders/} root is auto-detected — packs commonly nest their
 * shaders under a top-level folder matching the pack name, so we try
 * both {@code shaders/<path>} and {@code <pack>/shaders/<path>} forms.
 */
public final class ZipShaderPack extends ShaderPack {

    private final ZipFile zip;
    /** Prefix prepended to every relative lookup ("" or "<topdir>/"). */
    private final String prefix;

    public ZipShaderPack(String name, File zipFile, String autoDetectedPrefix) throws IOException {
        super(name);
        this.zip = new ZipFile(zipFile);
        this.prefix = autoDetectedPrefix;
    }

    @Override
    public InputStream openShaderResource(String relativePath) throws IOException {
        ZipEntry entry = zip.getEntry(prefix + "shaders/" + relativePath);
        if (entry == null) return null;
        return zip.getInputStream(entry);
    }

    @Override
    public boolean hasShaderResource(String relativePath) {
        return zip.getEntry(prefix + "shaders/" + relativePath) != null;
    }

    @Override
    public void close() {
        try { zip.close(); } catch (IOException ignored) {}
    }
}
