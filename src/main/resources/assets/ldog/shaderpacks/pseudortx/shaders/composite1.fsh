#version 120

// LDOG "Pseudo-RTX" — screen-space ambient occlusion + contact shadows.
//
// Not real ray tracing (impossible on MC 1.12.2's GL 2.1), but a depth-buffer
// approximation of the look: samples a ring of neighbours and darkens pixels
// whose surroundings sit closer to the camera (crevices, block edges, contact
// points), with a cool ambient tint in the occluded areas.

uniform sampler2D colortex0;   // lit scene
uniform sampler2D depthtex0;
uniform vec2 invMainSize;
uniform float near;
uniform float far;
varying vec2 texcoord;

float linearize(float z) {
    return (2.0 * near) / (far + near - z * (far - near));
}

void main() {
    vec3 col = texture2D(colortex0, texcoord).rgb;
    float zc = linearize(texture2D(depthtex0, texcoord).r);

    float ao = 0.0;
    if (zc < 0.999) {
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 0.5235987756;          // i * pi/6
            float r = 1.5 + mod(float(i), 3.0) * 1.5;    // a couple of radii
            vec2 off = vec2(cos(a), sin(a)) * invMainSize * r;
            float zn = linearize(texture2D(depthtex0, texcoord + off).r);
            ao += clamp((zc - zn) * 5.0, 0.0, 1.0);
        }
        ao = clamp(ao / 12.0, 0.0, 1.0);
        ao *= ao;                                        // tighten to creases
    }

    col *= mix(1.0, 0.45, ao * 0.9);
    col = mix(col, col * vec3(0.8, 0.88, 1.15), ao * 0.45);  // cool ambient bounce

    gl_FragData[0] = vec4(col, 1.0);
}
