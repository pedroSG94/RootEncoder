precision mediump float;

uniform sampler2D uSampler;

varying vec2 vTextureCoord;
varying float vShift;
varying vec3 vWeights;
varying vec3 vExponents;
varying float vSaturation;

void main() {
  vec4 oldcolor = texture2D(uSampler, vTextureCoord);
  float kv = dot(oldcolor.rgb, vWeights) + vShift;
  vec3 new_color = vSaturation * oldcolor.rgb + (1.0 - vSaturation) * kv;
  gl_FragColor= vec4(new_color, oldcolor.a);

  vec4 color = texture2D(uSampler, vTextureCoord);
  float de = dot(color.rgb, vWeights);
  float inv_de = 1.0 / de;
  vec3 verynew_color = de * pow(color.rgb * inv_de, vExponents);
  float max_color = max(max(max(verynew_color.r, verynew_color.g), verynew_color.b), 1.0);
  gl_FragColor = gl_FragColor + vec4(verynew_color / max_color, color.a);
}