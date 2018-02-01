precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying float vGamma;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4(pow(pixel.rgb, vec3(vGamma)), pixel.a);
}
