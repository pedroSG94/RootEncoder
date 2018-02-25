precision mediump float;

uniform sampler2D uSampler;
uniform vec2 uResolution;
uniform float uAAEnabled;

varying vec2 vTextureCoord;

vec4 FxaaTexTop(vec2 pos) {
	vec4 color;
	color.rgb = texture2D(uSampler, pos).rgb;
	color.a = dot(color.rgb, vec3(0.299, 0.587, 0.114));
	return color;
}

/* PC console FXAA implementation */
vec4 FxaaPixelShader(vec2 pos, vec4 fxaaConsolePosPos, vec2 fxaaConsoleRcpFrameOpt,
  vec2 fxaaConsoleRcpFrameOpt2, float fxaaConsoleEdgeSharpness, float fxaaConsoleEdgeThreshold,
  float fxaaConsoleEdgeThresholdMin) {

	float lumaNw = FxaaTexTop(fxaaConsolePosPos.xy).a;
	float lumaSw = FxaaTexTop(fxaaConsolePosPos.xw).a;
	float lumaNe = FxaaTexTop(fxaaConsolePosPos.zy).a;
	float lumaSe = FxaaTexTop(fxaaConsolePosPos.zw).a;

	vec4 rgbyM = FxaaTexTop(pos.xy);
	float lumaM = rgbyM.w;

	float lumaMaxNwSw = max(lumaNw, lumaSw);
	lumaNe += 1.0/384.0;
	float lumaMinNwSw = min(lumaNw, lumaSw);

	float lumaMaxNeSe = max(lumaNe, lumaSe);
	float lumaMinNeSe = min(lumaNe, lumaSe);

	float lumaMax = max(lumaMaxNeSe, lumaMaxNwSw);
	float lumaMin = min(lumaMinNeSe, lumaMinNwSw);

	float lumaMaxScaled = lumaMax * fxaaConsoleEdgeThreshold;

	float lumaMinM = min(lumaMin, lumaM);
	float lumaMaxScaledClamped = max(fxaaConsoleEdgeThresholdMin, lumaMaxScaled);
	float lumaMaxM = max(lumaMax, lumaM);
	float dirSwMinusNe = lumaSw - lumaNe;
	float lumaMaxSubMinM = lumaMaxM - lumaMinM;
	float dirSeMinusNw = lumaSe - lumaNw;

	if (lumaMaxSubMinM < lumaMaxScaledClamped)
		return rgbyM;

	vec2 dir;
	dir.x = dirSwMinusNe + dirSeMinusNw;
	dir.y = dirSwMinusNe - dirSeMinusNw;

	vec2 dir1 = normalize(dir.xy);
	vec4 rgbyN1 = FxaaTexTop(pos.xy - dir1 * fxaaConsoleRcpFrameOpt);
	vec4 rgbyP1 = FxaaTexTop(pos.xy + dir1 * fxaaConsoleRcpFrameOpt);

	float dirAbsMinTimesC = min(abs(dir1.x), abs(dir1.y)) * fxaaConsoleEdgeSharpness;
	vec2 dir2 = clamp(dir1.xy / dirAbsMinTimesC, -2.0, 2.0);

	vec4 rgbyN2 = FxaaTexTop(pos.xy - dir2 * fxaaConsoleRcpFrameOpt2);
	vec4 rgbyP2 = FxaaTexTop(pos.xy + dir2 * fxaaConsoleRcpFrameOpt2);

	vec4 rgbyA = rgbyN1 + rgbyP1;
	vec4 rgbyB = ((rgbyN2 + rgbyP2) * 0.25) + (rgbyA * 0.25);

	bool twoTap = (rgbyB.w < lumaMin) || (rgbyB.w > lumaMax);
	if (twoTap)
		rgbyB.xyz = rgbyA.xyz * 0.5;

	return rgbyB;
}

void main() {
  if (uAAEnabled == 1.0) {
	  vec2 uFxaaConsoleRcpFrameOpt = vec2(0.5 / uResolution.x, 0.5 / uResolution.y);
    vec2 uFxaaConsoleRcpFrameOpt2 = vec2(2.0 / uResolution.x, 2.0 / uResolution.y);
    vec4 uFrameSize = vec4(uResolution.x, uResolution.y, 1.0 / uResolution.x, 1.0 / uResolution.y);

  	vec4 fxaaConsolePosPos;
	  fxaaConsolePosPos.xy = vTextureCoord * uFrameSize.xy - 1.0;
	  fxaaConsolePosPos.zw = fxaaConsolePosPos.xy + 2.0;
	  fxaaConsolePosPos *= uFrameSize.zwzw;

	  const float fxaaConsoleEdgeSharpness = 8.0;
	  const float fxaaConsoleEdgeThreshold = 0.125;
	  const float fxaaConsoleEdgeThresholdMin = 0.0625;
	  gl_FragColor = FxaaPixelShader(vTextureCoord, fxaaConsolePosPos, uFxaaConsoleRcpFrameOpt,
	  uFxaaConsoleRcpFrameOpt2, fxaaConsoleEdgeSharpness, fxaaConsoleEdgeThreshold, fxaaConsoleEdgeThresholdMin);
  } else {
    gl_FragColor = texture2D(uSampler, vTextureCoord);
  }
}
