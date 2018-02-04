precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;

varying vec2 vTextureCoord;

vec2 texel_size = vec2(6.0, 6.0);

void main() {
  vec2 fragCoord = vTextureCoord * uResolution;
  fragCoord = floor(fragCoord / texel_size);	// Pixelify
  fragCoord /= uResolution / texel_size;	// Correct scale
  float reaction_coordinate = texture2D(uSampler, fragCoord).r;	// Use red channel
  float mixval = ((reaction_coordinate - 0.55) * 10.0 + 0.5) * 2.0;
  gl_FragColor = vec4(mix(vec3(1.0, 0.58, 0.0), vec3(1.0, 0.7, 0.4), mixval), reaction_coordinate);
  gl_FragColor.rgb = vec3(1.0, 0.2, 0.0);	// Red
  if (gl_FragColor.a > 0.65) gl_FragColor.rgb = vec3(1.0, 1.0, 1.0);	// White
  else if (gl_FragColor.a > 0.37) gl_FragColor.rgb = vec3(1.4, 0.8, 0.0);	// Yellow
  gl_FragColor.a = float(gl_FragColor.a > 0.1);
}
