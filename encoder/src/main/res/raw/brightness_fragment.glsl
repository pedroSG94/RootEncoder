precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying float vBrightness;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4(pixel.rgb + vec3(vBrightness), pixel.a);
}
