#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTextureCoord;
uniform samplerExternalOES uSampler;

void main() {
  vec3 negative = 1.0 - texture2D(uSampler, vTextureCoord).rgb;
  gl_FragColor = vec4(negative, 1.0);
}
