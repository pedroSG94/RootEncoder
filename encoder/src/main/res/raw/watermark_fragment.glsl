precision mediump float;

uniform sampler2D sCamera;
uniform sampler2D sWatermark;
uniform float uAlpha;

varying vec2 vTextureCameraCoord;
varying vec2 vTextureWatermarkCoord;

void main() {
  vec4 cameraPixel = texture2D(sCamera, vTextureCameraCoord);
  vec2 coord = vec2(1.0 - vTextureWatermarkCoord.y, 1.0 - vTextureWatermarkCoord.x);
  vec4 watermarkPixel = texture2D(sWatermark, coord);
  gl_FragColor = mix(cameraPixel, watermarkPixel, watermarkPixel.a * uAlpha);
}