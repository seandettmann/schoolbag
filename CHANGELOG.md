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
  the middle (a pill's good, bad or horse under its name); two show
  side by side at full size, without names. With two pocket items, the one
  you'll use next stays on the very right with a pencil line under it, and
  the next one is dimmed to its left; when you switch them, they slide past
  each other into their new places.
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
- The selected item on the Items and Room tabs gets the same pencil line
  under it as the pocket item in use, instead of a box. Items you can take
  only one of get one pencil line under them all, with "Take one" under it,
  instead of a loop round them.
- A card held on its own shows its name without the tarot number: "The
  Fool", not "0 - The Fool".
- Every paper scrap has some of the game's faint scribbles on it, cut from
  its own menu papers: scratches, drips off a top edge, a little doodle here
  and there. The pages, the strips under them, the tab scraps, the waiting
  papers, the settings paper, and the gear and back buttons all have some.
- A new intro while Isaac starts up: until the mod first answers, the app
  shows the game's own loading screen, as it looks up top while Isaac
  starts: "Loading..." and one of its four little pictures (a different one
  each time), both wobbling, with a white gear to match for the settings.
  After a run, the waiting page is as before. The question about the mod and
  `--luadebug` now comes after three minutes instead of one, since Isaac
  takes a minute or two to start.
- No more tapping the top screen when the game and Schoolbag start together:
  Schoolbag hands the controller back to the game as soon as it opens,
  through the system helper in the Thor's firmware. If that doesn't work,
  it says "Tap the top screen to play", on the loading screen too.
  Settings → About says how it went.
- The tabs stay where you put them. **Follow the game** and **Show new
  items** are gone: walking into a room, picking something up or pausing no
  longer switches tabs. The game's Map button (Select) still brings up the
  Map tab.
- The Room tab shows an item you haven't collected on this save as its
  picture with "???" and "Not in your collection yet", as the game does.
- Easier to read on the Thor: every word is in ink (no more greys), the
  small lettering is bigger (the Map strip's names, item numbers, pickup
  lines), and the closed tabs and the stat and pickup icons are brighter. A
  long name on the Map strip, or a long pickup line, steps down a size
  before it's cut short. With an item's full description open, its card
  shows how to close it in place of the pickup line.

**Mod 0.3.2**
- Banners show the name alone, on the ink: no paper strip with a pickup
  line or curse under it, for items, cards, pills and floors. The
  "Schoolbag is offline" banner keeps its strip, since its line is the fix.
- The Map button (Select) is noticed again, on any controller.
- Reads your save's item collection (with --luadebug, from the save file;
  the game's log says which slot), so the Room tab can leave out the name
  and description of items you haven't collected on that save, as the
  game's own Item Descriptions do. If the save can't be found, everything is
  described as before, and /watch's "collection" line says where it looked.

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
