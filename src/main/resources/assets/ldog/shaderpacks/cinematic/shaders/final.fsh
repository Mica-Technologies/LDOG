#version 120

// LDOG built-in pack: "Cinematic"
// The classic teal-and-orange movie grade — shadows pushed toward teal,
// highlights toward warm orange — with a filmic curve, slight desaturation,
// and a strong vignette for a moody, framed look.

uniform sampler2D colortex0;
varying vec2 texcoord;

void main() {
    vec3 c = texture2D(colortex0, texcoord).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));

    // Split-tone: blend a teal shadow tint and an orange highlight tint by
    // luminance, then mix it over the original at moderate strength.
    vec3 shadowTint = vec3(0.05, 0.42, 0.58);
    vec3 highTint   = vec3(1.05, 0.60, 0.22);
    vec3 tone = mix(shadowTint, highTint, smoothstep(0.05, 0.95, l));
    c = mix(c, c * tone * 1.8, 0.48);

    // Slight desaturation for the filmic feel, then a soft contrast curve.
    c = mix(vec3(l), c, 0.9);
    c = c / (c + 0.16) * 1.16;
    c = (c - 0.5) * 1.06 + 0.5;

    // Pronounced vignette.
    vec2 d = texcoord - 0.5;
    float vig = smoothstep(1.0, 0.22, dot(d, d) * 2.0);
    c *= mix(0.62, 1.0, vig);

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
