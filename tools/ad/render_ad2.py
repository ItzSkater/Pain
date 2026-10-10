#!/usr/bin/env python3
"""Реклама Pain 1.2.2 «Firrty Update». Без музыки: ритм задают сами звуки чита из записи.
Склейки, наезды камеры и подписи привязаны к моментам выстрелов и ударов, найденным в звуке.

  render_ad2.py test N [N ...]     — сохранить отдельные кадры в test_NNNN.png
  render_ad2.py audio OUT.wav      — собрать звук из тех же кусков записи
  render_ad2.py full OUT.mp4 A.wav — собрать ролик целиком
"""
import math
import os
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 60
SRC = os.path.expanduser("~/firtun.mov")
LOGO = os.path.expanduser("~/.claude/PainVideo/pain_logo_transparent.png")
FONT = "/usr/share/fonts/TTF/Rubik%5Bwght%5D.ttf"

RED = (255, 48, 48)
ORANGE = (232, 128, 92)
GA, GB = (255, 70, 60), (240, 150, 100)
WHITE = (255, 255, 255)

GRADE = ("hqdn3d=2:1.5:3:3,"
         "curves=all='0/0 0.08/0.10 0.25/0.36 0.5/0.66 0.8/0.92 1/1',"
         "eq=saturation=1.35:contrast=1.06,unsharp=5:5:0.4")
# области кадра исходника: (ширина, высота, x, y)
FULL = (1600, 900, 0, 120)         # фон заставки и финала: без миникарты
C_STATIC = (1280, 720, 130, 110)   # цель вдалеке, крупнее
C_BOW = (1600, 900, 100, 100)      # вид с луком: цель и лук в кадре, без HUD сверху и миникарты
C_MELEE = (1440, 810, 20, 110)     # ближний бой: цель по центру
GUI = (1440, 810, 238, 134)        # меню целиком

# моменты звуков в исходнике, секунды (найдены сравнением со звуками мода)
EVENTS = [6.196, 7.358, 8.542, 9.758, 15.327, 16.052, 22.623, 25.374, 25.982, 28.329, 28.980, 29.630, 32.382, 33.129,
          41.439, 42.975, 43.550, 44.212, 44.703, 46.484, 47.008, 47.367, 49.730, 50.156, 50.953, 51.359, 51.622,
          53.483, 54.089, 54.593, 55.038, 55.337, 57.00, 57.67, 58.20, 58.87, 59.46, 60.15, 60.66, 61.18, 61.70]

# раскладка: (раздел, начало в исходнике, длительность, область, вид, склейка, подпись звука)
LAYOUT = [
    ("intro", 5.90, 2.50, FULL, "bg", "none", None),
    ("f1", 8.30, 1.95, C_STATIC, "clip", "feature", None),
    ("f1", 17.30, 1.65, GUI, "clip", "cut", None),
    ("f1", 15.05, 1.45, C_BOW, "clip", "cut", None),
    ("f2", 20.20, 1.00, GUI, "clip", "feature", None),
    ("f2", 32.15, 1.30, C_BOW, "clip", "cut", "Nonagon shoot"),
    ("f2", 22.40, 0.95, C_BOW, "clip", "cut", "Металл"),
    ("f2", 25.75, 0.95, C_BOW, "clip", "cut", "Пузырь"),
    ("f2", 28.75, 1.05, C_BOW, "clip", "cut", "Щелчок"),
    ("f3", 39.15, 1.05, GUI, "clip", "feature", None),
    ("f3", 42.80, 2.07, C_MELEE, "clip", "cut", "Nonagon shoot"),
    ("f3", 46.85, 0.75, C_MELEE, "clip", "cut", "Звон"),
    ("f3", 50.80, 1.05, C_MELEE, "clip", "cut", "Металл"),
    ("f3", 53.95, 1.60, C_MELEE, "clip", "cut", "Пузырь"),
    ("f3", 57.25, 2.40, C_MELEE, "clip", "cut", "+ HitEffect: конфетти"),
    ("outro", 59.65, 4.20, FULL, "bg", "none", None),
]
CARDS = [
    ("f1", "01", "RageBot", "Сам наводится и стреляет: лук, арбалет, трезубец, снежки, яйца"),
    ("f2", "02", "ShootSounds", "Свой звук выстрела из лука и арбалета"),
    ("f3", "03", "HitSound", "Свой звук попадания — выбирай любой"),
]


