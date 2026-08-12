package com.limitlessdev.ldog.render.shaderpack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal GLSL preprocessor — just enough to resolve which conditional branch
 * is live so we can read the <em>active</em> {@code DRAWBUFFERS}/{@code RENDERTARGETS}
 * directive from a fragment shader.
 *
 * <p><b>Why this exists.</b> Packs gate their MRT output set behind conditionals:
 * <pre>
 *   #ifdef COLORED_LIGHT
 *   /* DRAWBUFFERS:0367 *​/
 *   #else
 *   /* DRAWBUFFERS:01 *​/
 *   #endif
 * </pre>
 * The directive lives in a <em>comment</em>, invisible to the GL compiler, so the
 * driver can't tell us which one it honoured. A naive "first directive wins" scan
 * picks the dead branch and mis-maps {@code gl_FragData[i]} → colortex. Stripping
 * inactive branches first makes the first <em>surviving</em> directive the real one.
 *
 * <p><b>Scope &amp; parity.</b> What matters is that this resolver evaluates the
 * SAME macro set the driver will see. Builtin OF macros ({@code MC_VERSION},
 * vendor/OS flags, …) ARE injected into the compiled source —
 * {@code ShaderPackRuntime.tryCompile} prepends them via
 * {@code ShaderMacros.injectDefines} — so callers must seed this resolver with
 * that same {@code ShaderMacros.standardDefines()} map (tryCompile does).
 * Otherwise a directive gated on a builtin resolves one way here and the other
 * way in the driver, and the DRAWBUFFERS mapping silently disagrees with what
 * the shader actually writes. Macros defined by neither the pack nor
 * {@code ShaderMacros} are absent on both sides, so {@code #ifdef} on them
 * resolves false consistently.
 *
 * <p>Supported: {@code #define}/{@code #undef} (object-like macros only),
 * {@code #ifdef}/{@code #ifndef}/{@code #if}/{@code #elif}/{@code #else}/{@code #endif},
 * {@code defined(X)}/{@code defined X}, integer literals, the C operators
 * {@code ! && || == != < > <= >= + - * /} and parentheses. Function-like macros,
 * string/float literals, and token pasting are out of scope (unused by DRAWBUFFERS
 * gating in practice).
 *
 * <p>Pure and side-effect free — unit-testable in isolation.
 */
public final class GlslPreprocessor {

    private GlslPreprocessor() {}

    /** Per-#if frame: was the enclosing region live, has any branch fired yet, is this branch live. */
    private static final class Frame {
        final boolean parentActive;
        boolean branchTaken;
        boolean currentActive;
        Frame(boolean parentActive, boolean currentActive) {
            this.parentActive = parentActive;
            this.currentActive = currentActive;
            this.branchTaken = currentActive;
        }
    }

