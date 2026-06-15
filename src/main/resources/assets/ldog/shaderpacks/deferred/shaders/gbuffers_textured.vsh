#version 120

// LDOG "Deferred" built-in — textured gbuffer (entities, clouds, hand, etc.).
varying vec4 vColor;
varying vec2 vUv;
varying vec2 vLm;

void main() {
    gl_Position = ftransform();
    vColor = gl_Color;
    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
    vLm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
}