class Shot:
    def __init__(self, section, src, dur, crop, kind, start, chip, f0):
        self.section, self.src, self.crop, self.kind, self.start, self.chip = section, src, crop, kind, start, chip
        self.n = int(round(dur * FPS))
        self.f0, self.f1 = f0, f0 + self.n
        self.speed = 1.0
        # звуки, попавшие в этот кусок: время от начала куска
        self.events = [e - src for e in EVENTS if src - 0.02 <= e < src + self.n / FPS]


def build_shots():
    shots, f = [], 0
    for section, src, dur, crop, kind, start, chip in LAYOUT:
        s = Shot(section, src, dur, crop, kind, start, chip, f)
        shots.append(s)
        f = s.f1
    return shots


SHOTS = build_shots()
N = SHOTS[-1].f1
for i, s in enumerate(SHOTS):
    s.last_of_section = i + 1 == len(SHOTS) or SHOTS[i + 1].section != s.section


def section_span(name):
    fs = [s for s in SHOTS if s.section == name]
    return fs[0].f0 / FPS, fs[-1].f1 / FPS


# ---------- декодирование ----------

def vf_for(shot):
    w, h, x, y = shot.crop
    if shot.kind == "bg":
        return f"crop={w}:{h}:{x}:{y},scale=960:540:flags=bicubic,gblur=sigma=10,eq=brightness=-0.02:saturation=1.3", (960, 540)
    return f"crop={w}:{h}:{x}:{y},{GRADE}", (w, h)


class Decoder:
    def __init__(self, shot, first=0, count=None):
        vf, self.size = vf_for(shot)
        ss = shot.src + first / FPS
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


def chip_sprite(text):
    """Плашка с названием звука; слева оставлено место под столбики эквалайзера."""
    f = font(46, 700)
    tw = int(ImageDraw.Draw(Image.new("RGBA", (4, 4))).textlength(text, font=f))
    w, h = tw + 190, 124
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((16, 22, w - 16, h - 12), radius=42, fill=(0, 0, 0, 170))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(10)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((16, 16, w - 16, h - 16), radius=42, fill=(14, 14, 19, 232), outline=(255, 255, 255, 54), width=2)
    d.text((126, h / 2 - 1), text, font=f, fill=(248, 248, 252, 255), anchor="lm")
    return img


