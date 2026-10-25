#version 150

uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform vec3 SunDir;
uniform float SunLight;
uniform float FadeStart;
uniform float FadeEnd;

in vec4 vertexColor;
in vec3 normal;
in float vertexDistance;

out vec4 fragColor;

// Lit from the sky above and the sun's side, darker underneath; fading into the fog colour, then out, with distance.
void main() {
    vec3 n = normalize(normal);
    float sky = 0.62 + 0.22 * (n.y * 0.5 + 0.5);
    float sun = max(dot(n, SunDir), 0.0) * SunLight;
    vec3 color = min(vertexColor.rgb * (sky + 0.3 * sun), vec3(1.0)) * ColorModulator.rgb;
    float fade = smoothstep(FadeStart, FadeEnd, vertexDistance);
    color = mix(color, FogColor.rgb, fade * 0.8);
    fragColor = vec4(color, vertexColor.a * ColorModulator.a * (1.0 - fade));
}
