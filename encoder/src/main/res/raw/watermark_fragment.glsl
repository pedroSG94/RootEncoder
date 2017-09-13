#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTextureCoord;
uniform samplerExternalOES uSampler;
uniform sampler2D watermark;

void main() {
    vec4 cameraPixel = texture2D(uSampler, vTextureCoord);
    vec4 watermarkPixel = texture2D(watermark, vec2(1.0 - vTextureCoord.y, vTextureCoord.x));
    gl_FragColor = cameraPixel + watermarkPixel;
}