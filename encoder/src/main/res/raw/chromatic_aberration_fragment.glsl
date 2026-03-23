precision mediump float;

uniform sampler2D uSampler;
uniform float uTime;
varying vec2 vTextureCoord;

void main() {
    float amount = 0.0;
    amount = (1.0 + sin(uTime * 6.0)) * 0.5;
    amount *= 1.0 + sin(uTime * 16.0) * 0.5;
    amount *= 1.0 + sin(uTime * 19.0) * 0.5;
    amount *= 1.0 + sin(uTime * 27.0) * 0.5;
    amount = pow(amount, 3.0);

    amount *= 0.05;

    vec3 col;
    col.r = texture2D(uSampler, vec2(vTextureCoord.x + amount, vTextureCoord.y)).r;
    col.g = texture2D(uSampler, vTextureCoord).g;
    col.b = texture2D(uSampler, vec2(vTextureCoord.x - amount, vTextureCoord.y)).b;

    col *= (1.0 - amount * 0.5);
    gl_FragColor = vec4(col, 1.0);
}
