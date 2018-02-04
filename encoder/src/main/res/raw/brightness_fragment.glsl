precision mediump float;

uniform sampler2D uSampler;
uniform float uBrightness;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4(pixel.rgb + vec3(uBrightness), pixel.a);
}
