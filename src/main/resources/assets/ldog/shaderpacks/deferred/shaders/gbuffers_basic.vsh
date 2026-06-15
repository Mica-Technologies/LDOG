#version 120

// LDOG "Deferred" built-in — basic (untextured) gbuffer.
varying vec4 vColor;

void main() {
    gl_Position = ftransform();
    vColor = gl_Color;
}
