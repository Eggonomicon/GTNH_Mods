"""Valier pipe benchmark charts: two SVGs for the README and a PDF, drawn from one layout to scale.

usage: python tools/bench_charts.py docs <pdf path>   (Windows: it embeds Arial from C:/Windows/Fonts in the PDF)
"""
import sys
from xml.sax.saxutils import escape

from reportlab.lib.pagesizes import letter
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# One font for measuring and drawing, embedded in the PDF, so text lands where it was measured.
pdfmetrics.registerFont(TTFont('VArial', 'C:/Windows/Fonts/arial.ttf'))
pdfmetrics.registerFont(TTFont('VArial-Bold', 'C:/Windows/Fonts/arialbd.ttf'))
FONT, FONT_B = 'VArial', 'VArial-Bold'

C = dict(
    card='#fdfdfe', card_border='#dfe3ea', ink='#10141a', ink2='#4a5260', muted='#6f7988', grid='#e4e8ee',
    axis='#b9c1cd', valier='#2a78d6', other='#a9b1bd', worse='#e34948',
    on_valier='#ffffff', on_other='#10141a', on_worse='#ffffff')

# ---- the data: ms a tick and amount moved a second, from the benchmark logs (22 Nov 2026) ----
UNIT = [
    dict(group='Items, 256 chest-to-chest rigs', unit='items', rows=[
        dict(vs='GregTech item pipes', tier='Valier Basic', v=(0.194, 2051), o=(0.195, 819)),
        dict(vs='EnderIO item conduits', tier='Valier Basic', v=(0.194, 2051), o=(0.526, 2048)),
        dict(vs='AE2 import and storage bus', tier='Valier Basic', v=(0.146, 2051), o=(1.101, 1007)),
    ]),
    dict(group='Fluids, 128 tank-to-tank rigs', unit='mB', rows=[
        dict(vs='GregTech fluid pipes', tier='Valier Basic', v=(0.421, 128267), o=(0.904, 81920)),
        dict(vs='EnderIO fluid conduits', tier='Valier Basic', v=(0.421, 128267), o=(1.313, 25600)),
        dict(vs='AE2 fluid buses', tier='Valier Basic', v=(0.303, 128133), o=(0.873, 507733)),
        dict(vs='AE2 fluid buses', tier='Valier Advanced', v=(0.471, 1025067), o=(0.873, 507733)),
        dict(vs='AE2 fluid buses', tier='Valier Elite', v=(0.728, 8200533), o=(0.873, 507733)),
    ]),
]
TICK = [
    dict(title='Against GregTech and EnderIO',
         note="One run. The rigs' chests and tanks are counted in every row alike, so Valier's real lead is a little "
              "bigger than shown.",
         groups=[
             dict(group='Items, 256 rigs', unit='items', rows=[
                 dict(name='Valier Basic', valier=True, ms=0.194, rate=2051),
                 dict(name='GregTech (LV conveyor)', ms=0.195, rate=819),
                 dict(name='EnderIO', ms=0.526, rate=2048)]),
             dict(group='Fluids, 128 rigs', unit='mB', rows=[
                 dict(name='Valier Basic', valier=True, ms=0.421, rate=128267),
                 dict(name='GregTech (LV pump)', ms=0.904, rate=81920),
                 dict(name='EnderIO', ms=1.313, rate=25600)]),
             dict(group='Idle, 2,048 item pipe blocks', unit='', rows=[
                 dict(name='Valier', valier=True, ms=-0.003, idle=True),
                 dict(name='GregTech', ms=0.111, idle=True),
                 dict(name='EnderIO', ms=0.785, idle=True)]),
         ]),
    dict(title='Against AE2',
         note="The pipes' own cost: the chests and tanks were timed alone and taken off. Each case ran twice, the "
              "second pass in reverse order, and these are the means.",
         groups=[
             dict(group='Items, 256 rigs', unit='items', rows=[
                 dict(name='Valier Basic', valier=True, ms=0.146, rate=2051),
                 dict(name='AE2 buses', ms=1.101, rate=1007)]),
             dict(group='Fluids, 128 rigs', unit='mB', rows=[
                 dict(name='Valier Basic', valier=True, ms=0.303, rate=128133),
                 dict(name='Valier Advanced', valier=True, ms=0.471, rate=1025067),
                 dict(name='Valier Elite', valier=True, ms=0.728, rate=8200533),
                 dict(name='AE2 fluid buses', ms=0.873, rate=507733)]),
         ]),
]
NOTES = [
    'Test server, 22 Nov 2026: GregTech 5.09.54.265, EnderIO 2.10.51, AE2 rv3-beta-1095-GTNH, ae2fc 1.5.116.',
    "Each case was built in the same empty corner, left 100 ticks to settle and timed for 600 ticks (the overworld's "
    "tick and the server's tick handlers), against the empty corner timed before and after. Autosave ticks and the "
    "slowest 2% are left out.",
    'Starting tiers, default settings: GregTech with LV covers, EnderIO conduits extracting always, AE2 with no upgrade '
    'cards on a creative energy cell. AE2 items and fluids come from two separate benchmarks.',
    'Costs move by about 0.1 ms between runs, and the fluid figures are the less certain ones. AE2 with acceleration '
    'cards was not tested. Run it again with "valiertweaks selftest pipebench" on the test server.',
]


