# Schoolbag app

The Android half of Schoolbag, the dual-screen companion: it draws the HUD on
the bottom screen from the mod's state (see `../docs/protocol.md`). Plain Java and the Android framework only, no
libraries.

- `src/main/java/.../core/`: everything that decides what's on screen (the
  state, the hearts, the layout of every page). Plain Java with no Android in
  it, so it compiles and runs off the device too.
- `src/main/java/.../`: the Android side: the full-screen view, the poller
  that reads the mod ten times a second, and drawing onto the Canvas.
- `dev/`: draws any page into a PNG off the device, from the same art, for
  checking against the mock-ups:

      javac -d /tmp/sb src/main/java/io/github/seandettmann/schoolbag/core/*.java dev/*.java
      java -cp /tmp/sb Render <art folder> <state.json or -> out.png [items|map|room] [main|language|about]

  `../tools/mock_server.py` makes state files and serves the same data live.

**Game art is never committed here.** The build reads it from
`src/main/assets/art` (git-ignored), which GitHub's build fills from the private
`schoolbag-art` repo. `../tools/artbundle/build_bundle.py` makes that folder from an
extraction of the game.

**Builds:** every push to `main` that touches `app/` builds the app on GitHub
and publishes it as a release (`.github/workflows/app.yml`), which Obtainium
picks up.

`icon/` holds the app icon (our own art, drawn by `../tools/draw_icon.py`): the
two adaptive-icon layers at 432 x 432 and a 192 x 192 square for older
launchers.
