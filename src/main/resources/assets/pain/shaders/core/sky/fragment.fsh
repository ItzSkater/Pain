#version 150

// Небо «Атмосфера». Воздух — градиент по высоте и солнцу; облака объёмные: луч идёт сквозь слой
// облаков шагами, на каждом шаге считается плотность и сколько света дошло от солнца сквозь
// соседние клубы. Отсюда тёмные днища, светящиеся края и тени одного облака на другом.

in vec3 Dir;

uniform vec3 SunDir;
uniform float Time;
uniform float Rain;
uniform float Clouds;
uniform float Flash;
uniform vec3 Bolt; // азимут молнии, её случайное число, яркость
uniform float Steps;

out vec4 OutColor;

const float BASE = 1.0;
const float TOP = 1.8;

float cover;

// hash без sin (Dave Hoskins, "Hash without Sine", MIT)
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

float fbm4(vec3 p) {
    float v = 0.5 * noise3(p);
    p = p * 2.02 + vec3(11.0, 5.0, 3.0);
    v += 0.25 * noise3(p);
    p = p * 2.03 + vec3(7.0, 13.0, 9.0);
    v += 0.125 * noise3(p);
    p = p * 2.01 + vec3(3.0, 2.0, 17.0);
    v += 0.0625 * noise3(p);
    return v / 0.9375;
}

float fbm2(vec3 p) {
    float v = 0.5 * noise3(p);
    p = p * 2.02 + vec3(11.0, 5.0, 3.0);
    v += 0.25 * noise3(p);
    return v / 0.75;
}

// плотность облака в точке; cheap — грубая версия для луча к солнцу
float density(vec3 p, bool cheap) {
    float h = (p.y - BASE) / (TOP - BASE);
    if (h < 0.0 || h > 1.0) {
        return 0.0;
    }

    // плоское днище, округлая вершина
    float profile = smoothstep(0.0, 0.1, h) * (1.0 - smoothstep(0.25, 1.0, h));
    vec3 q = vec3(p.x, p.y * 1.5, p.z) * 0.85 + vec3(Time * 0.02, 0.0, Time * 0.008);
    float shape = cheap ? fbm2(q) : fbm4(q);
    float d = clamp((shape - (1.0 - cover)) * 5.0, 0.0, 1.0) * profile;
    if (!cheap && d > 0.0) {
        // мелкая рябь выедает края, середина остаётся плотной
        float fine = noise3(q * 6.5 + vec3(0.0, Time * 0.03, 0.0)) * 0.62 + noise3(q * 15.0 + vec3(Time * 0.05, 0.0, 0.0)) * 0.38;
        d = max(d - (1.0 - d) * fine * 0.5, 0.0);
    }

    return d;
}

float star(vec3 d, float night) {
    vec3 g = floor(d * 190.0);
    float s = hash13(g);
    vec3 f = fract(d * 190.0) - 0.5;
    float spot = 1.0 - smoothstep(0.0, 0.42, length(f));
    float twinkle = 0.65 + 0.35 * sin(Time * (1.5 + s * 4.0) + s * 90.0);
    return step(0.9968, s) * spot * twinkle * night * 1.4;
}

