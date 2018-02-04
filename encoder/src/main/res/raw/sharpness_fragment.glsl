precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uStepSize;
uniform float uSharpness;

varying vec2 vTextureCoord;

void main() {
  vec3 nbr_color = vec3(0.0, 0.0, 0.0);
  vec2 coord;
  vec4 color = texture2D(uSampler, vTextureCoord);

  coord.x = vTextureCoord.x - 0.5 * uStepSize.x;
  coord.y = vTextureCoord.y - uStepSize.y;
  nbr_color += texture2D(uSampler, coord).rgb - color.rgb;

  coord.x = vTextureCoord.x - uStepSize.x;
  coord.y = vTextureCoord.y + 0.5 * uStepSize.y;
  nbr_color += texture2D(uSampler, coord).rgb - color.rgb;

  coord.x = vTextureCoord.x + uStepSize.x;
  coord.y = vTextureCoord.y - 0.5 * uStepSize.y;
  nbr_color += texture2D(uSampler, coord).rgb - color.rgb;

  coord.x = vTextureCoord.x + uStepSize.x;
  coord.y = vTextureCoord.y + 0.5 * uStepSize.y;
  nbr_color += texture2D(uSampler, coord).rgb - color.rgb;

  gl_FragColor = vec4(color.rgb - 2.0 * uSharpness * nbr_color, color.a);
}