def per_unit(r):
    return 100 * (1 - (r['v'][0] / r['v'][1]) / (r['o'][0] / r['o'][1]))


def less_more(p):
    a = int(abs(p) + 0.5)
    return f'{a}% less' if p >= 0 else f'{a}% more'


def compact(n):
    if n < 10000:
        return f'{n:,}'
    if n < 1_000_000:
        return f'{round(n / 1000)}K'
    return f'{n / 1e6:.1f}M'


def ms(v):
    return f'{max(0.0, v):.2f} ms'


def tw(s, size, bold=False, spacing=0.0):
    return stringWidth(s, FONT_B if bold else FONT, size) + spacing * max(0, len(s) - 1)


def wrap(s, size, maxw, bold=False):
    lines, cur = [], ''
    for w in s.split():
        t = (cur + ' ' + w).strip()
        if tw(t, size, bold) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


class Drawing:
    """Primitives in a top-left coordinate space; rendered to SVG or onto a PDF canvas."""

    def __init__(self):
        self.p = []

    def rect(self, x, y, w, h, fill, r=(0, 0, 0, 0), stroke=None):
        self.p.append(('rect', x, y, w, h, fill, r, stroke))

    def line(self, x1, y1, x2, y2, stroke, width=1.0):
        self.p.append(('line', x1, y1, x2, y2, stroke, width))

    def text(self, x, y, s, size, fill, bold=False, anchor='start', spacing=0.0):
        self.p.append(('text', x, y, s, size, fill, bold, anchor, spacing))


K = 0.5523  # circle-to-Bezier constant


def rr_points(x, y, w, h, r):
    """Path of a rectangle with its own radius at each corner (tl, tr, br, bl), as move/line/curve steps."""
    lim = min(w, h) / 2
    tl, tr, br, bl = (min(c, lim) for c in r)
    return [
        ('M', (x + tl, y)), ('L', (x + w - tr, y)),
        ('C', (x + w - tr + K * tr, y), (x + w, y + tr - K * tr), (x + w, y + tr)),
        ('L', (x + w, y + h - br)),
        ('C', (x + w, y + h - br + K * br), (x + w - br + K * br, y + h), (x + w - br, y + h)),
        ('L', (x + bl, y + h)),
        ('C', (x + bl - K * bl, y + h), (x, y + h - bl + K * bl), (x, y + h - bl)),
        ('L', (x, y + tl)),
        ('C', (x, y + tl - K * tl), (x + tl - K * tl, y), (x + tl, y)),
    ]


# ---- layout pieces ----

def header(d, y, x0, w, S, title, sub, legend):
    d.text(x0, y + S['title'] * 0.95, title, S['title'], C['ink'], True)
    y += S['title'] * 1.45
    for ln in wrap(sub, S['sub'], w):
        d.text(x0, y + S['sub'] * 1.0, ln, S['sub'], C['ink2'])
        y += S['sub'] * 1.45
    y += S['sub'] * 0.6
    x, sw = x0, S['legend']
    for kind, label in legend:
        d.rect(x, y, sw, sw, C[kind], (sw * 0.22,) * 4)
        d.text(x + sw + 6, y + sw * 0.86, label, S['legend'], C['ink2'])
        x += sw + 6 + tw(label, S['legend']) + 18
    return y + sw + 4


