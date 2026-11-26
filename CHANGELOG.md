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
- The strip under the map has your trinkets on the left and your pocket on
  the right, where the game's HUD has them, each in at most half the strip.
  One thing on a side shows at full size with its name beside it, towards
  the middle (a pill's good, bad or horse in grey under its name); two show
  side by side at full size, without names.
- On the Items and Room tabs, the card shows the item bigger, with just its
  name and pickup line. Tap it for the full description, as before.
- Between floors the Map tab keeps showing the map, instead of a loading
  picture.
- When you die, the ghost shows on all three tabs, not just the Map, at the
  same spot on each, and the strips under them are left blank.
- Each tab's page has its own set of the game's faint pencil sketches,
  about seven a page, scattered over the whole sheet and each turned a
  little, as if drawn by hand. The map and the items cover them where they
  need the room; on the death page they stay clear of the ghost.
- "My Stuff!" sits a little lower on the Items tab, clear of the paper's
  top edge, with the items under it.
- The Items and Room tabs both show 7 items a row at full size. Past three
  rows (21 items), an arrow near the bottom of the page scrolls down a row
  at a time, and points up at the end to go back to the top; a swipe up or
  down on the page scrolls too. The Items tab shows the newest rows until
  you scroll, and a Room with more than 7 pedestals now shows them all.

**Mod 0.3.1**
- A banner line too long for the paper strip under it gets a longer strip,
  so the words stay on the paper. Nearly half the cards need one (the tarot
  cards' lines especially), and some items and trinkets.

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
