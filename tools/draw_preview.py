"""Draws the Workshop thumbnail, mod/preview.png: a square paper scrap with a
pencil doodle of a two-screen handheld. Original art drawn in code (no game
art, no AI image generator). Drawn at 128x128 and scaled 4x to 512x512.

    python3 tools/draw_preview.py [out.png]

Needs Pillow and numpy.
"""
import math, random, sys
import numpy as np
from PIL import Image, ImageDraw

N = 128
BG = np.array((34, 24, 20), float)
BG_SPECK = np.array((44, 32, 26), float)
SHADOW = np.array((14, 9, 8), float)
PAPER = np.array((197, 179, 153), float)
SCRATCH_DK = np.array((178, 159, 133), float)
SCRATCH_LT = np.array((210, 194, 171), float)
OUTLINE = np.array((22, 17, 16), float)
FOLD = np.array((170, 150, 124), float)
INK = np.array((58, 46, 40), float)


def erode(mask, times):
    """Shrink a mask by one pixel per pass (4-neighbour), treating outside as empty."""
    m = mask.copy()
    for _ in range(times):
        p = np.pad(m, 1)
        m = m & p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
    return m


def wobble_path(pts, rnd, amt=0.45, closed=False, overshoot=1.2):
    """Resample a polyline and jitter it, with a little overshoot at the ends like a real pencil."""
    out = []
    seq = pts + ([pts[0]] if closed else [])
    for (ax, ay), (bx, by) in zip(seq, seq[1:]):
        L = math.hypot(bx - ax, by - ay)
        n = max(1, int(L / 3))
        for i in range(n):
            t = i / n
            out.append((ax + (bx - ax) * t + rnd.uniform(-amt, amt), ay + (by - ay) * t + rnd.uniform(-amt, amt)))
    out.append(seq[-1])
    if not closed and len(out) > 2 and overshoot:
        (ax, ay), (bx, by) = out[-2], out[-1]
        L = math.hypot(bx - ax, by - ay) or 1
        out.append((bx + (bx - ax) / L * overshoot, by + (by - ay) / L * overshoot))
    return out


