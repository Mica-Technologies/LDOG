#version 120

// LDOG "Realism" — basic (untextured) gbuffer fragment stage. Marked as full
// skylight so any untextured geometry is lit by the sky in the composite.
varying vec4 vColor;

/* DRAWBUFFERS:01 */
void main() {
    gl_FragData[0] = vColor;
    gl_FragData[1] = vec4(0.0, 1.0, 0.0, 1.0);
}