def chart_rows(d, y, x0, w, S, groups, dom, gridvals, spec):
    d0, d1 = dom
    px0, px1 = x0 + S['label_w'] + S['gap'], x0 + w

    def pos(v):
        return px0 + (v - d0) / (d1 - d0) * (px1 - px0)

    for g in groups:
        y += S['grp_top']
        d.text(x0, y + S['grp'], g['group'].upper(), S['grp'], C['muted'], True, spacing=S['grp_sp'])
        y += S['grp'] + S['grp_bottom']
        for r in g['rows']:
            s = spec(r, g)
            lines = [(t, S['name'], C['ink']) for t in wrap(s['name'], S['name'], S['label_w'] - 4)]
            if s.get('sub'):
                lines.append((s['sub'], S['subl'], C['muted']))
            text_h = sum(sz * S['lh'] for _, sz, _ in lines)
            rh = max(S['row_h'], text_h + S['row_pad'])
            for t in gridvals:
                X = pos(t)
                d.line(X, y, X, y + rh, C['axis'] if t == 0 else C['grid'], 1.0)
            cur = y + (rh - text_h) / 2
            for t, sz, col in lines:
                lh = sz * S['lh']
                d.text(x0, cur + lh / 2 + sz * 0.35, t, sz, col)
                cur += lh
            v = max(d0, min(d1, s['value']))
            bh, rad = S['bar_h'], S['bar_r']
            by, z, tip = y + (rh - bh) / 2, pos(0), pos(v)
            if v > 0:
                d.rect(z, by, tip - z, bh, C[s['kind']], (0, rad, rad, 0))
            elif v < 0:
                d.rect(tip, by, z - tip, bh, C[s['kind']], (rad, 0, 0, rad))
            vs = S['val']
            vw, bw = tw(s['label'], vs, True), abs(tip - z)
            base = y + rh / 2 + vs * 0.36
            if v >= 0:
                if bw >= vw + 16:
                    d.text(tip - 8, base, s['label'], vs, C['on_' + s['kind']], True, 'end')
                else:
                    d.text(min(tip + 6, px1 - vw), base, s['label'], vs, C['ink'], True)
            elif bw >= vw + 16:
                d.text(tip + 8, base, s['label'], vs, C['on_' + s['kind']], True)
            else:
                d.text(z + 6, base, s['label'], vs, C['ink'], True)
            y += rh
    return y


def chart_axis(d, y, x0, w, S, dom, ticks, caps=None):
    d0, d1 = dom
    px0, px1 = x0 + S['label_w'] + S['gap'], x0 + w
    y += 4
    for t, txt in ticks:
        X = px0 + (t - d0) / (d1 - d0) * (px1 - px0)
        anchor = 'start' if t == d0 else 'end' if t == d1 else 'middle'
        d.text(X, y + S['tick'], txt, S['tick'], C['muted'], anchor=anchor)
    y += S['tick'] + 6
    if caps:
        d.text(px0, y + S['tick'], caps[0], S['tick'], C['muted'])
        d.text(px1, y + S['tick'], caps[1], S['tick'], C['muted'], anchor='end')
        y += S['tick'] + 6
    return y


def unit_spec(r, g):
    p = per_unit(r)
    return dict(name='vs ' + r['vs'], sub=r['tier'], value=p, kind='valier' if p >= 0 else 'worse', label=less_more(p))


def tick_spec(r, g):
    sub = 'nothing to move' if r.get('idle') else f"{compact(r['rate'])} {g['unit']}/s"
    return dict(name=r['name'], sub=sub, value=max(0.0, r['ms']), kind='valier' if r.get('valier') else 'other',
                label=ms(r['ms']))


def card(d, w, h):
    d.p.insert(0, ('rect', 0.5, 0.5, w - 1, h - 1, C['card'], (10, 10, 10, 10), C['card_border']))


