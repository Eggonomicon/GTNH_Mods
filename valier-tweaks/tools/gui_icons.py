"""The pipe menu's icons, drawn from code (no image goes in; see ASSETS-PROVENANCE.md): one 512x16 strip of 16x16 icons (room for 32),
in the order GuiPipeFace's ICON_* constants name them. gen_textures.py writes it to textures/gui/pipe_icons.png."""
import math

from PIL import Image

K = (24, 24, 28)


def _px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y][x] = c


def _rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            _px(img, x, y, c)


def _box(img, fill, dark, top=9):
    """A crate: outlined, two darker planks."""
    _rect(img, 3, top, 12, 15, K)
    _rect(img, 4, top + 1, 11, 14, fill)
    for y in (top + 2, top + 4):
        if y < 15:
            _rect(img, 5, y, 10, y, dark)


def _arrow(img, cx, y0, y1, up, fill):
    """A 2-px shaft from y0 to y1 at columns cx, cx+1 with a head at the pointing end, outlined."""
    lo, hi = min(y0, y1), max(y0, y1)
    head = lo if up else hi
    _rect(img, cx - 1, lo, cx + 2, hi, K)
    _rect(img, cx, lo, cx + 1, hi, fill)
    for i in range(3):
        y = head + (i if up else -i) + (1 if up else -1)
        w = i + 1
        _rect(img, cx - w - 1, y, cx + w + 2, y, K)
        _rect(img, cx - w, y, cx + w + 1, y, fill)


def _sheet(img, paper, line, mark):
    _rect(img, 3, 1, 12, 14, K)
    _rect(img, 4, 2, 11, 13, paper)
    for y in (4, 6, 8, 10):
        _rect(img, 5, y, 10 if y != 8 else 8, y, line)
    if mark == 'check':
        for x, y in ((9, 12), (10, 13), (11, 12), (12, 11), (13, 10), (14, 9)):
            _rect(img, x, y, x, y + 1, (60, 190, 60))
    else:
        for i in range(5):
            _px(img, 9 + i, 9 + i, (220, 50, 50))
            _px(img, 13 - i, 9 + i, (220, 50, 50))


def _circle(img, cx, cy, r_in, r_out, inner, ring):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r_in:
                if inner is not None:
                    img[y][x] = inner
            elif d <= r_out:
                img[y][x] = ring


