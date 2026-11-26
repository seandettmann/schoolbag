"""Builds the app's art bundle: the game's art, fonts and words the app needs,
in the layout the app reads. The bundle is game art, so it never goes in this
repo: it goes in the private art repo, which the build checks out into
app/src/main/assets/art.

    python3 tools/artbundle/build_bundle.py --out <bundle folder> \
        --ui <gfx/ui> --collectibles <gfx/items/collectibles> \
        --trinkets <gfx/items/trinkets> --data <folder with items.xml,
        pocketitems.xml, info_display.xml and stringtable.sta> \
        --sheets <folder with the mock-up sheets: atlas.png/json,
        health_sheet, map_sheet, pills_sheet, and the extra pieces>

The mock-up sheets (papers in the game's tan palette, tab scraps, heart and
map sheets...) are made from the same extraction by the mock-up scripts;
folding those scripts in here, so the whole bundle comes from one extraction,
is next on the list.

What comes out:
    regions.json     every named sprite: [sheet file, x, y, w, h]
    sheets/          the sprite sheets
    fonts/           the game's fonts, as TrueType
    items/<id>.png   every collectible; trinkets/<id>.png every trinket
    data/items.json  names, pickup lines and info lines for items, trinkets,
                     cards and pill effects (as the game's string keys)
    strings/<lang>.json  the game's words the app and the mod need
"""
import argparse, json, os, re, shutil, xml.etree.ElementTree as ET

LANGS = {'English': 'en', 'Japanese': 'ja', 'Korean': 'ko', 'Chinese (Simple)': 'zh',
         'Russian': 'ru', 'German': 'de', 'Spanish': 'es', 'French': 'fr'}
CATEGORIES = {'Items', 'PocketItems', 'Stages', 'Curses', 'Players', 'Entities'}


def anm2_frames(path):
    """{animation: (x, y, w, h)} for the first frame of each animation's first layer."""
    s = open(path, encoding='utf-8', errors='replace').read()
    out = {}
    for m in re.finditer(r'<Animation Name="([^"]+)"[^>]*>(.*?)</Animation>', s, re.S):
        f = re.search(r'XCrop="(\d+)" YCrop="(\d+)" Width="(\d+)" Height="(\d+)"', m.group(2))
        if f:
            out[m.group(1)] = tuple(int(v) for v in f.groups())
    return out


def find(folder, name):
    for f in os.listdir(folder):
        if f.lower() == name.lower():
            return os.path.join(folder, f)
    raise FileNotFoundError(os.path.join(folder, name))


