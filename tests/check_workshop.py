"""Checks the file the Mod workflow gives steamcmd to update the Workshop item.

Usage (from the repo root): python3 tests/check_workshop.py
"""
import os, re, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools"))
import workshop_vdf

failures = []


def check(cond, msg):
    print(("  ok   " if cond else "  FAIL ") + msg)
    if not cond:
        failures.append(msg)


meta, text = workshop_vdf.build(commit="abc1234", note='Fix the "boss bar"')
pairs = dict(re.findall(r'^\t"(\w+)"\t\t"([^"]*)"$', text, re.M))
values = re.findall(r'"([^"]*)"', text)

check(text.startswith('"workshopitem"\n{\n') and text.rstrip().endswith("}"), "one workshopitem block")
check(len(values) % 2 == 1, "every value is closed by its own quote (no stray quotes)")
check(pairs.get("appid") == "250900", "for The Binding of Isaac (app 250900)")
check(pairs.get("publishedfileid") == meta["id"] == "3846036075", "updates item 3846036075, never a new one")
check("visibility" not in pairs, "leaves visibility to the Workshop page")
check(os.path.isfile(os.path.join(pairs.get("contentfolder", ""), "metadata.xml")), "the content is the mod folder")
check(pairs.get("previewfile", "").endswith("mod/preview.png"), "mod/preview.png is the image")
check(pairs.get("title") == "Schoolbag", "the name comes from metadata.xml")
check(pairs.get("description") == meta["description"] and "dual-screen companion" in meta["description"],
      "the description comes from metadata.xml")
note = pairs.get("changenote", "")
check(note.startswith(f"Schoolbag mod {meta['version']} (abc1234)"), "the change note names the version and commit")

# the change note: CHANGELOG's bullets when the version has them, else the note
check(workshop_vdf.changelog_notes("0.3.0")[:1] == ["Never makes the game wait on the app, and sends updates only when something changes."],
      "CHANGELOG's wrapped bullets become one line each")
check(workshop_vdf.changelog_notes("9.9.9") == [], "no bullets for a version CHANGELOG doesn't have")
check(workshop_vdf.change_note("9.9.9", "abc1234", "Fix it") == "Schoolbag mod 9.9.9 (abc1234): Fix it",
      "falls back to the commit's subject")
check(workshop_vdf.vdf_text('a "b" c') == "a “b” c", "straight quotes become curly ones")

print("all ok" if not failures else f"{len(failures)} failed")
sys.exit(1 if failures else 0)
