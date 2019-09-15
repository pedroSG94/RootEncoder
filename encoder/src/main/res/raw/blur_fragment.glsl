precision mediump float;

uniform sampler2D uSampler;
uniform float uBlur;
uniform float uRadius;

varying vec2 vTextureCoord;

void main() {
  vec3 sum = vec3(0);
  if (uBlur > 0.0) {
    for (float i = -uBlur; i < uBlur; i++) {
      for (float j = -uBlur; j < uBlur; j++) {
        sum += texture2D(uSampler, vTextureCoord + vec2(i, j) * (uRadius / uBlur)).rgb / pow(uBlur * 2.0, 2.0);
      }
    }
  } else {
    sum = texture2D(uSampler, vTextureCoord).rgb;
  }
  gl_FragColor = vec4(sum, 1.0);
}