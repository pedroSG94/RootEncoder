precision mediump float;

uniform sampler2D uSampler;
uniform sampler2D uObject;
uniform float uAlpha;

varying vec2 vTextureCoord;
varying vec2 vTextureObjectCoord;

void main() {
  vec4 samplerPixel = texture2D(uSampler, vTextureCoord);
  vec4 objectPixel = texture2D(uObject, vTextureObjectCoord);
  if (vTextureObjectCoord.x < 0.0 || vTextureObjectCoord.x > 1.0 ||
  vTextureObjectCoord.y < 0.0 || vTextureObjectCoord.y > 1.0) {
    gl_FragColor = samplerPixel;
  } else {
    gl_FragColor = mix(samplerPixel, objectPixel, objectPixel.a * uAlpha);
  }
}