    /**
     * Strip inactive conditional branches from {@code src}, given an initial set of
     * predefined macros. Surviving lines are returned verbatim; lines inside dead
     * branches (and the directive lines themselves) are replaced with blank lines so
     * line numbers are preserved. The {@code predefined} map is not mutated.
     *
     * @param src        include-expanded shader source
     * @param predefined initial macros (name → value; empty value = valueless define).
     *                   May be null/empty.
     * @return source with inactive branches blanked out
     */
    public static String stripInactiveBranches(String src, Map<String, String> predefined) {
        Map<String, String> macros = new HashMap<>();
        if (predefined != null) macros.putAll(predefined);

        // Split on \n, keep the splitting cheap; we re-join with \n. Any trailing \r
        // is preserved on the line (harmless to the GL parser and to directive scans).
        String[] lines = src.split("\n", -1);
        StringBuilder out = new StringBuilder(src.length());
        Deque<Frame> stack = new ArrayDeque<>();

        for (int li = 0; li < lines.length; li++) {
            String raw = lines[li];
            if (li > 0) out.append('\n');

            String trimmed = stripLeadingWs(raw);
            boolean active = allActive(stack);

            if (trimmed.startsWith("#")) {
                String directive = directiveName(trimmed);
                switch (directive) {
                    case "ifdef":
                    case "ifndef": {
                        boolean cond = false;
                        if (active) {
                            String name = firstToken(afterDirective(trimmed, directive));
                            boolean def = macros.containsKey(name);
                            cond = directive.equals("ifdef") ? def : !def;
                        }
                        stack.push(new Frame(active, active && cond));
                        continue; // directive line itself → blank
                    }
                    case "if": {
                        boolean cond = active && evalExpr(afterDirective(trimmed, "if"), macros);
                        stack.push(new Frame(active, cond));
                        continue;
                    }
                    case "elif": {
                        Frame f = stack.peek();
                        if (f != null) {
                            if (f.parentActive && !f.branchTaken) {
                                boolean cond = evalExpr(afterDirective(trimmed, "elif"), macros);
                                f.currentActive = cond;
                                if (cond) f.branchTaken = true;
                            } else {
                                f.currentActive = false;
                            }
                        }
                        continue;
                    }
                    case "else": {
                        Frame f = stack.peek();
                        if (f != null) {
                            f.currentActive = f.parentActive && !f.branchTaken;
                            if (f.currentActive) f.branchTaken = true;
                        }
                        continue;
                    }
                    case "endif": {
                        if (!stack.isEmpty()) stack.pop();
                        continue;
                    }
                    case "define": {
                        if (active) applyDefine(afterDirective(trimmed, "define"), macros);
                        continue; // keep directive out of the stripped copy; driver re-reads original
                    }
                    case "undef": {
                        if (active) macros.remove(firstToken(afterDirective(trimmed, "undef")));
                        continue;
                    }
                    default:
                        // Other directives (#version, #extension, #include-leftovers, …):
                        // emit only when active, blank otherwise.
                        if (active) out.append(raw);
                        continue;
                }
            }

            // Ordinary code/comment line: keep iff every enclosing branch is live.
            if (active) out.append(raw);
        }
        return out.toString();
    }

    private static boolean allActive(Deque<Frame> stack) {
        for (Frame f : stack) {
            if (!f.currentActive) return false;
        }
        return true;
    }

    private static String stripLeadingWs(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        return i == 0 ? s : s.substring(i);
    }

    /** Name of a {@code #directive} line (whitespace allowed between # and name). */
    private static String directiveName(String trimmed) {
        int i = 1; // skip '#'
        while (i < trimmed.length() && (trimmed.charAt(i) == ' ' || trimmed.charAt(i) == '\t')) i++;
        int start = i;
        while (i < trimmed.length() && isIdentChar(trimmed.charAt(i))) i++;
        return trimmed.substring(start, i);
    }

    /** Everything after {@code #<directive>} (the argument text), trimmed. */
    private static String afterDirective(String trimmed, String directive) {
        int idx = trimmed.indexOf(directive);
        String rest = trimmed.substring(idx + directive.length());
        // Drop trailing line comments so they don't pollute the expression/name.
        rest = stripLineComment(rest);
        return rest.trim();
    }

    private static String stripLineComment(String s) {
        int c = s.indexOf("//");
        if (c >= 0) s = s.substring(0, c);
        int b = s.indexOf("/*");
        if (b >= 0) s = s.substring(0, b);
        return s;
    }

    private static String firstToken(String s) {
        s = s.trim();
        int i = 0;
        while (i < s.length() && isIdentChar(s.charAt(i))) i++;
        return s.substring(0, i);
    }

    private static void applyDefine(String rest, Map<String, String> macros) {
        rest = rest.trim();
        if (rest.isEmpty()) return;
        int i = 0;
        while (i < rest.length() && isIdentChar(rest.charAt(i))) i++;
        String name = rest.substring(0, i);
        if (name.isEmpty()) return;
        // Function-like macro: "#define FOO(x) ..." — record name only (we don't
        // expand calls); its mere presence still satisfies #ifdef.
        if (i < rest.length() && rest.charAt(i) == '(') {
            macros.put(name, "");
            return;
        }
        String value = rest.substring(i).trim();
        macros.put(name, value);
    }

