#version 120

// LDOG built-in pack — shared fullscreen-triangle vertex stage.
varying vec2 texcoord;

void main() {
    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
    texcoord = gl_Vertex.xy * 0.5 + 0.5;
}
