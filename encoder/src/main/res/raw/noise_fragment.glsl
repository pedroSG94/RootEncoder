precision highp float;

uniform sampler2D uSampler;
uniform float uTime;
uniform float uStrength;

varying vec2 vTextureCoord;

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  float x = (vTextureCoord.x + 4.0) * (vTextureCoord.y + 4.0) * (uTime * 10.0);
  vec4 grain = vec4(mod((mod(x, 13.0) + 1.0) * (mod(x, 123.0) + 1.0), 0.01) - 0.005) * uStrength;
  gl_FragColor = color + grain;
}