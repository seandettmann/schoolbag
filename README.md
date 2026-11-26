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
| **App** (`app/`) | Draws the HUD on the bottom screen. | GitHub Releases, through Obtainium |

The mod listens on `127.0.0.1:8089` (loopback only). The app says hello over
UDP once a second, and the mod sends it the state whenever something changes
(and once a second anyway). If UDP doesn't get through, the app asks over HTTP
instead, about ten times a second. While the app is watching, the mod hides the
game's own HUD on the main screen, since the app shows it on the bottom screen;
it comes back a few seconds after the app stops. Hiding the HUD
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

- **Mod 0.3.1** (on the Workshop, private while testing): tested against a
  stand-in for the game's API (see `tests/`), and in the real game on the
  Thor up to 0.2.x. Since 0.3.0 it never makes the game wait on the app,
  sends the state over UDP only when it changes, and reports the pause menu,
  the Map button and its own timing for the app to follow. 0.3.1 gives a
  banner line too long for its paper strip (about half the cards) a longer
  strip.
- **App 0.3.0** (test builds, the newest a pre-release): 0.2.0 is the last
  release. 0.3.0 reworks the pages after the first Thor tests: tabs that
  don't move, a new strip under each page, scrolling grids of 7 a row on the
  Items and Room tabs, the map staying up between floors, the death page on
  every tab, and more of the game's sketches on the paper. The test builds
  are on the Thor for checking.

## Roadmap

**Next**
- The rest of the app: Jacob & Esau (the strip under the map shows Jacob's
  trinkets and pocket only), the Boss Rush and Hush countdowns,
  transformations, "killed by".
