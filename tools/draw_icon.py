"""Draws Schoolbag's app icon: a bold pencil doodle of a backpack on plain paper.
Original art drawn in code (no game art, no AI image generator).

Android draws app icons from two layers (adaptive icons): the paper is the
background and the doodle the foreground. Each layer is 108dp across, of which
the launcher shows the middle 72dp, cut to a circle, squircle or rounded
square. The doodle stays inside the 66dp circle that every shape keeps.
The art is drawn on a grid of 72 units for the whole layer (48 for the part
that shows), at 6 pixels a unit: 432 x 432, the size Android wants at xxxhdpi.

    python3 tools/draw_icon.py [out_dir]

Writes ic_launcher_background.png, ic_launcher_foreground.png and
ic_launcher.png (the visible square at 192 x 192, for older launchers) to
app/icon/ by default. Needs Pillow and numpy.
"""
import math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw

LAYER = 72          # grid units across a whole layer (108dp)
SHOWN = 48          # units the launcher shows (72dp)
EDGE = (LAYER - SHOWN) // 2
SCALE = 6           # pixels a unit: 432 px layers
PAPER = np.array((197, 179, 153), float)
SCRATCH_DK = np.array((178, 159, 133), float)
INK = np.array((54, 44, 38), float)
INK_STRENGTH = 0.92
BACKPACK_BOX = (30, 33, 98, 104)  # where the doodle sits in its own 128-unit drawing


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


def backpack_paths(seed=5):
    rnd = random.Random(seed)
    P = []
    def add(pts, closed=False, passes=1):
        for k in range(passes):  # kids go over their lines twice, never quite on top
            dx, dy = (rnd.uniform(-0.8, 0.8), rnd.uniform(-0.8, 0.8)) if k else (0, 0)
            P.append(wobble_path([(x + dx, y + dy) for x, y in pts], rnd, amt=0.75, closed=closed))
    # body: straight sides, a domed top, a slightly rounded bottom
    body = [(36, 102)] + [(36, 58)] + arc(64, 58, 28, 20, 180, 360)[1:] + [(92, 102)] + arc(64, 100, 28, 4, 0, 180)[1:]
    add(body, closed=True, passes=2)
    # handle loop on top
    add(arc(64, 40, 8, 7, 180, 360))
    # top zip: a curve following the dome, with a pull tab
    add(arc(64, 61, 22, 14, 195, 345))
    add([(83, 55), (85, 61), (83, 62)])
    # front pocket
    pocket = [(44, 76), (84, 76), (86, 80), (86, 99), (42, 99), (42, 80)]
    add(pocket, closed=True)
    add([(46, 82), (82, 82)])                     # pocket zip
    add([(76, 82), (77, 88), (79, 88), (78, 82)])  # its pull
    # straps peeking out at the sides
    add([(36, 64), (31, 68), (30, 92), (36, 96)])
    add([(92, 64), (97, 68), (98, 92), (92, 96)])
    # a little star patch on the flap, the way a kid would add one
    star = []
    for i in range(10):
        r = 5 if i % 2 == 0 else 2.2
        a = math.radians(-90 + i * 36)
        star.append((52 + r * math.cos(a), 63 + r * math.sin(a)))
    add(star, closed=True)
    return P


def raster_lines(paths, n, width, ss=8):
    big = Image.new('L', (n * ss, n * ss), 0)
    d = ImageDraw.Draw(big)
    for p in paths:
        d.line([(x * ss, y * ss) for x, y in p], fill=255, width=max(1, int(width * ss)), joint='curve')
    return np.array(big).reshape(n, ss, n, ss).mean(axis=(1, 3)) > 90


def place(paths, src_box, dst_center, dst_size):
    """Scale doodle paths drawn for the 128 grid into the icon."""
    sx0, sy0, sx1, sy1 = src_box
    s = dst_size / max(sx1 - sx0, sy1 - sy0)
    cx, cy = (sx0 + sx1) / 2, (sy0 + sy1) / 2
    return [[(dst_center[0] + (x - cx) * s, dst_center[1] + (y - cy) * s) for x, y in p] for p in paths]


def background():
    """Plain paper with a few faint scratches."""
    img = np.zeros((LAYER, LAYER, 3)) + PAPER
    rnd = random.Random(7)
    for _ in range(9):
        x, y = rnd.randint(2, SHOWN - 6), rnd.randint(2, SHOWN - 6)
        for k in range(rnd.randint(2, 4)):
            img[EDGE + min(SHOWN - 1, y + k), EDGE + min(SHOWN - 1, x + k)] = SCRATCH_DK
    return img


def foreground():
    """The doodle's pixels: a mask of where the ink goes."""
    paths = place(backpack_paths(), BACKPACK_BOX, (EDGE + 24, EDGE + 24.5), 31)
    return raster_lines(paths, LAYER, 1.25)


def save(img, path, size):
    Image.fromarray(img).resize((size, size), Image.NEAREST).save(path, optimize=True)


def main(out='app/icon'):
    os.makedirs(out, exist_ok=True)
    bg = background()
    ink = foreground()
    fg = np.zeros((LAYER, LAYER, 4), np.uint8)
    fg[ink, :3] = INK.astype(np.uint8)
    fg[ink, 3] = round(255 * INK_STRENGTH)
    save(np.clip(bg, 0, 255).astype(np.uint8), os.path.join(out, 'ic_launcher_background.png'), LAYER * SCALE)
    save(fg, os.path.join(out, 'ic_launcher_foreground.png'), LAYER * SCALE)
    flat = bg.copy()
    flat[ink] = flat[ink] * (1 - INK_STRENGTH) + INK * INK_STRENGTH
    shown = np.clip(flat[EDGE:EDGE + SHOWN, EDGE:EDGE + SHOWN], 0, 255).astype(np.uint8)
    save(shown, os.path.join(out, 'ic_launcher.png'), SHOWN * 4)


if __name__ == '__main__':
    main(*sys.argv[1:2])
