precision mediump float;

uniform sampler2D uSampler;
uniform vec3 uColor;
uniform vec3 uColor2;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  float grey = pixel.r + pixel.g + pixel.b / 3.0;
  gl_FragColor = vec4((1.0 - grey) * uColor + grey * uColor2, pixel.a);
}
