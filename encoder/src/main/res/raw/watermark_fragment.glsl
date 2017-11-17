#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sCamera;
uniform sampler2D sWatermark;

varying vec2 vTextureCoord;
varying float vAlpha;

void main() {
    vec4 cameraPixel = texture2D(sCamera, vTextureCoord);
    vec4 watermarkPixel = texture2D(sWatermark, vec2(1.0 - vTextureCoord.y, vTextureCoord.x));
    if (watermarkPixel.rgb == vec3(0.0, 0.0, 0.0)) {
      gl_FragColor = cameraPixel;
    } else {
      gl_FragColor = (cameraPixel * (1.0 - vAlpha)) + (watermarkPixel * vAlpha);
    }
}