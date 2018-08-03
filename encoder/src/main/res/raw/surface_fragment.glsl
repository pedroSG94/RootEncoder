#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uSamplerSurface;
uniform sampler2D uSampler;
uniform float uAlpha;

varying vec2 vTextureCoord;
varying vec2 vTextureSurfaceCoord;

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  vec4 surfaceColor = texture2D(uSamplerSurface, vTextureSurfaceCoord);
  if (vTextureSurfaceCoord.x < 0.0 || vTextureSurfaceCoord.x > 1.0 ||
  vTextureSurfaceCoord.y < 0.0 || vTextureSurfaceCoord.y > 1.0) {
    gl_FragColor = color;
  } else {
    gl_FragColor = mix(color, surfaceColor, surfaceColor.a * uAlpha);
  }
}