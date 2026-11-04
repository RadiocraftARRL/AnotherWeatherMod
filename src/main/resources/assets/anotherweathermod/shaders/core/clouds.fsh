#version 150

uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform vec3 SunDir;
uniform float SunLight;
uniform float FadeStart;
uniform float FadeEnd;
// Lightning in the cloud: x and z from the camera, glow radius, brightness (0 for an empty slot).
uniform vec4 Flash0;
uniform vec4 Flash1;
uniform vec4 Flash2;
uniform vec4 Flash3;
// How much of the day's own cloud (fair-weather cumulus, a rainy day's grey) shows, 0 to 1: it fades out and back
// in when the sort of day changes, instead of popping.
uniform float DayFade;
// How far this tile's changed cells are through dissolving from their old cloud into their new, 0 to 1.
uniform float CellFade;

in vec4 vertexColor;
in vec3 normal;
in float vertexDistance;
in vec3 relative;

out vec4 fragColor;

// How much a flash lights this spot: brightest at its middle, nothing past its radius, the underside and the
// flanks most (the flash is inside the cloud, lighting it from within and below).
float flash(vec4 f, vec3 n) {
    if (f.w <= 0.0) return 0.0;
    float d = length(relative.xz - f.xy) / f.z;
    return f.w * (1.0 - smoothstep(0.15, 1.0, d)) * (0.75 - 0.25 * n.y);
}

// Lit from the sky above and the sun's side, darker underneath; lightning glowing inside; fading into the fog
// colour, then out, with distance.
// A 4x4 ordered dither, 0 to 1: fading by leaving out more and more pixels keeps the clouds opaque, so nothing behind
// shows through the wrong way while they fade.
float bayer(vec2 p) {
    int x = int(mod(p.x, 4.0)), y = int(mod(p.y, 4.0));
    int m[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    return (float(m[y * 4 + x]) + 0.5) / 16.0;
}

void main() {
    // The vertex alpha byte is kind * 4 + role (CloudMesh): kind 0 fair-weather, 1 storm, 2 the grey deck; role 0
    // steady, 1 fading in, 2 fading out. Clouds themselves are opaque.
    int mark = int(vertexColor.a * 255.0 + 0.5);
    int kind = mark / 4, role = mark - kind * 4;
    float dither = bayer(gl_FragCoord.xy);
    // A changed cell's new cloud takes the pixels its old one gives up, so it dissolves from one to the other.
    if (role == 1 && dither >= CellFade || role == 2 && dither < CellFade) discard;
    // Fair-weather cloud and the grey deck fade with the day; storm cloud does not.
    if (kind != 1 && dither >= DayFade) discard;
    vec3 n = normalize(normal);
    float sky = 0.62 + 0.22 * (n.y * 0.5 + 0.5);
    float sun = max(dot(n, SunDir), 0.0) * SunLight;
    vec3 color = min(vertexColor.rgb * (sky + 0.3 * sun), vec3(1.0)) * ColorModulator.rgb;
    float glow = flash(Flash0, n) + flash(Flash1, n) + flash(Flash2, n) + flash(Flash3, n);
    color = mix(color, vec3(0.86, 0.88, 1.0), clamp(glow, 0.0, 0.9));
    // Storm cloud and the grey deck stay solid further out and grey less into the fog, so a storm shows on the horizon
    // as it comes in.
    float storm = kind == 0 ? 0.0 : 1.0;
    float fade = smoothstep(mix(FadeStart, FadeEnd * 0.85, storm), FadeEnd, vertexDistance);
    color = mix(color, FogColor.rgb, fade * mix(0.8, 0.55, storm));
    fragColor = vec4(color, ColorModulator.a * (1.0 - fade));
}
