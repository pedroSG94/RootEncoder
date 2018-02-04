precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uMode;
uniform float uRows;
uniform float uRotation;
uniform float uAntialias;
uniform vec2 uSampleDist;

varying vec2 vTextureCoord;

float rgbToGray(vec4 rgba) {
  const vec3 W = vec3(0.2125, 0.7154, 0.0721);
  return dot(rgba.xyz, W);
}

float avgerageGray(vec2 uv, float stepX, float stepY) {
	// get samples around pixel
	vec4 colors[9];
	colors[0] = texture2D(uSampler,uv + vec2(-stepX, stepY));
	colors[1] = texture2D(uSampler,uv + vec2(0, stepY));
	colors[2] = texture2D(uSampler,uv + vec2(stepX, stepY));
	colors[3] = texture2D(uSampler,uv + vec2(-stepX, 0));
	colors[4] = texture2D(uSampler,uv);
	colors[5] = texture2D(uSampler,uv + vec2(stepX, 0));
	colors[6] = texture2D(uSampler,uv + vec2(-stepX, -stepY));
	colors[7] = texture2D(uSampler,uv + vec2(0, -stepY));
	colors[8] = texture2D(uSampler,uv + vec2(stepX, -stepY));
	// sum + return averaged gray
  vec4 result = vec4(0);
	for (int i = 0; i < 9; i++) {
		result += colors[i];
	}
	return rgbToGray(result) / 9.0;
}

vec2 rotateCoord(vec2 uv, float rads) {
  uv *= mat2(cos(rads), sin(rads), -sin(rads), cos(rads));
	return uv;
}

void main() {
  // halftone line coords
  vec2 uvRow = fract(rotateCoord(vTextureCoord, uRotation) * uRows);
  vec2 uvFloorY = vec2(vTextureCoord.x, floor(vTextureCoord.y * uRows) / uRows) + vec2(0., (1.0 / uRows) * 0.5); // add y offset to get center row color
  // get averaged gray for row
  float averagedBW = avgerageGray(uvFloorY, uSampleDist.x/uResolution.x, uSampleDist.y/uResolution.y);
  // use averaged gray to set line thickness
  vec3 finalColor = vec3(1.);
  if(uMode == 1.) {
    if(uvRow.y > averagedBW) finalColor = vec3(0.0);
  } else if(uMode == 2.) {
    if(distance(uvRow.y + 0.5, averagedBW * 2.) < 0.2) finalColor = vec3(0.0);
  } else if(uMode == 3.) {
    float distFromRowCenter = 1.0 - distance(uvRow.y, 0.5) * 2.0;
    finalColor = vec3(1.0 - smoothstep(averagedBW - uAntialias, averagedBW + uAntialias, distFromRowCenter));
  } else if(uMode == 4.) {
    vec2 uvRow2 = fract(rotateCoord(vTextureCoord, -uRotation) * uRows);
    float distFromRowCenter1 = 1.0 - distance(uvRow.y, 0.5) * 2.0;
    float distFromRowCenter2 = 1.0 - distance(uvRow2.y, 0.5) * 2.0;
    float distFromRowCenter = min(distFromRowCenter1, distFromRowCenter2);
    finalColor = vec3(1.0 - smoothstep(averagedBW - uAntialias, averagedBW + uAntialias, distFromRowCenter));
  } else if(uMode == 5.) {
    vec2 uvRow2 = fract(rotateCoord(vTextureCoord, -uRotation) * uRows);
    float distFromRowCenter1 = 1.0 - distance(uvRow.y, 0.5) * 2.0;
    float distFromRowCenter2 = 1.0 - distance(uvRow2.y, 0.5) * 2.0;
    float distFromRowCenter = mix(distFromRowCenter1, distFromRowCenter2, 0.5);
    finalColor = vec3(1.0 - smoothstep(averagedBW - uAntialias, averagedBW + uAntialias, distFromRowCenter));
  } else if(uMode == 6.) {
    float rot = floor(averagedBW * 6.28) / 6.28;
    rot = rot * 4.;
    vec2 uvRow = fract(rotateCoord(vTextureCoord, rot) * uRows);
    float distFromRowCenter = 1.0 - distance(uvRow.y, 0.5) * 2.0;
    finalColor = vec3(1.0 - smoothstep(averagedBW - uAntialias, averagedBW + uAntialias, distFromRowCenter));
  } else if(uMode == 7.) {
    vec4 originalColor = texture2D(uSampler, uvFloorY);
    float distFromRowCenter = 1.0 - distance(uvRow.y, 0.5) * 2.0;
		float mixValue = 1.0 - smoothstep(averagedBW - uAntialias, averagedBW + uAntialias, distFromRowCenter);
    finalColor = mix(originalColor.rgb, vec3(1.), mixValue);
  }
  // draw
	gl_FragColor = vec4(finalColor, 1.0);
}