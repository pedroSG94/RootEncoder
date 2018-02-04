precision mediump float;

uniform sampler2D uSampler;
uniform float uGamma;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4(pow(pixel.rgb, vec3(uGamma)), pixel.a);
}
