#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uSampler;
uniform sampler2D watermark;

varying vec2 vTextureCoord;
varying float _alpha;

void main() {
    vec4 cameraPixel = texture2D(uSampler, vTextureCoord);
    vec4 watermarkPixel = texture2D(watermark, vec2(1.0 - vTextureCoord.y, vTextureCoord.x));
    if (watermarkPixel.rgb == vec3(0.0, 0.0, 0.0)) {
      gl_FragColor = cameraPixel;
    } else {
      gl_FragColor = (cameraPixel * (1.0 - _alpha)) + (watermarkPixel * _alpha);
    }
}