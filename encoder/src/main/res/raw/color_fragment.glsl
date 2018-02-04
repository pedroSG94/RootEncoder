precision mediump float;

uniform sampler2D uSampler;
uniform vec3 uColor;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  float grey = pixel.r + pixel.g + pixel.b / 3.0;
  vec3 average = vec3(grey, grey, grey);
  vec4 color = vec4(average.rgb * uColor, 1.0);
  gl_FragColor = color;
}
