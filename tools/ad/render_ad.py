#!/usr/bin/env python3
"""Реклама Pain 1.2. Покадровая сборка: ffmpeg декодирует и красит куски исходника,
Python двигает «камеру», ставит склейки в долю и рисует графику, ffmpeg кодирует результат.

  render_ad.py test N [N ...]   — сохранить отдельные кадры в test_NNNN.png
  render_ad.py full OUT.mp4     — собрать ролик целиком
"""
import math
import os
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 60
SRC = os.path.expanduser("~/Painvideo/VIDOS.mov")
LOGO = os.path.expanduser("~/.claude/PainVideo/pain_logo_transparent.png")
FONT = "/usr/share/fonts/TTF/Rubik%5Bwght%5D.ttf"

# Музыка в записи: 148 ударов в минуту, сетка долей начинается с 0.044 с.
BEAT = 60.0 / 148.0
PHASE = 0.044
FIRST_BEAT = 140                      # с этой доли исходника начинается ролик
SRC_T0 = PHASE + FIRST_BEAT * BEAT    # 56.80 с


def fr(k):
    """Номер кадра ролика, на который приходится доля k (считая от начала ролика)."""
    return int(round(k * BEAT * FPS))


K_INTRO, K_F1, K_F2, K_F3, K_F4, K_F5, K_OUTRO, K_END = 0, 5, 13, 29, 37, 45, 53, 65.5
N = fr(K_END)

RED = (255, 48, 48)
ORANGE = (232, 128, 92)
GA, GB = (255, 70, 60), (240, 150, 100)
WHITE = (255, 255, 255)

GRADE = ("hqdn3d=2:1.5:3:3,"
         "curves=all='0/0 0.10/0.17 0.30/0.46 0.60/0.78 1/1',"
         "eq=saturation=1.18:contrast=1.03,unsharp=5:5:0.35")
# области кадра исходника: (ширина, высота, x, y)
PT = (1680, 945, 0, 15)       # с верхним HUD, без табло сервера и чата
P2 = (1496, 842, 90, 118)     # только мир
PC = (1434, 806, 120, 118)    # только мир, без строки над хотбаром
GUI = (1440, 810, 239, 135)   # меню целиком
GUI2 = (1200, 675, 480, 120)  # настройки крупно


class Shot:
    def __init__(self, k0, k1, src, crop, kind="clip", start="cut", zbase=1.0, anchor=(0.0, 0.0), speed=1.0, end="hold"):
        self.f0, self.f1 = fr(k0), fr(k1)
        self.n = self.f1 - self.f0
        self.k0, self.k1 = k0, k1
        self.src, self.crop, self.kind = src, crop, kind
        self.start, self.end = start, end   # start: feature | cut | cutin | none;  end: hold | out
        self.zbase, self.anchor, self.speed = zbase, anchor, speed


def build_shots():
    s = []
    # заставка: тот же момент записи, что и звук, но размытый и тёмный
    s.append(Shot(K_INTRO, K_F1, SRC_T0, PT, kind="bg", start="none", end="out"))
    # 01 Music Lyrics — кадр идёт в то же время, что и звук, поэтому текст в мире совпадает с голосом
    s.append(Shot(K_F1, K_F1 + 4, SRC_T0 + fr(K_F1) / FPS, PT, start="feature", anchor=(0.0, 0.85)))
    s.append(Shot(K_F1 + 4, K_F2, SRC_T0 + fr(K_F1 + 4) / FPS, PT, start="cutin", zbase=1.07, anchor=(0.0, 0.85), end="out"))
    # 02 Pain Shaders — четыре кадра по четыре доли
    a = Shot(K_F2, K_F2 + 4, 67.90, P2, start="feature")
    s.append(a)
    s.append(Shot(K_F2 + 4, K_F2 + 8, a.src + a.n / FPS, P2, start="cutin", zbase=1.09))
    b = Shot(K_F2 + 8, K_F2 + 12, 14.25, PC, start="cut")
    s.append(b)
    s.append(Shot(K_F2 + 12, K_F3, b.src + b.n / FPS, PC, start="cutin", zbase=1.08, end="out"))
    # 03 Своя погода — дождливая улица и настройки погоды
    s.append(Shot(K_F3, K_F3 + 4, 80.30, P2, start="feature"))
    s.append(Shot(K_F3 + 4, K_F4, 34.30, GUI, start="cut", end="out"))
    # 04 Liquid Glass меню — список модулей, потом настройки крупно
    c = Shot(K_F4, K_F4 + 4, 73.05, GUI, start="feature")
    s.append(c)
    s.append(Shot(K_F4 + 4, K_F5, c.src + c.n / FPS, GUI2, start="cut", end="out"))
    # 05 Всё уже внутри — полёт над ночным городом
    d = Shot(K_F5, K_F5 + 4, 63.10, P2, start="feature")
    s.append(d)
    s.append(Shot(K_F5 + 4, K_OUTRO, d.src + d.n / FPS, P2, start="cutin", zbase=1.08, end="out"))
    # финал: замедленная аллея с сакурой, размытая
    s.append(Shot(K_OUTRO, K_END, 14.30, PC, kind="bgslow", start="none", speed=0.42))
    return s


