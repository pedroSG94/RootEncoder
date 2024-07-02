precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uSharpness;

varying vec2 vTextureCoord;

void main() {
  vec4 up = texture2D(uSampler, (vTextureCoord + vec2(0, 1) / uResolution));
  vec4 left = texture2D(uSampler, (vTextureCoord + vec2(-1, 0) / uResolution));
  vec4 center = texture2D(uSampler, vTextureCoord);
  vec4 right = texture2D(uSampler, (vTextureCoord + vec2(1, 0) / uResolution));
  vec4 down = texture2D(uSampler, (vTextureCoord + vec2(0, -1) / uResolution));

  gl_FragColor = (1.0 + 4.0 * uSharpness) * center - uSharpness * (up + left + right + down);
}
