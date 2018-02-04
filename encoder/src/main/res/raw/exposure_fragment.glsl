precision mediump float;

uniform sampler2D uSampler;
uniform float uExposure;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4(pixel.rgb * pow(2.0, uExposure), pixel.a);
}