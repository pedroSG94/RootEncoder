precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying float vExposure;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4(pixel.rgb * pow(2.0, vExposure), pixel.a);
}