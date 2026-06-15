#version 120

// LDOG built-in pack: "Pseudo-RTX"
// Not real ray tracing (impossible on MC 1.12.2's GL 2.1 pipeline) — a
// screen-space approximation of the look: depth-based ambient occlusion adds
// soft contact shadows in crevices, a cool ambient bounce tints occluded
// areas, and a filmic tonemap finishes it. Reads the scene depth buffer, so
// it needs the Post-Process Pipeline enabled.

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform vec2 invMainSize;
uniform float near;
uniform float far;
varying vec2 texcoord;

float linearize(float z) {
    return (2.0 * near) / (far + near - z * (far - near));
}

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;
    float zc = linearize(texture2D(depthtex0, texcoord).r);

    // Sky / far plane: skip AO so the horizon stays clean.
    float ao = 0.0;
    if (zc < 0.999) {
        // Sample a ring; where neighbours sit closer to the camera than this
        // pixel, treat it as occluded geometry casting a contact shadow.
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7853981634;          // i * pi/4
            vec2 off = vec2(cos(a), sin(a)) * invMainSize * 3.0;
            float zn = linearize(texture2D(depthtex0, texcoord + off).r);
            ao += clamp((zc - zn) * 6.0, 0.0, 1.0);
        }
        ao = clamp(ao / 8.0, 0.0, 1.0);
        ao *= ao;                                        // bias toward tight creases
    }

    // Darken occluded pixels and tint them with a cool ambient bounce.
    col *= mix(1.0, 0.5, ao * 0.85);
    col = mix(col, col * vec3(0.82, 0.9, 1.18), ao * 0.4);

    // Filmic finish + a touch of contrast.
    col = aces(col * 1.05);
    col = (col - 0.5) * 1.05 + 0.5;

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
