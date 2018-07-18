precision mediump float;

uniform sampler2D uSampler;
uniform vec3 uRGBSaturation;

varying vec2 vTextureCoord;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  gl_FragColor = vec4(pixel.r * uRGBSaturation.r, pixel.g * uRGBSaturation.g, pixel.b * uRGBSaturation.b, 1.0);
}
