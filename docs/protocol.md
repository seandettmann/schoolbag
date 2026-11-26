# Schoolbag protocol, version 1

The Schoolbag mod runs inside The Binding of Isaac: Repentance+ and listens on
**`127.0.0.1:8089`**, for HTTP (TCP) and UDP on the same port. The Schoolbag
app gets the state from it, pushed over UDP when it changes or read over HTTP
(below), and draws the bottom-screen HUD. While the app is watching, the mod
hides the game's own HUD (below), and the app sends the mod one thing: the
game's words for the banners the mod draws in the HUD's place
(`POST /strings`).

The server only listens on the loopback address, so nothing outside the
device can reach it.

## Endpoints

| Path | Answer |
|---|---|
| `GET /state` | The full state below, as JSON. Hides the game's HUD while the app keeps reading (below). |
| `GET /state?hud=keep` | The same state, without hiding the game's HUD (the app's "Hide the game's HUD" setting turned off). If the HUD was hidden, it comes straight back. |
| `GET /health` | `ok` (plain text). Cheap check that the mod is up. Doesn't hide the HUD. |
| `GET /watch` | A test page for a browser on the device: reads `/state` twice a second (so the HUD stays hidden, as with the app) and shows the values worth checking, plus `problems` and `misses`. Its **Copy state** button copies a plain-text report (the summary, problems, misses and the full state) to paste into a chat or an issue. For trying the mod before the app exists. |
| `POST /strings` | The game's words, from the app (below). Answers `ok <count>`, or `400` if the body isn't a JSON object. |
| anything else | `404`. |

Every response has `Access-Control-Allow-Origin: *`. Connections are kept
open for the next request (`Connection: keep-alive`) unless the request says
`Connection: close` (or is HTTP/1.0 without `keep-alive`); the mod then closes
after answering. A connection left with nothing going on for 5 seconds is
closed, and up to 8 can be open at once. Requests and answers never make the
game wait: the mod reads and sends a little each frame, and a request that
arrives in pieces is answered once it's all there. A body over 4 MB gets
`413` and the connection is closed.

## UDP: the state pushed when it changes

Instead of reading `/state` ten times a second, the app can have the mod send
it. Every second, the app sends one UDP message to `127.0.0.1:8089` from a
port of its own:

| Message | Meaning |
|---|---|
| `hello` | Send me the state. Counts as a `/state` read for hiding the game's HUD. |
| `hello keep` | The same, but like `?hud=keep`: the game's HUD isn't hidden (and comes straight back if it was). |

