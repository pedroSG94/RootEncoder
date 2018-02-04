precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  vec3 ncolor = vec3(0.0, 0.0, 0.0);
  float value;
  if (color.r < 0.5) value = color.r;
  else value = 1.0 - color.r;
  float red = 4.0 * value * value * value;
  if (color.r < 0.5) ncolor.r = red;
  else ncolor.r = 1.0 - red;
  if (color.g < 0.5) value = color.g;
  else value = 1.0 - color.g;
  float green = 2.0 * value * value;
  if (color.g < 0.5) ncolor.g = green;
  else ncolor.g = 1.0 - green;
  ncolor.b = color.b * 0.5 + 0.25;
  gl_FragColor = vec4(ncolor.rgb, color.a);
}