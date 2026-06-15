#version 120

// LDOG "Realism" — final stage. colortex0 holds the deferred-lit scene; apply a
// filmic tonemap + gentle saturation/contrast and a soft vignette.

uniform sampler2D colortex0;
varying vec2 texcoord;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 c = texture2D(colortex0, texcoord).rgb;

    c = aces(c * 1.05);

    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, 1.12);              // saturation
    c = (c - 0.5) * 1.05 + 0.5;             // contrast

    vec2 d = texcoord - 0.5;
    c *= mix(0.82, 1.0, smoothstep(0.9, 0.3, dot(d, d) * 2.0));  // vignette

    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