The mod answers each hello with the state at once, then sends it to that port
whenever it changes (checked 10 times a second; `run.timeFrames` alone
doesn't count) and at least once a second. Each message is the same JSON as
`GET /state`, in one datagram. A state over 60,000 bytes is sent as
`{"tooBig":true}` instead: read that one over HTTP. With no hello for 3
seconds, the mod stops sending (and the game's HUD comes back as usual).

The app should fall back to HTTP when no UDP arrives for a moment but `/state`
answers (a mod before 0.3.0 has no UDP), and try UDP again now and then.

**When nothing answers:** the mod can only answer while the game is rendering a
run, so it only listens during one: it starts listening when a run starts or is
continued and stops when you exit to the menu. On the title screen and in menus
the connection is refused straight away, and if `--luadebug` is missing the
server never starts. Give every request a short timeout (about 1.5 seconds) and
keep at most one waiting at a time: a request can still go unanswered, for
example on the game-over screen on the way back to the menu. The app treats
"no answer" (refused or timed out) as "not in a run" and, after a while, shows
a hint to check the mod and the launch option. In the game, the mod says the
same thing itself: from a few seconds into each run it keeps a banner up in the
style of the game's own banners,
"Schoolbag is offline", with "Needs --luadebug" (or "Port 8089 is busy") on
the paper strip under it. It uses the game's own banner sprite and fonts
(Upheaval on the ink, Upheaval mini on the paper) and steps aside whenever the
game shows a banner of its own in the same spot: a new floor, an item or
trinket held overhead, a pill or card used, and a card, pill or trinket picked
up. Brief pauses (room changes) leave it up; it hides while the game has been
paused for about 3/4 of a second (the pause menu).

**The game's own HUD:** while `/state` is being read (or the app keeps saying
hello over UDP), the mod hides the game's HUD on the main screen, since the
app shows it on the other screen. It comes back by itself 3 seconds after the
last `/state` read or hello, when the run ends, and
on the menus, so a closed app or a switched-off bottom screen never leaves the
player without a HUD. Reading while paused keeps it
hidden. Reading with `?hud=keep` never hides it, and gives it back at once if
it was hidden.

**Co-op:** with two or more people playing (counted by controller: Esau,
Tainted Forgotten's Soul, Strawman's Keeper and Found Soul move with their
player's controller, so they don't count), the mod leaves the game's HUD on,
since the other players only see the main screen. `humans` says how many
people there are and `hudHidden` stays false. Jacob & Esau is one person, so
the HUD still hides.

**Tainted Cain:** on a Tainted Cain run, the HUD stays showing while his Bag
of Crafting is full, or while the mod isn't sure what's in it (`bag.sure` is
false). The game only shows what the bag will make inside its own HUD, mods
can't read it, and the game only lets mods show or hide the whole HUD, so this
is the only way to keep the crafting preview on screen. It hides again once he
crafts. Other characters holding the bag don't get this. On the roadmap to
revisit.

**What the mod draws while the HUD is hidden:** hiding the game's HUD also hides
the banners it shows for a new floor (with its curse on the paper strip), a
picked-up item or card (with its one-line description), a pill or card used,
and the boss health bar. While the HUD is hidden, the mod draws these itself
on the main screen with the game's own art and fonts, in the same places and
with the same timing. A line too long for the paper strip gets a wider strip.
If the HUD is hidden partway through a banner, the mod's carries on from the
same frame. When the HUD is showing, the game draws its own and the
mod draws nothing.

**`POST /strings`, the game's words:** the game gives mods codes such as
`#THE_SAD_ONION_NAME`, not the words, and the words are in a game file mods
can't open. The app has them (from `stringtable.sta`, bundled with its art), so
it sends them: a JSON object of string keys **without the `#`** and their text,
in the language the app uses.

```json
{ "THE_SAD_ONION_NAME": "The Sad Onion", "THE_SAD_ONION_DESCRIPTION": "Tears up",
  "BASEMENT_NAME": "Basement", "CURSE_OF_THE_BLIND_NAME": "Curse of the Blind!" }
```

- Send the `Items`, `PocketItems`, `Stages` and `Curses` categories (about 2,100
  strings). Sending more is harmless; repeats replace earlier text.
- `/state` has `strings`, the number the mod holds. Send them whenever it's 0:
  on first connect, and again after the game restarts. A mod reload keeps them.
- Until they arrive, the mod makes do: names become words from their codes
  (`#MOMS_KNIFE_NAME` → "Moms Knife") and descriptions are left off.
- Floor numbers ("I", "II", "XL") are added by the mod, as the game does.

**Polling:** over HTTP, about 10 times a second is plenty (2 a second while
the game is paused is enough). The mod builds the state on request, or for
UDP only while the app says hello, so it costs nothing when the app isn't
running. The parts that rarely change (the map, each player's items) are
encoded again only when they change.

## Versioning

`protocol` is an integer. It goes up only when a change would break an older
app (a field removed, renamed or changing meaning). New fields can appear at any
time without a bump, so the app must ignore fields it doesn't know.

## Conventions

- Field names are camelCase. Numbers are plain JSON numbers; ints and floats as
  the game reports them.
- **Empty lists can arrive as `[]` or `{}`** (Lua can't tell an empty list from
  an empty object). The app treats both as an empty list.
- Fields that the game doesn't expose in this version are left out rather than
  sent as `null`.
- If one part of the state can't be read (for example after a game update
  renames something), that part is left out and the rest still arrives. The
  mod logs the problem once to the game's `log.txt` and lists it in
  `problems`.
- **Names:** item, trinket, card and pill names are sent exactly as the game's
  item config holds them. For vanilla content that is usually a string key such
  as `#THE_SAD_ONION_NAME`, which the app looks up in `stringtable.sta`. Modded
  content usually has a plain name. Either way, an older app can still label an
  item it has no art for.
- **Enums** (room types, room shapes, player types…) are sent as
  the game's own numbers from `enums.lua`.

## Spoiler rules

The mod never sends what the game is hiding from the player:

- **Curse of the Blind**, and any pedestal the game reports as blind: the
  pedestal is sent with `"blind": true` and no `id` or `name`.
- **Unidentified pills:** `identified: false` and no `effect`, and the colour
  isn't in `pills` until the game has identified it.
- **Curse of the Unknown:** `health` is `{"hidden": true}` and `shields` is
  empty.
- **Map:** only rooms the game itself shows on the minimap are sent (rooms with
  display flags 0 are left out). Under Curse of the Lost the list is empty and
  `map.hidden` is true.

## Top level

```json
{
  "protocol": 1,
  "mod": { "name": "Schoolbag", "version": "0.3.0" },
  "inRun": true,
  "paused": false,
  "pauseMenu": false,
  "input": { "mapAt": 81234 },
  "perf": { "answers": 50, "avgMs": 0.42, "maxMs": 1.1, "frameMaxMs": 1.3, "seconds": 5, "transport": "udp" },
  "humans": 1,
  "strings": 2104,
  "run": { ... },
  "floor": { ... },
  "map": { ... },
  "room": { ... },
  "players": [ { ... } ],
  "chances": { ... },
  "transition": "trapdoor",
  "death": { ... }
}
```

| Field | Meaning |
|---|---|
| `inRun` | `true` while a run is going. Since the mod only listens during a run, an answer almost always says `true`. |
| `humans` | How many people are playing (one per controller). More than 1 is co-op, and the mod leaves the game's HUD on. |
| `strings` | How many of the game's words the app has sent with `POST /strings`. 0 means send them. |
| `hudHidden` | `true` while the mod is hiding the game's HUD. |
| `problems` | For testing: parts of the mod that failed since the game started (a part of the state, a banner, the boss bar...), each once, at most 30. Empty when all is well. The app can ignore it. |
| `misses` | For testing: game calls the mod tried that didn't answer, each once, at most 30. Often fine (not every call exists for every character or game version), but a surprise here is the first thing to check when a value is missing. The app can ignore it. |
| `paused` | The game is paused: its pause screen, but also for a moment on every room change, and for boss intros and floor changes. The mod keeps answering while paused. |
| `pauseMenu` | The game's pause menu is open: the pause button was pressed, and the game has been paused for about 3/4 of a second since. The app shows its Items tab then, if it follows the game. |
| `input.mapAt` | When the Map button was last pressed, in the game's render frames (`Isaac.GetFrameCount()`, 60 a second, counting from the game's start). Only a change means anything: the app shows its Map tab. 0 until it's pressed. |
| `perf` | For testing: how long the mod spent answering the app over the last 5 seconds (`seconds`): `answers` sent, the average and worst time building and encoding one (`avgMs`, `maxMs`), the worst time the server took in one frame (`frameMaxMs`), and the last `transport` used (`"udp"` or `"http"`). Missing for the first 5 seconds. Changes every 5 seconds, so leave it out when deciding whether anything changed. |
| `transition` | Present while the player is leaving the floor: `"trapdoor"` or `"beam"`. The app keeps showing the map until the next floor's data arrives. |

