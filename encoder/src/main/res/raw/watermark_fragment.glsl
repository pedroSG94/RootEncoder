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
  //0.5 to support antialias on TextStreamObject
  if (watermarkPixel.a <= 0.5 || coord.x < 0.0 || coord.x > 1.0 || coord.y < 0.0 || coord.y > 1.0) {
    gl_FragColor = cameraPixel;
  } else {
    gl_FragColor = (watermarkPixel * uAlpha) + (cameraPixel * (1.0 - uAlpha));
  }
}