#version 120

// LDOG "Deferred" built-in — basic (untextured) gbuffer fragment stage.
varying vec4 vColor;

/* DRAWBUFFERS:0 */
void main() {
    gl_FragData[0] = vColor;
}