class Graphics:
    def __init__(self):
        self.shade_feature, self.shade_dark = make_shades()
        self.white = Image.new("RGB", (W, H), (255, 255, 255))
        self.black = Image.new("RGB", (W, H), (0, 0, 0))
        self.pill = split(pill_sprite("Pain 1.2.2"))
        self.cards = []
        for name, num, title, sub in CARDS:
            t0, t1 = section_span(name)
            n_img, nw = text_sprite([(num, "grad")], 190, 800, shadow_blur=16, shadow_off=8, shadow_alpha=230)
            t_img, _ = text_sprite([(title, WHITE)], 92, 800)
            s_img, _ = text_sprite([(sub, (232, 232, 240))], 42, 500, shadow_blur=8, shadow_off=3, shadow_alpha=250)
            bar = Image.new("RGBA", (8, 150), (0, 0, 0, 0))
            ImageDraw.Draw(bar).rounded_rectangle((0, 0, 7, 149), radius=4, fill=ORANGE + (255,))
            self.cards.append(dict(t0=t0, t1=t1, num=split(n_img), nw=nw, title=split(t_img), sub=split(s_img), bar=bar))
        self.chips = {}
        for s in SHOTS:
            if s.chip and s.chip not in self.chips:
                img = chip_sprite(s.chip)
                self.chips[s.chip] = (split(img), img.size)
        self.logo_hi = logo_sprite(460)
        self.glow = split(glow_sprite(300))
        t, self.intro_title_w = text_sprite([("Pain ", WHITE), ("1.2.2", "grad")], 150, 800, shadow_blur=18, shadow_off=8)
        self.intro_title = split(t)
        t, self.intro_sub_w = text_sprite([("Firrty Update", (236, 236, 244))], 54, 600, shadow_blur=10, shadow_off=4)
        self.intro_sub = split(t)
        self.glow_big = split(glow_sprite(330, blur=52))
        t, self.outro_title_w = text_sprite([("Pain", WHITE), (" Client", RED)], 150, 800, shadow_blur=18, shadow_off=8)
        self.outro_title = split(t)
        t, self.outro_sub_w = text_sprite([("v1.2.2  ·  Firrty Update", (236, 236, 244))], 48, 600, shadow_blur=10, shadow_off=4)
        self.outro_sub = split(t)
        t, self.outro_line_w = text_sprite([("Идеи — Firrty   ·   t.me/painclientbtw", (200, 200, 214))], 36, 500, shadow_blur=8, shadow_off=3)
        self.outro_line = split(t)

    def logo_at(self, frame, cx, cy, base_h, scale, alpha):
        h = max(8, int(round(base_h * scale)))
        w = int(round(self.logo_hi.width * h / self.logo_hi.height))
        spr = self.logo_hi.resize((w, h), Image.BICUBIC)
        put(frame, split(spr), cx - w / 2, cy - h / 2, alpha)

    def card(self, frame, c, t):
        t0, t1 = c["t0"], c["t1"]
        if t < t0 or t >= t1:
            return
        x0, y0 = 90, 760
        tx = x0 + c["nw"] + 44
        out = ease_in((t - (t1 - 0.30)) / 0.24)
        p = ease_out((t - t0 - 0.04) / 0.36)
        put(frame, c["num"], x0 - PAD, y0 - PAD + 70 * (1 - p) - 30 * out, p * (1 - out))
        p = ease_out((t - t0 - 0.14) / 0.30)
        if p > 0.02:
            bar = c["bar"].crop((0, 0, 8, max(1, int(150 * p))))
            put(frame, split(bar), tx - 24, y0 + 38, (1 - out))
        p = ease_out((t - t0 - 0.16) / 0.38)
        put(frame, c["title"], tx - PAD - 60 * (1 - p), y0 + 22 - PAD, p * (1 - out))
        p = ease_out((t - t0 - 0.30) / 0.40)
        put(frame, c["sub"], tx + 4 - PAD - 44 * (1 - p), y0 + 138 - PAD, p * (1 - out))

    def chip(self, frame, shot, tau):
        """Название звука сверху по центру; столбики подпрыгивают в момент каждого звука."""
        spr, (w, h) = self.chips[shot.chip]
        p = ease_out(tau / 0.16)
        x = (W - w) / 2
        y = 58 - 18 * (1 - p)
        hit = 0.0
        for e in shot.events:
            if tau >= e:
                hit = max(hit, math.exp(-(tau - e) / 0.16))
        y += 7 * hit
        put(frame, spr, x, y, p)
        if p < 0.3:
            return
        d = ImageDraw.Draw(frame, "RGBA")
        bx, cy = x + 44, y + h / 2
        weights = (0.55, 1.0, 0.75, 0.9)
        for i, wgt in enumerate(weights):
            wob = 0.5 + 0.5 * math.sin(tau * (9.0 + 2.3 * i) + i * 1.7)
            hh = 8 + 34 * hit * wgt * (0.65 + 0.35 * wob) + 5 * wob * (1 - hit)
            col = (255, int(86 + 60 * i / 3), int(70 + 30 * i / 3), int(255 * p))
            d.rounded_rectangle((bx + i * 16, cy - hh / 2, bx + i * 16 + 9, cy + hh / 2), radius=4, fill=col)

    def progress(self, frame, t, alpha):
        if alpha <= 0.01:
            return
        d = ImageDraw.Draw(frame, "RGBA")
        x0, x1, gap, y = 90, W - 90, 12, 1044
        n = len(self.cards)
        sw = (x1 - x0 - gap * (n - 1)) / n
        for i, c in enumerate(self.cards):
            xa = x0 + i * (sw + gap)
            d.rounded_rectangle((xa, y, xa + sw, y + 5), radius=3, fill=(255, 255, 255, int(58 * alpha)))
            p = clamp((t - c["t0"]) / (c["t1"] - c["t0"]))
            if p > 0:
                col = (255, 255, 255, int(190 * alpha)) if p >= 1 else (255, 86, 70, int(255 * alpha))
                d.rounded_rectangle((xa, y, xa + max(6, sw * p), y + 5), radius=3, fill=col)


T_F1 = section_span("f1")[0]
T_OUTRO = section_span("outro")[0]
INTRO_EV = SHOTS[0].events          # выстрелы под заставкой: на них появляются логотип и название
OUTRO_EV = SHOTS[-1].events         # удары под финалом