def main():
    ap = argparse.ArgumentParser()
    for k in ('out', 'ui', 'collectibles', 'trinkets', 'data', 'sheets', 'fonts'):
        ap.add_argument('--' + k, required=True)
    a = ap.parse_args()
    out = a.out
    for d in ('sheets', 'fonts', 'items', 'trinkets', 'data', 'strings'):
        os.makedirs(os.path.join(out, d), exist_ok=True)
    regions = {}

    def sheet(src, name):
        shutil.copyfile(src, os.path.join(out, 'sheets', name))
        return name

    # the mock-up sheets
    at = json.load(open(os.path.join(a.sheets, 'atlas.json')))
    sheet(os.path.join(a.sheets, 'atlas.png'), 'atlas.png')
    for k, (x, y, w, h) in at['pos'].items():
        regions[k] = ['atlas.png', x, y, w, h]
    for js, png in (('health_sheet.json', 'health.png'), ('map_sheet.json', 'map.png'), ('pills_sheet.json', 'pills.png')):
        d = json.load(open(os.path.join(a.sheets, js)))
        sheet(os.path.join(a.sheets, js.replace('.json', '.png')), png)
        for k, (x, y, w, h) in d['pos'].items():
            assert k not in regions, k
            regions[k] = [png, x, y, w, h]
    # single pictures: settings pieces, tags, Esau's scrap, floors
    for f in sorted(os.listdir(a.sheets)):
        if f.startswith('piece_') and f.endswith('.png'):
            name = f[len('piece_'):-4]
            from PIL import Image
            w, h = Image.open(os.path.join(a.sheets, f)).size
            regions[name] = [sheet(os.path.join(a.sheets, f), f), 0, 0, w, h]

    # the game's own UI sheets, by animation
    for png, anm, prefix in (('ui_chargebar.png', 'ui_chargebar.anm2', 'bar-'),
                             ('ui_cardfronts.png', 'ui_cardfronts.anm2', 'cardfront-')):
        sheet(find(a.ui, png), png)
        for anim, (x, y, w, h) in anm2_frames(find(a.ui, anm)).items():
            regions[prefix + anim] = [png, x, y, w, h]

    # fonts (TrueType versions of the game's bitmap fonts), with a full name
    # table: Java and some Android versions refuse a font without names
    from fontTools.ttLib import TTFont
    for f in os.listdir(a.fonts):
        if f.endswith('.ttf'):
            font = TTFont(os.path.join(a.fonts, f))
            family = f[:-4].replace('-', ' ').title().replace(' ', '')
            if 'name' not in font:
                from fontTools.ttLib import newTable
                font['name'] = newTable('name')
                font['name'].names = []
            for nid, val in ((1, family), (2, 'Regular'), (3, family + '-Regular'), (4, family), (6, family)):
                font['name'].setName(val, nid, 3, 1, 0x409)
                font['name'].setName(val, nid, 1, 0, 0)
            font.save(os.path.join(out, 'fonts', f))

    # items, trinkets, cards, pills
    items = ET.parse(find(a.data, 'items.xml')).getroot()
    info = {}
    raw = open(find(a.data, 'info_display.xml'), encoding='utf-8').read()
    raw = re.sub(r'<\?xml[^>]*\?>', '', raw)
    for group in ET.fromstring('<root>' + raw + '</root>'):  # several top-level lists
        if group.tag != 'collectibles':
            continue
        for c in group:
            lines = [i.get('text') for i in c if not (set(i.attrib) - {'text'})]
            info[c.get('id')] = [re.sub(r'<[^>]*>', '', t) for t in lines if t]
    data = {'collectibles': {}, 'trinkets': {}, 'cards': {}, 'pills': {}}
    for e in items:
        gid, gfx = e.get('id'), e.get('gfx')
        if not gid or not gfx:
            continue
        if e.tag == 'trinket':
            src, kind, dst = a.trinkets, 'trinkets', 'trinkets'
        elif e.tag in ('passive', 'active', 'familiar'):
            src, kind, dst = a.collectibles, 'collectibles', 'items'
        else:
            continue
        try:
            shutil.copyfile(find(src, gfx), os.path.join(out, dst, gid + '.png'))
        except FileNotFoundError:
            continue
        entry = {'name': e.get('name'), 'desc': e.get('description')}
        if kind == 'collectibles' and info.get(gid):
            entry['lines'] = info[gid]
        data[kind][gid] = entry
    pocket = ET.parse(find(a.data, 'pocketitems.xml')).getroot()
    for e in pocket:
        if e.tag in ('card', 'rune') and e.get('id'):
            data['cards'][e.get('id')] = {'name': e.get('name'), 'desc': e.get('description')}
        elif e.tag == 'pilleffect' and e.get('id'):
            data['pills'][e.get('id')] = {'name': e.get('name'), 'class': e.get('class')}
    json.dump(data, open(os.path.join(out, 'data', 'items.json'), 'w'), ensure_ascii=False)

    # the game's words, one file per language
    st = ET.parse(find(a.data, 'stringtable.sta')).getroot()
    langs = {l.get('index'): l.get('name') for l in st.find('languages')}
    words = {code: {} for code in LANGS.values()}
    for cat in st.iter('category'):
        if cat.get('name') not in CATEGORIES:
            continue
        for key in cat.iter('key'):
            k = key.get('name')
            for i, s in enumerate(key.iter('string')):
                code = LANGS.get(langs.get(str(i + 1)))  # index 0 is the key itself
                if code and s.text:
                    words[code][k] = s.text
    for code, w in words.items():
        json.dump(w, open(os.path.join(out, 'strings', code + '.json'), 'w'), ensure_ascii=False)

    json.dump(regions, open(os.path.join(out, 'regions.json'), 'w'))
    print(len(regions), 'regions,', len(data['collectibles']), 'items,', len(data['trinkets']), 'trinkets,',
          {c: len(w) for c, w in words.items()}, 'words')


if __name__ == '__main__':
    main()
