precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;

varying vec2 vTextureCoord;

void main() {
  float phase = uTime * 0.5;
	float levels = 8.0;
	vec4 tx = texture2D(uSampler, vTextureCoord);
	vec4 x = tx;
	x = mod(x + phase, 1.0);
	x = floor(x * levels);
	x = mod(x, 2.0);
	gl_FragColor= vec4(vec3(x), tx.a);
}
