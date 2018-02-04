precision mediump float;

uniform sampler2D uSampler;
uniform float uContrast;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4((pixel.rgb - vec3(0.5)) * uContrast + vec3(0.5), pixel.a);
}
