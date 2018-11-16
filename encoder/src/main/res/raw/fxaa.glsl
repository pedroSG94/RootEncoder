/*
* http://www.geeks3d.com/20110405/fxaa-fast-approximate-anti-aliasing-demo-glsl-opengl-test-radeon-geforce/3/
*/
precision highp float;

#define FXAA_SPAN_MAX 8.0
#define FXAA_REDUCE_MUL (1.0 / 8.0)
#define FXAA_REDUCE_MIN (1.0 / 256.0)

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uAAEnabled;

varying vec2 vTextureCoord;

vec4 FxaaTexOff(sampler2D tex, vec2 p, vec2 o, vec2 r){
  return texture2D(tex, p + (o * r));
}
/**
 *
 * @param posPos {@link vec4} Output of FxaaVertexShader interpolated across screen.
 * @param tex {@link sampler2D} The input texture.
 * @param rcpFrame {@link vec2} Constant {1.0/frameWidth, 1.0/frameHeight}.
 */
vec3 FxaaPixelShader(sampler2D tex, vec2 uv, vec2 pos, vec2 rcpFrame) {
/*---------------------------------------------------------*/
    vec3 rgbNW = texture2D(tex, pos).xyz;
    vec3 rgbNE = FxaaTexOff(tex, pos, vec2(1,0), rcpFrame).xyz;
    vec3 rgbSW = FxaaTexOff(tex, pos, vec2(0,1), rcpFrame).xyz;
    vec3 rgbSE = FxaaTexOff(tex, pos, vec2(1,1), rcpFrame).xyz;
    vec3 rgbM  = texture2D(tex, uv).xyz;
/*---------------------------------------------------------*/
    vec3 luma = vec3(0.299, 0.587, 0.114);
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM  = dot(rgbM,  luma);
/*---------------------------------------------------------*/
    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
/*---------------------------------------------------------*/
    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));
/*---------------------------------------------------------*/
    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * FXAA_REDUCE_MUL), FXAA_REDUCE_MIN);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = min(vec2( FXAA_SPAN_MAX,  FXAA_SPAN_MAX),
          max(vec2(-FXAA_SPAN_MAX, -FXAA_SPAN_MAX),
          dir * rcpDirMin)) * rcpFrame.xy;
/*--------------------------------------------------------*/
    vec3 rgbA = (1.0 / 2.0) * (
        texture2D(tex, uv + dir * (1.0 / 3.0 - 0.5)).xyz +
        texture2D(tex, uv + dir * (2.0 / 3.0 - 0.5)).xyz);
    vec3 rgbB = rgbA * (1.0 / 2.0) + (1.0 / 4.0) * (
        texture2D(tex, uv + dir * (0.0 / 3.0 - 0.5)).xyz +
        texture2D(tex, uv + dir * (3.0 / 3.0 - 0.5)).xyz);
    float lumaB = dot(rgbB, luma);
    if((lumaB < lumaMin) || (lumaB > lumaMax)) {
        return rgbA;
    }
    return rgbB;
}

vec4 PostFX(sampler2D tex, vec2 uv) {
  vec2 rcpFrame = vec2(1.0 / uResolution.x, 1.0 / uResolution.y);
  vec4 c = vec4(0.0);
  vec2 pos = uv - (rcpFrame * (0.5 + FXAA_REDUCE_MUL));
  c.rgb = FxaaPixelShader(tex, uv, pos, rcpFrame);
  c.a = 1.0;
  return c;
}

void main() {
	gl_FragColor = uAAEnabled == 1.0 ? PostFX(uSampler, vTextureCoord) : texture2D(uSampler, vTextureCoord);
}