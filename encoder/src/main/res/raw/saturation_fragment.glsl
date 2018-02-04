precision mediump float;

uniform sampler2D uSampler;
uniform float uShift;
uniform vec3 uWeights;
uniform vec3 uExponents;
uniform float uSaturation;

varying vec2 vTextureCoord;

void main() {
  vec4 oldcolor = texture2D(uSampler, vTextureCoord);
  float kv = dot(oldcolor.rgb, uWeights) + uShift;
  vec3 new_color = uSaturation * oldcolor.rgb + (1.0 - uSaturation) * kv;
  gl_FragColor= vec4(new_color, oldcolor.a);

  vec4 color = texture2D(uSampler, vTextureCoord);
  float de = dot(color.rgb, uWeights);
  float inv_de = 1.0 / de;
  vec3 verynew_color = de * pow(color.rgb * inv_de, uExponents);
  float max_color = max(max(max(verynew_color.r, verynew_color.g), verynew_color.b), 1.0);
  gl_FragColor = gl_FragColor + vec4(verynew_color / max_color, color.a);
}