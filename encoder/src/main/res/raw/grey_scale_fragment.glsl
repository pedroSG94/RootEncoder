#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTextureCoord;
uniform samplerExternalOES uSampler;

void main() {
  vec4 pixel = texture2D(uSampler, vTextureCoord);
  float grey = pixel.x + pixel.y + pixel.z / 3.0;
  gl_FragColor = vec4(grey, grey, grey, 1.0);
}