SHOTS = build_shots()
FEATURES = [
    (K_F1, K_F2, "01", "Music Lyrics", "Текст песни в такт музыке — прямо в мире"),
    (K_F2, K_F3, "02", "Pain Shaders", "Свой шейдерпак: свет, туман и свечение"),
    (K_F3, K_F4, "03", "Своя погода", "Дождь, снег и гроза — видишь только ты"),
    (K_F4, K_F5, "04", "Liquid Glass меню", "Поиск, слайдеры и 90+ модулей"),
    (K_F5, K_OUTRO, "05", "Всё уже внутри", "Iris и Sodium встроены — ставить ничего не нужно"),
]


# ---------- декодирование ----------

def vf_for(shot):
    w, h, x, y = shot.crop
    if shot.kind == "bg":
        return f"crop={w}:{h}:{x}:{y},scale=960:540:flags=bicubic,gblur=sigma=10,eq=brightness=-0.02:saturation=1.3", (960, 540)
    if shot.kind == "bgslow":
        return (f"crop={w}:{h}:{x}:{y},setpts=PTS/{shot.speed},minterpolate=fps=60:mi_mode=blend,"
                f"scale=960:540:flags=bicubic,gblur=sigma=10,eq=brightness=-0.02:saturation=1.3"), (960, 540)
    return f"crop={w}:{h}:{x}:{y},{GRADE}", (w, h)


class Decoder:
    def __init__(self, shot, first=0, count=None):
        vf, self.size = vf_for(shot)
        ss = shot.src + (first / FPS) * (shot.speed if shot.kind == "bgslow" else 1.0)
        frames = (shot.n if count is None else count) + 2
        cmd = ["ffmpeg", "-v", "error", "-ss", f"{ss:.5f}", "-i", SRC, "-an", "-vf",
               vf + ",scale=in_color_matrix=bt709:in_range=tv,format=rgb24",
               "-frames:v", str(frames), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
        self.bytes = self.size[0] * self.size[1] * 3
        self.p = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=self.bytes)
        self.last = None

    def read(self):
        buf = self.p.stdout.read(self.bytes)
        if len(buf) == self.bytes:
            self.last = Image.frombytes("RGB", self.size, buf)
        elif self.last is None:
            self.last = Image.new("RGB", self.size, (0, 0, 0))
        return self.last

    def close(self):
        try:
            self.p.stdout.close()
            self.p.kill()
            self.p.wait()
        except Exception:
            pass


# ---------- математика движения ----------

def clamp(x, a=0.0, b=1.0):
    return a if x < a else b if x > b else x


def ease_out(p):
    p = clamp(p)
    return 1.0 - (1.0 - p) ** 3


def ease_in(p):
    p = clamp(p)
    return p ** 3


def ease_out_back(p, s=1.6):
    p = clamp(p) - 1.0
    return p * p * ((s + 1.0) * p + s) + 1.0


def beats_in(k0, k1):
    k = math.ceil(k0 - 1e-6)
    while k < k1 - 1e-6:
        yield k
        k += 1


def camera(img, z, anchor=(0.0, 0.0), resample=Image.BICUBIC):
    cw, ch = img.size
    s = W / cw
    a = 1.0 / (s * z)
    ox = anchor[0] * (z - 1.0) * W / 2
    oy = anchor[1] * (z - 1.0) * H / 2
    c = cw / 2 - (W / 2 + ox) * a
    f = ch / 2 - (H / 2 + oy) * a
    return img.transform((W, H), Image.AFFINE, (a, 0, c, 0, a, f), resample=resample)


def rgb_split(img, d):
    d = int(round(d))
    if d == 0:
        return img
    r, g, b = img.split()
    return Image.merge("RGB", (ImageChops.offset(r, d, 0), g, ImageChops.offset(b, -d, 0)))