## `run`

| Field | Meaning |
|---|---|
| `seed` | Start seed as the game shows it, e.g. `"ABCD EFGH"`. |
| `difficulty` | 0 normal, 1 hard, 2 greed, 3 greedier. |
| `challenge` | Challenge ID (from `challenges.xml`), 0 = none. |
| `victoryLap` | Victory lap count, 0 = none. |
| `timeFrames` | Run time in game frames (30 per second). |
| `bossRushDone`, `blueWombDone` | The game's own state flags. |
| `ended` | Present once the run is over: `"win"` or `"loss"`. |

## `floor`

| Field | Meaning |
|---|---|
| `stage` | `LevelStage` number (1 = Basement I … 13 = Home; 1–7 in Greed modes). |
| `stageType` | `StageType` number. 4 and 5 are the Repentance alternate path. |
| `altPath` | `true` on the Downpour/Dross/Mines/Ashpit/Mausoleum/Gehenna path. Used for the 25:00 Boss Rush deadline. |
| `name` | The floor name, when the game provides it. |
| `curses` | Curse bit flags: 1 Darkness, 2 Labyrinth, 4 Lost, 8 Unknown, 16 Cursed, 32 Maze, 64 Blind, 128 Giant. |
| `bossCleared` | Every boss room on this floor has been cleared. |
| `greedWave`, `greedWaves` | Greed modes only: current wave and total waves. |

**Boss Rush / Hush countdowns** are worked out by the app from `timeFrames`,
`stage`, `altPath`, `bossCleared`, `bossRushDone` and `blueWombDone`.

## `map`

```json
{ "hidden": false, "width": 13, "current": 45,
  "rooms": [ { "index": 45, "shape": 1, "type": 4, "visited": true,
               "clear": true, "display": 5 } ] }
```

