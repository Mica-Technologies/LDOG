#version 120

// LDOG "Deferred" — composite stage 1: blur the bright-pass from colortex1
// and add it back into the scene (colortex0). Reading colortex1 here proves
// the colortex flip from composite stage 0 landed.

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform vec2 invMainSize;
varying vec2 texcoord;

/* DRAWBUFFERS:0 */
void main() {
    vec3 scene = texture2D(colortex0, texcoord).rgb;
    vec3 b = texture2D(colortex1, texcoord).rgb * 0.4;
    b += texture2D(colortex1, texcoord + vec2( 2.0, 0.0) * invMainSize).rgb * 0.15;
    b += texture2D(colortex1, texcoord - vec2( 2.0, 0.0) * invMainSize).rgb * 0.15;
    b += texture2D(colortex1, texcoord + vec2( 0.0, 2.0) * invMainSize).rgb * 0.15;
    b += texture2D(colortex1, texcoord - vec2( 0.0, 2.0) * invMainSize).rgb * 0.15;
    gl_FragData[0] = vec4(scene + b * 0.8, 1.0);   // -> colortex0
}
