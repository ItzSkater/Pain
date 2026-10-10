#version 150

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 Center;   // центр искажения на экране, 0..1
uniform float Aspect;  // ширина / высота экрана
uniform float Radius;  // радиус волны (в долях высоты экрана)
uniform float Width;   // толщина кольца волны
uniform float Strength; // сила радиального сдвига
uniform float Twist;   // закручивание вокруг центра, радианы в самом центре

out vec4 OutColor;

void main() {
    vec2 d = TexCoord - Center;
    d.x *= Aspect;
    float dist = length(d);
    vec2 uv = TexCoord;
    float glow = 0.0;

    if (Strength != 0.0) {
        // ударная волна: кольцо, внутри которого картинка сдвигается от центра и обратно
        float ring = (dist - Radius) / Width;
        float profile = exp(-ring * ring);
        vec2 dir = dist > 0.0001 ? d / dist : vec2(0.0);
        dir.x /= Aspect;
        uv -= dir * profile * Strength * -ring * 1.6;
        glow = profile * abs(Strength) * 1.2;
    }

    if (Twist != 0.0) {
        // воронка: чем ближе к центру, тем сильнее поворот; к краю радиуса сходит на нет
        float k = clamp(1.0 - dist / Radius, 0.0, 1.0);
        float angle = Twist * k * k;
        float s = sin(angle);
        float c = cos(angle);
        vec2 r = vec2(d.x * c - d.y * s, d.x * s + d.y * c);
        r.x /= Aspect;
        uv = Center + r;
    }

    uv = clamp(uv, vec2(0.001), vec2(0.999));
    // лёгкое расслоение цвета на волне
    vec2 shift = (uv - TexCoord) * 0.1;
    vec3 color = vec3(texture(Sampler0, uv + shift).r, texture(Sampler0, uv).g, texture(Sampler0, uv - shift).b);
    OutColor = vec4(color + glow, 1.0);
}
