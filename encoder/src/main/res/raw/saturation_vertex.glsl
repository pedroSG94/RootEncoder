attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
uniform float uShift;
uniform vec3 uWeights;
uniform vec3 uExponents;
uniform float uSaturation;

varying vec2 vTextureCoord;
varying float vShift;
varying vec3 vWeights;
varying vec3 vExponents;
varying float vSaturation;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
  vShift = uShift;
  vWeights = uWeights;
  vExponents = uExponents;
  vSaturation = uSaturation;
}