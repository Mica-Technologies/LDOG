package com.limitlessdev.ldog.render.shaderpack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * In-memory representation of a discovered shader pack — either a directory
 * or a .zip file under {@code shaderpacks/}.
 *
 * The format LDOG targets is the OptiFine / Iris convention used by every
 * meaningful 1.12.2 shader pack:
 *
 * <pre>
 * shaderpacks/
 * └── PackName.zip or PackName/
 *     └── shaders/
 *         ├── shaders.properties
 *         ├── gbuffers_basic.vsh / .fsh
 *         ├── gbuffers_textured.vsh / .fsh
 *         ├── gbuffers_terrain.vsh / .fsh
 *         ├── gbuffers_water.vsh / .fsh
 *         ├── gbuffers_skybasic.vsh / .fsh
 *         ├── gbuffers_clouds.vsh / .fsh
 *         ├── gbuffers_entities.vsh / .fsh
 *         ├── shadow.vsh / .fsh
 *         ├── composite.vsh / .fsh        (post-process)
 *         ├── composite1.vsh / .fsh       (chained composite stages, up to ~16)
 *         ├── final.vsh / .fsh
 *         └── world0/, world-1/, world1/  (per-dimension overrides)
 * </pre>
 *
 * <p>Scaffold v1: discovery + properties parsing + raw shader source access.
 * Compile-and-bind is deferred to {@link ShaderPackPipeline} once the rest
 * of the LDOG rendering pipeline is ready to defer to a pack-managed
 * gbuffer path. See master plan §13 "Real shader pack support".
 */
public abstract class ShaderPack {

    /** Display name (filename without extension, or directory name). */
    public final String name;

    /** Parsed {@code shaders.properties}; empty when absent. */
    public final Properties properties = new Properties();

    protected ShaderPack(String name) {
        this.name = name;
    }

    /**
     * Open a file from inside the {@code shaders/} root of the pack.
     * Returns null when the file isn't present. Caller closes the stream.
     */
    public abstract InputStream openShaderResource(String relativePath) throws IOException;

    /** Whether the pack contains a file at {@code shaders/<path>}. */
    public abstract boolean hasShaderResource(String relativePath);

    /** Convenience: read a UTF-8 text file from {@code shaders/}. */
    public String readShaderText(String relativePath) throws IOException {
        try (InputStream in = openShaderResource(relativePath)) {
            if (in == null) return null;
            byte[] buf = new byte[Math.max(16, in.available())];
            int total = 0;
            int read;
            while ((read = in.read(buf, total, buf.length - total)) != -1) {
                total += read;
                if (total == buf.length) {
                    byte[] grown = new byte[buf.length * 2];
                    System.arraycopy(buf, 0, grown, 0, total);
                    buf = grown;
                }
            }
            return new String(buf, 0, total, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** Best-effort cleanup. Pack instances are reusable across reloads. */
    public abstract void close();

    /**
     * Resolve a property with reasonable defaults. Returns the raw string;
     * callers parse to int/float as needed.
     */
    public String property(String key, String defaultValue) {
        String v = properties.getProperty(key);
        return v == null ? defaultValue : v.trim();
    }

    /** True if at least one gbuffer .vsh/.fsh pair exists. */
    public boolean hasAnyProgram() {
        for (Map.Entry<String, String> e : ShaderProgramId.STANDARD_PAIRS.entrySet()) {
            if (hasShaderResource(e.getKey()) && hasShaderResource(e.getValue())) return true;
        }
        return false;
    }
}
