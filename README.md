# Schoolbag

[![Made with Claude](https://img.shields.io/badge/made%20with-Claude-6A5C52)](#made-with-ai)

Schoolbag, a dual-screen companion for The Binding of Isaac: Repentance+,
comes in two pieces:

- **The mod** (`mod/schoolbag`) runs inside the game and sends your run to
  the app. It never changes the game. Get it from the Steam Workshop.
- **The app** (`app/`) shows your HUD on the bottom screen of an Android
  handheld like the AYN Thor. Get it from GitHub Releases, through
  Obtainium.

## How it works

Your hearts, items, map and stats move to the bottom screen, drawn in the
game's own notebook-and-paper style, so the main screen is just the game.
The game's banners (floor names, items, pills and cards) and the boss health
bar still show up top.

The game's own HUD comes back by itself:

- **In co-op,** since the other players only see the main screen. (Jacob &
  Esau count as one player, so it stays hidden for them.)
- **On Tainted Cain, while his Bag of Crafting is full,** so you can see
  what it will make. It hides again once he crafts.
- **When the app closes,** a few seconds later, so you're never left without
  a HUD.

## On the bottom screen

- **Always showing:** your active item and its charge, hearts, coins, bombs
  and keys, your stats, and the devil deal, angel deal and Planetarium
  chances. Along
  the top: the floor, its curses and the run's clock.
- **Map:** the floor's map, as much as you've uncovered, with your trinkets
  and pocket items underneath.
- **Items** ("My Stuff!"): everything you've picked up, in order. Tap one
  for its description.
- **Room:** the items in the room you're in. As in the game, an item you
  haven't collected on this save yet isn't named or described.
- **Switching tabs:** tap a tab or swipe sideways. The game's Map button
  (Select) jumps to the Map; otherwise the tabs stay where you put them.
- **When you die,** Isaac's ghost shows on every tab.
- **Screen care:** the picture shifts very slightly over time and dims when
  nothing's happening, to protect the screen.
- **Settings** (the gear, before and between runs): dimming, brightness,
  hiding the game's HUD, and the language of the banners up top.

## Installing (once released)

1. **Mod:** subscribe to Schoolbag on the Steam Workshop. On the Thor,
   GameNative installs it for you.
2. **Launch option:** add `--luadebug`. In GameNative, it goes in the game
   container's Exec Arguments; in Steam on a PC, right-click the game →
   Properties → Launch Options. Without it, the mod only shows "Schoolbag is
   offline" at the top of the screen.
3. **App:** add this repo to Obtainium and install the latest release.
   Obtainium will let you know when there's an update.
4. **Bottom screen:** on the Thor, long-press Schoolbag's icon and set
   **Screen Launch** to the bottom screen.
5. **Play:** start a run. The app connects by itself, and shows the game's
   loading screen while Isaac starts up (it takes a minute or two).

Good to know:

- `--luadebug` gives every Lua mod you have installed access to your files
  and the network, not just Schoolbag. Only use it with mods you trust.
- Turn off any other mod that hides the game's HUD. It would keep the HUD
  hidden when Schoolbag needs it back.
- Opening the app doesn't take the controller away from the game. If the
  app ever says **"Tap the top screen to play"**, tap the top screen once.

## Status

Schoolbag is still being tested on the Thor, and the mod isn't public on the
Workshop yet.

- **Mod 0.3.3:** on the Steam Workshop, private while testing.
- **App 0.3.0:** in testing; 0.2 is the latest release. Test builds are
  pre-releases, which Obtainium skips unless you turn on "Include
  prereleases".

What changed in each version is in [`CHANGELOG.md`](CHANGELOG.md).

## Roadmap

**Next**
- Jacob & Esau: both brothers' trinkets and pocket items (only Jacob's show
  today).
- The Boss Rush and Hush countdowns, transformations, and what killed you.
- Finishing the testing on the Thor.

**Parked** (on hold, with the work kept)
- **Pill tracker:** a page of all 13 pill colours, each named once you've
  identified it in the run, so nothing is spoiled.
- **Co-op on the bottom screen:** showing more than one player at once.

**Later**
- **A background that follows the run:** the bottom screen sits on the
  Basement's floor today. It would change with the floor you're on, from the
  Caves to the Womb and beyond.

**To revisit**
- **Tainted Cain's crafting preview** on the bottom screen, so the game's
  HUD can stay hidden while his bag is full.
- **D Infinity:** the app shows the plain D Infinity, not the die it's set
  to.

## Technical details

### How the mod and the app talk

The mod listens on `127.0.0.1:8089` (loopback only, so nothing outside the
device can reach it). The app says hello over UDP once a second, and the mod
sends it the state whenever something changes (and once a second anyway).
If UDP doesn't get through, the app asks over HTTP instead, about ten times
a second. The mod never makes the game wait on the app. The format is in
[`docs/protocol.md`](docs/protocol.md).

Without `--luadebug`, the game doesn't let mods open network connections or
read files. That's why the mod needs it, and why it opens those up to every
other mod too. When the mod can't listen, its offline banner says why:
"Needs --luadebug", or "Port 8089 is busy".

### The save's collection

The game's mod API can't tell which items you've collected on a save
(REPENTOGON can, but isn't out for Repentance+). So the mod reads the game's
save file itself, once each run, and the Room tab only names and describes
items the save has. Items you're holding count too, since the game only
saves between runs. If the save can't be found or read, every item is
described. The test page (`/watch`, below) shows how it went, or where it
looked.

### Hiding the game's HUD

While the app is reading, the mod hides the game's HUD on the main screen.
It comes back a few seconds after the app stops reading, when the run ends,
and on the menus.

The game only lets mods show or hide the whole HUD, not parts of it, so
hiding it also hides the game's floor, item, pill and card banners and the
boss health bar. The mod draws those itself, with the game's own art and
the game's words sent over by the app (in the language set in **Banner
language**; Automatic follows the Thor's). Each banner is the title alone,
in the game's ink. Only the "Schoolbag is offline" banner keeps its paper
strip, since it's the one that tells you to do something. **Hide the game's
HUD** in the settings (on by default) can turn all of this off, leaving the
game's HUD, banners and boss bar alone.

Co-op means two or more people, one per controller. Jacob & Esau is one
person, so the HUD still hides. On Tainted Cain, the game only shows what
the Bag of Crafting will make inside its own HUD, and mods can't read it,
so the HUD comes back while the bag is full. On the bottom screen, Schoolbag
shows what's in the bag (the mod follows what goes in).

Another mod that hides the HUD would keep it hidden when Schoolbag brings
it back, which is why it should be off.

### The controller

When the app opens, Android gives it the controller, as it does any app
that opens last. Schoolbag hands it straight back to the top screen, so you
don't need to tap there first. Android only lets the system move the
controller between screens, so on the Thor this goes through the system
helper in AYN's firmware (the one behind its "Run Script as Root").
Schoolbag asks it for that one thing only, and only in the first few
seconds after opening. Where the helper can't be reached (with the Thor's
**Force SELinux** on, say), the app says "Tap the top screen to play".

Taps on the bottom screen leave the controller with the game. If taps ever
start taking the controller (a Thor update could change how this works),
set the Thor's **Focus Mode** to keep focus on the top screen.

If the app opens on the top screen (without Screen Launch set), it moves
itself to the bottom one.

### The bottom screen

- **Tabs:** the open one is the bright one with a line under its name.
- **Map:** drawn in the game's own minimap art, sized to fill the page.
  Under it, trinkets on the left and the pocket on the right. The pocket
  item you'll use next is on the right with a line under it, and the next
  one is dimmed beside it; they swap places when you switch. Between floors
  the map stays until the next one's arrives.
- **Items and Room:** 7 items a row, numbered in the order you picked them
  up (on the Room tab, left to right in the room). Tap one to see it on the
  card underneath (the selected one has a line under it), and tap the card
  for the game's whole description. In a room where you can take only one,
  one line runs under all of them, with "Take one". With more than three
  rows, tap the arrow near the bottom of the page or swipe up or down to
  scroll.
- **Along the top:** in Greed mode, the wave instead of the clock. Extra
  lives show with the hearts.
- **Screen care:** the picture creeps a pixel at a time, up to 8 pixels
  from the middle. **Dim when nothing changes** fades it after 2 minutes
  with nothing happening during a run (30 seconds while the game is
  paused). **Brightness** (100, 85 or 70%) darkens the whole bottom screen.
  The first tap on a dimmed screen only wakes it.
- **About** (in the settings) shows the version, and how fast the app is
  drawing and reading the mod, for testing.

### Roadmap notes

- **Jacob & Esau** follows the game's own layout (designed, not built).
- **Testing:** the mod is tested against a stand-in for the game's API (see
  `tests/`), and was tested in the real game on the Thor up to 0.3.1. Still
  to do on the Thor: banners, boss bar, characters, Jacob & Esau, the Bag of
  Crafting, death, and the 0.3.0 test builds.
