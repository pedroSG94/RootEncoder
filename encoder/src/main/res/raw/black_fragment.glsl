precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  //Ignore color and set black color. I think this is not the best way
  gl_FragColor = vec4(0.0, 0.0, 0.0, pixel.a);
}
