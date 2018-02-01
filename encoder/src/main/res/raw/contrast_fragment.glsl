precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying float vContrast;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4((pixel.rgb - vec3(0.5)) * vContrast + vec3(0.5), pixel.a);
}
