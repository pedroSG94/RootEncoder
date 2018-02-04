#extension GL_OES_standard_derivatives : enable
precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  float gray = length(color.rgb);
  gl_FragColor = vec4(vec3(step(0.06, length(vec2(dFdx(gray), dFdy(gray))))), 1.0);
}