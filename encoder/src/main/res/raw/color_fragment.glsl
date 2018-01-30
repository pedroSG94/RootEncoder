precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying vec3 vColor;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  float grey = pixel.r + pixel.g + pixel.b / 3.0;
  vec3 average = vec3(grey, grey, grey);
  vec4 color = vec4(average.rgb * vColor, 1.0);
  gl_FragColor = color;
}
