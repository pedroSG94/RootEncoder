precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;
uniform float uLevels;

varying vec2 vTextureCoord;

void main() {
  float phase = uTime * 0.5;
	vec4 tx = texture2D(uSampler, vTextureCoord);
	vec4 x = tx;
	x = mod(x + phase, 1.0);
	x = floor(x * uLevels);
	x = mod(x, 2.0);
	gl_FragColor= vec4(vec3(x), tx.a);
}
