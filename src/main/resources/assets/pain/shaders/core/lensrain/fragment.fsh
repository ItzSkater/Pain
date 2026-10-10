#version 150

// Капли на «стекле» камеры. Каждая капля — маленькая линза: картинка под ней перевёрнута и сжата,
// по краю тёмный ободок, сверху блик. Крупные сползают рывками и оставляют дорожку, мелкие сидят на месте.

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform float Time;
uniform float Amount;
uniform float Aspect;

out vec4 OutColor;

float hash(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// xy — нормаль капли (куда наклонена её поверхность), z — есть ли тут вода, w — дорожка
vec4 running(vec2 uv, float t, float cols) {
    vec2 st = vec2(uv.x * Aspect * cols, uv.y * cols * 0.28);
    float column = floor(st.x);
    float n = hash(column + cols);
    st.y += n * 17.0 + t * (0.05 + 0.12 * n);
    vec2 id = floor(st);
    vec2 f = fract(st) - 0.5;
    float m = hash2(id + cols);
    float gate = Amount * 0.30;
    if (m > gate) {
        return vec4(0.0);
    }

    float r = m / max(gate, 1.0e-3);
    float slide = sin(t * (0.5 + r) + r * 40.0);
    float y = -0.3 * slide * slide * slide;
    float x = (hash(m * 53.0) - 0.5) * 0.5 + sin(st.y * 6.0 + r * 30.0) * 0.05;
    vec2 p = vec2(f.x - x, (f.y - y) / 0.28);
    float size = 0.10 + 0.09 * hash(r * 7.0);
    // капля чуть вытянута вниз
    vec2 e = p * vec2(1.0, p.y < 0.0 ? 0.85 : 1.15);
    float dist = length(e) / size;
    float drop = 1.0 - smoothstep(0.82, 1.0, dist);
    float above = clamp((f.y - y) * 2.4, 0.0, 1.0);
    float trail = (1.0 - smoothstep(0.0, size * 0.3 * (1.0 - above * 0.7), abs(f.x - x))) * step(y, f.y) * (1.0 - above) * (1.0 - drop);
    return vec4(e / size * drop, drop, trail);
}

vec3 still(vec2 uv, float cells) {
    vec2 st = vec2(uv.x * Aspect, uv.y) * cells;
    vec2 id = floor(st);
    vec2 f = fract(st) - 0.5;
    float m = hash2(id + cells * 3.0);
    if (m > Amount * 0.10) {
        return vec3(0.0);
    }

    vec2 c = vec2(hash(m * 31.0), hash(m * 77.0)) * 0.5 - 0.25;
    float size = 0.10 + 0.20 * hash(m * 13.0);
    // мелкие капли высыхают и появляются снова
    float life = 0.5 + 0.5 * sin(Time * (0.15 + m) + m * 60.0);
    size *= 0.5 + 0.5 * life;
    float dist = length(f - c) / size;
    float drop = 1.0 - smoothstep(0.8, 1.0, dist);
    return vec3((f - c) / size * drop, drop);
}

void main() {
    vec4 a = running(TexCoord, Time, 18.0);
    vec4 b = running(TexCoord * 1.31 + vec2(3.1, 7.7), Time * 1.2, 29.0);
    vec3 s = still(TexCoord, 30.0) + still(TexCoord + vec2(0.37, 0.61), 52.0);
    vec2 normal = a.xy + b.xy + s.xy;
    float water = clamp(a.z + b.z + s.z, 0.0, 1.0);
    float trail = clamp(a.w + b.w, 0.0, 1.0);
    // линза: под каплей видна уменьшенная перевёрнутая картинка
    vec2 offset = normal * vec2(0.035 / Aspect, 0.035) + vec2(0.0, trail * 0.004);
    vec3 color = texture(Sampler0, clamp(TexCoord - offset, 0.001, 0.999)).rgb;
    float rim = water * smoothstep(0.55, 1.0, length(normal));
    color *= 1.0 - rim * 0.35;
    // блик неба в верхней части капли
    float glint = water * pow(clamp(dot(normalize(vec3(normal, 0.6)), normalize(vec3(-0.35, 0.6, 0.7))), 0.0, 1.0), 14.0);
    color += vec3(0.55, 0.6, 0.65) * glint;
    color = mix(color, color * 1.06 + 0.01, trail * 0.5);
    OutColor = vec4(color, 1.0);
}