# ---------- графика ----------

def font(size, weight):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([weight])
    return f


def split(sprite):
    return sprite.convert("RGB"), sprite.getchannel("A")


def gradient(w, h, c1=GA, c2=GB):
    g = Image.new("RGB", (w, 1))
    px = g.load()
    for x in range(w):
        t = x / max(1, w - 1)
        px[x, 0] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return g.resize((w, h))


PAD = 48


def text_sprite(parts, size, weight, shadow_blur=14, shadow_off=6, shadow_alpha=235, grad=False):
    """parts — [(текст, цвет)]. Возвращает спрайт, у которого начало текста в точке (PAD, PAD)."""
    f = font(size, weight)
    asc, desc = f.getmetrics()
    probe = ImageDraw.Draw(Image.new("RGBA", (4, 4)))
    widths = [probe.textlength(t, font=f) for t, _ in parts]
    tw = int(math.ceil(sum(widths)))
    w, h = tw + 2 * PAD, asc + desc + 2 * PAD
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if shadow_alpha:
        sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((PAD, PAD + shadow_off), "".join(t for t, _ in parts), font=f, fill=(0, 0, 0, shadow_alpha))
        sh = sh.filter(ImageFilter.GaussianBlur(shadow_blur))
        img.alpha_composite(sh)
        img.alpha_composite(sh)
    x = PAD
    d = ImageDraw.Draw(img)
    for (t, col), tw_i in zip(parts, widths):
        if col == "grad" or grad:
            mask = Image.new("L", (w, h), 0)
            ImageDraw.Draw(mask).text((x, PAD), t, font=f, fill=255)
            g = Image.new("RGB", (w, h), GA)
            g.paste(gradient(int(tw_i) + 12, h), (int(x), 0))
            img.paste(g, (0, 0), mask)
        else:
            d.text((x, PAD), t, font=f, fill=col + (255,))
        x += tw_i
    return img, tw


LOGO_IMG = Image.open(LOGO).convert("RGBA")


def logo_sprite(height):
    w = int(LOGO_IMG.width * height / LOGO_IMG.height)
    return LOGO_IMG.resize((w, height), Image.LANCZOS)


def glow_sprite(height, blur=46, tint=(255, 40, 40)):
    lg = logo_sprite(height)
    pad = blur * 3
    canvas = Image.new("RGBA", (lg.width + 2 * pad, lg.height + 2 * pad), (0, 0, 0, 0))
    solid = Image.new("RGBA", lg.size, tint + (255,))
    canvas.paste(solid, (pad, pad), lg.getchannel("A"))
    return canvas.filter(ImageFilter.GaussianBlur(blur))


def pill_sprite(text):
    f = font(32, 600)
    tw = int(ImageDraw.Draw(Image.new("RGBA", (4, 4))).textlength(text, font=f))
    w, h = tw + 150, 104
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((18, 22, w - 18, h - 14), radius=34, fill=(0, 0, 0, 150))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(9)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((18, 18, w - 18, h - 18), radius=34, fill=(14, 14, 19, 225), outline=(255, 255, 255, 46), width=2)
    lg = LOGO_IMG.resize((44, int(44 * LOGO_IMG.height / LOGO_IMG.width)), Image.LANCZOS)
    img.alpha_composite(lg, (36, int(h / 2 - lg.height / 2)))
    d.text((36 + 44 + 14, h / 2), text, font=f, fill=(245, 245, 250, 255), anchor="lm")
    return img


_LUT = {}


def put(frame, spr, x, y, alpha=1.0):
    """Кладёт спрайт (RGB, маска) на кадр с общей прозрачностью alpha."""
    if alpha <= 0.004:
        return
    rgb, a = spr
    if alpha < 0.996:
        k = int(round(alpha * 64))
        if k <= 0:
            return
        if k not in _LUT:
            _LUT[k] = [int(i * k / 64) for i in range(256)]
        a = a.point(_LUT[k])
    frame.paste(rgb, (int(round(x)), int(round(y))), a)


