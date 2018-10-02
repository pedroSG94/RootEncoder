#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES sCamera;
uniform vec2 uOnFlip;
varying vec2 vTextureCoord;

void main() {
  vec2 coord = vec2(uOnFlip.y == 1.0 ? 1.0 - vTextureCoord.x : vTextureCoord.x, uOnFlip.x == 1.0 ? 1.0 - vTextureCoord.y : vTextureCoord.y);
  gl_FragColor = texture2D(sCamera, coord);
}