# Schoolbag protocol, version 1

The Schoolbag mod runs inside The Binding of Isaac: Repentance+ and answers HTTP
requests on **`http://127.0.0.1:8089`**. The Schoolbag app polls it and draws
the bottom-screen HUD. Nothing is ever sent back to the game.

Port 8089 was picked so Schoolbag can run alongside hud-stream (isaac-hud's mod,
which uses 8088). The server only listens on the loopback address.

## Endpoints

| Path | Answer |
|---|---|
| `GET /state` | The full state below, as JSON. |
| `GET /health` | `ok` (plain text). Cheap check that the mod is up. |
| anything else | `404`. |

Every response has `Connection: close` and `Access-Control-Allow-Origin: *`.

**When nothing answers:** the mod can only answer while the game is rendering a
run. On the title screen and in menus there is no answer at all, and if
`--luadebug` is missing the server never starts. The app treats "no answer" as
"not in a run" and, after a while, shows a hint to check the mod and the launch
option. In the game, the mod says the same thing itself: from a few seconds
into each run it keeps a banner up in the style of the game's own banners,
"Schoolbag is offline", with "Needs --luadebug" (or "Port 8089 is busy") on
the paper strip under it. It uses the game's own banner sprite and fonts and
steps aside whenever the game shows a banner of its own in the same spot.

**Polling:** about 10 times a second is plenty. The mod builds the state on
request, so it costs nothing when the app isn't running.

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
  mod logs the problem once to the game's `log.txt`.
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
- **Unidentified pills:** `identified: false` and no `effect`.
- **Curse of the Unknown:** `health` is `{"hidden": true}` and `shields` is
  empty.
- **Map:** only rooms the game itself shows on the minimap are sent (rooms with
  display flags 0 are left out). Under Curse of the Lost the list is empty and
  `map.hidden` is true.

## Top level

```json
{
  "protocol": 1,
  "mod": { "name": "Schoolbag", "version": "0.1.0" },
  "inRun": true,
  "paused": false,
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
| `inRun` | `true` while a run is going. |
| `paused` | The game is on its pause screen. The mod keeps answering while paused. |
| `transition` | Present while the player is leaving the floor: `"trapdoor"` or `"beam"`. The app shows the loading doodle until the next floor's data arrives. |

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

Player 1 is first. Co-op players, Jacob & Esau and similar follow.

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
  "items": [ { "id": 4, "name": "#CRICKETS_HEAD_NAME" } ],
  "forms": [ 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 ]
}
```

| Field | Meaning |
|---|---|
| `type` | `PlayerType` number; the app uses it for special health (Keeper's coins, The Lost…) and the portrait. |
| `health.*` | Raw values in half hearts, as the game reports them. `blackMask` is the game's black-heart bit mask over soul-heart slots; `boneMask` marks which heart slots after the red containers are bone hearts. The app rebuilds the heart row from these. |
| `lives`, `livesMaybe` | Extra lives; `livesMaybe` is true when one depends on chance (Guppy's Collar, Broken Ankh), so the app shows "x1?". |
| `shields` | Holy Mantle-type shields in draw order (`holyMantle`, `blanket`, `holyCard`, `woodenCross`). **The last entry breaks first.** Which item gave which shield is the mod's best guess from what the player holds; the count is exact. |
| `pickups.*` | Coins, bombs, keys and their golden/giga variants. `poopMana` (Tainted ???), `soulCharge` and `bloodCharge` (Bethanys) appear when the game provides them. |
| `stats.*` | Found HUD values: `tears` is shots per second, `range` is in the game's display units. |
| `actives[]` | `slot` 0 primary, 1 Schoolbag, 2–3 pocket actives. `battery` is the extra charge from The Battery. |
| `trinkets[]` | Up to two; `golden` set for golden trinkets (the ID is sent without the golden bit). |
| `pockets[]` | Cards and runes (`kind: "card"`), pills (`kind: "pill"`, with `color`, `identified` and, once identified, `effect`). Pocket actives are in `actives`. |
| `items[]` | Passive and active collectibles **in pickup order**, duplicates kept. Saved with the run, so continuing a run keeps the order. |
| `forms[]` | Progress toward each transformation, indexed by `PlayerForm` number. |

## `chances`

Present when the game exposes them: `devil` (0–1) and `planetarium` (0–1).

## `death`

```json
{ "gameOver": true, "killer": { "type": 10, "variant": 1, "subtype": 0 } }
```

`killer` is the last thing that damaged the player (for projectiles and similar,
whatever fired them). The app turns it into a name and drawing with
`entities2.xml`. A death that an extra life undoes doesn't set `gameOver`.
