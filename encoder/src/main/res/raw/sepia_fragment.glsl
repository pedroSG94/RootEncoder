#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTextureCoord;
uniform samplerExternalOES uSampler;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  vec4 sepia = vec4(clamp(pixel.x * 0.393 + pixel.y * 0.769 + pixel.z * 0.189, 0.0, 1.0),
    clamp(pixel.x * 0.349 + pixel.y * 0.686 + pixel.z * 0.168, 0.0, 1.0),
    clamp(pixel.x * 0.272 + pixel.y * 0.534 + pixel.z * 0.131, 0.0, 1.0),
    pixel.w);
  gl_FragColor = sepia;
}