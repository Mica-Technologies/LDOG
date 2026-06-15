#version 120

// LDOG "Realism" — volumetric sun shafts (god rays).
//
// Screen-space radial light scattering: project the sun to screen space, then
// from each pixel march toward the sun accumulating "open sky" samples (where
// the depth buffer shows the far plane). The result is bright shafts radiating
// from the sun wherever the path to it is unoccluded — strongest when looking
// toward the sun, fading at the screen edges and at night.

uniform sampler2D colortex0;   // lit scene
uniform sampler2D depthtex0;

uniform mat4 gbufferModelView;
uniform mat4 gbufferProjection;
uniform vec4 sunPosition;
uniform float rainStrength;

varying vec2 texcoord;

const int SAMPLES = 48;

void main() {
    vec3 scene = texture2D(colortex0, texcoord).rgb;

    vec3 sunDirWorld = normalize(sunPosition.xyz);
    float day = clamp(sunDirWorld.y * 3.0, 0.0, 1.0);
    if (day <= 0.0) { gl_FragData[0] = vec4(scene, 1.0); return; }

    // Sun position in screen space.
    vec4 sunClip = gbufferProjection * gbufferModelView * vec4(sunDirWorld * 1000.0, 1.0);
    if (sunClip.w <= 0.0) { gl_FragData[0] = vec4(scene, 1.0); return; }  // behind camera
    vec2 sunScreen = sunClip.xy / sunClip.w * 0.5 + 0.5;

    // Fade out as the sun leaves the screen.
    vec2 edge = abs(sunScreen - 0.5);
    float onScreen = (1.0 - smoothstep(0.5, 0.95, max(edge.x, edge.y)));
    if (onScreen <= 0.0) { gl_FragData[0] = vec4(scene, 1.0); return; }

    // March from this pixel toward the sun, accumulating open-sky samples.
    vec2 delta = (sunScreen - texcoord) / float(SAMPLES);
    vec2 uv = texcoord;
    float accum = 0.0;
    float decay = 1.0;
    for (int i = 0; i < SAMPLES; i++) {
        uv += delta;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;
        float d = texture2D(depthtex0, uv).r;
        accum += step(0.9999, d) * decay;   // sky => light passes
        decay *= 0.97;
    }
    accum /= float(SAMPLES);

    // Brighter the closer you look to the sun.
    float toward = 1.0 - clamp(length(sunScreen - texcoord) * 1.2, 0.0, 1.0);
    vec3 sunTint = mix(vec3(1.0, 0.6, 0.35), vec3(1.0, 0.95, 0.8),
                       smoothstep(0.05, 0.3, sunDirWorld.y));

    vec3 rays = sunTint * accum * onScreen * day * (0.35 + 0.65 * toward);
    rays *= (1.0 - rainStrength * 0.6) * 1.4;

    gl_FragData[0] = vec4(scene + rays, 1.0);
}
