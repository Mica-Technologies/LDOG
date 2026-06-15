#version 120

// LDOG "Deferred" built-in — terrain gbuffer fragment stage.
// Writes lit + shadowed albedo to colortex0 and the encoded normal to
// colortex1, demonstrating MRT output and shadow-map sampling.

uniform sampler2D texture;     // block atlas (unit 0)
uniform sampler2D lightmap;    // unit 1
uniform sampler2D shadowtex0;  // sun-POV depth (unit 9 when Pack Shadows on)

varying vec4 vColor;
varying vec2 vUv;
varying vec2 vLm;
varying vec3 vNormal;
varying vec4 vShadowPos;

/* DRAWBUFFERS:01 */
void main() {
    vec4 albedo = texture2D(texture, vUv) * vColor;
    if (albedo.a < 0.1) discard;

    vec3 lit = albedo.rgb * texture2D(lightmap, vLm).rgb;

    // Shadow lookup: project to [0,1], compare against the stored depth.
    vec3 sc = vShadowPos.xyz / vShadowPos.w;
    sc = sc * 0.5 + 0.5;
    float shade = 1.0;
    if (sc.x > 0.0 && sc.x < 1.0 && sc.y > 0.0 && sc.y < 1.0 && sc.z < 1.0) {
        float d = texture2D(shadowtex0, sc.xy).r;
        if (sc.z - 0.0015 > d) shade = 0.5;   // occluded → darken
    }
    lit *= shade;

    gl_FragData[0] = vec4(lit, albedo.a);
    gl_FragData[1] = vec4(vNormal * 0.5 + 0.5, 1.0);
}