def render(n, shot, raw, g):
    t = n / FPS
    tau = (n - shot.f0) / FPS
    dur = shot.n / FPS
    left = shot.f1 - 1 - n
    section = shot.section

    # --- камера ---
    z = 1.0 + 0.03 * (tau / max(dur, 1e-3))
    z += {"feature": 0.10, "cut": 0.06, "none": 0.0}[shot.start] * math.exp(-tau / 0.11)
    kick = 0.0
    for e in shot.events:                               # толчок камеры на каждый звук
        if tau >= e:
            kick = max(kick, math.exp(-(tau - e) / 0.09))
    blur = 0.0
    if shot.kind == "clip":
        z += 0.035 * kick
        if shot.last_of_section and left < 6:           # разгон в следующий раздел
            p = (6 - left) / 6.0
            z *= 1.0 + 0.13 * p * p
            blur = 7.0 * p * p
        if shot.start == "feature":
            blur = max(blur, 7.0 * math.exp(-tau / 0.035))
    elif section == "intro":
        z = 1.10 - 0.05 * ease_out(tau / dur) + 0.02 * kick
        if left < 8:
            p = (8 - left) / 8.0
            z *= 1.0 + 0.20 * p * p
    else:
        z = 1.12 - 0.07 * ease_out(tau / 3.0) + 0.02 * kick

    frame = camera(raw, z, (0.0, 0.0), Image.BILINEAR if shot.kind != "clip" else Image.BICUBIC)
    if blur > 0.6:
        frame = frame.filter(ImageFilter.BoxBlur(blur))
    if shot.kind == "clip":
        split_px = {"feature": 12.0, "cut": 6.0, "none": 0.0}[shot.start] * math.exp(-tau / 0.045) + 4.0 * kick * kick
        frame = rgb_split(frame, split_px)

    # --- затемнение под графику ---
    if section == "outro":
        # резкость уходит в размытый тёмный фон не мгновенно, а за треть секунды
        k = ease_out(tau / 0.35)
        shade = g.shade_dark if k >= 0.999 else Image.blend(g.shade_feature, g.shade_dark, k)
        frame = ImageChops.multiply(frame, shade)
    else:
        frame = ImageChops.multiply(frame, g.shade_dark if section == "intro" else g.shade_feature)

    # --- вспышка ---
    fl = {"feature": 0.30, "cut": 0.12, "none": 0.0}[shot.start] * math.exp(-tau / 0.055)
    if shot.kind == "clip":
        fl += 0.05 * kick
    if fl > 0.01:
        frame = Image.blend(frame, g.white, min(0.6, fl))

    # --- графика ---
    if section in ("f1", "f2", "f3"):
        for c in g.cards:
            g.card(frame, c, t)
        if shot.chip:
            g.chip(frame, shot, tau)
        vis = ease_out((t - T_F1) / 0.35)
        put(frame, g.pill, W - g.pill[0].width - 58, 30 - 26 * (1 - vis), vis)
        g.progress(frame, t, vis)
    elif section == "intro":
        t_logo = INTRO_EV[0] if INTRO_EV else 0.3
        t_title = INTRO_EV[1] if len(INTRO_EV) > 1 else t_logo + 1.1
        out = ease_in((t - (dur - 0.26)) / 0.22)
        a_all = 1.0 - out
        pulse = max([math.exp(-(t - e) / 0.14) for e in INTRO_EV if t >= e] + [0.0])
        cx, cy = W / 2, 318
        u = t - t_logo
        if u >= 0:
            s = 1.0 + 0.55 * (1.0 - ease_out(u / 0.30)) + 0.10 * out
            a = clamp(u / 0.06) * a_all
            put(frame, g.glow, cx - g.glow[0].width / 2, cy - g.glow[0].height / 2, (0.45 + 0.50 * pulse) * a)
            g.logo_at(frame, cx, cy, 300, s, a)
            fl = 0.40 * math.exp(-u / 0.06)
            if fl > 0.01:
                frame = Image.blend(frame, g.white, fl)
        p = ease_out((t - t_title) / 0.30)
        put(frame, g.intro_title, (W - g.intro_title_w) / 2 - PAD, 508 - PAD + 46 * (1 - p), p * a_all)
        p = ease_out((t - t_title - 0.20) / 0.32)
        put(frame, g.intro_sub, (W - g.intro_sub_w) / 2 - PAD, 690 - PAD + 26 * (1 - p), p * a_all)
    else:
        ev = OUTRO_EV + [OUTRO_EV[-1] + 0.5 * (i + 1) for i in range(4)] if OUTRO_EV else [0.5, 1.0, 1.5, 2.0]
        pulse = max([math.exp(-(tau - e) / 0.16) for e in OUTRO_EV if tau >= e] + [0.0])
        cx, cy = W / 2, 300
        u = tau - ev[0]
        if u >= 0:
            s = 1.0 + 0.55 * (1.0 - ease_out(u / 0.30))
            a = clamp(u / 0.06)
            put(frame, g.glow_big, cx - g.glow_big[0].width / 2, cy - g.glow_big[0].height / 2, (0.45 + 0.50 * pulse) * a)
            g.logo_at(frame, cx, cy, 330, s, a)
            fl = 0.42 * math.exp(-u / 0.06)
            if fl > 0.01:
                frame = Image.blend(frame, g.white, fl)
        p = ease_out((tau - ev[1]) / 0.34)
        put(frame, g.outro_title, (W - g.outro_title_w) / 2 - PAD, 520 - PAD + 46 * (1 - p), p)
        p = ease_out((tau - ev[2]) / 0.34)
        put(frame, g.outro_sub, (W - g.outro_sub_w) / 2 - PAD, 702 - PAD + 26 * (1 - p), p)
        p = ease_out((tau - ev[3]) / 0.38)
        put(frame, g.outro_line, (W - g.outro_line_w) / 2 - PAD, 794 - PAD + 20 * (1 - p), p)
        fade = clamp((tau - (dur - 1.15)) / 1.05)
        if fade > 0:
            frame = Image.blend(frame, g.black, fade)
    if n < 8:                                             # ролик открывается из чёрного
        frame = Image.blend(frame, g.black, 1.0 - (n + 1) / 9.0)
    return frame


