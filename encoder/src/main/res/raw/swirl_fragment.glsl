precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;
uniform vec2 uResolution;
uniform float uRadius;
uniform vec2 uCenter;

varying vec2 vTextureCoord;

#define PI 3.14159

void main() {
  float effectRadius = 0.2;
  float effectAngle = 2.0 * PI * uTime;
  vec2 uv = vTextureCoord - uCenter;
  float len = length(uv * vec2(uResolution.x / uResolution.y, 1.0));
  float angle = atan(uv.y, uv.x) + effectAngle * smoothstep(uRadius, 0.0, len);
  float radius = length(uv);

  gl_FragColor = texture2D(uSampler, vec2(radius * cos(angle), radius * sin(angle)) + uCenter);
}