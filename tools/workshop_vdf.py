"""Writes the steamcmd file that updates Schoolbag's Steam Workshop item.

Usage (from the repo root):
    python3 tools/workshop_vdf.py OUT.vdf [--commit SHA] [--note TEXT]

It reads mod/schoolbag/metadata.xml (the item's ID, name, description and
version) and writes a `workshop_build_item` file for The Binding of Isaac
(app 250900), with the same result ModUploader.exe had: the mod folder as the
item's content, metadata.xml's name and description on its page, and
mod/preview.png as its image. The change note is the version's "Mod X.Y.Z"
bullets in CHANGELOG.md, or --note (the commit's subject) when there are none.

It never sets the item's visibility, so the item stays as its Workshop page
says: private while testing, public once it's switched there. And it refuses
to run without the item's ID, since steamcmd would make a new item instead.
"""
import argparse, os, re, sys
import xml.etree.ElementTree as ET

ISAAC_APP_ID = "250900"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = os.path.join(ROOT, "mod", "schoolbag")
PREVIEW = os.path.join(ROOT, "mod", "preview.png")


def fail(msg):
    print(f"workshop_vdf: {msg}", file=sys.stderr)
    sys.exit(1)


def metadata():
    tree = ET.parse(os.path.join(MOD, "metadata.xml")).getroot()
    get = lambda tag: (tree.findtext(tag) or "").strip()
    meta = {k: get(k) for k in ("id", "name", "description", "version")}
    if not re.fullmatch(r"\d+", meta["id"]):
        fail("metadata.xml has no Workshop <id>, so steamcmd would make a new item. Add the item's ID first.")
    if not meta["name"] or not meta["version"]:
        fail("metadata.xml needs a <name> and a <version>.")
    lua = open(os.path.join(MOD, "main.lua"), encoding="utf-8").read()
    m = re.search(r'^local VERSION = "([^"]+)"', lua, re.M)
    if not m or m.group(1) != meta["version"]:
        fail(f"metadata.xml's version ({meta['version']}) isn't main.lua's ({m.group(1) if m else 'none'}).")
    return meta


def changelog_notes(version):
    """The bullets under "**Mod <version>**" in CHANGELOG.md, one line each."""
    try:
        lines = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read().splitlines()
    except FileNotFoundError:
        return []
    try:
        start = lines.index(f"**Mod {version}**") + 1
    except ValueError:
        return []
    bullets = []
    for line in lines[start:]:
        if line.startswith("- "):
            bullets.append(line[2:].strip())
        elif line.startswith("  ") and line.strip() and bullets:
            bullets[-1] += " " + line.strip()
        elif not line.strip() and not bullets:
            continue
        else:
            break
    return bullets


def vdf_text(s):
    """steamcmd reads these files without escapes, so a straight double quote
    would end the value: use curly quotes instead."""
    out, opening = [], True
    for ch in s:
        if ch == '"':
            out.append("“" if opening else "”")
            opening = not opening
        else:
            out.append(ch)
    return "".join(out)


def change_note(version, commit, note):
    head = f"Schoolbag mod {version}" + (f" ({commit})" if commit else "")
    bullets = changelog_notes(version)
    if bullets:
        return "\n".join([head] + [f"- {b}" for b in bullets])
    return f"{head}: {note.strip()}" if note and note.strip() else head


def build(commit="", note=""):
    meta = metadata()
    fields = [
        ("appid", ISAAC_APP_ID),
        ("publishedfileid", meta["id"]),
        ("contentfolder", MOD),
        ("title", meta["name"]),
        ("description", meta["description"]),
        ("changenote", change_note(meta["version"], commit, note)),
    ]
    if os.path.isfile(PREVIEW):
        fields.insert(3, ("previewfile", PREVIEW))
    body = "\n".join(f'\t"{k}"\t\t"{vdf_text(v)}"' for k, v in fields)
    return meta, '"workshopitem"\n{\n' + body + "\n}\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("out")
    ap.add_argument("--commit", default="", help="short commit ID for the change note")
    ap.add_argument("--note", default="", help="change note when CHANGELOG.md has none for this version")
    args = ap.parse_args()
    meta, text = build(args.commit, args.note)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"Workshop item {meta['id']}: {meta['name']} {meta['version']}")
    print(text)


if __name__ == "__main__":
    main()
