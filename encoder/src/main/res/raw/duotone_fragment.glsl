precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying vec3 vColor;
varying vec3 vColor2;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  float grey = pixel.r + pixel.g + pixel.b / 3.0;
  gl_FragColor = vec4((1.0 - grey) * vColor + grey * vColor2, pixel.a);
}
