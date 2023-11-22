#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uObject;
uniform sampler2D uSampler;
uniform float uAlpha;

varying vec2 vTextureCoord;
varying vec2 vTextureObjectCoord;

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  vec4 surfaceColor = texture2D(uObject, vTextureObjectCoord);
  if (vTextureObjectCoord.x < 0.0 || vTextureObjectCoord.x > 1.0 ||
  vTextureObjectCoord.y < 0.0 || vTextureObjectCoord.y > 1.0) {
    gl_FragColor = color;
  } else {
    gl_FragColor = mix(color, surfaceColor, surfaceColor.a * uAlpha);
  }
}