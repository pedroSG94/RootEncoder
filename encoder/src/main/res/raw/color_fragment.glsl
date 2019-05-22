precision mediump float;

uniform sampler2D uSampler;
uniform vec3 uColor;

varying vec2 vTextureCoord;
const vec3 luma = vec3(0.299, 0.587, 0.114);

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  float grey = dot(pixel.rgb, luma);
  gl_FragColor = vec4(grey * uColor, pixel.a);
}