| Field | Meaning |
|---|---|
| `hidden` | `true` under Curse of the Lost. |
| `hiddenReason` | `"lost"` when hidden by the curse. |
| `mirrored` | `true` in the mirror world (Downpour II / Dross II). The app flips the map left to right, as the game does. |
| `width` | Grid width (13). Room `index` = `y * width + x`. |
| `current` | Grid index of the room the player is in. |
| `rooms[].index` | The room's grid index (top-left cell for big rooms). |
| `rooms[].shape` | `RoomShape` number (1 = 1×1, 8 = 2×2, 9–12 = L shapes…). |
| `rooms[].type` | `RoomType` number (1 normal, 2 shop, 4 treasure, 5 boss…). |
| `rooms[].display` | The game's minimap flags: 1 shown, 2 shadow, 4 icon shown. |

## `room`

```json
{ "index": 45, "type": 4,
  "pedestals": [ { "id": 118, "name": "#BRIMSTONE_NAME", "group": 0 },
                 { "blind": true, "group": 0 } ] }
```

| Field | Meaning |
|---|---|
| `pedestals[].id`, `name` | The item on the pedestal. Left out when blind. Empty pedestals aren't listed. |
| `pedestals[].blind` | `true` when the game shows a "?" instead of the item. |
| `pedestals[].group` | "Choose one" group (`OptionsPickupIndex`); 0 = not part of a group. |

Prices aren't sent. The game already shows them on screen, so the app numbers
the items the same way in every room, shops and devil deals included.

## `players[]`

Player 1 is first. Co-op players, Esau, Tainted Forgotten's Soul, helpers
such as Strawman's Keeper, and similar follow, in the game's own order
(`Isaac.GetPlayer`).

```json
{
  "index": 0, "type": 0, "name": "Isaac",
  "health": { "hidden": false, "maxHearts": 12, "hearts": 9, "soulHearts": 5,
              "blackMask": 4, "boneHearts": 0, "boneMask": 0, "goldenHearts": 0,
              "eternalHearts": 0, "rottenHearts": 0, "brokenHearts": 0,
              "heartLimit": 24 },
  "lives": 1, "livesMaybe": true,
  "shields": [ "holyMantle", "woodenCross" ],
  "pickups": { "coins": 7, "bombs": 2, "keys": 1, "goldenBomb": false,
               "goldenKey": false, "gigaBombs": 0 },
  "stats": { "speed": 1.0, "tears": 2.73, "damage": 5.25, "range": 6.5,
             "shotSpeed": 1.0, "luck": 1.0 },
  "actives": [ { "slot": 0, "id": 105, "name": "#THE_D6_NAME", "charge": 4,
                 "maxCharge": 6, "battery": 0 } ],
  "trinkets": [ { "slot": 0, "id": 2, "golden": false, "name": "#PETRIFIED_POOP_NAME" } ],
  "pockets": [ { "slot": 0, "kind": "card", "id": 1, "name": "#FOOL_NAME" },
               { "slot": 1, "kind": "pill", "color": 3, "identified": false } ],
  "pills": [ { "color": 5, "effect": 5, "name": "#FULL_HEALTH_NAME" } ],
  "items": [ { "id": 4, "name": "#CRICKETS_HEAD_NAME" } ],
  "forms": [ 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 ],
  "controller": 0,
  "twin": { "index": 1, "main": true }
}
```

