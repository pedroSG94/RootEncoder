precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;

varying vec2 vTextureCoord;

#define posterSteps 4.0
#define lumaMult 0.5
#define timeMult 0.15
#define BW 0

float rgbToGray(vec4 rgba) {
	const vec3 W = vec3(0.2125, 0.7154, 0.0721);
  return dot(rgba.xyz, W);
}

vec3 hsv2rgb(vec3 c) {
  vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  float luma = rgbToGray(color) * lumaMult;
  float lumaIndex = floor(luma * posterSteps);
  float lumaFloor = lumaIndex / posterSteps;
  float lumaRemainder = (luma - lumaFloor) * posterSteps;
  if(mod(lumaIndex, 2.) == 0.) lumaRemainder = 1.0 - lumaRemainder; // flip luma remainder for smooth color transitions
  float lumaCycle = mod(luma + uTime * timeMult, 1.);
  vec3 roygbiv = hsv2rgb(vec3(lumaCycle, 1., lumaRemainder));
  if(BW == 1) {
    float bw = rgbToGray(vec4(roygbiv, 1.));
    gl_FragColor = vec4(vec3(bw), 1.0);
  } else {
    gl_FragColor = vec4(roygbiv, 1.0);
  }
}

