precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying float vPixelated;

void main() {
  vec2 coord = vec2(vPixelated * floor(vTextureCoord.x / vPixelated), vPixelated * floor(vTextureCoord.y / vPixelated));
  gl_FragColor = texture2D(uSampler, coord);
}
