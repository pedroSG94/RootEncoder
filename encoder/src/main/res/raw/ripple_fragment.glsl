precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uSpeed;
uniform float uTime;

varying vec2 vTextureCoord;

void main() {
  vec2 cPos = -1.0 + 2.0 * vTextureCoord;
  float ratio = uResolution.x / uResolution.y;
  cPos.x *= ratio;
  float cLength = length(cPos);
  vec2 uv = vTextureCoord + (cPos / cLength) * cos(cLength * uSpeed - uTime * uSpeed) * 0.03;
  gl_FragColor = texture2D(uSampler, uv);
}