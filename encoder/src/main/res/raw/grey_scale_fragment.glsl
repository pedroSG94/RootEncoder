precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  float grey = pixel.r + pixel.g + pixel.b / 3.0;
  gl_FragColor = vec4(grey, grey, grey, 1.0);
}