void main() {
    vec3 d = normalize(Dir);
    float sunH = SunDir.y;
    float day = smoothstep(-0.18, 0.22, sunH);
    float dusk = (1.0 - smoothstep(0.0, 0.42, abs(sunH))) * smoothstep(-0.3, 0.0, sunH);
    float night = 1.0 - day;
    float h = clamp(d.y, 0.0, 1.0);
    float sd = max(dot(d, SunDir), 0.0);
    float md = max(dot(d, -SunDir), 0.0);

    // воздух
    vec3 zenith = mix(vec3(0.010, 0.016, 0.040), vec3(0.13, 0.34, 0.78), day);
    vec3 horizon = mix(vec3(0.035, 0.045, 0.085), vec3(0.62, 0.78, 0.96), day);
    vec3 sky = mix(horizon, zenith, pow(h, 0.42));
    vec3 warm = vec3(1.0, 0.42, 0.14);
    vec3 rose = vec3(0.95, 0.45, 0.55);
    float low = pow(1.0 - h, 5.0);
    sky = mix(sky, mix(rose, warm, pow(sd, 2.0)), dusk * low * (0.35 + 0.65 * pow(sd, 1.5)));
    vec3 grey = mix(vec3(0.03, 0.035, 0.045), vec3(0.56, 0.60, 0.66), day);
    sky = mix(sky, grey, Rain * 0.8);

    if (night > 0.01 && d.y > -0.05) {
        sky += vec3(0.9, 0.95, 1.0) * star(d, night) * (1.0 - Rain);
    }

    vec3 sunColor = mix(vec3(1.0, 0.48, 0.18), vec3(1.0, 0.96, 0.88), smoothstep(0.0, 0.45, sunH));
    float sunUp = smoothstep(-0.08, 0.02, sunH);
    float moonUp = smoothstep(-0.08, 0.02, -sunH);
    float disc = smoothstep(0.99935, 0.99965, sd);
    sky += sunColor * (disc * 6.0 + pow(sd, 420.0) * 1.3 + pow(sd, 36.0) * 0.30 + pow(sd, 6.0) * 0.10) * sunUp * (1.0 - Rain * 0.7);
    float moon = smoothstep(0.99955, 0.99975, md);
    sky += vec3(0.80, 0.86, 1.0) * (moon * 1.6 + pow(md, 300.0) * 0.35 + pow(md, 20.0) * 0.06) * moonUp * (1.0 - Rain * 0.7);

    if (d.y <= 0.0) {
        sky = mix(sky, mix(horizon, grey, Rain * 0.8), clamp(-d.y * 6.0, 0.0, 1.0));
    }

    // облака
    cover = mix(mix(0.42, 0.62, Clouds), 0.76, Rain);
    if (d.y > 0.015) {
        float t0 = BASE / d.y;
        float len = min((TOP - BASE) / d.y, 2.6);
        float dt = len / Steps;
        float jitter = hash13(vec3(gl_FragCoord.xy, 3.0));
        // светит солнце, а ночью — луна
        bool sunLit = sunH > -0.06;
        vec3 lightDir = sunLit ? SunDir : -SunDir;
        float toLight = sunLit ? sd : md;
        vec3 lightColor = sunLit ? sunColor * (0.45 + 1.25 * smoothstep(-0.06, 0.25, sunH)) : vec3(0.20, 0.24, 0.34) * moonUp;
        lightColor *= 1.0 - Rain * 0.6;
        vec3 ambient = mix(vec3(0.015, 0.02, 0.035), mix(zenith, vec3(1.0), 0.62) * 0.86, day);
        ambient = mix(ambient, grey * 0.95, Rain * 0.85);
        // вперёд по лучу солнца облако светится сильнее — отсюда горящие края
        float phase = 0.72 + 1.5 * pow(toLight, 5.0) + 0.5 * pow(toLight, 40.0);
        float trans = 1.0;
        vec3 light = vec3(0.0);

        for (int i = 0; i < 28; i++) {
            if (float(i) >= Steps || trans < 0.03) {
                break;
            }

            vec3 p = d * (t0 + (float(i) + jitter) * dt);
            float dn = density(p, false);
            if (dn > 0.01) {
                float shade = density(p + lightDir * 0.09, true) + density(p + lightDir * 0.22, true) + density(p + lightDir * 0.42, true);
                // свет не только гаснет, но и рассеивается внутри облака — поэтому толща не уходит в чёрное
                float sunlight = max(exp(-shade * 1.5), exp(-shade * 0.4) * 0.55);
                float powder = 1.0 - exp(-dn * 5.0);
                float hh = (p.y - BASE) / (TOP - BASE);
                vec3 c = lightColor * sunlight * (0.45 + 0.55 * powder) * phase + ambient * (0.45 + 0.75 * hh);
                float a = 1.0 - exp(-dn * dt * 15.0);
                light += trans * a * c;
                trans *= 1.0 - a;
            }
        }

        // даль тонет в дымке горизонта
        float haze = 1.0 - exp(-max(t0 - 1.0, 0.0) * 0.11);
        float alpha = (1.0 - trans) * (1.0 - haze * 0.9) * smoothstep(0.015, 0.07, d.y);
        vec3 cloud = light / max(1.0 - trans, 1.0e-3);
        sky = mix(sky, cloud, alpha);
    }

    // молния
    if (Bolt.z > 0.01) {
        float da = atan(d.z, d.x) - Bolt.x;
        da = atan(sin(da), cos(da));
        sky += vec3(0.45, 0.5, 0.7) * exp(-da * da * 1.6) * Bolt.z * 0.4;
        if (d.y > 0.0) {
            float y = d.y;
            float jag = (noise3(vec3(y * 8.0, Bolt.y * 50.0, 1.0)) - 0.5) * 0.24 + (noise3(vec3(y * 28.0, Bolt.y * 91.0, 2.0)) - 0.5) * 0.09 + (noise3(vec3(y * 95.0, Bolt.y * 33.0, 3.0)) - 0.5) * 0.028;
            float x = da + jag;
            float span = (1.0 - smoothstep(0.58, 0.74, y)) * smoothstep(0.0, 0.03, y);
            float core = exp(-x * x / (0.0035 * 0.0035));
            float halo = exp(-x * x / (0.03 * 0.03)) * 0.3;
            float side = Bolt.y > 0.5 ? 1.0 : -1.0;
            float bx = x + side * (0.42 - y) * 0.55 + (noise3(vec3(y * 40.0, Bolt.y * 17.0 + 5.0, 4.0)) - 0.5) * 0.08;
            float branch = exp(-bx * bx / (0.0028 * 0.0028)) * step(y, 0.42) * step(0.16, y) * 0.55;
            sky += vec3(0.82, 0.87, 1.0) * (core * 3.0 + halo + branch * 2.0) * span * Bolt.z;
        }
    }

    sky += vec3(0.75, 0.82, 1.0) * Flash;
    OutColor = vec4(sky, 1.0);
}
