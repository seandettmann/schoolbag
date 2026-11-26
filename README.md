# Schoolbag

[![Made with Claude](https://img.shields.io/badge/made%20with-Claude-6A5C52)](#made-with-ai)

A bottom-screen HUD for **The Binding of Isaac: Repentance+** on dual-screen
Android handhelds like the AYN Thor. Your hearts, items, map, room and stats
live on the second screen, drawn in the game's own notebook-and-parchment
style, so the main screen stays clear.

Schoolbag is an unofficial fan project. It isn't made or endorsed by Edmund
McMillen or Nicalis, and it needs your own copy of the game.

## How it works

There are two pieces, both called Schoolbag:

| Piece | What it does | Where to get it |
|---|---|---|
| **Mod** (`mod/schoolbag`) | Runs inside the game and shares your run with the app over a local-only connection. It never changes the game. | Steam Workshop |
| **App** (`app/`, coming next) | Draws the HUD on the bottom screen. | GitHub Releases, through Obtainium |

The mod answers on `http://127.0.0.1:8089` (loopback only). The app asks it
for the current state about ten times a second. While the app is reading, the
mod hides the game's own HUD on the main screen, since the app shows it on the
bottom screen; it comes back a few seconds after the app stops. Hiding the HUD
also hides the game's floor, item, pill and card banners and the boss health
bar, so the mod draws those itself, with the game's own art and the game's
words sent over by the app.

Two exceptions. In **co-op** (two or more people, one per controller), the
game's HUD stays on, since the other players only see the main screen. Jacob &
Esau is one person, so it still hides. And on **Tainted Cain** runs, the game's
HUD comes back while his Bag of Crafting is full. The game only shows what the bag will make inside its
own HUD, and mods can't read it, so this keeps the crafting preview on screen.
It hides again once he crafts. (The game only lets mods show or hide the whole
HUD, not parts of it.) On the bottom screen, Schoolbag shows what's in the bag.

The format is in [`docs/protocol.md`](docs/protocol.md).

## Status

- **Mod 0.2.0:** written and tested against a stand-in for the game's API
  (see `tests/`). Not yet tested in the real game.
- **App:** not started yet. The design mock-ups are done.

## Roadmap

**Next**
- The app, built from the finished mock-ups.
- A first test of the mod in the real game on the Thor.

**Parked** (set aside for now, with the work kept)
- **Pill tracker.** A "Pills" tag on the Items tab opening a page of the 13
  pill colours, each named once the run has identified it (never before, so
  nothing is spoiled). Progress: designed; the mod already sends the data
  (`players[].pills`, tested against the stand-in); not in the app's first
  version. Needs more thought before it's built.
- **Co-op on the bottom screen.** Progress: the mod sends every player and
  leaves the game's own HUD on in co-op, since the other players only see the
  main screen. How the app shows several people at once isn't designed yet.
  Local co-op on a handheld is unusual. (Jacob & Esau is designed: it follows
  the game's own layout.)

**To revisit**
- **Tainted Cain's crafting preview.** For now the game's whole HUD returns on
  Tainted Cain runs while the Bag of Crafting is full (see above), with no
  setting. Revisit if the game's mod API ever lets mods read the bag's result,
  or a calculation that's reliable on Repentance+ turns up, so the preview can
  move to the bottom screen and the main screen can stay clear.

## Installing (once released)

1. **Mod:** subscribe to Schoolbag on the Steam Workshop. On the Thor,
   GameNative syncs it for you.
2. **Launch option:** the mod needs `--luadebug`. In GameNative, add it to the
   game container's Exec Arguments; on a PC, in Steam: right-click the game →
   Properties → Launch Options. Without it, the mod keeps a banner at the top
   of the screen all run, "Schoolbag is offline / Needs --luadebug", and
   nothing else happens.
3. **App:** add this repository to Obtainium and install the latest release.
   Obtainium will offer updates as they come out.
4. Start a run. The app connects on its own.

`--luadebug` gives every Lua mod you have installed access to files and the
network, not just this one. Only run mods you trust alongside it.

Turn off any other mod that hides the game's HUD while you use Schoolbag.
Schoolbag brings the HUD back when the app stops reading (and on Tainted Cain
runs while the Bag of Crafting is full), and another mod would keep hiding it.

## Credits and game art

The Binding of Isaac and all of its art are © Edmund McMillen and Nicalis.
**No game art is in this repository.** The release APK is built with art
extracted from the maintainer's own copy of the game, using the game's
`tools/ResourceExtractor`. If the rights holders ask, releases will come down
promptly.

Inspired by [isaac-hud](https://github.com/SufficientOO/isaac-hud) and the
Eden Duo emulator's dual-screen
[companion for The Binding of Isaac](https://github.com/igawa6/eden-duo-companions#the-binding-of-isaac-afterbirth-and-repentance-dlc).

## Made with AI

Most of Schoolbag's code, docs and design were written with Claude, Anthropic's
AI model. No AI image generator was used: the app's art is the game's own, and
the Workshop image and the app icon are doodles drawn in code.

## For developers

```
docs/protocol.md        what the mod sends (protocol 1)
mod/schoolbag/          the Workshop mod: main.lua + metadata.xml, and resources/ with
                        the boss bar's animation file (it points at the game's own image)
tools/make_bossbar_anm2.py  writes that animation file
tools/draw_preview.py   draws the Workshop image, mod/preview.png
tools/draw_icon.py      draws the app icon's layers into app/icon/
tools/mock_server.py    a fake mod with scenarios, for building the app without the game
tests/                  runs the real mod against a stubbed game API, and checks the fake
```

**Fake mod:** `python3 tools/mock_server.py`, then open
`http://127.0.0.1:8089/state`. Switch scenarios with `/scenario/<name>`
(`/scenarios` lists them: normal, dead, loading, greed, shields, devil, offline…).
Add `--host 0.0.0.0` to reach it from another device on your Wi-Fi.

**Tests** (need Lua 5.3 and LuaSocket, e.g. `apt install lua5.3 lua-socket`):

```
python3 tests/check_mod.py    # the real mod, stubbed game, real HTTP
python3 tests/check_mock.py   # every fake-mod scenario against the protocol
lua5.3 tests/check_reminder.lua  # the "Schoolbag is offline" banner
lua5.3 tests/check_hud.lua       # hiding the game's HUD while the app reads
lua5.3 tests/check_banners.lua   # the banners and boss bar drawn in the HUD's place
lua5.3 tests/check_bag.lua       # tracking what goes into the Bag of Crafting
```

**Trying the mod:** copy `mod/schoolbag` into the game's `mods`
folder, launch with `--luadebug`, start a run and open
`http://127.0.0.1:8089/watch` in a browser (on the Thor, on the bottom
screen). That test page reads the mod twice a second, like the app will, so
the game's HUD stays hidden, and shows the values worth checking. It also
lists any **problems** (parts of the mod that failed) and the game calls that
didn't answer, and its **Copy state** button copies all of it, with the full
state, to paste into a chat or an issue. `/state` is the raw data. The game's
`log.txt` also shows lines starting with `[Schoolbag]`.

**Uploading to the Workshop (Windows):** copy `mod/schoolbag` into the game's
`mods` folder, run the game's `tools/ModUploader/ModUploader.exe` and choose
that folder's `metadata.xml`. It goes up as **private** (`metadata.xml` says
so), which is enough to subscribe on your own account and let GameNative
install it on the Thor. For updates, run the uploader again on the same folder.
Switch the item to public on its Workshop page at release. `mod/preview.png`
is the Workshop image (our own art, no game art) if the uploader asks for one.

## License

GPL-3.0 (see [`LICENSE`](LICENSE)). It covers Schoolbag's own code and docs. The game's art isn't in this repository and
isn't covered by it: it belongs to Edmund McMillen and Nicalis.