    private static boolean isIdentChar(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    // ------------------------------------------------------------------
    // #if / #elif expression evaluation
    // ------------------------------------------------------------------

    /** Evaluate a preprocessor constant expression to a boolean (nonzero = true). */
    static boolean evalExpr(String expr, Map<String, String> macros) {
        try {
            List<String> tokens = tokenize(expr);
            tokens = resolveDefined(tokens, macros);
            tokens = expandMacros(tokens, macros, 0);
            int[] pos = {0};
            long v = parseOr(tokens, pos);
            return v != 0;
        } catch (RuntimeException e) {
            // Malformed expression — fail closed (branch inactive) rather than throw.
            return false;
        }
    }

    private static List<String> tokenize(String s) {
        List<String> toks = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r') { i++; continue; }
            if (isIdentChar(c) && !(c >= '0' && c <= '9')) {
                int start = i;
                while (i < n && isIdentChar(s.charAt(i))) i++;
                toks.add(s.substring(start, i));
            } else if (c >= '0' && c <= '9') {
                int start = i;
                while (i < n && (isIdentChar(s.charAt(i)) || s.charAt(i) == 'x' || s.charAt(i) == 'X')) i++;
                toks.add(s.substring(start, i));
            } else {
                // Multi-char operators first.
                if (i + 1 < n) {
                    String two = s.substring(i, i + 2);
                    if (two.equals("&&") || two.equals("||") || two.equals("==")
                        || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                        toks.add(two);
                        i += 2;
                        continue;
                    }
                }
                toks.add(String.valueOf(c));
                i++;
            }
        }
        return toks;
    }

    /** Replace {@code defined NAME} / {@code defined(NAME)} with {@code 1} or {@code 0}. */
    private static List<String> resolveDefined(List<String> in, Map<String, String> macros) {
        List<String> out = new ArrayList<>(in.size());
        for (int i = 0; i < in.size(); i++) {
            String t = in.get(i);
            if (t.equals("defined")) {
                String name;
                if (i + 1 < in.size() && in.get(i + 1).equals("(")) {
                    name = (i + 2 < in.size()) ? in.get(i + 2) : "";
                    // skip name and ')'
                    i += 3;
                } else {
                    name = (i + 1 < in.size()) ? in.get(i + 1) : "";
                    i += 1;
                }
                out.add(macros.containsKey(name) ? "1" : "0");
            } else {
                out.add(t);
            }
        }
        return out;
    }

    /** Substitute object-like macros by value; valueless → 1, unknown identifier → 0. */
    private static List<String> expandMacros(List<String> in, Map<String, String> macros, int depth) {
        if (depth > 32) return in; // cycle guard
        List<String> out = new ArrayList<>(in.size());
        boolean changed = false;
        for (String t : in) {
            char c0 = t.isEmpty() ? '\0' : t.charAt(0);
            boolean isIdent = isIdentChar(c0) && !(c0 >= '0' && c0 <= '9');
            if (isIdent) {
                if (macros.containsKey(t)) {
                    String val = macros.get(t);
                    if (val == null || val.trim().isEmpty()) {
                        out.add("1"); // defined-but-valueless behaves as true in #if
                    } else {
                        out.addAll(tokenize(val));
                        changed = true;
                    }
                } else {
                    out.add("0"); // unknown identifier → 0, per C preprocessor rules
                }
            } else {
                out.add(t);
            }
        }
        return changed ? expandMacros(out, macros, depth + 1) : out;
    }

    // Recursive-descent over the token list. Booleans are 0/1 longs.

    private static long parseOr(List<String> t, int[] p) {
        long v = parseAnd(t, p);
        while (peek(t, p).equals("||")) { p[0]++; long r = parseAnd(t, p); v = (v != 0 || r != 0) ? 1 : 0; }
        return v;
    }

