#version 120

// LDOG "Cinematic" — final stage. colortex0 holds the deferred-lit scene.
// The classic teal-and-orange film grade: shadows toward teal, highlights
// toward warm orange, a filmic curve, gentle desaturation, a soft edge
// blur (faux depth-of-field at the frame edges), and a strong vignette.

uniform sampler2D colortex0;
uniform vec2 invMainSize;
varying vec2 texcoord;

void main() {
    vec3 c = texture2D(colortex0, texcoord).rgb;

    // Faux DoF: blend a small blur in toward the frame edges for a cinematic
    // focus falloff (keeps the center sharp).
    vec2 d = texcoord - 0.5;
    float edge = smoothstep(0.15, 0.5, length(d));
    if (edge > 0.01) {
        vec3 blur = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            float a = float(i) * 1.0471975512;
            blur += texture2D(colortex0, texcoord + vec2(cos(a), sin(a)) * invMainSize * 2.5).rgb;
        }
        c = mix(c, blur / 6.0, edge * 0.6);
    }

    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));

    // Teal/orange split tone.
    vec3 shadowTint = vec3(0.05, 0.42, 0.55);
    vec3 highTint   = vec3(1.05, 0.62, 0.25);
    vec3 tone = mix(shadowTint, highTint, smoothstep(0.05, 0.95, l));
    c = mix(c, c * tone * 1.8, 0.42);

    // Filmic curve + slight desaturation.
    c = mix(vec3(l), c, 0.92);
    c = c / (c + 0.16) * 1.16;
    c = (c - 0.5) * 1.07 + 0.5;

    // Strong vignette.
    c *= mix(0.6, 1.0, smoothstep(1.0, 0.2, dot(d, d) * 2.0));

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
