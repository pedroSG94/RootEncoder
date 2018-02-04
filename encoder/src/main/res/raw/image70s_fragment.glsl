precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;

void main() {
  vec4 texColor = texture2D(uSampler, vTextureCoord);
  float avg = (texColor.r + texColor.g + texColor.b) / 3.0; // grayscale
	texColor.r *= abs(cos(avg));
  texColor.g *= abs(sin(avg));
  texColor.b *= abs(atan(avg) * sin(avg));
  gl_FragColor = texColor;
}