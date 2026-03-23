precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;
varying vec2 vTextureCoord;

vec3 mod289(highp vec3 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec2 mod289(highp vec2 x) {
    return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec3 permute(highp vec3 x) {
    return mod289(((x * 34.0) + 1.0) * x);
}

float snoise(highp vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);

    highp vec2 i = floor(v + dot(v, C.yy));
    highp vec2 x0 = v - i + dot(i, C.xx);

    vec2 i1;
    i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;

    i = mod289(i);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0 ))
    + i.x + vec3(0.0, i1.x, 1.0 ));

    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;

    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;

    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);

    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

float staticV(vec2 uv) {
    float staticHeight = snoise(vec2(9.0, uTime * 1.2 + 3.0)) * 0.3 + 5.0;
    float staticAmount = snoise(vec2(1.0, uTime * 1.2 - 6.0)) * 0.1 + 0.3;
    float staticStrength = snoise(vec2(-9.75, uTime * 0.6 - 3.0)) * 2.0 + 2.0;
    return (1.0 - step(snoise(vec2(5.0 * pow(uTime, 2.0) + pow(uv.x * 7.0, 1.2), pow((mod(uTime, 100.0) + 100.0) * uv.y * 0.3 + 3.0, staticHeight))), staticAmount)) * staticStrength;
}


void main() {

    float jerkOffset = (1.0 - step(snoise(vec2(uTime * 1.3, 5.0)), 0.8)) * 0.05;
    float fuzzOffset = snoise(vec2(uTime * 15.0, vTextureCoord.y * 80.0)) * 0.003;
    float largeFuzzOffset = snoise(vec2(uTime * 1.0, vTextureCoord.y * 25.0)) * 0.004;
    float vertJerk = (1.0 - step(snoise(vec2(uTime * 1.5, 5.0)), 0.6));
    float vertJerk2 = (1.0 - step(snoise(vec2(uTime * 5.5, 5.0)), 0.2));
    float yOffset = vertJerk * vertJerk2 * 0.3;
    float y = mod(vTextureCoord.y + yOffset, 1.0);
    float xOffset = fuzzOffset + largeFuzzOffset;

    float staticVal = 0.0;
    for (float y = -1.0; y <= 1.0; y += 1.0) {
        float maxDist = 5.0 / 200.0;
        float dist = y / 200.0;
        staticVal += staticV(vec2(vTextureCoord.x , vTextureCoord.y + dist)) * (maxDist - abs(dist)) * 1.5;
    }

    float red = texture2D(uSampler, vec2(vTextureCoord.x + xOffset - 0.01, y)).r + staticVal;
    float green = texture2D(uSampler, vec2(vTextureCoord.x + xOffset, y)).g + staticVal;
    float blue = texture2D(uSampler, vec2(vTextureCoord.x + xOffset + 0.01, y)).b + staticVal;

    vec3 color = vec3(red, green, blue);
    float scanline = sin(vTextureCoord.y * 800.0) * 0.04;
    color -= scanline;

    gl_FragColor = vec4(color, 1.0);
}
