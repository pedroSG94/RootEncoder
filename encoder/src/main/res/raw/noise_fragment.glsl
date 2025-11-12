precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;
uniform float uStrength;

varying vec2 vTextureCoord;

float hash(highp vec2 p) {
  p = fract(p * 0.3183099) * 50.0;
  return fract(p.x * p.y * (p.x + p.y));
}

void main() {
  vec2 seed = vTextureCoord * 100.0 + uTime;
  float r = hash(seed) * 0.01 - 0.005;
  gl_FragColor = texture2D(uSampler, vTextureCoord) + vec4(r) * uStrength;
}