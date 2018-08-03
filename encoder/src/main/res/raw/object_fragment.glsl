precision mediump float;

uniform sampler2D uSampler;
uniform sampler2D uObject;
uniform float uAlpha;

varying vec2 vTextureCoord;
varying vec2 vTextureObjectCoord;

void main() {
  vec4 samplerPixel = texture2D(uSampler, vTextureCoord);
  vec4 objectPixel = texture2D(uObject, vec2(1.0 - vTextureObjectCoord.y, 1.0 - vTextureObjectCoord.x));
  gl_FragColor = mix(samplerPixel, objectPixel, objectPixel.a * uAlpha);
}