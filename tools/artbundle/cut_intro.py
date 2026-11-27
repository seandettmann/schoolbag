"""Cuts the pieces for the app's intro page (shown while Isaac starts up): the
game's loading screen ("Loading..." and its four little pictures, two frames
each), and a gear to match: the settings gear's ink, drawn white and thicker
like the loading pictures, in two slightly different frames so it wobbles with
them.

    python3 tools/artbundle/cut_intro.py --ui <gfx/ui> --gear <sheets>/piece_gear_button.png --out <sheets folder>

Writes intro_sheet.png and intro_sheet.json ({"pos": {name: [x, y, w, h]}}) for
build_bundle.py. Game art: the output goes in the private art repo, never here.
"""
import argparse, json, os
from PIL import Image

# name, picture (under gfx/ui), x, y, w, h; from loading.anm2
CUTS = [
    ('loading_words_a', 'loading.png', 0, 0, 160, 32),
    ('loading_words_b', 'loading.png', 0, 32, 160, 32),
    ('loading_run_a', 'loading.png', 0, 64, 64, 64),
    ('loading_run_b', 'loading.png', 64, 64, 64, 64),
    ('loading_star_a', 'loading.png', 128, 64, 64, 64),
    ('loading_star_b', 'loading.png', 192, 64, 64, 64),
    ('loading_face_a', 'loading.png', 0, 128, 64, 64),
    ('loading_face_b', 'loading.png', 64, 128, 64, 64),
    ('loading_poop_a', 'loading.png', 0, 192, 64, 64),
    ('loading_poop_b', 'loading.png', 64, 192, 64, 64),
]
GEAR_INK = (54, 47, 45)
WHITE = (255, 255, 255, 255)


def white_gear(path):
    """The gear's ink lines, white and two pixels thick (grown right or left, and
    down), on nothing: two frames a pixel apart."""
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    px = im.load()
    ink = {(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 200 and px[x, y][:3] == GEAR_INK}
    frames = []
    for dx in (1, -1):
        out = Image.new('RGBA', (w + 2, h + 1), (0, 0, 0, 0))
        for x, y in ink:
            for qx, qy in ((x, y), (x + dx, y), (x, y + 1)):
                out.putpixel((qx + 1, qy), WHITE)
        frames.append(out)
    # both frames cut to the same box, so they sit in the same place
    b1, b2 = frames[0].getbbox(), frames[1].getbbox()
    box = (min(b1[0], b2[0]), min(b1[1], b2[1]), max(b1[2], b2[2]), max(b1[3], b2[3]))
    return [f.crop(box) for f in frames]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ui', required=True)
    ap.add_argument('--gear', required=True)
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    pieces = [(n, Image.open(os.path.join(a.ui, f)).convert('RGBA').crop((x, y, x + w, y + h)))
              for n, f, x, y, w, h in CUTS]
    ga, gb = white_gear(a.gear)
    pieces += [('gear_white_a', ga), ('gear_white_b', gb)]
    pos, x, y, row = {}, 0, 0, 0
    for n, im in pieces:
        if x + im.width > 256:
            x, y, row = 0, y + row + 1, 0
        pos[n] = [x, y, im.width, im.height]
        x += im.width + 1
        row = max(row, im.height)
    sheet = Image.new('RGBA', (256, y + row), (0, 0, 0, 0))
    for n, im in pieces:
        sheet.paste(im, tuple(pos[n][:2]))
    sheet.save(os.path.join(a.out, 'intro_sheet.png'))
    json.dump({'pos': pos}, open(os.path.join(a.out, 'intro_sheet.json'), 'w'))
    print(len(pos), 'intro pieces,', sheet.size)


if __name__ == '__main__':
    main()
