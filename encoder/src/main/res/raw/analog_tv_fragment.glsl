precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uTime;

varying vec2 vTextureCoord;

float rand(vec2 co) {
    return fract(sin(dot(co.xy , vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = vTextureCoord;
    uv -= 0.5;

    // Take video pixel
    vec3 col = texture2D(uSampler, (uv + 0.5)).rgb;

    // Glitch color
    vec2 ruv = uv;
    ruv.x += 0.02;
    col.rgb += texture2D(uSampler, (ruv + 0.5)).rgb * 0.1;

    // Color noise
    col += rand(fract(floor((ruv + uTime) * uResolution.y) * 0.7)) * 0.2;

    // Make small lines
    col *= clamp(fract(uv.y * 100.0 + uTime * 8.0), 0.8, 1.0);

    // Make big lines
    float bf = fract(uv.y * 3.0 + uTime * 26.0);
    float ff = min(bf, 1.0 - bf) + 0.35;
    col *= clamp(ff, 0.5, 0.75) + 0.75;

    // Make low Hz
    col *= (sin(uTime * 120.0) * 0.5 + 0.5) * 0.1 + 0.9;

    // Make borders
    col *= smoothstep(-0.51, -0.50, uv.x) * smoothstep(0.51, 0.50, uv.x);
    col *= smoothstep(-0.51, -0.50, uv.y) * smoothstep(0.51, 0.50, uv.y);

    gl_FragColor = vec4(col, 1.0);
}
