#version 120

// LDOG "Deferred" built-in — final stage. colortex0 holds the lit + shadowed
// scene from the gbuffer pass, with the composite chain's bloom added back in.
// A light filmic tonemap finishes it.

uniform sampler2D colortex0;
varying vec2 texcoord;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;
    col = aces(col * 1.04);
    gl_FragColor = vec4(col, 1.0);
}
