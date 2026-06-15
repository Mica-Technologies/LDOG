#version 120

// LDOG "Deferred" built-in — textured gbuffer fragment stage.
uniform sampler2D texture;
uniform sampler2D lightmap;

varying vec4 vColor;
varying vec2 vUv;
varying vec2 vLm;

/* DRAWBUFFERS:0 */
void main() {
    vec4 albedo = texture2D(texture, vUv) * vColor;
    if (albedo.a < 0.1) discard;
    vec3 lit = albedo.rgb * texture2D(lightmap, vLm).rgb;
    gl_FragData[0] = vec4(lit, albedo.a);
}
