#version 120

// LDOG "Deferred" built-in — terrain gbuffer vertex stage.
// Computes the camera-relative world position and projects it into shadow
// space so the fragment stage can look up the shadow map directly.

uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;

varying vec4 vColor;
varying vec2 vUv;
varying vec2 vLm;
varying vec3 vNormal;
varying vec4 vShadowPos;

void main() {
    vec4 eye = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * eye;

    vColor  = gl_Color;
    vUv     = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
    vLm     = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
    vNormal = normalize(gl_NormalMatrix * gl_Normal);

    // eye -> camera-relative world -> light clip space.
    vec4 worldPos = gbufferModelViewInverse * eye;
    vShadowPos = shadowProjection * shadowModelView * worldPos;
}
