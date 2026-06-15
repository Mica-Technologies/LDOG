package com.limitlessdev.ldog.render.shaderpack;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
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

    /**
     * Per-dimension subfolder to probe first when resolving a program, e.g.
     * {@code "world0"} (overworld), {@code "world-1"} (nether), {@code "world1"}
     * (end). Mirrors the OptiFine / Iris layout where packs put dimension
     * variants under {@code shaders/worldN/} and only a subset (or none) at the
     * {@code shaders/} root. Null = probe the root only.
     */
    public String worldDir;

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

    /**
     * Resolve a program/file base path against the active dimension folder.
     * Returns {@code worldDir/<base>} when that exists, else {@code <base>} at
     * the root when that exists, else null. Used so a pack that ships only
     * {@code world0/composite.fsh} (no root copy) still resolves in the
     * overworld.
     */
    public String resolvePath(String base) {
        if (worldDir != null) {
            String scoped = worldDir + "/" + base;
            if (hasShaderResource(scoped)) return scoped;
        }
        return hasShaderResource(base) ? base : null;
    }

    /**
     * Read a shader file and recursively inline its {@code #include} directives,
     * following OptiFine / Iris semantics: {@code #include "/abs/from/root"}
     * resolves from the {@code shaders/} root; a relative path resolves against
     * the including file's directory. Cycles are broken and depth is bounded.
     */
    public String readShaderWithIncludes(String relativePath) throws IOException {
        StringBuilder out = new StringBuilder();
        expandIncludes(relativePath, out, new ArrayDeque<>(), 0);
        return out.toString();
    }

    private void expandIncludes(String path, StringBuilder out, Deque<String> stack, int depth)
            throws IOException {
        if (depth > 64) throw new IOException("#include nesting too deep at " + path);
        String src = readShaderText(path);
        if (src == null) {
            out.append("// [ldog] missing include: ").append(path).append('\n');
            return;
        }
        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        for (String line : src.split("\n", -1)) {
            String trimmed = line.trim();
            String target = parseIncludeTarget(trimmed);
            if (target != null) {
                String resolved = target.startsWith("/")
                    ? normalize(target.substring(1))
                    : normalize(dir.isEmpty() ? target : dir + "/" + target);
                if (stack.contains(resolved)) {
                    out.append("// [ldog] skipped recursive include: ").append(resolved).append('\n');
                    continue;
                }
                stack.push(resolved);
                expandIncludes(resolved, out, stack, depth + 1);
                stack.pop();
                out.append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
    }

    /** Returns the include target (unquoted) for an {@code #include} line, else null. */
    private static String parseIncludeTarget(String trimmedLine) {
        if (!trimmedLine.startsWith("#include")) return null;
        String rest = trimmedLine.substring("#include".length()).trim();
        // Strip a trailing line comment.
        int c = rest.indexOf("//");
        if (c >= 0) rest = rest.substring(0, c).trim();
        if (rest.length() >= 2
            && (rest.charAt(0) == '"' || rest.charAt(0) == '<')) {
            char close = rest.charAt(0) == '"' ? '"' : '>';
            int end = rest.indexOf(close, 1);
            if (end > 1) return rest.substring(1, end);
        }
        // Bare form: #include path
        return rest.isEmpty() ? null : rest;
    }

    /** Collapse {@code .} / {@code ..} segments in a shaders-root-relative path. */
    private static String normalize(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String seg : path.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.addLast(seg);
        }
        return String.join("/", parts);
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

    /** True if at least one gbuffer/program .vsh/.fsh pair exists (dimension-aware). */
    public boolean hasAnyProgram() {
        for (Map.Entry<String, String> e : ShaderProgramId.STANDARD_PAIRS.entrySet()) {
            if (resolvePath(e.getKey()) != null && resolvePath(e.getValue()) != null) return true;
        }
        return false;
    }
}
