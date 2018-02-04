precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uBlur;

varying vec2 vTextureCoord;

float sCurve(float x) {
  x = x * 2.0 - 1.0;
  return -x * abs(x) * 0.5 + x + 0.5;
}

void main() {
  vec4 A = vec4(0.0);
	vec4 C = vec4(0.0);
	float width = 1.0 / uResolution.x;
	float divisor = 0.0;
  float weight = 0.0;
  float radiusMultiplier = 1.0 / uBlur;
  for (float x = -20.0; x <= 20.0; x++)	{
    A = texture2D(uSampler, vTextureCoord + vec2(x * width, 0.0));
    weight = sCurve(1.0 - (abs(x) * radiusMultiplier));
    C += A * weight;
		divisor += weight;
  }
  gl_FragColor = vec4(C.rgb / divisor, 1.0);
}