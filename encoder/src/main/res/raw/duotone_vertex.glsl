attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
uniform vec3 uColor;
uniform vec3 uColor2;

varying vec2 vTextureCoord;
varying vec3 vColor;
varying vec3 vColor2;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
  vColor = uColor;
  vColor2 = uColor2;
}