def chart_unit(W, S, source=True):
    d, pad = Drawing(), S['pad']
    x0, w = pad, W - 2 * pad
    y = header(d, pad, x0, w, S, 'Server time for the same amount moved',
               'How much less server time Valier takes per item or mB moved, with every system running flat out. '
               'A bar to the right means Valier takes less.',
               [('valier', 'Valier takes less'), ('worse', 'Valier takes more')])
    y = chart_rows(d, y + 2, x0, w, S, UNIT, (-50, 100), (-50, 0, 50, 100), unit_spec)
    y = chart_axis(d, y, x0, w, S, (-50, 100), [(-50, '50%'), (0, '0'), (50, '50%'), (100, '100%')],
                   ('Valier takes more', 'Valier takes less'))
    if source:
        y += 6
        for ln in wrap('Against GregTech and EnderIO: one run, chests and tanks counted alike. Against AE2: the pipes\' '
                       'own cost, the mean of two runs. Test server, 22 Nov 2026, valiertweaks selftest pipebench.',
                       S['note'], w):
            d.text(x0, y + S['note'], ln, S['note'], C['muted'])
            y += S['note'] * 1.45
    y += pad - 4
    card(d, W, y)
    return d, y


def chart_tick(W, S, source=True):
    d, pad = Drawing(), S['pad']
    x0, w = pad, W - 2 * pad
    y = header(d, pad, x0, w, S, 'Cost per tick, flat out',
               'Milliseconds of server time each tick, out of the 50 ms a tick can take. The costliest case here, '
               '256 AE2 item rigs, took 1.1 ms.',
               [('valier', 'Valier'), ('other', 'Other mods')])
    for i, panel in enumerate(TICK):
        y += S['h3'] * (1.4 if i == 0 else 2.2)
        d.text(x0, y, panel['title'], S['h3'], C['ink'], True)
        y += S['h3'] * 0.45
        for ln in wrap(panel['note'], S['note'], w):
            y += S['note'] * 1.45
            d.text(x0, y, ln, S['note'], C['muted'])
        y = chart_rows(d, y, x0, w, S, panel['groups'], (0, 1.4), (0, 0.5, 1.0), tick_spec)
        y = chart_axis(d, y, x0, w, S, (0, 1.4), [(0, '0'), (0.5, '0.5'), (1.0, '1.0 ms')])
    if source:
        y += 6
        d.text(x0, y + S['note'], 'Test server, 22 Nov 2026, valiertweaks selftest pipebench.', S['note'], C['muted'])
        y += S['note'] * 1.45
    y += pad - 4
    card(d, W, y)
    return d, y


# ---- renderers ----

def to_svg(d, W, H, title, desc):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W:.0f}" height="{H:.0f}" viewBox="0 0 {W:.0f} {H:.0f}" '
           f'role="img" aria-labelledby="t d" font-family="Arial, Helvetica, sans-serif">',
           f'<title id="t">{escape(title)}</title>', f'<desc id="d">{escape(desc)}</desc>']
    for p in d.p:
        if p[0] == 'rect':
            _, x, y, w, h, fill, r, stroke = p
            if w <= 0 or h <= 0:
                continue
            path = []
            for step in rr_points(x, y, w, h, r):
                path.append(step[0] + ' '.join(f'{a:.2f},{b:.2f}' for a, b in step[1:]))
            st = f' stroke="{stroke}" stroke-width="1"' if stroke else ''
            out.append(f'<path d="{" ".join(path)}Z" fill="{fill}"{st}/>')
        elif p[0] == 'line':
            _, x1, y1, x2, y2, stroke, width = p
            out.append(f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" stroke="{stroke}" '
                       f'stroke-width="{width}"/>')
        else:
            _, x, y, s, size, fill, bold, anchor, spacing = p
            attrs = f' font-weight="700"' if bold else ''
            if anchor != 'start':
                attrs += f' text-anchor="{anchor}"'
            if spacing:
                attrs += f' letter-spacing="{spacing}"'
            out.append(f'<text x="{x:.2f}" y="{y:.2f}" font-size="{size}" fill="{fill}"{attrs}>{escape(s)}</text>')
    out.append('</svg>')
    return '\n'.join(out) + '\n'


