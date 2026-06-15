#version 120

// LDOG "Deferred" — composite stage 0: bright-pass extract -> colortex1.
// Demonstrates a multi-write composite: it writes a different buffer than it
// reads, and composite1 reads the result back.

uniform sampler2D colortex0;
varying vec2 texcoord;

/* DRAWBUFFERS:1 */
void main() {
    vec3 c = texture2D(colortex0, texcoord).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 bright = c * max(l - 0.6, 0.0);
    gl_FragData[0] = vec4(bright, 1.0);   // -> colortex1
}
