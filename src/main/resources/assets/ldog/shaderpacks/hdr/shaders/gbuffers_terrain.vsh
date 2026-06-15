#version 120

// LDOG "Realism" — terrain/textured gbuffer vertex stage. Passes albedo UV +
// the vanilla lightmap coordinate through; the lighting itself is deferred to
// the composite pass (which reconstructs normals from the depth buffer).
varying vec4 vColor;
varying vec2 vUv;
varying vec2 vLm;

void main() {
    gl_Position = ftransform();
    vColor = gl_Color;
    vUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
    vLm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
}
