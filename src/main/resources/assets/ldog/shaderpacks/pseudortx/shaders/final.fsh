#version 120

// LDOG "Pseudo-RTX" — final stage. colortex0 holds the deferred-lit + AO scene.
// A crisp, high-contrast, slightly cool "rendered" look with a tight highlight
// bloom and a light sharpen.

uniform sampler2D colortex0;
uniform vec2 invMainSize;
varying vec2 texcoord;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;

    // Light unsharp-mask for a crisp, rendered edge.
    vec3 blur = texture2D(colortex0, texcoord + vec2( invMainSize.x, 0.0)).rgb
              + texture2D(colortex0, texcoord + vec2(-invMainSize.x, 0.0)).rgb
              + texture2D(colortex0, texcoord + vec2(0.0,  invMainSize.y)).rgb
              + texture2D(colortex0, texcoord + vec2(0.0, -invMainSize.y)).rgb;
    col += (col - blur * 0.25) * 0.35;

    // Tight highlight bloom.
    float hi = max(dot(col, vec3(0.2126, 0.7152, 0.0722)) - 0.75, 0.0);
    col += col * hi * 0.4;

    col = aces(col * 1.08);

    // Cool, contrasty grade.
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(l), col, 1.2);
    col = (col - 0.5) * 1.14 + 0.5;
    col *= vec3(0.97, 1.0, 1.05);

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
