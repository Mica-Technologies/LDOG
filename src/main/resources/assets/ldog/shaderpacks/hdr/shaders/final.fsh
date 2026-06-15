#version 120

// LDOG built-in pack: "HDR"
// A filmic tone-grade that lifts the scene into a punchy high-dynamic-range
// look — ACES tonemap, a touch of exposure, and a gentle highlight glow
// drawn straight from the framebuffer (no MRT / gbuffer needed).

uniform sampler2D colortex0;   // scene colour
uniform vec2 invMainSize;      // 1 / screen size
varying vec2 texcoord;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;

    // Cheap bright-pass glow: average a small cross of taps, keep only the
    // part above a threshold, add it back for a soft highlight bloom.
    vec3 b = texture2D(colortex0, texcoord + vec2( 2.0, 0.0) * invMainSize).rgb
           + texture2D(colortex0, texcoord + vec2(-2.0, 0.0) * invMainSize).rgb
           + texture2D(colortex0, texcoord + vec2( 0.0, 2.0) * invMainSize).rgb
           + texture2D(colortex0, texcoord + vec2( 0.0,-2.0) * invMainSize).rgb;
    b *= 0.25;
    float bl = max(dot(b, vec3(0.2126, 0.7152, 0.0722)) - 0.6, 0.0);
    col += b * bl * 1.3;

    // Exposure + filmic tonemap.
    col *= 1.28;
    col = aces(col);

    // Saturation + contrast lift so the HDR look reads clearly vivid.
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(l), col, 1.35);
    col = (col - 0.5) * 1.12 + 0.5;

    gl_FragColor = vec4(col, 1.0);
}