def icon(n):
    img = [[None] * 16 for _ in range(16)]
    brown, dbrown = (150, 100, 50), (100, 64, 30)
    if n == 0:  # off: a ring with a slash
        _circle(img, 7.5, 7.5, 5.2, 7.2, None, (130, 130, 135))
        for i in range(3, 13):
            _px(img, i, i, (210, 60, 60))
            _px(img, i + 1, i, (150, 40, 40))
    elif n == 1:  # provide: a green crate offering things (a plus), sparkles above
        _box(img, (90, 170, 80), (50, 120, 45), top=6)
        _rect(img, 7, 9, 8, 14, (230, 255, 230))
        _rect(img, 5, 11, 10, 12, (230, 255, 230))
        for x, y in ((3, 2), (8, 1), (12, 3)):
            _px(img, x, y, (120, 230, 120))
            _px(img, x, y + 1, (60, 160, 60))
    elif n == 2:  # push: a blue arrow down into a crate
        _box(img, brown, dbrown)
        _arrow(img, 7, 0, 7, False, (77, 141, 255))
    elif n == 3:  # pull: an orange arrow up out of a crate
        _box(img, brown, dbrown)
        _arrow(img, 7, 0, 8, True, (255, 150, 50))
    elif n == 4:  # request: a clipboard with a list
        _rect(img, 3, 2, 12, 15, K)
        _rect(img, 4, 3, 11, 14, (236, 236, 240))
        _rect(img, 6, 1, 9, 3, (150, 90, 210))
        for y, w in ((6, 6), (8, 4), (10, 6), (12, 3)):
            _rect(img, 5, y, 4 + w, y, (120, 70, 180))
    elif n == 5:  # push/pull: up and down arrows over a crate
        _box(img, (60, 160, 150), (30, 110, 100), top=10)
        _arrow(img, 3, 0, 9, True, (255, 150, 50))
        _arrow(img, 11, 0, 8, False, (77, 141, 255))
    elif n == 6:
        _sheet(img, (245, 245, 245), (90, 90, 90), 'check')
    elif n == 7:
        _sheet(img, (60, 60, 66), (150, 150, 150), 'cross')
    elif n == 8:  # clear: a big red X
        for i in range(2, 14):
            for t in (0, 1):
                _px(img, i + t, i, (220, 50, 50))
                _px(img, 15 - i - t, i, (220, 50, 50))
    elif n == 9:  # check badge (bottom right)
        for x, y in ((9, 12), (10, 13), (11, 12), (12, 11), (13, 10), (14, 9)):
            _rect(img, x - 1, y - 1, x + 1, y + 2, K)
        for x, y in ((9, 12), (10, 13), (11, 12), (12, 11), (13, 10), (14, 9)):
            _rect(img, x, y, x, y + 1, (70, 220, 70))
    elif n == 10:  # cross badge (bottom right)
        for i in range(6):
            _rect(img, 9 + i - 1, 9 + i - 1, 9 + i + 1, 9 + i + 1, K)
            _rect(img, 14 - i - 1, 9 + i - 1, 14 - i + 1, 9 + i + 1, K)
        for i in range(6):
            _px(img, 9 + i, 9 + i, (240, 70, 70))
            _px(img, 14 - i, 9 + i, (240, 70, 70))
    elif n == 11:  # item list: a 2 x 2 grid of slots with things in them
        for gx, gy, c in ((2, 2, (200, 80, 60)), (9, 2, (90, 160, 220)), (2, 9, (230, 200, 80)), (9, 9, (120, 200, 100))):
            _rect(img, gx, gy, gx + 5, gy + 5, K)
            _rect(img, gx + 1, gy + 1, gx + 4, gy + 4, (120, 120, 125))
            _rect(img, gx + 2, gy + 2, gx + 3, gy + 3, c)
    elif n == 12:  # mod list: a book with an M on it
        _rect(img, 2, 2, 13, 14, K)
        _rect(img, 3, 3, 12, 13, (60, 90, 170))
        _rect(img, 3, 3, 4, 13, (40, 60, 120))
        m = ["k...k", "kk.kk", "k.k.k", "k...k", "k...k"]
        for y, row in enumerate(m):
            for x, ch in enumerate(row):
                if ch == 'k':
                    _px(img, 6 + x, 5 + y, (240, 210, 90))
    elif n == 13:  # existing: an eye
        for y in range(16):
            for x in range(16):
                dx, dy = (x - 7.5) / 7.0, (y - 7.5) / 4.2
                v = dx * dx + dy * dy
                if v <= 1.0:
                    img[y][x] = (240, 240, 240)
                elif v <= 1.35:
                    img[y][x] = K
        _circle(img, 7.5, 7.5, 1.4, 3.2, (15, 15, 20), (60, 150, 90))
        _px(img, 6, 6, (255, 255, 255))
    elif n == 14:  # snapshot: a camera
        _rect(img, 1, 4, 14, 13, K)
        _rect(img, 2, 5, 13, 12, (80, 80, 88))
        _rect(img, 4, 2, 7, 4, K)
        _rect(img, 5, 3, 6, 4, (80, 80, 88))
        _circle(img, 7.5, 8.5, 3.0, 3.7, None, K)
        _circle(img, 7.5, 8.5, 1.2, 3.0, (30, 50, 90), (140, 190, 240))
        _px(img, 12, 6, (240, 200, 80))
    elif n == 15:  # tag: a label with a hole
        pts = set((x, y) for y in range(4, 12) for x in range(3, 14) if x - 3 >= abs(y - 7.5) - 1)
        for x, y in pts:
            _px(img, x, y, (230, 200, 120))
        for x, y in list(pts):
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if (nx, ny) not in pts:
                    _px(img, nx, ny, K)
        _rect(img, 5, 7, 6, 8, K)
    elif n == 16:  # void: a black hole, a dark core in a violet swirl
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d <= 3.0:
                    img[y][x] = (8, 4, 14)
                elif d <= 6.8:
                    a = math.atan2(y - 7.5, x - 7.5) + d * 0.9
                    light = 0.5 + 0.5 * math.sin(a * 2)
                    img[y][x] = (int(70 + 110 * light), int(20 + 40 * light), int(110 + 120 * light))
                elif d <= 7.6:
                    img[y][x] = K
    elif n in (17, 18, 19):  # spread: in order, round robin, even split; three bins, filled to show how
        fills = {17: (11, 3, 0), 18: (6, 5, 6), 19: (7, 7, 7)}[n]
        for i, h in enumerate(fills):
            x0 = 1 + i * 5
            _rect(img, x0, 3, x0 + 3, 14, K)
            _rect(img, x0 + 1, 4, x0 + 2, 13, (200, 200, 205))
            if h:
                _rect(img, x0 + 1, 14 - h, x0 + 2, 13, (70, 140, 230))
        if n == 17:  # a "1" over the first bin
            for y in range(0, 3):
                _px(img, 2, y, (240, 210, 90))
                _px(img, 3, y, (240, 210, 90))
        if n == 18:  # a loop arrow along the top
            for x in range(2, 14):
                _px(img, x, 1, (240, 210, 90))
            _px(img, 13, 0, (240, 210, 90))
            _px(img, 13, 2, (240, 210, 90))
            _px(img, 12, 0, (240, 210, 90))
            _px(img, 12, 2, (240, 210, 90))
    return img


COUNT = 20


def strip():
    out = Image.new('RGBA', (512, 16), (0, 0, 0, 0))
    for n in range(COUNT):
        img = icon(n)
        for y in range(16):
            for x in range(16):
                if img[y][x] is not None:
                    out.putpixel((n * 16 + x, y), img[y][x] + (255,))
    return out
