precision mediump float;

uniform sampler2D uSampler;
uniform float uPixelated;

varying vec2 vTextureCoord;

void main() {
  vec2 coord = vec2(uPixelated * floor(vTextureCoord.x / uPixelated), uPixelated * floor(vTextureCoord.y / uPixelated));
  gl_FragColor = texture2D(uSampler, coord);
}
