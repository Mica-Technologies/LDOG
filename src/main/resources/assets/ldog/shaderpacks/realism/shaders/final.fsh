#version 120

// LDOG built-in pack: "Realism"
// A crisp, natural grade aimed at a clean photographic look: a light
// unsharp-mask for micro-detail, balanced contrast, a small saturation
// nudge, gentle warm white balance, and a soft vignette to draw the eye.

uniform sampler2D colortex0;
uniform vec2 invMainSize;
varying vec2 texcoord;

void main() {
    vec3 c = texture2D(colortex0, texcoord).rgb;

    // Unsharp mask: subtract a 4-tap box blur to accentuate edges.
    vec3 blur = texture2D(colortex0, texcoord + vec2( invMainSize.x, 0.0)).rgb
              + texture2D(colortex0, texcoord + vec2(-invMainSize.x, 0.0)).rgb
              + texture2D(colortex0, texcoord + vec2(0.0,  invMainSize.y)).rgb
              + texture2D(colortex0, texcoord + vec2(0.0, -invMainSize.y)).rgb;
    blur *= 0.25;
    c += (c - blur) * 0.45;

    // Saturation + contrast.
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, 1.18);
    c = (c - 0.5) * 1.08 + 0.5;

    // Subtle warm white balance (lift reds, trim blues a hair).
    c *= vec3(1.03, 1.0, 0.97);

    // Soft vignette.
    vec2 d = texcoord - 0.5;
    float vig = smoothstep(0.95, 0.35, dot(d, d) * 2.0);
    c *= mix(0.82, 1.0, vig);

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
