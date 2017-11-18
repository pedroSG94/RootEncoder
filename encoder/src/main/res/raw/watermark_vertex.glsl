attribute vec4 aPosition;
attribute vec4 aTextureCameraCoord;
attribute vec4 aTextureWatermarkCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;
uniform float uAlpha;

varying vec2 vTextureCameraCoord;
varying vec2 vTextureWatermarkCoord;
varying float vAlpha;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCameraCoord = (uSTMatrix * aTextureCameraCoord).xy;
  vTextureWatermarkCoord = (uSTMatrix * aTextureWatermarkCoord).xy;
  vAlpha = uAlpha;
}