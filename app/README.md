# Schoolbag app

Coming next. The Android app that draws the bottom-screen HUD from the mod's
state (see `../docs/protocol.md`). Until it exists, `../tools/mock_server.py`
serves the same data for building and testing it.

Game art is never committed here: the build reads it from a git-ignored `art/`
folder made from your own extraction of the game.

`icon/` holds the app icon (our own art, drawn by `../tools/draw_icon.py`): the
two adaptive-icon layers at 432 x 432 and a 192 x 192 square for older
launchers.
