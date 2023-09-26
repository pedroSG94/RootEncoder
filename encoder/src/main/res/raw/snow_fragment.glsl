precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uTime;

varying vec2 vTextureCoord;

#define pi 3.1415926

vec2 hash(vec2 p) {
	p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
	return fract(sin(p) * 18.5453);
}

float simplegridnoise(vec2 v) {
	float s = 1.0 / 256.;
	vec2 fl = floor(v), fr = fract(v);
	float mindist = 1e9;
	for(int y = -1; y <= 1; y++) {
		for(int x = -1; x <= 1; x++) {
			vec2 offset = vec2(x, y);
			vec2 pos = 0.5 + 0.5 * cos(2.0 * pi * (uTime * 0.1 + hash(fl+offset)) + vec2(0, 1.6));
			mindist = min(mindist, length(pos+offset -fr));
		}
	}
	return mindist;
}

float blobnoise(vec2 v, float s) {
	return pow(0.5 + 0.5 * cos(pi * clamp(simplegridnoise(v) * 2.0, 0.0, 1.0)), s);
}

float fractalblobnoise(vec2 v, float s) {
	float val = 0.0;
	const float n = 4.0;
	for(float i = 0.0; i < n; i++) {
		val += pow(0.5, i + 1.0) * blobnoise(exp2(i) * v + vec2(0, uTime), s);
	}
	return val;
}

void main() {
	vec2 r = vec2(1.0, uResolution.y / uResolution.x);
	vec2 uv = vTextureCoord.xy / uResolution.xy;
	float val = fractalblobnoise(r * vTextureCoord * 20.0, 5.0); //more snowflakes r * uv * 40.0, 1.25
	gl_FragColor = mix(texture2D(uSampler, vTextureCoord), vec4(1.0), vec4(val));
}