- **Pill tracker:** designed, as a "Pills" tag on the Items tab opening the
  page. The mod already sends the data (`players[].pills`, tested against
  the stand-in). Needs more thought before it's built.
- **Co-op:** the mod already sends every player. How the app shows several
  people at once isn't designed yet. Local co-op on a handheld is unusual.
- **A background that follows the run:** the game has its own floor art for
  every chapter, and the mod already sends the floor and its path. Still to
  design: which part of each floor's art to use, and how it changes over
  (when the next floor's data arrives, or with a transition of its own).
- **Tainted Cain's crafting preview:** revisit if the game's mod API ever
  lets mods read the bag's result, or a calculation that's reliable on
  Repentance+ turns up.
- **D Infinity's selected die:** the game's HUD shows the die D Infinity is
  set to, with that die's charge bar. The game keeps the choice in the
  item's hidden value (`VarData`), which mods can't read without
  REPENTOGON. The likely way: the mod follows the drop button while D
  Infinity is held (a best guess, saved with the run, marked "not sure" when
  it can't be certain), as it does for the Bag of Crafting. Needs first: a
  photo of the game's HUD on two different dice (which pictures it uses, and
  where the cycle starts), and, if the game has its own D Infinity pictures,
  those from the extraction on the PC.

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
lua5.3 tests/check_net.lua       # never blocking the game, keep-alive, UDP, timing
lua5.3 tests/check_collection.lua  # reading the save's collection
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
test build, a pre-release that replaces the one before ("Schoolbag 0.3.0 test
16"). To release: write the version's section in `CHANGELOG.md`, then run the
App workflow (Actions tab) with the task `release`; it publishes "Schoolbag
X.Y" (tag `vX.Y.Z`) from `main` with that section as its notes and removes the
test builds toward it.

## Credits and game art

Schoolbag is an unofficial, fan-made dual-screen companion. It isn't made or
endorsed by Edmund McMillen or Nicalis, and it needs your own copy of the
game.

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

## License

GPL-3.0 (see [`LICENSE`](LICENSE)). It covers Schoolbag's own code and docs. The game's art isn't in this repository and
isn't covered by it: it belongs to Edmund McMillen and Nicalis.
