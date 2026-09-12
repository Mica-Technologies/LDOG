package com.limitlessdev.ldog.render.shaderpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser + evaluator for the expression language OptiFine accepts in
 * {@code shaders.properties} custom uniforms and variables:
 *
 * <pre>
 *   uniform.float.shadowFade = clamp(1.0 - (abs(abs(sunAngle - 0.5) - 0.25) - 0.23) * 100.0, 0.0, 1.0)
 *   variable.float.hA        = if(tAlin > 0.5, 1.0, 0.0)
 *   uniform.float.isDesert   = smooth(2, if(in(biome, 2, 17, 130), 1, 0), 10, 10)
 *   uniform.float.framemod8  = frameCounter % 8
 * </pre>
 *
 * <p>Values are small float vectors (1–4 components); booleans are 1.0 / 0.0;
 * ints are floats that happen to be integral. Arithmetic is component-wise with
 * scalar broadcast, which matches GLSL and is what packs assume.
 *
 * <p>Deliberately LDOG-original: a plain recursive-descent parser over the
 * documented operator set, with the functions real packs use (BSL, Sildur's,
 * Complementary). Unknown identifiers resolve through {@link Env#get} so the
 * caller decides what a name means (built-in uniform, another custom
 * variable, a {@code BIOME_*} constant); unknown functions are a parse error
 * so a typo in a pack is reported once rather than silently reading as zero.
 *
 * <p>Pure Java, no GL — unit-tested on its own.
 */
public final class ShaderUniformExpression {

    /** Where identifiers and the stateful {@code smooth()} come from. */
    public interface Env {
        /** Value of a name, or null when unknown. Never throws. */
        float[] get(String name);

        /** Temporal smoothing keyed by {@code id}; fade times are seconds. */
        float smooth(int id, float value, float fadeUp, float fadeDown);
    }

    private ShaderUniformExpression() {}

    /** Parse once; evaluate many times. */
    public static Node parse(String source) {
        Parser p = new Parser(source);
        Node n = p.parseExpr();
        p.expectEnd();
        return n;
    }

    /** Convenience for tests and one-off evaluation. */
    public static float[] evaluate(String source, Env env) {
        return parse(source).eval(env);
    }

    // ------------------------------------------------------------------ AST

    public abstract static class Node {
        public abstract float[] eval(Env env);
    }

    static final class Const extends Node {
        final float[] v;
        Const(float... v) { this.v = v; }
        @Override public float[] eval(Env env) { return v.clone(); }
    }

    static final class Ident extends Node {
        final String name;
        Ident(String name) { this.name = name; }
        @Override public float[] eval(Env env) {
            float[] v = env.get(name);
            if (v == null) throw new IllegalArgumentException("unknown identifier '" + name + "'");
            return v.clone();
        }
    }

    static final class Swizzle extends Node {
        final Node inner; final int[] idx;
        Swizzle(Node inner, int[] idx) { this.inner = inner; this.idx = idx; }
        @Override public float[] eval(Env env) {
            float[] v = inner.eval(env);
            float[] out = new float[idx.length];
            for (int i = 0; i < idx.length; i++) {
                if (idx[i] >= v.length) throw new IllegalArgumentException("swizzle component out of range");
                out[i] = v[idx[i]];
            }
            return out;
        }
    }

    static final class Unary extends Node {
        final char op; final Node a;
        Unary(char op, Node a) { this.op = op; this.a = a; }
        @Override public float[] eval(Env env) {
            float[] v = a.eval(env);
            for (int i = 0; i < v.length; i++) {
                if (op == '-') v[i] = -v[i];
                else if (op == '!') v[i] = v[i] != 0f ? 0f : 1f;
            }
            return v;
        }
    }

    static final class Binary extends Node {
        final String op; final Node a, b;
        Binary(String op, Node a, Node b) { this.op = op; this.a = a; this.b = b; }
        @Override public float[] eval(Env env) {
            // Short-circuit logical ops on scalars.
            if (op.equals("&&")) {
                return new float[]{ truthy(a.eval(env)) && truthy(b.eval(env)) ? 1f : 0f };
            }
            if (op.equals("||")) {
                return new float[]{ truthy(a.eval(env)) || truthy(b.eval(env)) ? 1f : 0f };
            }
            float[] x = a.eval(env), y = b.eval(env);
            int n = Math.max(x.length, y.length);
            if (x.length != 1 && y.length != 1 && x.length != y.length) {
                throw new IllegalArgumentException("component count mismatch in '" + op + "'");
            }
            float[] out = new float[n];
            for (int i = 0; i < n; i++) {
                float p = x[x.length == 1 ? 0 : i], q = y[y.length == 1 ? 0 : i];
                switch (op) {
                    case "+": out[i] = p + q; break;
                    case "-": out[i] = p - q; break;
                    case "*": out[i] = p * q; break;
                    case "/": out[i] = q == 0f ? 0f : p / q; break;
                    case "%": out[i] = q == 0f ? 0f : p - q * (float) Math.floor(p / q); break;
                    case "==": out[i] = p == q ? 1f : 0f; break;
                    case "!=": out[i] = p != q ? 1f : 0f; break;
                    case "<": out[i] = p < q ? 1f : 0f; break;
                    case "<=": out[i] = p <= q ? 1f : 0f; break;
                    case ">": out[i] = p > q ? 1f : 0f; break;
                    case ">=": out[i] = p >= q ? 1f : 0f; break;
                    default: throw new IllegalStateException(op);
                }
            }
            return out;
        }
    }

    static final class Call extends Node {
        final String fn; final List<Node> args;
        Call(String fn, List<Node> args) { this.fn = fn; this.args = args; }

        @Override public float[] eval(Env env) {
            switch (fn) {
                case "if": {
                    // if(c1, v1, [c2, v2, ...], else)
                    if (args.size() < 3 || args.size() % 2 == 0) {
                        throw new IllegalArgumentException("if() needs cond,value pairs plus an else");
                    }
                    for (int i = 0; i + 1 < args.size(); i += 2) {
                        if (truthy(args.get(i).eval(env))) return args.get(i + 1).eval(env);
                    }
                    return args.get(args.size() - 1).eval(env);
                }
                case "in": {
                    float[] x = args.get(0).eval(env);
                    for (int i = 1; i < args.size(); i++) {
                        if (x[0] == args.get(i).eval(env)[0]) return new float[]{1f};
                    }
                    return new float[]{0f};
                }
                case "smooth": {
                    int id = (int) args.get(0).eval(env)[0];
                    float v = args.get(1).eval(env)[0];
                    float up = args.size() > 2 ? args.get(2).eval(env)[0] : 1f;
                    float down = args.size() > 3 ? args.get(3).eval(env)[0] : up;
                    return new float[]{ env.smooth(id, v, up, down) };
                }
                case "vec2": case "vec3": case "vec4": {
                    int n = fn.charAt(3) - '0';
                    float[] out = new float[n];
                    int k = 0;
                    for (Node a : args) {
                        for (float f : a.eval(env)) { if (k < n) out[k++] = f; }
                    }
                    if (k == 1) for (int i = 1; i < n; i++) out[i] = out[0]; // vec3(x) broadcast
                    return out;
                }
                case "random":
                    return new float[]{ (float) Math.random() };
                default:
                    break;
            }
            float[][] a = new float[args.size()][];
            int n = 1;
            for (int i = 0; i < a.length; i++) { a[i] = args.get(i).eval(env); n = Math.max(n, a[i].length); }
            float[] out = new float[n];
            for (int i = 0; i < n; i++) {
                float[] c = new float[a.length];
                for (int j = 0; j < a.length; j++) c[j] = a[j][a[j].length == 1 ? 0 : i];
                out[i] = scalarFn(fn, c);
            }
            return out;
        }

        private static float scalarFn(String fn, float[] c) {
            switch (fn) {
                case "sin": return (float) Math.sin(c[0]);
                case "cos": return (float) Math.cos(c[0]);
                case "tan": return (float) Math.tan(c[0]);
                case "asin": return (float) Math.asin(c[0]);
                case "acos": return (float) Math.acos(c[0]);
                case "atan": return c.length > 1 ? (float) Math.atan2(c[0], c[1]) : (float) Math.atan(c[0]);
                case "atan2": return (float) Math.atan2(c[0], c[1]);
                case "radians": return (float) Math.toRadians(c[0]);
                case "degrees": return (float) Math.toDegrees(c[0]);
                case "exp": return (float) Math.exp(c[0]);
                case "log": return (float) Math.log(c[0]);
                case "exp2": return (float) Math.pow(2.0, c[0]);
                case "log2": return (float) (Math.log(c[0]) / Math.log(2.0));
                case "sqrt": return (float) Math.sqrt(c[0]);
                case "abs": return Math.abs(c[0]);
                case "sign": return Math.signum(c[0]);
                case "floor": return (float) Math.floor(c[0]);
                case "ceil": return (float) Math.ceil(c[0]);
                case "round": return Math.round(c[0]);
                case "frac": case "fract": return c[0] - (float) Math.floor(c[0]);
                case "mod": return c[1] == 0f ? 0f : c[0] - c[1] * (float) Math.floor(c[0] / c[1]);
                case "min": { float m = c[0]; for (float f : c) m = Math.min(m, f); return m; }
                case "max": { float m = c[0]; for (float f : c) m = Math.max(m, f); return m; }
                case "clamp": return Math.max(c[1], Math.min(c[2], c[0]));
                case "mix": case "lerp": return c[0] + (c[1] - c[0]) * c[2];
                case "step": return c[1] < c[0] ? 0f : 1f;
                case "smoothstep": {
                    float t = Math.max(0f, Math.min(1f, (c[2] - c[0]) / (c[1] - c[0])));
                    return t * t * (3f - 2f * t);
                }
                case "pow": return (float) Math.pow(c[0], c[1]);
                case "between": return c[0] >= c[1] && c[0] <= c[2] ? 1f : 0f;
                case "equals": return Math.abs(c[0] - c[1]) <= (c.length > 2 ? c[2] : 0f) ? 1f : 0f;
                case "not": return c[0] != 0f ? 0f : 1f;
                default: throw new IllegalArgumentException("unknown function '" + fn + "'");
            }
        }
    }

    static boolean truthy(float[] v) { return v.length > 0 && v[0] != 0f; }

    // --------------------------------------------------------------- parser

    static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        Node parseExpr() { return parseOr(); }

        private Node parseOr() {
            Node a = parseAnd();
            while (peek("||")) { i += 2; a = new Binary("||", a, parseAnd()); }
            return a;
        }

        private Node parseAnd() {
            Node a = parseCmp();
            while (peek("&&")) { i += 2; a = new Binary("&&", a, parseCmp()); }
            return a;
        }

        private Node parseCmp() {
            Node a = parseAdd();
            for (String op : new String[]{"==", "!=", "<=", ">=", "<", ">"}) {
                if (peek(op)) { i += op.length(); return new Binary(op, a, parseAdd()); }
            }
            return a;
        }

        private Node parseAdd() {
            Node a = parseMul();
            while (true) {
                skipWs();
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    String op = String.valueOf(s.charAt(i++));
                    a = new Binary(op, a, parseMul());
                } else return a;
            }
        }

        private Node parseMul() {
            Node a = parseUnary();
            while (true) {
                skipWs();
                if (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/' || s.charAt(i) == '%')) {
                    String op = String.valueOf(s.charAt(i++));
                    a = new Binary(op, a, parseUnary());
                } else return a;
            }
        }

        private Node parseUnary() {
            skipWs();
            if (i < s.length()) {
                char c = s.charAt(i);
                if (c == '-' || c == '!') { i++; return new Unary(c, parseUnary()); }
                if (c == '+') { i++; return parseUnary(); }
            }
            return parsePostfix();
        }

        private Node parsePostfix() {
            Node a = parsePrimary();
            while (true) {
                skipWs();
                if (i < s.length() && s.charAt(i) == '.' && i + 1 < s.length() && Character.isLetter(s.charAt(i + 1))) {
                    i++;
                    int start = i;
                    while (i < s.length() && Character.isLetter(s.charAt(i))) i++;
                    String sw = s.substring(start, i);
                    int[] idx = new int[sw.length()];
                    for (int k = 0; k < sw.length(); k++) {
                        switch (sw.charAt(k)) {
                            case 'x': case 'r': case 's': idx[k] = 0; break;
                            case 'y': case 'g': case 't': idx[k] = 1; break;
                            case 'z': case 'b': case 'p': idx[k] = 2; break;
                            case 'w': case 'a': case 'q': idx[k] = 3; break;
                            default: throw error("bad swizzle '" + sw + "'");
                        }
                    }
                    a = new Swizzle(a, idx);
                } else return a;
            }
        }

        private Node parsePrimary() {
            skipWs();
            if (i >= s.length()) throw error("unexpected end of expression");
            char c = s.charAt(i);
            if (c == '(') {
                i++;
                Node inner = parseExpr();
                expect(')');
                return inner;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                // exponent
                if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    int save = i;
                    i++;
                    if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                    if (i < s.length() && Character.isDigit(s.charAt(i))) {
                        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                    } else i = save;
                }
                // GLSL-style suffixes some packs carry over (1.0f, 2F)
                if (i < s.length() && (s.charAt(i) == 'f' || s.charAt(i) == 'F')) i++;
                String num = s.substring(start, i).replaceAll("[fF]$", "");
                try {
                    return new Const(Float.parseFloat(num));
                } catch (NumberFormatException e) {
                    throw error("bad number '" + num + "'");
                }
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                String name = s.substring(start, i);
                skipWs();
                if (i < s.length() && s.charAt(i) == '(') {
                    i++;
                    List<Node> args = new ArrayList<>();
                    skipWs();
                    if (i < s.length() && s.charAt(i) == ')') { i++; }
                    else {
                        while (true) {
                            args.add(parseExpr());
                            skipWs();
                            if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                            expect(')');
                            break;
                        }
                    }
                    String fn = name.toLowerCase(Locale.ROOT);
                    validateFunction(fn, args.size());
                    return new Call(fn, args);
                }
                if (name.equals("true")) return new Const(1f);
                if (name.equals("false")) return new Const(0f);
                return new Ident(name);
            }
            throw error("unexpected '" + c + "'");
        }

        private void validateFunction(String fn, int argc) {
            switch (fn) {
                case "if": if (argc < 3 || argc % 2 == 0) throw error("if() needs cond,value pairs plus an else"); return;
                case "in": if (argc < 2) throw error("in() needs a value and candidates"); return;
                case "smooth": if (argc < 2 || argc > 4) throw error("smooth(id, value[, fadeUp[, fadeDown]])"); return;
                case "vec2": case "vec3": case "vec4": if (argc < 1) throw error(fn + "() needs arguments"); return;
                case "random": return;
                case "sin": case "cos": case "tan": case "asin": case "acos": case "radians": case "degrees":
                case "exp": case "log": case "exp2": case "log2": case "sqrt": case "abs": case "sign":
                case "floor": case "ceil": case "round": case "frac": case "fract": case "not":
                    if (argc != 1) throw error(fn + "() takes one argument"); return;
                case "atan": if (argc < 1 || argc > 2) throw error("atan() takes one or two arguments"); return;
                case "atan2": case "mod": case "pow": case "step":
                    if (argc != 2) throw error(fn + "() takes two arguments"); return;
                case "min": case "max": if (argc < 2) throw error(fn + "() needs at least two arguments"); return;
                case "clamp": case "mix": case "lerp": case "smoothstep": case "between":
                    if (argc != 3) throw error(fn + "() takes three arguments"); return;
                case "equals": if (argc < 2 || argc > 3) throw error("equals(a, b[, epsilon])"); return;
                default: throw error("unknown function '" + fn + "'");
            }
        }

        void expectEnd() {
            skipWs();
            if (i != s.length()) throw error("trailing input '" + s.substring(i) + "'");
        }

        private void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c) throw error("expected '" + c + "'");
            i++;
        }

        private boolean peek(String tok) {
            skipWs();
            return s.startsWith(tok, i);
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at " + i + " in \"" + s + "\"");
        }
    }
}
