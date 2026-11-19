"""Draws every Valier Tweaks texture from code (no image goes in; see ASSETS-PROVENANCE.md).

    python tools/gen_textures.py            # writes src/main/resources/assets/valiertweaks/textures/...
    python tools/gen_textures.py --preview  # also docs/textures-preview.png (8x, side by side)

Each texture is a function (x, y) -> (r, g, b, a) on a 16x16 grid. Shading is a fixed light from the top left.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'valiertweaks', 'textures')
N = 16


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(rgb, f):
    return tuple(clamp(c * f) for c in rgb)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(seed):
    rnd = random.Random(seed)
    return [[rnd.random() for _ in range(N)] for _ in range(N)]


IRON = (58, 62, 70)
IRON_HI = (118, 124, 134)
GOLD = (196, 152, 52)
GOLD_HI = (246, 214, 120)
GOLD_LO = (120, 86, 26)


def loot_spawner():
    """A dark steel cage with a gold inner frame, gold bar tips and corner rivets; open (transparent) between bars."""
    n = noise(11)

    def px(x, y):
        edge = x in (0, 15) or y in (0, 15)
        inner = x in (1, 14) or y in (1, 14)
        bar = x in (4, 7, 8, 11) and 2 <= y <= 13
        cross = y in (7, 8) and 2 <= x <= 13
        rivet = (x in (1, 2, 13, 14)) and (y in (1, 2, 13, 14))
        if rivet:
            hi = (x in (1, 13)) and (y in (1, 13))
            return GOLD_HI + (255,) if hi else GOLD + (255,)
        if edge:
            f = 1.15 if (x == 0 or y == 0) else 0.75
            return shade(mix(IRON, IRON_HI, n[y][x] * 0.35), f) + (255,)
        if inner:
            f = 1.2 if (x == 1 or y == 1) else 0.7
            return shade(mix(GOLD_LO, GOLD, 0.6 + n[y][x] * 0.4), f) + (255,)
        if bar or cross:
            tip = bar and y in (2, 13)
            if tip:
                return GOLD + (255,)
            lit = x in (4, 8) if bar else y == 7
            return shade(mix(IRON, IRON_HI, 0.25 + n[y][x] * 0.3), 1.25 if lit else 0.85) + (255,)
        return (0, 0, 0, 0)

    return px


CASE = (46, 64, 60)
CASE_HI = (92, 118, 110)
XP = (126, 230, 60)
XP_HI = (214, 255, 150)
XP_LO = (52, 128, 26)


def casing(x, y, n, rim=True):
    """Machine casing: bevelled 1px rim, faint plate noise, rivets in the corners."""
    if rim and (x in (0, 15) or y in (0, 15)):
        f = 1.25 if (x == 0 or y == 0) else 0.65
        return shade(CASE_HI if (x == 0 or y == 0) else CASE, f)
    if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        return CASE_HI
    return shade(mix(CASE, CASE_HI, 0.15 + n[y][x] * 0.2), 1.0)


def xp_crafter_side():
    """Casing with a round window onto glowing liquid XP, swirled lighter towards the top left."""
    n = noise(23)

    def px(x, y):
        dx, dy = x - 7.5, y - 7.5
        d = (dx * dx + dy * dy) ** 0.5
        if d <= 4.6:
            t = max(0.0, min(1.0, (-(dx + dy) / 9.0) + 0.5 + (n[y][x] - 0.5) * 0.3))
            return mix(XP_LO, XP_HI, t) + (255,) if d < 3.6 else XP + (255,)
        if d <= 5.6:
            f = 1.3 if dy < 0 or dx < 0 else 0.7
            return shade(GOLD, f) + (255,)
        return casing(x, y, n) + (255,)

    return px


def xp_crafter_top():
    """Casing with a funnel grate (where liquid XP goes in), a green glow through the slots."""
    n = noise(31)

    def px(x, y):
        if 3 <= x <= 12 and 3 <= y <= 12:
            if x in (3, 12) or y in (3, 12):
                return shade(IRON_HI, 1.1 if (x == 3 or y == 3) else 0.7) + (255,)
            if y % 2 == 0:
                return mix(XP_LO, XP, 0.4 + n[y][x] * 0.4) + (255,)
            return shade(IRON, 1.0 + (0.2 if x < 8 else 0.0)) + (255,)
        return casing(x, y, n) + (255,)

    return px


def xp_crafter_bottom():
    n = noise(37)
    return lambda x, y: casing(x, y, n) + (255,)


# Pipe tiers: Basic bronze, Advanced steel, Elite aluminium blue, Wireless ender violet.
TIER_METAL = [((150, 98, 48), (222, 168, 96)), ((86, 90, 98), (170, 176, 186)), ((70, 110, 160), (150, 196, 240)),
              ((88, 52, 120), (186, 130, 230))]


def pipe(tier, fluid):
    """Banded metal pipe: lit edge rows, a darker seam band every 8 px; fluid pipes show a glass stripe of water."""
    lo, hi = TIER_METAL[tier]
    n = noise(41 + tier * 3 + (1 if fluid else 0))

    def px(x, y):
        if y in (0, 15):
            return shade(hi, 1.05 if y == 0 else 0.7) + (255,)
        if x % 8 in (0, 7):
            return shade(mix(lo, hi, 0.3), 0.8 if x % 8 == 7 else 1.15) + (255,)
        if fluid and 6 <= y <= 9:
            glass = (70, 130, 220) if y in (7, 8) else (150, 200, 245)
            return mix(glass, (255, 255, 255), 0.15 if (x + y) % 5 == 0 else 0.0) + (255,)
        if not fluid and y in (7, 8):
            return shade(lo, 0.75) + (255,)
        t = 0.35 + (1 - abs(y - 7.5) / 7.5) * 0.45 + (n[y][x] - 0.5) * 0.12
        return mix(lo, hi, t) + (255,)

    return px


def pipe_plate():
    """The plate where a pipe touches a block: light grey (tinted by the face's mode), bevelled, a centre dot."""
    n = noise(53)

    def px(x, y):
        if x in (0, 15) or y in (0, 15):
            return shade((235, 235, 235), 1.0 if (x == 0 or y == 0) else 0.6) + (255,)
        if 6 <= x <= 9 and 6 <= y <= 9:
            return (120, 120, 120, 255)
        v = 200 + int((n[y][x] - 0.5) * 24)
        return (v, v, v, 255)

    return px


# name -> (folder, function)
TEXTURES = {
    'pipe_plate': ('blocks', pipe_plate),
    'item_pipe_0': ('blocks', lambda: pipe(0, False)),
    'item_pipe_1': ('blocks', lambda: pipe(1, False)),
    'item_pipe_2': ('blocks', lambda: pipe(2, False)),
    'item_pipe_3': ('blocks', lambda: pipe(3, False)),
    'fluid_pipe_0': ('blocks', lambda: pipe(0, True)),
    'fluid_pipe_1': ('blocks', lambda: pipe(1, True)),
    'fluid_pipe_2': ('blocks', lambda: pipe(2, True)),
    'fluid_pipe_3': ('blocks', lambda: pipe(3, True)),
    'loot_spawner': ('blocks', loot_spawner),
    'xp_crafter_side': ('blocks', xp_crafter_side),
    'xp_crafter_top': ('blocks', xp_crafter_top),
    'xp_crafter_bottom': ('blocks', xp_crafter_bottom),
}


def render(fn):
    img = Image.new('RGBA', (N, N))
    for y in range(N):
        for x in range(N):
            img.putpixel((x, y), fn(x, y))
    return img


def main():
    images = []
    import gui_icons
    icons = gui_icons.strip()
    os.makedirs(os.path.join(OUT, 'gui'), exist_ok=True)
    icons.save(os.path.join(OUT, 'gui', 'pipe_icons.png'))
    print('wrote gui/pipe_icons.png')
    for name, (folder, make) in TEXTURES.items():
        img = render(make())
        d = os.path.join(OUT, folder)
        os.makedirs(d, exist_ok=True)
        img.save(os.path.join(d, name + '.png'))
        images.append(img)
        print('wrote', os.path.join(folder, name + '.png'))
    if '--preview' in sys.argv:
        scale = 8
        sheet = Image.new('RGBA', (len(images) * (N * scale + 8), N * scale), (40, 40, 40, 255))
        for i, img in enumerate(images):
            big = img.resize((N * scale, N * scale), Image.NEAREST)
            sheet.alpha_composite(big, (i * (N * scale + 8), 0))
        os.makedirs(os.path.join(ROOT, 'docs'), exist_ok=True)
        sheet.save(os.path.join(ROOT, 'docs', 'textures-preview.png'))
        bg = Image.new('RGBA', (256 * 4 + 8, 16 * 4 + 8), (198, 198, 198, 255))
        bg.alpha_composite(icons.resize((256 * 4, 16 * 4), Image.NEAREST), (4, 4))
        bg.save(os.path.join(ROOT, 'docs', 'pipe-icons-preview.png'))
        print('wrote docs/textures-preview.png')


if __name__ == '__main__':
    main()