    private static long parseAnd(List<String> t, int[] p) {
        long v = parseEquality(t, p);
        while (peek(t, p).equals("&&")) { p[0]++; long r = parseEquality(t, p); v = (v != 0 && r != 0) ? 1 : 0; }
        return v;
    }

    private static long parseEquality(List<String> t, int[] p) {
        long v = parseRelational(t, p);
        while (true) {
            String op = peek(t, p);
            if (op.equals("==")) { p[0]++; v = (v == parseRelational(t, p)) ? 1 : 0; }
            else if (op.equals("!=")) { p[0]++; v = (v != parseRelational(t, p)) ? 1 : 0; }
            else break;
        }
        return v;
    }

    private static long parseRelational(List<String> t, int[] p) {
        long v = parseAdditive(t, p);
        while (true) {
            String op = peek(t, p);
            if (op.equals("<")) { p[0]++; v = (v < parseAdditive(t, p)) ? 1 : 0; }
            else if (op.equals(">")) { p[0]++; v = (v > parseAdditive(t, p)) ? 1 : 0; }
            else if (op.equals("<=")) { p[0]++; v = (v <= parseAdditive(t, p)) ? 1 : 0; }
            else if (op.equals(">=")) { p[0]++; v = (v >= parseAdditive(t, p)) ? 1 : 0; }
            else break;
        }
        return v;
    }

    private static long parseAdditive(List<String> t, int[] p) {
        long v = parseMultiplicative(t, p);
        while (true) {
            String op = peek(t, p);
            if (op.equals("+")) { p[0]++; v += parseMultiplicative(t, p); }
            else if (op.equals("-")) { p[0]++; v -= parseMultiplicative(t, p); }
            else break;
        }
        return v;
    }

    private static long parseMultiplicative(List<String> t, int[] p) {
        long v = parseUnary(t, p);
        while (true) {
            String op = peek(t, p);
            if (op.equals("*")) { p[0]++; v *= parseUnary(t, p); }
            else if (op.equals("/")) { p[0]++; long d = parseUnary(t, p); v = (d == 0) ? 0 : v / d; }
            else break;
        }
        return v;
    }

    private static long parseUnary(List<String> t, int[] p) {
        String op = peek(t, p);
        if (op.equals("!")) { p[0]++; return parseUnary(t, p) == 0 ? 1 : 0; }
        if (op.equals("-")) { p[0]++; return -parseUnary(t, p); }
        if (op.equals("+")) { p[0]++; return parseUnary(t, p); }
        return parsePrimary(t, p);
    }

    private static long parsePrimary(List<String> t, int[] p) {
        String tok = peek(t, p);
        // Operand expected but the stream is exhausted (e.g. "( 1 +" or "") — that's
        // a malformed expression. Throw so evalExpr() catches it and fails closed
        // rather than silently materialising a phantom 0 operand.
        if (tok.isEmpty()) throw new IllegalStateException("expected operand, got end of expression");
        if (tok.equals("(")) {
            p[0]++;
            long v = parseOr(t, p);
            if (!peek(t, p).equals(")")) throw new IllegalStateException("unmatched '('");
            p[0]++;
            return v;
        }
        p[0]++;
        return parseInt(tok);
    }

    private static long parseInt(String tok) {
        if (tok.isEmpty()) return 0;
        try {
            String s = tok;
            // Strip integer suffixes (u/U/l/L).
            int end = s.length();
            while (end > 0 && "uUlL".indexOf(s.charAt(end - 1)) >= 0) end--;
            s = s.substring(0, end);
            if (s.startsWith("0x") || s.startsWith("0X")) return Long.parseLong(s.substring(2), 16);
            if (s.length() > 1 && s.charAt(0) == '0') {
                // octal-ish; parse base 8 if all octal digits, else fall through
                boolean oct = true;
                for (int i = 1; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '7') { oct = false; break; }
                if (oct) return Long.parseLong(s, 8);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String peek(List<String> t, int[] p) {
        return p[0] < t.size() ? t.get(p[0]) : "";
    }
}