def draw_pdf(c, d, ox, oy, page_h):
    """Draw a Drawing with its top-left at (ox, oy) in top-left page coordinates."""
    for p in d.p:
        if p[0] == 'rect':
            _, x, y, w, h, fill, r, stroke = p
            if w <= 0 or h <= 0:
                continue
            path = c.beginPath()
            for step in rr_points(ox + x, oy + y, w, h, r):
                pts = [(a, page_h - b) for a, b in step[1:]]
                if step[0] == 'M':
                    path.moveTo(*pts[0])
                elif step[0] == 'L':
                    path.lineTo(*pts[0])
                else:
                    path.curveTo(pts[0][0], pts[0][1], pts[1][0], pts[1][1], pts[2][0], pts[2][1])
            path.close()
            c.setFillColor(fill)
            if stroke:
                c.setStrokeColor(stroke)
                c.setLineWidth(0.8)
            c.drawPath(path, fill=1, stroke=1 if stroke else 0)
        elif p[0] == 'line':
            _, x1, y1, x2, y2, stroke, width = p
            c.setStrokeColor(stroke)
            c.setLineWidth(width * 0.7)
            c.line(ox + x1, page_h - (oy + y1), ox + x2, page_h - (oy + y2))
        else:
            _, x, y, s, size, fill, bold, anchor, spacing = p
            font = FONT_B if bold else FONT
            c.setFillColor(fill)
            X = ox + x
            wdt = tw(s, size, bold, spacing)
            if anchor == 'middle':
                X -= wdt / 2
            elif anchor == 'end':
                X -= wdt
            t = c.beginText(X, page_h - (oy + y))
            t.setFont(font, size)
            t.setCharSpace(spacing)
            t.textOut(s)
            c.drawText(t)


SVG_S = dict(pad=28, title=19, sub=13.5, legend=12, grp=11, grp_sp=0.8, grp_top=16, grp_bottom=8, name=13.5,
             subl=11.5, lh=1.32, row_pad=8, row_h=42, bar_h=18, bar_r=4, val=12.5, tick=11.5, label_w=230, gap=16, h3=15, note=12)
PDF_S = dict(pad=16, title=12.5, sub=9, legend=8, grp=7, grp_sp=0.6, grp_top=9, grp_bottom=4, name=8.8,
             subl=7.4, lh=1.2, row_pad=4, row_h=22, bar_h=11, bar_r=2.6, val=8, tick=7.4, label_w=148, gap=10, h3=9.6, note=7.8)


