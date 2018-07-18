attribute vec4 aPosition;
attribute vec4 aTextureCoord;
attribute vec4 aTextureSurfaceCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

varying vec2 vTextureCoord;
varying vec2 vTextureSurfaceCoord;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
  vTextureSurfaceCoord = (uSTMatrix * aTextureSurfaceCoord).xy;
}