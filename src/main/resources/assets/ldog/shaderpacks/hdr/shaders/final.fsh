#version 120

// LDOG "HDR" — final stage. colortex0 holds the deferred-lit + god-ray scene.
// A bright, punchy high-dynamic-range look: a wide bright-pass bloom, exposure
// lift, vivid saturation, and an ACES filmic tonemap.

uniform sampler2D colortex0;
uniform vec2 invMainSize;
varying vec2 texcoord;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;

    // Wide bright-pass bloom: average a ring of taps, keep the part above a
    // threshold, add it back for a soft glow on bright surfaces.
    vec3 b = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.7853981634;
        vec2 o = vec2(cos(a), sin(a)) * invMainSize * 3.0;
        b += texture2D(colortex0, texcoord + o).rgb;
    }
    b *= 0.125;
    float bl = max(dot(b, vec3(0.2126, 0.7152, 0.0722)) - 0.55, 0.0);
    col += b * bl * 1.6;

    // Exposure + filmic tonemap.
    col = aces(col * 1.25);

    // Vivid saturation + a touch of contrast.
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(l), col, 1.35);
    col = (col - 0.5) * 1.1 + 0.5;

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