| Field | Meaning |
|---|---|
| `controller` | The player's `ControllerIndex`. Players on the same controller are one person (Jacob and Esau, a player and their Strawman Keeper). |
| `twin` | Only for pairs that play as one: Jacob and Esau, Tainted Forgotten and his Soul, Tainted Lazarus and his other form (with Birthright). `index` is the other one's place in `players`; `main` is true for the first of the pair (Jacob, Tainted Forgotten, Tainted Lazarus) and false for the second (Esau…). The app shows the main twin's HUD where a solo player's goes and Esau's in the bottom-right, like the game. |
| `ghost`, `baby`, `child` | Sent only as `true`. `ghost`: a co-op player who died and is back as a little ghost. `baby`: a co-op baby (the game's co-op babies, Found Soul and the co-op ghosts are all babies). `child`: a helper with a parent player, such as Strawman's Keeper. |
| `type` | `PlayerType` number; the app uses it for special health (Keeper's coins, The Lost…) and the portrait. |
| `health.*` | Raw values in half hearts, as the game reports them. `blackMask` is the game's black-heart bit mask over soul-heart slots; `boneMask` marks which heart slots after the red containers are bone hearts. The app rebuilds the heart row from these. |
| `lives`, `livesMaybe` | Extra lives; `livesMaybe` is true when one depends on chance (Guppy's Collar, Broken Ankh), so the app shows "x1?". |
| `shields` | Holy Mantle-type shields in draw order (`holyMantle`, `blanket`, `holyCard`, `woodenCross`). **The last entry breaks first.** Which item gave which shield is the mod's best guess from what the player holds; the count is exact. |
| `pickups.*` | Coins, bombs, keys and their golden/giga variants. `poopMana` (Tainted ???), `soulCharge` and `bloodCharge` (Bethanys) appear when the game provides them. |
| `stats.*` | Found HUD values: `tears` is shots per second, `range` is in the game's display units. |
| `actives[]` | `slot` 0 primary, 1 Schoolbag, 2–3 pocket actives. `battery` is the extra charge from The Battery. |
| `trinkets[]` | Up to two; `golden` set for golden trinkets (the ID is sent without the golden bit). |
| `pockets[]` | Cards and runes (`kind: "card"`), pills (`kind: "pill"`, with `color`, `identified` and, once identified, `effect`). Pocket actives are in `actives`. |
| `pills[]` | For the pill tracker, which is parked for now (the app doesn't use this yet): every pill colour the run has identified, in colour order (`PillColor` 1–13, the frames of the pills in `ui_cardspills.png`), with its `effect` (`PillEffect`) and `name` **for this player** (PHD and False PHD change some effects). Colours the game hasn't identified are left out. Gold pills are never listed, since they do something different each time. Horse pills share their colour's entry. |
| `items[]` | Passive and active collectibles **in pickup order**, duplicates kept. Saved with the run, so continuing a run keeps the order. |
| `forms[]` | Progress toward each transformation, indexed by `PlayerForm` number. |
| `poops` | Tainted ??? only. `queue` is his upcoming poop spells, next first (up to 6, the length of the game's queue), as `PoopSpellType` numbers (1 normal, 2 corny, 3 burning, 4 stone, 5 stinky, 6 black, 7 holy, 8 liquid, 9 fart, 10 bomb, 11 diarrhea; the frames of `ui_poops.anm2`). `max` is how many poops he can hold. How many he holds now is `pickups.poopMana`. |
| `otherForm` | The Forgotten and The Soul only: `type` and `health` of the form **not** in control (`type` and `health` above are the one in control). Left out under Curse of the Unknown, like `health`. |
| `bag` | Only while the player holds the Bag of Crafting (Tainted Cain's pocket). `items` lists what's in it, oldest first, as the game's crafting components 1–29 (the frames of `ui_crafting.anm2`: 1 red heart, 2 soul heart, 3 black heart, 4 eternal heart, 5 gold heart, 6 bone heart, 7 rotten heart, 8 penny, 9 nickel, 10 dime, 11 lucky penny, 12 key, 13 golden key, 14 charged key, 15 bomb, 16 golden bomb, 17 giga bomb, 18 micro battery, 19 lil' battery, 20 mega battery, 21 card, 22 pill, 23 rune or soul stone, 24 dice shard, 25 cracked key, 26 golden penny, 27 golden pill, 28 golden battery, 29 poop). At most 8. See "The Bag of Crafting" below. |

### The Bag of Crafting

The game's mod API can't read the bag (only REPENTOGON can), so the mod works it out and `bag` is a best guess:

- A pickup goes in when it vanishes within half a second of the swung bag reaching it (80 world units, or the game reporting the bag touching it), **and** the player's own counter for that kind of pickup (coins, keys, bombs, hearts, cards and pills, charges, poops) didn't change between the bag first reaching it and it vanishing (a pickup the player takes changes their counter at once, then lingers for its pickup animation). Double packs add two; a blended heart adds a red and a soul heart.
- A full bag drops its oldest pickup for the new one. The drop button cycles the bag (oldest to the end). A full bag empties when a new item turns up in the player's inventory (a craft).
- `sure` is false when the mod knows it may have missed something: it joined a run without knowing the bag (mod save data missing or from another run), or Tainted Cain crafted an active item while holding one (the old item turns into pickups that go into the bag unseen). It turns true again after the next craft.
- The bag is saved with the run whenever it changes, so continuing a run keeps it.
- No crafting result: the mod doesn't send what the bag would make (mods can't read it, and the known calculation isn't reliable on Repentance+). On Tainted Cain runs the game's own HUD comes back while the bag is full, so the game's preview stays on the main screen (see above).

## `chances`

Present when the game exposes them: `devil` (0–1) and `planetarium` (0–1).
The app shows `planetarium` as a ninth stat under the angel chance; the game
itself doesn't show it.

## `death`

```json
{ "gameOver": true, "killer": { "type": 10, "variant": 1, "subtype": 0 } }
```

`killer` is the last thing that damaged the player (for projectiles and similar,
whatever fired them). The app turns it into a name and drawing with
`entities2.xml`. A death that an extra life undoes doesn't set `gameOver`.
