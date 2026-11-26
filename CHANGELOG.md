# Changelog

Each version's notes become its GitHub release's notes. From 0.3 on, the app
and the mod share the version's middle number: app 0.3.x works with mod 0.3.x.

## 0.3.0

Needs the Schoolbag mod 0.3.0 (Workshop).

**App**
- The Map, Items and Room tabs have room round their labels: the labels are
  a size smaller and the scraps a little wider.
- On a Curse of the Lost floor, "the map returns next floor" wraps onto two
  lines instead of running off the page.
- The tabs stay where they are when you switch: the open one is bright,
  with a pencil line under its label, and the others are dimmed.
- The strip under the map is two small cards side by side, where the game's
  HUD has them: your trinkets on the left, one a line, and your pocket on
  the right (with what's next in it, in grey).
- On the Items and Room tabs, the card shows the item bigger, with just its
  name and pickup line. Tap it for the full description, as before.

## 0.2.0

Needs the Schoolbag mod 0.3.0 (Workshop).

**App**
- Tapping the bottom screen no longer takes the controller from the game.
  Just after opening, tap the top screen once ("Tap the top screen to play").
- Opens on the bottom screen even if launched on the top one.
- The Room tab is back. With **Follow the game** on, the tabs switch by
  themselves: Room in a room with items, Map on leaving it or on the Map
  button, Items on the pause menu. **Show new items** shows a new item's card
  for 4 seconds.
- Swipe between tabs; a short buzz on taps; a resting thumb no longer counts
  as a tap.
- Uses much less power when nothing is happening: it only redraws when
  something changes, and the screen can sleep after a run.
- Screen care: a wider pixel shift, a **Brightness** setting (100/85/70%),
  and dimming after 30 seconds on the pause menu.

**Mod 0.3.0**
- Never makes the game wait on the app, and sends updates only when
  something changes.
- Builds its updates about twice as fast.
- Tells the app about the pause menu and the Map button.

## 0.1.0

Needs the Schoolbag mod 0.2.2 (Workshop).

**App**
- First version of the bottom-screen HUD: hearts, pickups, stats, the
  header, and the Map and Items tabs with the item card.
- Pictures for every item, trinket, card, rune and soul stone.
- Settings: hide the game's HUD, dimming, banner language, About.

**Mod 0.2.2**
- Shares your run with the app and hides the game's HUD while the app is
  watching, drawing the game's banners and boss bar in its place.
- Shows "Schoolbag is offline" in the game when `--luadebug` is missing.
