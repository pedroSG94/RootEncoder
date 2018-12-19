precision mediump float;

uniform sampler2D uSampler;
uniform sampler2D uSnow;
uniform float uTime;

varying vec2 vTextureCoord;

/*
	Returns a texel of snowflake.
*/
vec4 getSnow(vec2 pos) {
	// Get a texel of the noise image.
	vec3 texel;
	if (pos.x < 0.0 || pos.x > 1.0 || pos.y < 0.0 || pos.y > 1.0) {
	  texel = vec3(0.0, 0.0, 0.0);
	} else {
	  texel = texture2D(uSnow, pos).rgb;
	}
	// Only use extremely bright values.
	texel = smoothstep(0.6, 1.0, texel);
	// Okay how can this give a transparent rgba value?
	return vec4(texel, 1.0);
}

/*
	Provides a 2D vector with which to warp the sampling location
	of the snow_flakes texture.
*/
vec2 warpSpeed(float time, float gravity, vec2 pos) {
	// Do some things to stretch out the timescale based on 2D position and actual time.
	return vec2(-time * 5.55 + sin(pos.x * 10.0 * sin(time * 0.2)) * 0.4,
  time * gravity + sin(pos.y * 10.0 * sin(time * 0.4)) * 0.4);
}

vec4 getSnowField(vec2 pos) {
	// Just some not-so-magic inversely related values.
	// That's all they are.
	return getSnow(pos - 0.75 + uTime * 0.25) +
	       getSnow(pos - 0.5 + uTime * 0.25) +
	       getSnow(pos - 0.25 + uTime * 0.25) +
         getSnow(pos - 0.0 + uTime * 0.25);
}

void main() {
	gl_FragColor = max(texture2D(uSampler, vTextureCoord), getSnowField(vTextureCoord));
}