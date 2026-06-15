#version 120

// LDOG "Realism" — deferred lighting pass.
//
// Reconstructs world position + a per-pixel surface normal from the depth
// buffer (MC 1.12.2 terrain has no vertex normals), then applies directional
// sunlight with shadows, sky ambient, and warm block (torch) light. Writes the
// lit result back to colortex0; the final stage tonemaps it.

uniform sampler2D colortex0;       // albedo
uniform sampler2D colortex1;       // lightmap (r=block, g=sky)
uniform sampler2D depthtex0;       // scene depth
uniform sampler2DShadow shadowtex0;

uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowModelView;
uniform vec4 sunPosition;
uniform float rainStrength;

varying vec2 texcoord;

vec3 reconstructWorld(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = gbufferProjectionInverse * ndc;
    view /= view.w;
    return (gbufferModelViewInverse * view).xyz;
}

void main() {
    float depth = texture2D(depthtex0, texcoord).r;
    vec3 albedo = texture2D(colortex0, texcoord).rgb;

    // Sky / far plane — already the sky colour, leave it alone.
    if (depth >= 1.0) {
        gl_FragData[0] = vec4(albedo, 1.0);
        return;
    }

    vec2 lm = texture2D(colortex1, texcoord).rg;
    float blockLight = lm.r;
    float skyLight = lm.g;

    vec3 worldPos = reconstructWorld(texcoord, depth);
    // Geometric normal from screen-space derivatives of world position.
    vec3 normal = normalize(cross(dFdx(worldPos), dFdy(worldPos)));
    // Make it face the camera (camera is the origin in this camera-relative space).
    if (dot(normal, normalize(-worldPos)) < 0.0) normal = -normal;

    vec3 sunDir = normalize(sunPosition.xyz);
    float dayFactor = clamp(sunDir.y * 1.4 + 0.25, 0.0, 1.0);
    float NdotL = max(dot(normal, sunDir), 0.0);

    // Shadow lookup (compare-mode depth; reads "lit" when shadows are off).
    float shade = 1.0;
    vec4 sc = shadowProjection * shadowModelView * vec4(worldPos, 1.0);
    sc.xyz = sc.xyz / sc.w * 0.5 + 0.5;
    if (sc.x > 0.0 && sc.x < 1.0 && sc.y > 0.0 && sc.y < 1.0 && sc.z < 1.0) {
        shade = shadow2D(shadowtex0, vec3(sc.xy, sc.z - 0.0008)).x;
    }

    // Light terms.
    vec3 sunColor = mix(vec3(1.0, 0.55, 0.3), vec3(1.0, 0.96, 0.88), smoothstep(0.0, 0.25, sunDir.y));
    sunColor *= (1.0 - rainStrength * 0.7);
    vec3 skyAmbient = mix(vec3(0.10, 0.13, 0.20), vec3(0.45, 0.62, 0.85), dayFactor);

    vec3 sunlight = NdotL * shade * skyLight * sunColor * dayFactor * 1.5;
    vec3 ambient  = skyAmbient * (skyLight * 0.85 + 0.05);
    vec3 torch    = blockLight * blockLight * vec3(1.0, 0.55, 0.25) * 1.6;
    vec3 light    = ambient + sunlight + torch + 0.025;

    gl_FragData[0] = vec4(albedo * light, 1.0);
}
