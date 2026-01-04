precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying vec2 vLeftCoord;
varying vec2 vRightCoord;
varying vec2 vTopCoord;
varying vec2 vBottomCoord;

uniform vec3 uEdgeColor;
uniform vec3 uBackgroundColor;

void main() {
  float h0 = length(texture2D(uSampler, vLeftCoord).rgb);
  float h1 = length(texture2D(uSampler, vRightCoord).rgb);
  float v0 = length(texture2D(uSampler, vTopCoord).rgb);
  float v1 = length(texture2D(uSampler, vBottomCoord).rgb);

  float edge = length(vec2(h1 - h0, v1 - v0));

  vec4 bg;
  if (uBackgroundColor.r < 0.0 || uBackgroundColor.g < 0.0 || uBackgroundColor.b < 0.0) {
    bg = texture2D(uSampler, vTextureCoord);
  } else {
    bg = vec4(uBackgroundColor, 1.0);
  }
  gl_FragColor = mix(bg, vec4(uEdgeColor, 1.0), step(0.06, edge));
}