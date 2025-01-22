precision highp float;

uniform sampler2D uSampler;
uniform float uTime;
uniform float uLayers;
uniform float uDepth;
uniform float uWidth;
uniform float uSpeed;

varying vec2 vTextureCoord;

const mat3 p = mat3(13.323122, 23.5112, 21.71123, 21.1212, 28.7312, 11.9312, 21.8112, 14.7212, 61.3934);

void main() {
	vec3 acc = vec3(0.0);
	float dof = 5.0 * sin(uTime * 0.1);

	for (float i = 0.0; i < uLayers; i++) {
		vec2 q = vTextureCoord * (1.0 + i * uDepth);
		q += vec2(q.y * (uWidth * mod(i * 7.238917, 1.0) - uWidth * 0.5), uSpeed * uTime / (1.0 + i * uDepth * 0.03));
		vec3 n = vec3(floor(q), 31.189 + i);
		vec3 m = floor(n) * 0.00001 + fract(n);
		vec3 mp = (31415.9 + m ) / fract(p * m);
		vec3 r = fract(mp);
		vec2 s = abs(mod(q, 1.0) - 0.5 + 0.9 * r.xy - 0.45);
		s += 0.01 * abs(2.0 * fract(10.0 * q.yx) - 1.0);
		float d = .6 * max(s.x - s.y, s.x + s.y) + max(s.x, s.y) - 0.01;
		float edge = 0.005 + 0.05 * min(0.5 * abs(i - 5.0 - dof), 1.0);
		acc += vec3(smoothstep(edge, -edge, d) * (r.x / (1.0 + 0.02 * i * uDepth)));
	}
	gl_FragColor = texture2D(uSampler, vTextureCoord) + vec4(vec3(acc), 1.0);
}