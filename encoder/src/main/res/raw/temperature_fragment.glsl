precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying float vTemperature;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  pixel.r = pixel.r + pixel.r * (1.0 - pixel.r) * vTemperature;
  pixel.b = pixel.b - pixel.b * (1.0 - pixel.b) * vTemperature;
  if (vTemperature > 0.0) pixel.g = pixel.g + pixel.g * (1.0 - pixel.g) * vTemperature * 0.25;
  float value = max(pixel.r, max(pixel.g, pixel.b));
  if (value > 1.0) pixel.rgb /= value;
  gl_FragColor = pixel;
}