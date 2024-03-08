precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;
uniform float uLevels;

varying vec2 vTextureCoord;

void main() {
  float phase = uTime * 0.5;
  vec4 color = texture2D(uSampler, vTextureCoord);
  vec4 tempColor = color;
  tempColor = mod(tempColor + phase, 1.0);
  tempColor = floor(tempColor * uLevels);
  tempColor = mod(tempColor, 2.0);
  gl_FragColor = vec4(vec3(tempColor), color.a);
}