def shot_at(n):
    for s in SHOTS:
        if s.f0 <= n < s.f1:
            return s
    return SHOTS[-1]


def build_audio(out):
    """Звук из тех же кусков записи, что и картинка, с микрозатуханиями на склейках."""
    parts, labels = [], []
    for i, s in enumerate(SHOTS):
        d = s.n / FPS
        parts.append(f"[0:a]atrim=start={s.src:.5f}:duration={d:.5f},asetpts=PTS-STARTPTS,"
                     f"afade=t=in:st=0:d=0.012,afade=t=out:st={d - 0.014:.5f}:d=0.014[a{i}]")
        labels.append(f"[a{i}]")
    total = N / FPS
    parts.append("".join(labels) + f"concat=n={len(labels)}:v=0:a=1[mix]")
    tmp = out + ".raw.wav"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", SRC, "-filter_complex", ";".join(parts), "-map", "[mix]",
                    "-ar", "48000", "-c:a", "pcm_s16le", tmp], check=True)
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", tmp, "-af", "ebur128", "-f", "null", "-"],
                       capture_output=True, text=True).stderr
    lufs = float([ln for ln in r.splitlines() if " I:" in ln][-1].split("I:")[1].split("LUFS")[0])
    gain = max(-12.0, min(12.0, -15.0 - lufs))
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-af",
                    f"volume={gain:.2f}dB,alimiter=limit=0.89:attack=2:release=60:level=false,"
                    f"afade=t=in:st=0:d=0.10,afade=t=out:st={total - 1.25:.3f}:d=1.25",
                    "-ar", "48000", "-c:a", "pcm_s16le", out], check=True)
    os.remove(tmp)
    print(f"audio: {total:.2f} s, было {lufs:.1f} LUFS, усиление {gain:+.1f} dB")


def main():
    mode = sys.argv[1]
    if mode == "audio":
        build_audio(sys.argv[2])
        return
    if mode == "plan":
        for s in SHOTS:
            print(f"{s.f0 / FPS:6.2f}-{s.f1 / FPS:6.2f}  {s.section:<5} src {s.src:6.2f}  {s.kind:<4} {s.start:<7} "
                  f"chip={s.chip}  звуки на {[round(s.f0 / FPS + e, 2) for e in s.events]}")
        print("кадров", N, "длительность", N / FPS)
        return
    g = Graphics()
    if mode == "test":
        for a in sys.argv[2:]:
            n = int(a)
            s = shot_at(n)
            dec = Decoder(s, first=n - s.f0, count=4)
            raw = dec.read()
            for _ in range(3):
                raw = dec.read()
            dec.close()
            render(n, s, raw, g).save(f"test_{n:04d}.png")
            print("frame", n, f"t={n / FPS:.2f}", s.section, s.kind)
        return
    out, audio = sys.argv[2], sys.argv[3]
    enc = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
         "-i", audio, "-vf", "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p",
         "-c:v", "libx264", "-preset", "ultrafast", "-crf", "12", "-threads", "4",
         "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv",
         "-c:a", "aac", "-b:a", "256k", "-shortest", "-movflags", "+faststart", out],
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
