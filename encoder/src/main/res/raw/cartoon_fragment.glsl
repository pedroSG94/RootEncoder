precision mediump float;

uniform sampler2D uSampler;
uniform float uCartoon;

varying vec2 vTextureCoord;

#define PI 3.1415927

void main(){
	vec3 t = texture2D(uSampler, vTextureCoord).rgb;
	vec3 t00 = texture2D(uSampler, vTextureCoord + vec2(-uCartoon, -uCartoon)).rgb;
	vec3 t10 = texture2D(uSampler, vTextureCoord + vec2(uCartoon, -uCartoon)).rgb;
	vec3 t01 = texture2D(uSampler, vTextureCoord + vec2(-uCartoon, uCartoon)).rgb;
	vec3 t11 = texture2D(uSampler, vTextureCoord + vec2(uCartoon, uCartoon)).rgb;
	vec3 tm = (t00 + t01 + t10 + t11) / 4.0;
	t = t - tm;
	t = t * t * t;
	vec3 v = 10000.0 * t;
	float g = (tm.x - 0.3) * 5.0;
	vec3 col0 = vec3(0.0, 0.0, 0.0);
	vec3 col1 = vec3(0.2, 0.5, 1.0);
	vec3 col2 = vec3(1.0, 0.8, 0.7);
	vec3 col3 = vec3(1.0, 1.0, 1.0);
	vec3 c;
	if (g > 2.0) c = mix(col2, col3, g - 2.0);
	else if (g > 1.0) c = mix(col1, col2, g - 1.0);
	else c = mix(col0, col1, g);
	c = clamp(c, 0.0, 1.0);
	v = clamp(v, 0.0, 1.0);
	v = c * (1.0 - v);
	v = clamp(v, 0.0, 1.0);
	if (v == col0) v = col3;
	gl_FragColor = vec4(v, 1.0);
}
