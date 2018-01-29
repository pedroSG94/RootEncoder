precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;

void main() {
  vec3 negative = 1.0 - texture2D(uSampler, vTextureCoord).rgb;
  gl_FragColor = vec4(negative, 1.0);
}