def arc(cx, cy, rx, ry, a0, a1, n=14):
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def scrap_shape(seed=4, box=(9, 9, 117, 116), ss=4):
    """Wavy-edged square with a folded top-left corner and one small nick, as a mask."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    fold = 10
    pts = []
    def edge(a, b, fixed, horiz, phase, nick=None):
        n = int(abs(b - a) / 2)
        out = []
        for i in range(n + 1):
            t = a + (b - a) * i / n
            off = 1.1 * math.sin(i / n * math.pi * 2.2 + phase) + rnd.uniform(-0.25, 0.25)
            if nick is not None and abs(i - nick) <= 1:
                off += -2.2 if i == nick else -1.0
            out.append((t, fixed + off) if horiz else (fixed + off, t))
        return out
    pts += edge(x0 + fold, x1 - 2, y0, True, 0.3)
    pts += edge(y0 + 2, y1 - 2, x1, False, 1.7, nick=46)  # a small nick in the right edge
    pts += edge(x1 - 2, x0 + 2, y1, True, 2.9)
    pts += edge(y1 - 2, y0 + fold, x0, False, 4.1)
    big = Image.new('L', (N * ss, N * ss), 0)
    ImageDraw.Draw(big).polygon([(x * ss, y * ss) for x, y in pts], fill=255)
    mask = np.array(big).reshape(N, ss, N, ss).mean(axis=(1, 3)) > 127
    # the folded-over flap: a small triangle lying on the paper
    flap = Image.new('L', (N * ss, N * ss), 0)
    ImageDraw.Draw(flap).polygon([((x0 + fold) * ss, y0 * ss), (x0 * ss, (y0 + fold) * ss),
                                  ((x0 + fold - 1) * ss, (y0 + fold - 1) * ss)], fill=255)
    flap = np.array(flap).reshape(N, ss, N, ss).mean(axis=(1, 3)) > 127
    return mask, flap


def scrap(seed=4):
    rnd = random.Random(seed)
    img = np.zeros((N, N, 3), float) + BG
    for _ in range(150):
        x, y = rnd.randrange(N), rnd.randrange(N)
        img[y, x] = BG_SPECK
    mask, flap = scrap_shape(seed)
    full = mask | flap
    sh = np.zeros_like(full)
    sh[3:, 3:] = full[:-3, :-3]
    img[sh & ~full] = SHADOW
    img[mask] = PAPER
    img[flap] = FOLD
    # scratches: short strokes, darker and lighter, like the app's scrap
    d = Image.new('L', (N, N), 0)
    dd = ImageDraw.Draw(d)
    lt = Image.new('L', (N, N), 0)
    dl = ImageDraw.Draw(lt)
    for k in range(11):
        x = rnd.randint(16, 108)
        y = rnd.randint(16, 108)
        L = rnd.randint(4, 9)
        ang = math.radians(rnd.choice((-35, -30, 55, 60, 120)))
        (dd if k % 3 else dl).line((x, y, x + L * math.cos(ang), y + L * math.sin(ang)), fill=255)
    sc = (np.array(d) > 0) & mask
    sl = (np.array(lt) > 0) & mask
    img[sc] = SCRATCH_DK
    img[sl] = SCRATCH_LT
    # thick dark outline round the scrap and the flap
    edge = full & ~erode(full, 2)
    fedge = flap & ~erode(flap, 1)
    img[edge | fedge] = OUTLINE
    return img, mask


def rrect(x0, y0, x1, y1, r):
    return (arc(x1 - r, y0 + r, r, r, 270, 360, 4) + arc(x1 - r, y1 - r, r, r, 0, 90, 4) +
            arc(x0 + r, y1 - r, r, r, 90, 180, 4) + arc(x0 + r, y0 + r, r, r, 180, 270, 4))


def handheld_paths(seed=6):
    """A two-screen clamshell handheld, front view, in wobbly pencil. Generic: no logo."""
    rnd = random.Random(seed)
    P = []
    def add(pts, closed=False, passes=1, amt=0.7):
        for k in range(passes):
            dx, dy = (rnd.uniform(-0.8, 0.8), rnd.uniform(-0.8, 0.8)) if k else (0, 0)
            P.append(wobble_path([(x + dx, y + dy) for x, y in pts], rnd, amt=amt, closed=closed))
    # top half and its wide screen
    add(rrect(26, 20, 102, 63, 5), closed=True, passes=2)
    add(rrect(32, 25, 96, 58, 2), closed=True)
    # glare on the top screen
    add([(80, 29), (91, 40)])
    add([(86, 29), (92, 35)])
    # hinge
    add([(34, 64), (94, 64)])
    add([(34, 67), (94, 67)])
    # bottom half and its near-square screen
    add(rrect(26, 68, 102, 110, 5), closed=True, passes=2)
    add(rrect(48, 72, 80, 101, 2), closed=True)
    # a little heart and two lines of writing on the bottom screen: the HUD
    heart = []
    for i in range(24):
        t = 2 * math.pi * i / 24
        hx = 16 * math.sin(t) ** 3
        hy = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        heart.append((64 + hx * 0.36, 82 - hy * 0.36))
    add(heart, closed=True, amt=0.25)
    add([(54, 92), (74, 92)], amt=0.4)
    add([(54, 96), (68, 96)], amt=0.4)
    # left: stick above a d-pad; right: buttons above a stick
    add(arc(37, 77, 4, 4, 0, 360, 10), closed=True, amt=0.35)
    add([(35, 88), (39, 88), (39, 92), (43, 92), (43, 96), (39, 96), (39, 100), (35, 100), (35, 96),
         (31, 96), (31, 92), (35, 92)], closed=True, amt=0.3)
    for bx, by in ((91, 76), (95, 80), (91, 84), (87, 80)):
        add(arc(bx, by, 1.6, 1.6, 0, 360, 6), closed=True, amt=0.2)
    add(arc(91, 95, 4, 4, 0, 360, 10), closed=True, amt=0.35)
    return P


def draw_doodle(paths, width=1.5, ss=8):
    big = Image.new('L', (N * ss, N * ss), 0)
    d = ImageDraw.Draw(big)
    for p in paths:
        d.line([(x * ss, y * ss) for x, y in p], fill=255, width=int(width * ss), joint='curve')
    a = np.array(big).reshape(N, ss, N, ss).mean(axis=(1, 3))
    return a > 80


def main(out='mod/preview.png', strength=0.55):
    img, mask = scrap()
    lines = draw_doodle(handheld_paths(), width=1.5) & mask
    img = np.where(lines[..., None], img * (1 - strength) + INK[None, None] * strength, img)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')
    im.resize((512, 512), Image.NEAREST).save(out, optimize=True)


if __name__ == '__main__':
    main(*sys.argv[1:2])
