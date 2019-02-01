precision mediump float;

uniform sampler2D uSampler;
uniform float uRadius;
uniform vec2 uCenter;
uniform vec2 uResolution;

varying vec2 vTextureCoord;

void main() {
  vec2 uv;
  vec2 center;
  if (uResolution.x < uResolution.y) {
    float scale = uResolution.y / uResolution.x;
    uv = vec2(vTextureCoord.x, vTextureCoord.y * scale);
    center = vec2(uCenter.x, uCenter.y * scale);
  } else {
    float scale = uResolution.x / uResolution.y;
    uv = vec2(vTextureCoord.x * scale, vTextureCoord.y);
    center = vec2(uCenter.x * scale, uCenter.y);
  }

  float distance = length(uv - center);
  if (distance < uRadius) gl_FragColor = texture2D(uSampler, vTextureCoord);
}