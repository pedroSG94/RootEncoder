precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;

varying vec2 vTextureCoord;

mat3 saturationMatrix(float saturation) {
  vec3 luminance = vec3(0.3086, 0.6094, 0.0820);
  float oneMinusSat = 1.0 - saturation;
  vec3 red = vec3(luminance.x * oneMinusSat);
  red.r += saturation;

  vec3 green = vec3(luminance.y * oneMinusSat);
  green.g += saturation;

  vec3 blue = vec3(luminance.z * oneMinusSat);
  blue.b += saturation;

  return mat3(red, green, blue);
}

void levels(inout vec3 col, in vec3 inleft, in vec3 inright, in vec3 outleft, in vec3 outright) {
  col = clamp(col, inleft, inright);
  col = (col - inleft) / (inright - inleft);
  col = outleft + col * (outright - outleft);
}

void main() {
  vec3 col = texture2D(uSampler, vTextureCoord).rgb;
  vec2 fragCoord = vTextureCoord * uResolution;
  vec2 coord = (fragCoord + fragCoord -  uResolution) / uResolution.y;
  vec3 gradient = vec3(pow(1.0 - length(coord * 0.4), 0.6) * 1.2);
  vec3 grey = vec3(184.0 / 255.0);
  vec3 tint = vec3(252.0, 243.0, 213.0) / 255.0;
  col = saturationMatrix(0.68) * col;
  levels(col, vec3(0.0), vec3(1.0), vec3(27.0, 0.0, 0.0) / 255.0, vec3(255.0) / 255.0);
  col = pow(col, vec3(1.19));
  //brightnessAdjust
  col += 0.13;
  //contrastAdjust
  float t = 0.5 - 1.05 * 0.5;
  col = col * 1.05 + t;

  col = saturationMatrix(0.85) * col;
  levels(col, vec3(0.0), vec3(235.0 / 255.0), vec3(0.0, 0.0, 0.0) / 255.0, vec3(255.0) / 255.0);
  col = mix(tint * col, col, gradient);
  col = 1.0 - (1.0 - col) / grey; //colorBurn
  gl_FragColor = vec4(col, 1.0);
}