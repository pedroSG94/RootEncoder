#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES uSamplerView;
uniform sampler2D uSampler;

varying vec2 vTextureCoord;

void main() {
  vec4 color = texture2D(uSampler, vTextureCoord);
  vec4 viewColor = texture2D(uSamplerView, vec2(vTextureCoord.x, 1.0 - vTextureCoord.y));
  color.rgb *= 1.0 - viewColor.a;
  gl_FragColor = color + viewColor;
}