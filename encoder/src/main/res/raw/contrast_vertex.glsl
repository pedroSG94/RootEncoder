attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
uniform float uContrast;

varying vec2 vTextureCoord;
varying float vContrast;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
  vContrast = uContrast;
}