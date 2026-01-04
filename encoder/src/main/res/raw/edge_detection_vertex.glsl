attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

varying vec2 vTextureCoord;

varying vec2 vLeftCoord;
varying vec2 vRightCoord;
varying vec2 vTopCoord;
varying vec2 vBottomCoord;

uniform float uPixelSize;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = (uSTMatrix * aTextureCoord).xy;

  vLeftCoord = vTextureCoord + vec2(-uPixelSize, 0.0);
  vRightCoord = vTextureCoord + vec2(uPixelSize, 0.0);
  vTopCoord = vTextureCoord + vec2(0.0, -uPixelSize);
  vBottomCoord = vTextureCoord + vec2(0.0, uPixelSize);
}