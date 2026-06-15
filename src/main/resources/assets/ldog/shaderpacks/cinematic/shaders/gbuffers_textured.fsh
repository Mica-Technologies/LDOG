#version 120

// LDOG "Realism" — textured gbuffer fragment stage.
uniform sampler2D texture;

varying vec4 vColor;
varying vec2 vUv;
varying vec2 vLm;

/* DRAWBUFFERS:01 */
void main() {
    vec4 a = texture2D(texture, vUv) * vColor;
    if (a.a < 0.1) discard;
    gl_FragData[0] = vec4(a.rgb, 1.0);
    gl_FragData[1] = vec4(clamp(vLm, 0.0, 1.0), 0.0, 1.0);
}
