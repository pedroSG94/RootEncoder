precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;

varying vec2 vTextureCoord;

const int kNumPatrones = 6;

void main() {
  vec2 texCoord = vTextureCoord * uResolution.xy;
  vec2 xy = texCoord.xy / uResolution.yy;
  float amplitud = 0.03;
  float frecuencia = 10.0;
  float gris = 1.0;
  float divisor = 8.0 / uResolution.y;
  float grosorInicial = divisor * 0.2;
  // x: seno del angulo, y: coseno del angulo, z: factor de suavizado
  vec3 datosPatron[kNumPatrones];
  datosPatron[0] = vec3(-0.7071, 0.7071, 3.0); // -45
  datosPatron[1] = vec3(0.0, 1.0, 0.6); // 0
  datosPatron[2] = vec3(0.0, 1.0, 0.5); // 0
  datosPatron[3] = vec3(1.0, 0.0, 0.4); // 90
  datosPatron[4] = vec3(1.0, 0.0, 0.3); // 90
  datosPatron[5] = vec3(0.0, 1.0, 0.2); // 0
  vec4 color = texture2D(uSampler, vec2(texCoord.x / uResolution.x, xy.y));
  gl_FragColor = color;
  for(int i = 0; i < kNumPatrones; i++) {
    float coseno = datosPatron[i].x;
    float seno = datosPatron[i].y;
    vec2 punto = vec2(xy.x * coseno - xy.y * seno, xy.x * seno + xy.y * coseno);
    float grosor = grosorInicial * float(i + 1);
    float dist = mod(punto.y + grosor * 0.5 - sin(punto.x * frecuencia) * amplitud, divisor);
    float brillo = 0.3 * color.r + 0.4 * color.g + 0.3 * color.b;
    if(dist < grosor && brillo < 0.75 - 0.12 * float(i)) {
      // Suavizado
      float k = datosPatron[i].z;
      float x = (grosor - dist) / grosor;
      float fx = abs((x - 0.5) / k) - (0.5 - k) / k;
      gris = min(fx, gris);
    }
  }
  gl_FragColor = vec4(gris, gris, gris, 1.0);
}
