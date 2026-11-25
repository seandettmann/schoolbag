# Schoolbag

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
for the current state about ten times a second. The format is in
[`docs/protocol.md`](docs/protocol.md).

## Status

- **Mod 0.1.0:** written and tested against a stand-in for the game's API
  (see `tests/`). Not yet tested in the real game.
- **App:** not started yet. The design mock-ups are done.

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

Schoolbag uses port 8089, so it can run alongside isaac-hud's hud-stream mod
(port 8088).

## Credits and game art

The Binding of Isaac and all of its art are © Edmund McMillen and Nicalis.
**No game art is in this repository.** Like isaac-hud, the release APK is built
with art extracted from the maintainer's own copy of the game, using the game's
`tools/ResourceExtractor`. If the rights holders ask, releases will come down
promptly.

Inspired by [isaac-hud](https://github.com/SufficientOO/isaac-hud) and the
dual-screen companion for the Eden Duo.

## For developers

```
docs/protocol.md        what the mod sends (protocol 1)
mod/schoolbag/          the Workshop mod: main.lua + metadata.xml
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
```

**Trying the mod on a PC:** copy `mod/schoolbag` into the game's `mods`
folder, launch with `--luadebug`, start a run and open
`http://127.0.0.1:8089/state` in a browser. The game's `log.txt` shows lines
starting with `[Schoolbag]`.

**Uploading to the Workshop:** run the game's `tools/ModUploader/ModUploader.exe`
on the `mods/schoolbag` folder. Upload as private first, test on the Thor, then
make it public.

## License

Not chosen yet.
