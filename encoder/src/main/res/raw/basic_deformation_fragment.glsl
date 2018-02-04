precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;

varying vec2 vTextureCoord;

void main() {
	float waveu = sin((vTextureCoord.y + uTime) * 20.0) * 0.5 * 0.05 * 0.3;
  gl_FragColor = texture2D(uSampler, vTextureCoord + vec2(waveu, 0));
}
