#version 120

// LDOG built-in pack — shared fullscreen-triangle vertex stage.
// The composite runner draws a triangle whose gl_Vertex.xy is already in
// NDC ([-1,3]), so we pass it straight through and derive the [0,1] sample
// coordinate from it (no gl_MultiTexCoord dependency).
varying vec2 texcoord;

void main() {
    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
    texcoord = gl_Vertex.xy * 0.5 + 0.5;
}
