#version 150

// Вспышка удара: квадрат, развёрнутый к камере, вся картинка считается здесь.
// Mode: 0 плазма, 1 ударная волна, 2 разрез, 3 искры. Progress идёт от 0 до 1 за жизнь вспышки.

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 FragColor;

uniform float Progress;
uniform float Time;
uniform float Mode;
uniform float Bright;
uniform float Seed;

out vec4 OutColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + 17.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 p = FragCoord * 2.0 - 1.0;
    float r = length(p);
    float t = clamp(Progress, 0.0, 1.0);
    float grow = 1.0 - pow(1.0 - t, 3.0);
    float fade = (1.0 - t) * (1.0 - t);
    float glow = 0.0; // сколько света
    float heat = 0.0; // насколько он белый

    if (Mode < 0.5) {
        // плазма: горячее ядро и клубящиеся языки, которые расползаются и тают
        vec2 q = p / (0.28 + 0.6 * grow);
        float rq = length(q);
        vec2 warp = vec2(fbm(q * 2.2 + Seed * 13.0 + Time * 0.8), fbm(q * 2.2 - Seed * 7.0 - Time * 0.6));
        float swirl = fbm(q * 3.0 + warp * 2.4 + vec2(Time * 0.5, -Time * 0.4));
        float core = exp(-rq * rq * 22.0);
        float body = (1.0 - smoothstep(0.1, 1.0, rq)) * pow(swirl, 2.4) * 2.6;
        float rim = exp(-pow((rq - 0.78) * 7.0, 2.0)) * swirl * 0.9;
        glow = (core * 0.9 + body + rim) * fade;
        heat = core + body * 0.25;
    } else if (Mode < 1.5) {
        // ударная волна: рваное кольцо уходит наружу, в центре короткая вспышка
        float n = fbm(p * 4.0 + Seed * 9.0 + Time * 0.5);
        float ringR = (0.18 + 0.7 * grow) * (0.93 + 0.14 * n);
        float w = 0.018 + 0.03 * (1.0 - t);
        float d = r - ringR;
        float ring = exp(-d * d / (w * w));
        float wake = smoothstep(ringR * 0.3, ringR, r) * step(r, ringR) * 0.12 * (0.4 + n);
        float flash = exp(-r * r * 70.0) * pow(1.0 - t, 10.0) * 0.9;
        glow = (ring * 1.2 + exp(-d * d / (w * w * 14.0)) * 0.22 + wake) * (1.0 - t) + flash;
        heat = ring * 0.55 + flash;
    } else if (Mode < 2.5) {
        // разрез: тонкий серп, который прочерчивается от края до края и гаснет с хвоста
        float ang = Seed * 6.2831853;
        vec2 s = vec2(p.x * cos(ang) - p.y * sin(ang), p.x * sin(ang) + p.y * cos(ang));
        float arc = abs(length(s - vec2(0.0, -1.3)) - 1.3);
        float head = t * 4.2 - 1.0;
        float behind = head - s.x;
        float drawn = step(0.0, behind) * clamp(1.0 - behind / 2.6, 0.0, 1.0);
        float taper = 1.0 - smoothstep(0.15, 0.88, abs(s.x));
        float thick = 0.045 * taper + 0.004;
        float blade = exp(-arc * arc / (thick * thick));
        float halo = exp(-arc * arc / (thick * thick * 20.0)) * 0.4;
        float tip = exp(-behind * behind * 30.0);
        glow = (blade * (0.9 + 1.4 * tip) + halo) * drawn * taper * (1.0 - smoothstep(0.5, 1.0, t));
        heat = blade * 0.8 + tip * 0.3;
    } else {
        // искры: лучи разной длины вылетают из точки, головка яркая, хвост тает
        float cells = 28.0;
        float ca = (atan(p.y, p.x) / 6.2831853 + 0.5) * cells;
        float id = floor(ca);
        float fa = fract(ca) - 0.5;
        float h1 = hash(vec2(id, Seed * 91.0 + 1.0));
        float h2 = hash(vec2(id * 1.7 + 3.0, Seed * 37.0 + 5.0));
        float reach = (0.3 + 0.62 * h1) * grow;
        float len = 0.08 + 0.3 * h2 * (1.0 - t);
        float along = smoothstep(reach - len, reach, r) * (1.0 - smoothstep(reach, reach + 0.025, r));
        float off = fa / cells * 6.2831853 * r;
        float wdt = 0.006 + 0.004 * h1;
        float streak = exp(-off * off / (wdt * wdt));
        float alive = step(0.3, h2);
        float flash = exp(-r * r * 80.0) * pow(1.0 - t, 10.0) * 0.9;
        glow = streak * along * alive * 1.7 * (1.0 - t * t) + flash;
        heat = streak * along * 0.7 + flash;
    }

    glow *= (1.0 - smoothstep(0.86, 1.0, r)) * Bright;
    vec3 color = mix(FragColor.rgb, vec3(1.0), clamp(heat, 0.0, 1.0) * 0.85);
    OutColor = vec4(color * glow, 1.0);
}