def write_pdf(path):
    W, H = letter
    M = 42
    cw = W - 2 * M
    c = canvas.Canvas(path, pagesize=letter)
    c.setTitle('Valier pipe benchmark')
    c.setSubject('Server time Valier pipes take against GregTech pipes, EnderIO conduits and AE2 buses')
    c.setCreator('valier-tweaks PipeBench charts')
    page = [1]

    def footer():
        c.setFillColor(C['muted'])
        c.setFont(FONT, 7.4)
        c.drawString(M, 26, 'Valier pipe benchmark  ·  test server, 22 Nov 2026')
        c.drawRightString(W - M, 26, f'Page {page[0]}')

    def new_page():
        footer()
        c.showPage()
        page[0] += 1

    # page 1: title, headline figures, the per-amount chart
    y = M
    c.setFillColor(C['muted'])
    t = c.beginText(M, H - (y + 8))
    t.setFont(FONT_B, 7.6)
    t.setCharSpace(0.7)
    t.textOut('VALIER TWEAKS 2.3.3  ·  TEST SERVER  ·  22 NOV 2026')
    t.setCharSpace(0)  # the spacing is text state: it would carry over to every line after
    c.drawText(t)
    y += 16
    c.setFillColor(C['ink'])
    c.setFont(FONT_B, 24)
    c.drawString(M, H - (y + 22), 'Valier pipe benchmark')
    y += 34
    c.setFillColor(C['ink2'])
    c.setFont(FONT, 10.5)
    for ln in wrap('How much server time Valier pipes take to move items and fluids, against GregTech pipes, EnderIO '
                   'conduits and AE2 buses doing the same job on the same rigs.', 10.5, cw):
        y += 14.5
        c.drawString(M, H - y, ln)
    y += 18
    figs = [('60–95%', 'less server time per item or mB moved than GregTech, EnderIO or AE2. One exception: Basic '
                       'fluid pipes against AE2\'s fluid bus.'),
            ('0 ms', 'for 2,048 Valier pipes with nothing to move. The same number of EnderIO conduits took 0.79 ms, '
                     'every tick.'),
            ('64×', 'the fluid for 2.4× the cost when you go from Basic to Elite fluid pipes.')]
    gap = 18
    fw = (cw - 2 * gap) / 3
    bottom = y
    for i, (num, what) in enumerate(figs):
        x = M + i * (fw + gap)
        c.setStrokeColor(C['axis'])
        c.setLineWidth(0.8)
        c.line(x, H - y, x + fw, H - y)
        c.setFillColor(C['ink'])
        c.setFont(FONT_B, 22)
        c.drawString(x, H - (y + 28), num)
        yy = y + 32
        c.setFillColor(C['ink2'])
        c.setFont(FONT, 8.6)
        for ln in wrap(what, 8.6, fw):
            yy += 11.5
            c.drawString(x, H - yy, ln)
        bottom = max(bottom, yy)
    y = bottom + 14
    d, h = chart_unit(cw, PDF_S)
    draw_pdf(c, d, M, y, H)
    y += h + 16

    # how it was measured: here if it fits, else after the second chart
    def notes_height():
        n = 18
        for s in NOTES:
            n += len(wrap(s, 8.6, cw - 12)) * 11.2 + 3
        return n

    def draw_notes(y):
        c.setFillColor(C['ink'])
        c.setFont(FONT_B, 11)
        c.drawString(M, H - (y + 10), 'How it was measured')
        y += 18
        for s in NOTES:
            lines = wrap(s, 8.6, cw - 12)
            c.setFillColor(C['ink2'])
            c.setFont(FONT, 8.6)
            c.drawString(M, H - (y + 8.4), '•')
            for ln in lines:
                y += 11.2
                c.drawString(M + 12, H - (y - 2.8), ln)
            y += 3
        return y

    notes_done = False
    if y + notes_height() < H - 40:
        draw_notes(y)
        notes_done = True
    new_page()

    # page 2: cost per tick
    y = M
    d, h = chart_tick(cw, PDF_S)
    draw_pdf(c, d, M, y, H)
    y += h + 22
    if not notes_done:
        if y + notes_height() >= H - 46:
            new_page()
            y = M
        draw_notes(y)
    footer()
    c.save()
    return page[0]


def main():
    svg_dir, pdf_path = sys.argv[1], sys.argv[2]
    W = 880
    d, h = chart_unit(W, SVG_S)
    desc = '; '.join(f"{r['tier']} vs {r['vs']}: {less_more(per_unit(r))} server time per {g['unit']} moved"
                     for g in UNIT for r in g['rows'])
    open(f'{svg_dir}/pipe-benchmark-per-amount.svg', 'w', encoding='utf-8', newline='\n').write(
        to_svg(d, W, h, 'Valier pipes: server time for the same amount moved', desc))
    d, h2 = chart_tick(W, SVG_S)
    desc = '; '.join(f"{p['title']}, {g['group']}: {r['name']} {ms(r['ms'])} a tick"
                     for p in TICK for g in p['groups'] for r in g['rows'])
    open(f'{svg_dir}/pipe-benchmark-per-tick.svg', 'w', encoding='utf-8', newline='\n').write(
        to_svg(d, W, h2, 'Valier pipes: cost per tick, flat out', desc))
    pages = write_pdf(pdf_path)
    print(f'svg heights {h:.0f} {h2:.0f}; pdf pages {pages}')
    for g in UNIT:
        for r in g['rows']:
            print(f"  {r['tier']} vs {r['vs']}: {less_more(per_unit(r))}")


if __name__ == '__main__':
    main()
