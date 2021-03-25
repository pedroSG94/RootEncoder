precision mediump float;

uniform sampler2D uSampler;
uniform sampler2D uObject;
uniform float uSensitive;

varying vec2 vTextureCoord;
varying vec2 vTextureObjectCoord;

void main() {
    vec4 samplerPixel = texture2D(uSampler, vTextureCoord);
    vec4 objectPixel = texture2D(uObject, vTextureObjectCoord);

    float maxrb = max(samplerPixel.r, samplerPixel.b);
    float k = clamp((samplerPixel.g - maxrb) * uSensitive, 0.0, 1.0);

    float dg = samplerPixel.g;
    samplerPixel.g = min(samplerPixel.g, maxrb * 0.9);
    samplerPixel += dg - samplerPixel.g;

    gl_FragColor = vec4(mix(samplerPixel, objectPixel, k).rgb, 1.0);
}