- The angel deal chance (the mod doesn't send it yet).
- The rest of the test checklist on the Thor (banners, boss bar, characters,
  Jacob & Esau, the Bag of Crafting, death), and the 0.3.0 test builds.

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

**Later** (ideas for eventual development)
- **A background that follows the run.** The floor behind the pages is the
  Basement's today. It would change with the floor Isaac is on (the Caves,
  the Depths, the Womb, the alternate paths and the rest), so the bottom
  screen moves through the run with him. The game has its own floor art for
  every chapter, and the mod already sends the floor and its path. Still to
  design: which part of each floor's art to use, and how it changes over
  (when the next floor's data arrives, or with a transition of its own).

**To revisit**
- **Tainted Cain's crafting preview.** For now the game's whole HUD returns on
  Tainted Cain runs while the Bag of Crafting is full (see above), with no
  setting. Revisit if the game's mod API ever lets mods read the bag's result,
  or a calculation that's reliable on Repentance+ turns up, so the preview can
  move to the bottom screen and the main screen can stay clear.
- **D Infinity's selected die.** The game's HUD shows the die D Infinity is
  set to, with that die's charge bar; the app shows the plain D Infinity
  picture. The game keeps the choice in the item's hidden value (`VarData`),
  which mods can't read without REPENTOGON. The likely way: the mod follows
  the drop button while D Infinity is held (a best guess, saved with the run,
  marked "not sure" when it can't be certain), as it does for the Bag of
  Crafting. Needs first: a photo of the game's HUD on two different dice
  (which pictures it uses, and where the cycle starts), and, if the game has
  its own D Infinity pictures, those from the extraction on the PC.

## Installing (once released)

1. **Mod:** subscribe to Schoolbag on the Steam Workshop. On the Thor,
   GameNative syncs it for you.
2. **Launch option:** the mod needs `--luadebug`. In GameNative, add it to the
   game container's Exec Arguments; on a PC, in Steam: right-click the game →
   Properties → Launch Options. Without it, the mod keeps a banner at the top
   of the screen all run, "Schoolbag is offline / Needs --luadebug", and
   nothing else happens.
3. **App:** add this repository to Obtainium and install the latest release.
   Obtainium will offer updates as they come out. Releases are versions
   (0.1, 0.2…), with what changed in [`CHANGELOG.md`](CHANGELOG.md). Test
   builds are pre-releases ("Schoolbag 0.3.0 test 16"), each replacing the
   last; Obtainium skips them unless you turn on "Include prereleases".
4. **Bottom screen:** on the Thor, long-press Schoolbag's icon and set
   **Screen Launch** to the bottom screen. (If it opens on the top screen
   anyway, it moves itself to the bottom one.)
5. Start a run. The app connects on its own.

Taps on the bottom screen leave the controller with the game. Just after the
app opens, it has the controller like any app ("Tap the top screen to play"):
tap the top screen once. If taps ever start taking the controller (a Thor
update could change how this works), set the Thor's **Focus Mode** to keep
focus on the top screen.

`--luadebug` gives every Lua mod you have installed access to files and the
network, not just this one. Only run mods you trust alongside it.

Turn off any other mod that hides the game's HUD while you use Schoolbag.
Schoolbag brings the HUD back when the app stops reading (and on Tainted Cain
runs while the Bag of Crafting is full), and another mod would keep hiding it.

## On the bottom screen

- **Always there:** your active item and its charge, hearts and extra lives,
  coins, bombs and keys, and the stats, with the devil deal and Planetarium
  chances (the angel deal chance is still to come). Along the top, the
  floor's curses, its name and the run's clock (the wave, in Greed mode).
- **Tabs:** Map, Items and Room. Tap a tab, or swipe sideways anywhere. The
  open tab is the bright one with a line under its name.
- **Map:** the floor's map in the game's own minimap art, as much of it as
  the game has shown, sized to fill the page. Under it, your trinkets on the
  left and your pocket on the right. The pocket item you'll use next is on
  the right with a line under it, and the next one is dimmed beside it; they
  swap places when you switch. Between floors the map stays until the next
  one's arrives.
- **Items** ("My Stuff!") and **Room:** 7 items a row, numbered in the order
  you picked them up (or, on the Room tab, left to right in the room). Tap
  one to see it on the card underneath, and tap the card for the game's whole
  description. With more than three rows, tap the arrow near the bottom of
  the page or swipe up or down to scroll.
- **When you die,** every tab shows Isaac's ghost.
- **Follow the game** (a setting, on by default): the Room tab when you walk
  into a room with items and the Map tab when you leave it, the Map tab when
  you press the game's Map button, and the Items tab while the pause menu is
  open.
- **Show new items** (a setting, on by default): a new item's card on the
  Items tab for 4 seconds, then back to where you were.
- **Screen care:** the picture creeps a pixel at a time, up to 8 pixels from
  the middle, and **Dim when nothing changes** fades it after 2 minutes with
  nothing happening during a run (30 seconds on the pause menu).
  **Brightness** (100, 85 or 70%) darkens the whole bottom screen. The first
  tap on a dimmed screen only wakes it.
- **Settings** (the gear on the waiting page, between runs): besides the
  ones above, **Hide the game's HUD** (on by default; off leaves the game's
  own HUD, banners and boss bar alone) and **Banner language** (the language
  of the words the mod draws in the game's banners; Automatic follows the
  Thor's).
- **About** (in the settings) shows the version, and how fast the app is
  drawing and reading the mod, for testing.

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
app/                    the Android app (Java): core/ draws the HUD, the rest is the
                        Android side; dev/ renders a frame to a PNG on a computer
docs/protocol.md        what the mod sends (protocol 1)
mod/schoolbag/          the Workshop mod: main.lua + metadata.xml, and resources/ with
                        animation files for the boss bar and the banners' longer paper
                        strip (they point at the game's own images)
tools/make_bossbar_anm2.py  writes the boss bar's animation file
tools/make_paper_anm2.py    writes the paper strip's animation file
tools/artbundle/        builds the app's art bundle from the game's extracted files
                        (the bundle is game art: it lives in a private repo)
tools/draw_preview.py   draws the Workshop image, mod/preview.png
tools/draw_icon.py      draws the app icon's layers into app/icon/
tools/mock_server.py    a fake mod with scenarios, for building the app without the game
tools/workshop_vdf.py   writes what the Mod workflow sends to the Workshop
tools/steam_login.sh    saves the Steam login the Mod workflow uploads with (run on a Mac)
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
python3 tests/check_workshop.py  # what the Mod workflow sends to the Workshop
lua5.3 tests/check_reminder.lua  # the "Schoolbag is offline" banner
lua5.3 tests/check_hud.lua       # hiding the game's HUD while the app reads
lua5.3 tests/check_banners.lua   # the banners and boss bar drawn in the HUD's place
lua5.3 tests/check_bag.lua       # tracking what goes into the Bag of Crafting
lua5.3 tests/check_net.lua       # never blocking the game, keep-alive, UDP, timing
```

`check_banners.lua` also checks that every card's line stays on its paper
strip: with `SCHOOLBAG_GAME` (a folder of the game's unpacked resources, with
`font/upheavalmini.fnt` and `other/pocketitems.xml`) and `SCHOOLBAG_WORDS`
(the art bundle's `strings/en.json`) set, the game's own cards in the game's
own font; without them, made-up lines.

**Trying the mod:** copy `mod/schoolbag` into the game's `mods`
folder, launch with `--luadebug`, start a run and open
`http://127.0.0.1:8089/watch` in a browser (on the Thor, on the bottom
screen). That test page reads the mod twice a second, as the app does, so
the game's HUD stays hidden, and shows the values worth checking. The mod only
listens during a run, so on the menus the page says "No answer" (and a fresh
load of it there just fails to connect). It also
lists any **problems** (parts of the mod that failed) and the game calls that
didn't answer, and its **Copy state** button copies all of it, with the full
state, to paste into a chat or an issue. `/state` is the raw data. The game's
`log.txt` also shows lines starting with `[Schoolbag]`.

**Versions and releases:** the version is `versionName` in `app/build.gradle`
and `VERSION` in `mod/schoolbag/main.lua` (also in its `metadata.xml`). From
0.3 on, the app and the mod share the middle number. The first change after a
release raises the version. Every push to `main` that touches `app/` makes a
test build, a pre-release that replaces the one before. To release: write the
version's section in `CHANGELOG.md`, then run the App workflow (Actions tab)
with the task `release`; it publishes "Schoolbag X.Y" (tag `vX.Y.Z`) from
`main` with that section as its notes and removes the test builds toward it.

## License

GPL-3.0 (see [`LICENSE`](LICENSE)). It covers Schoolbag's own code and docs. The game's art isn't in this repository and
isn't covered by it: it belongs to Edmund McMillen and Nicalis.
