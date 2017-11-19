#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sCamera;
uniform sampler2D sWatermark;

varying vec2 vTextureCameraCoord;
varying vec2 vTextureWatermarkCoord;
varying float vAlpha;

void main() {
    vec4 cameraPixel = texture2D(sCamera, vTextureCameraCoord);
    vec4 watermarkPixel = texture2D(sWatermark, vTextureWatermarkCoord);
    if (vTextureWatermarkCoord.x < 0.0 || vTextureWatermarkCoord.x > 1.0 ||
        vTextureWatermarkCoord.y < 0.0 || vTextureWatermarkCoord.y > 1.0 || watermarkPixel.rgb == vec3(0.0, 0.0, 0.0)) {
      gl_FragColor = cameraPixel;
    } else {
      gl_FragColor = (cameraPixel * (1.0 - vAlpha)) + (watermarkPixel * vAlpha);
    }
}