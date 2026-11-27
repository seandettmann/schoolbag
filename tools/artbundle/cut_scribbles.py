"""Cuts the game's faint paper scribbles (scratches, drips, little doodles) out of
its menu papers, for the app's paper scraps. The game draws them in one tone a
little darker than the paper; this keeps just those pixels, as ink, so the app
can draw them at the same faintness on its own tan papers.

    python3 tools/artbundle/cut_scribbles.py --menu "<gfx/ui/main menu>" --out <sheets folder>

Writes scribbles_sheet.png and scribbles_sheet.json ({"pos": {name: [x, y, w, h]}})
for build_bundle.py. Game art: the output goes in the private art repo, never here.
"""
import argparse, json, os
from PIL import Image

MARK = (196, 184, 188)   # the menu papers' scribble tone
INK = (54, 47, 45, 255)

# name, menu picture, and where the mark sits in it (x, y, w, h)
CUTS = [
    # little doodles
    ('sc_star', 'optionsmenu.png', 32, 17, 29, 24),
    ('sc_cat', 'statsmenu.png', 122, 152, 56, 55),
    ('sc_poop', 'saveselectmenu.png', 396, 279, 36, 61),
    ('sc_xface', 'saveselectmenu.png', 180, 345, 35, 31),
    ('sc_sun', 'saveselectmenu.png', 94, 360, 48, 38),
    ('sc_moon', 'saveselectmenu.png', 325, 392, 19, 19),
    ('sc_smile', 'gamemenu.png', 283, 155, 30, 28),
    ('sc_coin', 'seedunlockpaper.png', 196, 52, 40, 46),
    ('sc_guy', 'collectionmenu.png', 2, 21, 32, 37),
    ('sc_wave', 'collectionmenu.png', 346, 24, 29, 35),
    ('sc_eye', 'collectionmenu.png', 326, 126, 44, 44),
    ('sc_splat', 'charactermenu.png', 152, 169, 42, 59),
    ('sc_blot', 'charactermenu.png', 139, 103, 16, 17),
    # scratches and a crack
    ('sc_s1', 'optionsmenu.png', 35, 64, 29, 23),
    ('sc_s2', 'statsmenu.png', 35, 393, 29, 23),
    ('sc_s3', 'saveselectmenu.png', 205, 304, 17, 21),
    ('sc_s4', 'saveselectmenu.png', 342, 349, 22, 25),
    ('sc_s5', 'saveselectmenu.png', 245, 365, 37, 37),
    ('sc_s6', 'gamemenu.png', 153, 120, 23, 23),
    ('sc_s7', 'challengemenucustom.png', 118, 218, 25, 17),
    ('sc_s9', 'menu_dailyrun.png', 307, 48, 24, 27),
    ('sc_s10', 'menu_dailyrun.png', 382, 66, 23, 23),
    ('sc_s12', 'saveselectmenu.png', 97, 306, 28, 48),
    ('sc_crack', 'saveselectmenu.png', 22, 365, 38, 46),
    # drips, from a paper's top edge down
    ('sc_drip1', 'statsmenu.png', 111, 336, 57, 45),
    ('sc_drip3', 'saveselectmenu.png', 258, 275, 35, 31),
]


def main_part(im):
    """A doodle without stray specks from the paper's other marks nearby: drops
    any group of pixels (counting ones up to 2 apart as touching) that's under
    a fifth the size of the biggest."""
    w, h = im.size
    px = im.load()
    on = {(i, j) for j in range(h) for i in range(w) if px[i, j][3]}
    groups, seen = [], set()
    for start in on:
        if start in seen:
            continue
        group, todo = [], [start]
        seen.add(start)
        while todo:
            i, j = todo.pop()
            group.append((i, j))
            for di in range(-2, 3):
                for dj in range(-2, 3):
                    q = (i + di, j + dj)
                    if q in on and q not in seen:
                        seen.add(q)
                        todo.append(q)
        groups.append(group)
    if not groups:
        return im
    big = max(len(g) for g in groups)
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for g in groups:
        if len(g) * 5 >= big:
            for q in g:
                out.putpixel(q, INK)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--menu', required=True)
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    pieces = []
    for name, src, x, y, w, h in CUTS:
        im = Image.open(os.path.join(a.menu, src)).convert('RGBA').crop((x, y, x + w, y + h))
        out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        px, op = im.load(), out.load()
        for j in range(h):
            for i in range(w):
                r, g, b, al = px[i, j]
                if al > 200 and (r, g, b) == MARK:
                    op[i, j] = INK
        if not name.startswith(('sc_s', 'sc_crack', 'sc_drip', 'sc_poop')):
            out = main_part(out)
        bb = out.getbbox()
        if bb:
            out = out.crop(bb)
        pieces.append((name, out))
    # one row after another, 256 wide, a pixel apart
    sheet_w, x, y, row_h, pos = 256, 0, 0, 0, {}
    for name, im in pieces:
        if x + im.width > sheet_w:
            x, y, row_h = 0, y + row_h + 1, 0
        pos[name] = [x, y, im.width, im.height]
        x += im.width + 1
        row_h = max(row_h, im.height)
    sheet = Image.new('RGBA', (sheet_w, y + row_h), (0, 0, 0, 0))
    for name, im in pieces:
        sheet.paste(im, tuple(pos[name][:2]))
    sheet.save(os.path.join(a.out, 'scribbles_sheet.png'))
    json.dump({'pos': pos}, open(os.path.join(a.out, 'scribbles_sheet.json'), 'w'))
    print(len(pos), 'scribbles,', sheet.size)


if __name__ == '__main__':
    main()