def make_shades():
    # под карточкой фичи: тёмный низ, лёгкая тень сверху под плашку, слабая виньетка
    col = Image.new("L", (1, H))
    for y in range(H):
        bottom = clamp((y - 540) / 540.0) ** 1.25 * 0.90
        top = clamp((150 - y) / 150.0) ** 1.5 * 0.42
        col.putpixel((0, y), int(255 * (1.0 - max(bottom, top))))
    feature = col.resize((W, H))
    vig = Image.new("L", (W // 8, H // 8), 0)
    ImageDraw.Draw(vig).ellipse((-40, -30, W // 8 + 40, H // 8 + 30), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(26)).resize((W, H), Image.BILINEAR)
    vig = vig.point([int(205 + i * 50 / 255) for i in range(256)])
    feature = ImageChops.multiply(feature, vig)
    # под заставкой и финалом: ровное затемнение и сильная виньетка, чтобы логотип и текст читались
    dark = Image.new("L", (W // 8, H // 8), 0)
    ImageDraw.Draw(dark).ellipse((10, -6, W // 8 - 10, H // 8 + 6), fill=255)
    dark = dark.filter(ImageFilter.GaussianBlur(30)).resize((W, H), Image.BILINEAR)
    dark = dark.point([int(70 + i * 105 / 255) for i in range(256)])
    return Image.merge("RGB", (feature,) * 3), Image.merge("RGB", (dark,) * 3)


class Graphics:
    def __init__(self):
        self.shade_feature, self.shade_dark = make_shades()
        self.white = Image.new("RGB", (W, H), (255, 255, 255))
        self.black = Image.new("RGB", (W, H), (0, 0, 0))
        self.pill = split(pill_sprite("Pain 1.2"))
        self.cards = []
        probe = ImageDraw.Draw(Image.new("RGBA", (4, 4)))
        for k0, k1, num, title, sub in FEATURES:
            n_img, nw = text_sprite([(num, "grad")], 190, 800, shadow_blur=16, shadow_off=8, shadow_alpha=230)
            t_img, _ = text_sprite([(title, WHITE)], 92, 800)
            s_img, _ = text_sprite([(sub, (232, 232, 240))], 42, 500, shadow_blur=8, shadow_off=3, shadow_alpha=250)
            bar = Image.new("RGBA", (8, 150), (0, 0, 0, 0))
            ImageDraw.Draw(bar).rounded_rectangle((0, 0, 7, 149), radius=4, fill=ORANGE + (255,))
            self.cards.append(dict(k0=k0, k1=k1, num=split(n_img), nw=nw, title=split(t_img), sub=split(s_img), bar=bar))
        # заставка
        self.logo_hi = logo_sprite(460)
        self.glow = split(glow_sprite(300))
        self.intro_title, self.intro_title_w = text_sprite([("Pain ", WHITE), ("1.2", "grad")], 150, 800, shadow_blur=18, shadow_off=8)
        self.intro_title = split(self.intro_title)
        self.intro_sub, self.intro_sub_w = text_sprite([("Shaders Update", (236, 236, 244))], 54, 600, shadow_blur=10, shadow_off=4)
        self.intro_sub = split(self.intro_sub)
        # финал
        self.glow_big = split(glow_sprite(330, blur=52))
        t, self.outro_title_w = text_sprite([("Pain", WHITE), (" Client", RED)], 150, 800, shadow_blur=18, shadow_off=8)
        self.outro_title = split(t)
        t, self.outro_sub_w = text_sprite([("v1.2  ·  Shaders Update", (236, 236, 244))], 48, 600, shadow_blur=10, shadow_off=4)
        self.outro_sub = split(t)
        t, self.outro_line_w = text_sprite([("Pain Shaders  ·  Своя погода  ·  Music Lyrics  ·  Liquid Glass меню", (200, 200, 214))], 34, 500, shadow_blur=8, shadow_off=3)
        self.outro_line = split(t)

    def logo_at(self, frame, cx, cy, base_h, scale, alpha):
        h = max(8, int(round(base_h * scale)))
        w = int(round(self.logo_hi.width * h / self.logo_hi.height))
        spr = self.logo_hi.resize((w, h), Image.BICUBIC)
        put(frame, split(spr), cx - w / 2, cy - h / 2, alpha)

    def card(self, frame, c, t):
        t0, t1 = c["k0"] * BEAT, c["k1"] * BEAT
        x0, y0 = 90, 760
        tx = x0 + c["nw"] + 44
        out = ease_in((t - (t1 - 0.30)) / 0.24)          # 0 → 1 перед концом фичи
        if t < t0 or t >= t1:
            return
        # номер поднимается снизу
        p = ease_out((t - t0 - 0.04) / 0.36)
        put(frame, c["num"], x0 - PAD, y0 - PAD + 70 * (1 - p) - 30 * out, p * (1 - out))
        # полоска вырастает сверху вниз
        p = ease_out((t - t0 - 0.14) / 0.30)
        if p > 0.02:
            hgt = max(1, int(150 * p))
            bar = c["bar"].crop((0, 0, 8, hgt))
            put(frame, split(bar), tx - 24, y0 + 38, (1 - out))
        # заголовок и подпись въезжают слева
        p = ease_out((t - t0 - 0.16) / 0.38)
        put(frame, c["title"], tx - PAD - 60 * (1 - p), y0 + 22 - PAD, p * (1 - out))
        p = ease_out((t - t0 - 0.30) / 0.40)
        put(frame, c["sub"], tx + 4 - PAD - 44 * (1 - p), y0 + 138 - PAD, p * (1 - out))

    def progress(self, frame, t, alpha):
        if alpha <= 0.01:
            return
        d = ImageDraw.Draw(frame, "RGBA")
        x0, x1, gap, y = 90, W - 90, 12, 1044
        n = len(FEATURES)
        sw = (x1 - x0 - gap * (n - 1)) / n
        for i, (k0, k1, *_rest) in enumerate(FEATURES):
            a, b = k0 * BEAT, k1 * BEAT
            xa = x0 + i * (sw + gap)
            d.rounded_rectangle((xa, y, xa + sw, y + 5), radius=3, fill=(255, 255, 255, int(58 * alpha)))
            p = clamp((t - a) / (b - a))
            if p > 0:
                col = (255, 255, 255, int(190 * alpha)) if p >= 1 else (255, 86, 70, int(255 * alpha))
                d.rounded_rectangle((xa, y, xa + max(6, sw * p), y + 5), radius=3, fill=col)


def beat_pulse(t, k_from, k_to, decay=0.10):
    """Затухающий всплеск от последней доли в интервале [k_from, k_to)."""
    best = 0.0
    for k in beats_in(k_from, k_to):
        dt = t - k * BEAT
        if dt >= 0:
            best = max(best, math.exp(-dt / decay))
    return best


def render(n, shot, raw, g):
    t = n / FPS
    tau = (n - shot.f0) / FPS
    dur = shot.n / FPS
    left = shot.f1 - 1 - n                       # сколько кадров до конца кадра
    section = "intro" if n < fr(K_F1) else "outro" if n >= fr(K_OUTRO) else "feature"

    # --- камера ---
    z = shot.zbase + 0.035 * (tau / max(dur, 1e-3))
    punch = {"feature": 0.10, "cut": 0.07, "cutin": 0.05, "none": 0.0}[shot.start]
    z += punch * math.exp(-tau / 0.11)
    for k in beats_in(shot.k0 + 0.5, shot.k1):
        dt = t - k * BEAT
        if dt >= 0:
            z += 0.011 * math.exp(-dt / 0.08)
    blur = 0.0
    if shot.end == "out" and left < 6:              # разгон в следующую фичу
        p = (6 - left) / 6.0
        z *= 1.0 + 0.13 * p * p
        blur = 7.0 * p * p
    if shot.start == "feature":
        blur = max(blur, 7.0 * math.exp(-tau / 0.035))
    if section == "intro":
        z = 1.10 - 0.05 * ease_out(tau / dur)
        if left < 8:
            p = (8 - left) / 8.0
            z *= 1.0 + 0.20 * p * p
    if section == "outro":
        z = 1.12 - 0.07 * ease_out(tau / 3.0)

    frame = camera(raw, z, shot.anchor, Image.BILINEAR if shot.kind != "clip" else Image.BICUBIC)
    if blur > 0.6:
        frame = frame.filter(ImageFilter.BoxBlur(blur))
    if shot.kind == "clip":
        split_px = {"feature": 12.0, "cut": 7.0, "cutin": 4.0, "none": 0.0}[shot.start] * math.exp(-tau / 0.045)
        frame = rgb_split(frame, split_px)

    # --- затемнение под графику ---
    frame = ImageChops.multiply(frame, g.shade_feature if section == "feature" else g.shade_dark)

    # --- вспышка на склейке ---
    fl = {"feature": 0.30, "cut": 0.14, "cutin": 0.08, "none": 0.0}[shot.start] * math.exp(-tau / 0.055)
    if section == "outro":
        fl = 0.42 * math.exp(-tau / 0.07)
    if fl > 0.01:
        frame = Image.blend(frame, g.white, min(0.6, fl))

    # --- графика ---
    if section == "feature":
        for c in g.cards:
            g.card(frame, c, t)
        t_in = clamp((t - K_F1 * BEAT) / 0.35)
        vis = ease_out(t_in)
        put(frame, g.pill, W - g.pill[0].width - 58, 30 - 26 * (1 - vis), vis)
        g.progress(frame, t, vis)
    elif section == "intro":
        end = K_F1 * BEAT
        out = ease_in((t - (end - 0.26)) / 0.22)
        a_all = 1.0 - out
        pulse = beat_pulse(t, 0, K_F1)
        cx, cy = W / 2, 318
        s = 1.0 + 0.50 * (1.0 - ease_out(t / 0.34)) + 0.10 * out
        a = clamp(t / 0.09) * a_all
        put(frame, g.glow, cx - g.glow[0].width / 2, cy - g.glow[0].height / 2, (0.50 + 0.45 * pulse) * a)
        g.logo_at(frame, cx, cy, 300, s, a)
        p = ease_out((t - 1 * BEAT) / 0.34)
        put(frame, g.intro_title, (W - g.intro_title_w) / 2 - PAD, 508 - PAD + 46 * (1 - p), p * a_all)
        p = ease_out((t - 2 * BEAT) / 0.34)
        put(frame, g.intro_sub, (W - g.intro_sub_w) / 2 - PAD, 690 - PAD + 26 * (1 - p), p * a_all)
    else:
        u = t - K_OUTRO * BEAT
        pulse = beat_pulse(t, K_OUTRO, K_OUTRO + 8, decay=0.16)
        cx, cy = W / 2, 300
        s = 1.0 + 0.55 * (1.0 - ease_out(u / 0.32))
        a = clamp(u / 0.07)
        put(frame, g.glow_big, cx - g.glow_big[0].width / 2, cy - g.glow_big[0].height / 2, (0.45 + 0.50 * pulse) * a)
        g.logo_at(frame, cx, cy, 330, s, a)
        p = ease_out((u - 1 * BEAT) / 0.36)
        put(frame, g.outro_title, (W - g.outro_title_w) / 2 - PAD, 520 - PAD + 46 * (1 - p), p)
        p = ease_out((u - 2 * BEAT) / 0.36)
        put(frame, g.outro_sub, (W - g.outro_sub_w) / 2 - PAD, 702 - PAD + 26 * (1 - p), p)
        p = ease_out((u - 3 * BEAT) / 0.40)
        put(frame, g.outro_line, (W - g.outro_line_w) / 2 - PAD, 792 - PAD + 20 * (1 - p), p)
        fade = clamp((u - 3.75) / 1.25)
        if fade > 0:
            frame = Image.blend(frame, g.black, fade)
    if n < 6:                                            # ролик открывается из чёрного
        frame = Image.blend(frame, g.black, 1.0 - (n + 1) / 7.0)
    return frame


def shot_at(n):
    for s in SHOTS:
        if s.f0 <= n < s.f1:
            return s
    return SHOTS[-1]


def main():
    mode = sys.argv[1]
    g = Graphics()
    if mode == "test":
        for a in sys.argv[2:]:
            n = int(a)
            s = shot_at(n)
            dec = Decoder(s, first=n - s.f0, count=4)
            raw = dec.read()
            for _ in range(3):                              # дать шумодаву пару кадров
                raw = dec.read()
            dec.close()
            render(n, s, raw, g).save(f"test_{n:04d}.png")
            print("frame", n, f"t={n / FPS:.2f}", s.kind, s.start)
        return
    out = sys.argv[2]
    audio = sys.argv[3]
    enc = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
         "-i", audio, "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
         "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-colorspace", "bt709", "-color_primaries", "bt709",
         "-color_trc", "bt709", "-color_range", "tv", "-c:a", "aac", "-b:a", "256k", "-shortest", "-movflags", "+faststart", out],
        stdin=subprocess.PIPE)
    dec, cur = None, None
    for n in range(N):
        s = shot_at(n)
        if s is not cur:
            if dec:
                dec.close()
            dec, cur = Decoder(s), s
        enc.stdin.write(render(n, s, dec.read(), g).tobytes())
        if n % 120 == 0:
            print(f"{n}/{N}", flush=True)
    if dec:
        dec.close()
    enc.stdin.close()
    enc.wait()
    print("done", N, "frames", N / FPS, "s")


if __name__ == "__main__":
    main()
