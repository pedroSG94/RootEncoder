#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uSamplerSurface;
uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying vec2 vTextureSurfaceCoord;

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  vec2 coord = vec2(vTextureSurfaceCoord.y, 1.0 - vTextureSurfaceCoord.x);
  vec4 surfaceColor = texture2D(uSamplerSurface, coord);
  if (surfaceColor.a <= 0.5 || coord.x < 0.0 || coord.x > 1.0 || coord.y < 0.0 || coord.y > 1.0) {
    gl_FragColor = color;
  } else {
    gl_FragColor = surfaceColor;
  }
}