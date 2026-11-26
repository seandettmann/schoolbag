-- Schoolbag: sends the game's state to the Schoolbag app on the same device.
--
-- It answers HTTP requests on http://127.0.0.1:8089 (see docs/protocol.md),
-- and, when the app asks for it, sends the state to the app over UDP on the
-- same port whenever it changes. It never makes the game wait on the app: every
-- read and send is non-blocking, and half-finished requests carry on next frame.
-- It only reads game state, apart from hiding the game's HUD while the app
-- is reading (below).
-- The server needs the game's --luadebug launch option, because that is what
-- lets a mod load LuaSocket. Without it, the mod shows a short reminder at the
-- start of each run, drawn like the game's own item banner, that stays up for
-- the whole run, and does nothing else.
--
-- While the app is reading the run, the mod hides the game's own HUD on the
-- main screen; it comes back a few seconds after the app stops, and on Tainted
-- Cain runs while his Bag of Crafting is full (for the game's crafting preview).

local mod = RegisterMod("Schoolbag", 1)

local VERSION = "0.3.0"
local PROTOCOL = 1
local HOST, PORT = "127.0.0.1", 8089
local SCAN_EVERY = 10 -- game updates between inventory scans (30 updates = 1 s)

local json = require("json")
local game = Game()

------------------------------------------------------------------------------
-- Helpers
------------------------------------------------------------------------------

-- An error message without the folders in front of the file name.
local function shortErr(e)
  return (tostring(e):gsub("^.-([%w_%-]+%.lua:%d+:)", "%1"))
end

-- Game calls that didn't answer, for /watch. Often fine: not every call exists
-- for every character or game version, which is why `try` is used. Kept to
-- the first 30 different ones.
local misses, missSeen = {}, {}

-- Run fn and return its result, or nil if it errors (for API calls that may
-- not exist in every game version).
local function try(fn)
  local ok, v = pcall(fn)
  if ok then return v end
  if not missSeen[v] then
    missSeen[v] = true
    if #misses < 30 then misses[#misses + 1] = shortErr(v) end
  end
  return nil
end

-- A finite number, or the fallback (JSON can't hold NaN or infinity).
local function num(x, fallback)
  if type(x) ~= "number" or x ~= x or x == math.huge or x == -math.huge then
    return fallback or 0
  end
  return x
end

local function round(x, places)
  local m = 10 ^ (places or 2)
  return math.floor(num(x) * m + 0.5) / m
end

-- Look up an enum value from the game's enums.lua, with a fallback number in
-- case the name differs in some game version.
local function enum(tbl, key, fallback)
  if type(tbl) == "table" and tbl[key] ~= nil then return tbl[key] end
  return fallback
end

local function log(msg)
  Isaac.DebugString("[Schoolbag] " .. tostring(msg))
end

-- Parts of the mod that failed, for /watch as well as the game's log.txt
-- (which is hard to reach on a handheld). Each one once; at most 30.
local problems, problemSeen = {}, {}
local function problem(msg)
  msg = tostring(msg)
  if problemSeen[msg] then return end
  problemSeen[msg] = true
  log(msg)
  if #problems < 30 then problems[#problems + 1] = msg end
end
local logOnce = problem -- for things that could otherwise repeat every frame

local K = {
  -- items and trinkets
  HOLY_MANTLE    = enum(CollectibleType, "COLLECTIBLE_HOLY_MANTLE", 313),
  BLANKET        = enum(CollectibleType, "COLLECTIBLE_BLANKET", 535),
  GUPPYS_COLLAR  = enum(CollectibleType, "COLLECTIBLE_GUPPYS_COLLAR", 212),
  BROKEN_ANKH    = enum(TrinketType, "TRINKET_BROKEN_ANKH", 28),
  WOODEN_CROSS   = enum(TrinketType, "TRINKET_WOODEN_CROSS", 121),
  GOLDEN_TRINKET = enum(TrinketType, "TRINKET_GOLDEN_FLAG", 32768),
  HORSE_PILL     = enum(PillColor, "PILL_GIANT_FLAG", 2048),
  -- curses (bit flags)
  CURSE_LABYRINTH = enum(LevelCurse, "CURSE_OF_LABYRINTH", 2),
  CURSE_LOST     = enum(LevelCurse, "CURSE_OF_THE_LOST", 4),
  CURSE_UNKNOWN  = enum(LevelCurse, "CURSE_OF_THE_UNKNOWN", 8),
  CURSE_BLIND    = enum(LevelCurse, "CURSE_OF_BLIND", 64),
  -- entities
  ENTITY_PLAYER  = enum(EntityType, "ENTITY_PLAYER", 1),
  ENTITY_TEAR    = enum(EntityType, "ENTITY_TEAR", 2),
  ENTITY_PICKUP  = enum(EntityType, "ENTITY_PICKUP", 5),
  ENTITY_LASER   = enum(EntityType, "ENTITY_LASER", 7),
  ENTITY_KNIFE   = enum(EntityType, "ENTITY_KNIFE", 8),
  ENTITY_PROJECTILE = enum(EntityType, "ENTITY_PROJECTILE", 9),
  ENTITY_EFFECT  = enum(EntityType, "ENTITY_EFFECT", 1000),
  COLLECTIBLE    = enum(PickupVariant, "PICKUP_COLLECTIBLE", 100),
  -- rooms and stages
  ROOM_BOSS      = enum(RoomType, "ROOM_BOSS", 5),
  STAGE_ALT_A    = enum(StageType, "STAGETYPE_REPENTANCE", 4),
  STAGE_ALT_B    = enum(StageType, "STAGETYPE_REPENTANCE_B", 5),
  NUM_FORMS      = enum(PlayerForm, "NUM_PLAYER_FORMS", 16),
}

------------------------------------------------------------------------------
-- Run bookkeeping
------------------------------------------------------------------------------

local run = {
  inRun = false,
  ended = nil,       -- "win" / "loss" once the run is over
  lastDamage = nil,  -- what last hurt a player (for the death screen)
  players = {},      -- per player index: { order = {ids...}, counts = {id = n} }
  bags = {},         -- per player index: Bag of Crafting contents (see below)
  bagsKnown = false, -- false when the mod joined a run without knowing what was in the bags
  bagsDirty = false, -- a bag changed since the last save
}

local function playerBook(i)
  local b = run.players[i + 1]
  if not b then
    b = { order = {}, counts = {} }
    run.players[i + 1] = b
  end
  return b
end

local function resetRun()
  run.ended = nil
  run.lastDamage = nil
  run.players = {}
  run.bags = {}
  run.bagsKnown = true
  run.bagsDirty = false
end

-- Record pickup order by watching how many of each collectible a player has.
-- New copies go on the end; lost copies come off the end.
local validIds = nil -- every collectible ID the game knows, found once

local function collectibleIds()
  if validIds then return validIds end
  local config = Isaac.GetItemConfig()
  local size = config:GetCollectibles().Size
  validIds = {}
  for id = 1, size - 1 do
    if config:GetCollectible(id) then validIds[#validIds + 1] = id end
  end
  return validIds
end

local function scanItems(i, player)
  local book = playerBook(i)
  for _, id in ipairs(collectibleIds()) do
    local n = player:GetCollectibleNum(id, true)
    local prev = book.counts[id] or 0
    if n > prev then
      for _ = 1, n - prev do book.order[#book.order + 1] = id end
    elseif n < prev then
      local remove = prev - n
      for j = #book.order, 1, -1 do
        if remove == 0 then break end
        if book.order[j] == id then
          table.remove(book.order, j)
          remove = remove - 1
        end
      end
    end
    if n > 0 then book.counts[id] = n else book.counts[id] = nil end
  end
end

local function scanAllPlayers()
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    if p then scanItems(i, p) end
  end
end

local function saveRun()
  local seed = try(function() return game:GetSeeds():GetStartSeedString() end)
  local data = { v = 1, order = {}, bags = {}, seed = seed }
  for i, book in ipairs(run.players) do data.order[i] = book.order end
  for i = 1, #run.bags do
    local b = run.bags[i]
    data.bags[i] = b and { items = b.items, sure = b.sure } or false
  end
  local ok, text = pcall(json.encode, data)
  if ok then mod:SaveData(text) end
  run.bagsDirty = false
end

local function loadRun()
  run.bagsKnown = false -- unless the save below has this run's bags
  if not mod:HasData() then return end
  local ok, data = pcall(json.decode, mod:LoadData())
  if not ok or type(data) ~= "table" or type(data.order) ~= "table" then return end
  for i, order in ipairs(data.order) do
    local book = { order = {}, counts = {} }
    for _, id in ipairs(order) do
      if type(id) == "number" then
        book.order[#book.order + 1] = id
        book.counts[id] = (book.counts[id] or 0) + 1
      end
    end
    run.players[i] = book
  end
  local seed = try(function() return game:GetSeeds():GetStartSeedString() end)
  if type(data.bags) == "table" and data.seed ~= nil and data.seed == seed then
    run.bagsKnown = true
    for i, b in ipairs(data.bags) do
      if type(b) == "table" and type(b.items) == "table" then
        local items = {}
        for _, c in ipairs(b.items) do
          if type(c) == "number" then items[#items + 1] = c end
        end
        run.bags[i] = { items = items, sure = b.sure ~= false }
      end
    end
  end
end

------------------------------------------------------------------------------
-- Bag of Crafting: what's in the bag
------------------------------------------------------------------------------
-- The vanilla API can't read the bag (only REPENTOGON can), so the mod works it
-- out: a pickup that vanishes soon after the swung bag touched it, while the
-- player's own counters for that kind of pickup stay put, went into the bag.
-- Best effort; `sure` turns false when the mod knows it may have missed some.
-- Components use the game's numbering (ui_crafting.anm2 frames, 1-29).

local BAG_ITEM   = enum(CollectibleType, "COLLECTIBLE_BAG_OF_CRAFTING", 710)
local BAG_KNIFE  = enum(KnifeVariant, "BAG_OF_CRAFTING", 4)
local BAG_SIZE   = 8
local BAG_REACH  = 80 -- world units between the swung bag and a pickup it can scoop
local BAG_WINDOW = 15 -- game updates a pickup stays "touched by the bag"
local PV = {
  HEART   = enum(PickupVariant, "PICKUP_HEART", 10),
  COIN    = enum(PickupVariant, "PICKUP_COIN", 20),
  KEY     = enum(PickupVariant, "PICKUP_KEY", 30),
  BOMB    = enum(PickupVariant, "PICKUP_BOMB", 40),
  POOP    = enum(PickupVariant, "PICKUP_POOP", 42),
  PILL    = enum(PickupVariant, "PICKUP_PILL", 70),
  BATTERY = enum(PickupVariant, "PICKUP_LIL_BATTERY", 90),
  CARD    = enum(PickupVariant, "PICKUP_TAROTCARD", 300),
}
local PILL_GOLD = enum(PillColor, "PILL_GOLD", 14)
local CARD_DICE_SHARD = enum(Card, "CARD_DICE_SHARD", 49)
local CARD_CRACKED_KEY = enum(Card, "CARD_CRACKED_KEY", 78)

-- pickup subtype -> the component(s) it adds; double packs add two
local BAG_PARTS = {
  [PV.HEART] = { [1] = { 1 }, [2] = { 1 }, [3] = { 2 }, [4] = { 4 }, [5] = { 1, 1 }, [6] = { 3 }, [7] = { 5 },
    [8] = { 2 }, [9] = { 1 }, [10] = { 1, 2 }, [11] = { 6 }, [12] = { 7 } },
  [PV.COIN] = { [1] = { 8 }, [2] = { 9 }, [3] = { 10 }, [4] = { 8, 8 }, [5] = { 11 }, [6] = { 9 }, [7] = { 26 } },
  [PV.KEY] = { [1] = { 12 }, [2] = { 13 }, [3] = { 12, 12 }, [4] = { 14 } },
  [PV.BOMB] = { [1] = { 15 }, [2] = { 15, 15 }, [4] = { 16 }, [7] = { 17 } },
  [PV.BATTERY] = { [1] = { 19 }, [2] = { 18 }, [3] = { 20 }, [4] = { 28 } },
}

local function isRune(card)
  return (card >= 32 and card <= 41) or card == 55 or (card >= 81 and card <= 97)
end

local function bagParts(variant, subtype)
  if variant == PV.PILL then
    return { (subtype & (K.HORSE_PILL - 1)) == PILL_GOLD and 27 or 22 }
  elseif variant == PV.CARD then
    if subtype <= 0 then return nil end
    if subtype == CARD_DICE_SHARD then return { 24 } end
    if subtype == CARD_CRACKED_KEY then return { 25 } end
    return { isRune(subtype) and 23 or 21 }
  elseif variant == PV.POOP then
    return { 29 }
  end
  local byType = BAG_PARTS[variant]
  return byType and byType[subtype] or nil
end

-- Which of the player's own counters a pickup kind would change if it were
-- picked up the normal way. If that changes as the pickup vanishes, the player
-- took it, not the bag.
local function counterKind(variant)
  if variant == PV.HEART then return "hearts"
  elseif variant == PV.COIN then return "coins"
  elseif variant == PV.KEY then return "keys"
  elseif variant == PV.BOMB then return "bombs"
  elseif variant == PV.BATTERY then return "charge"
  elseif variant == PV.PILL or variant == PV.CARD then return "pockets"
  elseif variant == PV.POOP then return "poop"
  end
end

local function counters(p)
  local c = {}
  c.coins = tostring(try(function() return p:GetNumCoins() end))
  c.keys = tostring(try(function() return p:GetNumKeys() end)) .. "/" .. tostring(try(function() return p:HasGoldenKey() end))
  c.bombs = tostring(try(function() return p:GetNumBombs() end)) .. "/" .. tostring(try(function() return p:HasGoldenBomb() end))
    .. "/" .. tostring(try(function() return p:GetNumGigaBombs() end))
  local h = {}
  for _, f in ipairs({ "GetMaxHearts", "GetHearts", "GetSoulHearts", "GetBlackHearts", "GetBoneHearts",
      "GetEternalHearts", "GetGoldenHearts", "GetRottenHearts" }) do
    h[#h + 1] = tostring(try(function() return p[f](p) end))
  end
  c.hearts = table.concat(h, "/")
  local k = {}
  for slot = 0, 3 do
    k[#k + 1] = tostring(try(function() return p:GetCard(slot) end)) .. ":" .. tostring(try(function() return p:GetPill(slot) end))
  end
  c.pockets = table.concat(k, "/")
  local ch = { tostring(try(function() return p:GetBatteryCharge() end)) }
  for slot = 0, 3 do ch[#ch + 1] = tostring(try(function() return p:GetActiveCharge(slot) end)) end
  c.charge = table.concat(ch, "/")
  c.poop = tostring(try(function() return p:GetPoopMana() end))
  return c
end

local function holdsBag(p)
  for slot = 0, 3 do
    if try(function() return p:GetActiveItem(slot) end) == BAG_ITEM then return true end
  end
  return false
end

local function bagOf(i)
  local b = run.bags[i + 1]
  if not b then
    b = { items = {}, sure = run.bagsKnown }
    run.bags[i + 1] = b
  end
  return b
end

local function bagAdd(i, parts)
  local b = bagOf(i)
  for _, c in ipairs(parts) do
    -- a full bag drops its first pickup to make room (Tainted Cain)
    if #b.items >= BAG_SIZE then table.remove(b.items, 1) end
    b.items[#b.items + 1] = c
  end
  run.bagsDirty = true
end

local function hashOf(e)
  return try(function() return GetPtrHash(e) end) or try(function() return e.InitSeed end) or e
end

local bagWatch = {
  room = nil,      -- room the snapshot below belongs to
  pickups = {},    -- hash -> { variant, subtype, touched = update it was last near the bag }
  holders = {},    -- player index -> { hash, counters, count, active }
}

local function resetBagWatch()
  bagWatch.room = nil
  bagWatch.pickups = {}
end

-- Called from the pickup-collision callback too, in case the game reports the
-- bag touching a pickup before it's gone.
local function markTouched(pickup)
  local seen = bagWatch.pickups[hashOf(pickup)]
  if seen then
    local now = game:GetFrameCount()
    if not seen.touched or now - seen.touched > BAG_WINDOW then seen.first = now end
    seen.touched = now
  end
end

local function ownerIndex(knife, holders)
  local parent = try(function() return knife.Parent end) or try(function() return knife.SpawnerEntity end)
  if parent then
    local h = hashOf(parent)
    for i, w in pairs(holders) do
      if w.hash == h then return i end
    end
  end
  for i in pairs(holders) do return i end -- one holder in nearly every run
end

local function watchBag()
  local holders = {}
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    if p and holdsBag(p) then holders[i] = p end
  end
  if next(holders) == nil then
    resetBagWatch()
    bagWatch.holders = {}
    return
  end

  local now = game:GetFrameCount()
  local roomIndex = try(function() return game:GetLevel():GetCurrentRoomIndex() end)
  if roomIndex ~= bagWatch.room then
    resetBagWatch() -- everything vanishes when the room changes
    bagWatch.room = roomIndex
  end

  -- counters and crafting, per holder
  local prev = bagWatch.holders
  local current = {}
  for i, p in pairs(holders) do
    local w = {
      hash = hashOf(p),
      counters = counters(p),
      changed = {}, -- counter kind -> the last update it changed
      count = try(function() return p:GetCollectibleCount() end) or 0,
      active = try(function() return p:GetActiveItem(0) end) or 0,
    }
    local before = prev[i]
    if before then
      for kind, v in pairs(w.counters) do
        w.changed[kind] = v ~= before.counters[kind] and now or before.changed[kind]
      end
    end
    local b = bagOf(i)
    if before and #b.items >= BAG_SIZE and w.count > before.count then
      -- a full bag and a new item in the inventory: Tainted Cain just crafted
      b.items = {}
      b.sure = true
      run.bagsDirty = true
      -- crafting an active item while holding one turns the old one into
      -- pickups that go into the bag, which the mod can't see
      if before.active ~= 0 and before.active ~= BAG_ITEM and w.active ~= before.active then b.sure = false end
    end
    current[i] = w
  end
  bagWatch.holders = current

  -- where the swung bag is this update
  local knives = {}
  for _, k in ipairs(try(function() return Isaac.FindByType(K.ENTITY_KNIFE, BAG_KNIFE, -1, false, false) end) or {}) do
    knives[#knives + 1] = { pos = k.Position, owner = ownerIndex(k, current) }
  end

  -- pickups now; mark any the bag is touching
  local seenNow = {}
  for _, e in ipairs(try(function() return Isaac.FindByType(K.ENTITY_PICKUP, -1, -1, false, false) end) or {}) do
    local parts = bagParts(e.Variant, e.SubType)
    if parts then
      local h = hashOf(e)
      local old = bagWatch.pickups[h]
      local entry = { variant = e.Variant, subtype = e.SubType,
        first = old and old.first, touched = old and old.touched, owner = old and old.owner }
      for _, k in ipairs(knives) do
        local dx, dy = e.Position.X - k.pos.X, e.Position.Y - k.pos.Y
        if dx * dx + dy * dy <= BAG_REACH * BAG_REACH then
          if not entry.touched or now - entry.touched > BAG_WINDOW then entry.first = now end
          entry.touched, entry.owner = now, k.owner
        end
      end
      seenNow[h] = entry
    end
  end

  -- pickups that vanished since the last update
  for h, old in pairs(bagWatch.pickups) do
    if not seenNow[h] and old.touched and now - old.touched <= BAG_WINDOW then
      local i = old.owner
      if i == nil then for j in pairs(current) do i = j break end end
      -- A pickup the player takes changes their counter at once but lingers for
      -- its pickup animation, so look back to when the bag first reached it.
      local kind = counterKind(old.variant)
      local w = current[i]
      local since = (old.first or old.touched) - 2
      local tookIt = kind and w and w.changed[kind] and w.changed[kind] >= since
      if not tookIt then bagAdd(i, bagParts(old.variant, old.subtype)) end
    end
  end
  bagWatch.pickups = seenNow
end

-- Tainted Cain cycles the bag with the drop button. Order only decides which
-- pickup a full bag drops next; the result doesn't depend on it.
local function watchBagCycle()
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    local b = run.bags[i + 1]
    if p and b and #b.items > 1 and holdsBag(p) then
      local pressed = try(function()
        return Input.IsActionTriggered(ButtonAction.ACTION_DROP, p.ControllerIndex)
      end)
      if pressed then
        table.insert(b.items, table.remove(b.items, 1))
        run.bagsDirty = true
      end
    end
  end
end

local function bagInfo(i, p)
  if not holdsBag(p) then return nil end
  local b = bagOf(i)
  local items = {}
  for n, c in ipairs(b.items) do items[n] = c end
  return { items = items, sure = b.sure }
end

-- How long the game has been paused, in render frames, and when the Map and
-- pause buttons were last pressed (Isaac.GetFrameCount()), for the app to
-- follow the game.
local signals = { pausedFrames = 0, pauseBegan = 0, mapAt = 0, pauseAt = -1000, PAUSE_MENU = 45 }
do
  local ACTION_PAUSE = enum(ButtonAction, "ACTION_PAUSE", 12)
  local ACTION_MAP = enum(ButtonAction, "ACTION_MAP", 13)
  function signals.watchMap()
    local p = Isaac.GetPlayer(0)
    local ci = p and try(function() return p.ControllerIndex end)
    if not ci then return end
    if try(function() return Input.IsActionTriggered(ACTION_MAP, ci) end) then
      signals.mapAt = Isaac.GetFrameCount()
    end
    if try(function() return Input.IsActionTriggered(ACTION_PAUSE, ci) end) then
      signals.pauseAt = Isaac.GetFrameCount()
    end
  end
  -- The pause menu: paused for 45 render frames (3/4 s), and the pause button
  -- started it. The game also pauses for room changes (briefly) and for boss
  -- intros and floor changes (longer), without the button.
  function signals.pauseMenu()
    return signals.pausedFrames >= signals.PAUSE_MENU and signals.pauseAt >= signals.pauseBegan - 3
  end
end

------------------------------------------------------------------------------
-- A fast JSON encoder for the state
------------------------------------------------------------------------------
-- The state is encoded up to ten times a second, on the game's own thread. This
-- encoder writes into one buffer, and can splice in text encoded earlier
-- (fast.raw()), so the parts that rarely change (the map, each player's items)
-- are only encoded again when they do. Empty tables are arrays, like the game's
-- json.lua; numbers that aren't finite become null.
local fast = {}
do
  local RAW = {}
  function fast.raw(text) return setmetatable({ text = text }, RAW) end

  local ESCAPES = { ['"'] = '\\"', ['\\'] = '\\\\', ['\n'] = '\\n', ['\r'] = '\\r', ['\t'] = '\\t' }
  local function escapeChar(c) return ESCAPES[c] or string.format("\\u%04x", c:byte()) end

  local function encodeInto(v, out)
    local t = type(v)
    if t == "string" then
      out[#out + 1] = '"' .. v:gsub('[%c"\\]', escapeChar) .. '"'
    elseif t == "number" then
      if v ~= v or v == math.huge or v == -math.huge then
        out[#out + 1] = "null"
      elseif math.type(v) == "integer" then
        out[#out + 1] = tostring(v)
      else
        out[#out + 1] = string.format("%.14g", v)
      end
    elseif t == "boolean" then
      out[#out + 1] = v and "true" or "false"
    elseif t == "table" then
      if getmetatable(v) == RAW then
        out[#out + 1] = v.text
      elseif next(v) == nil then
        out[#out + 1] = "[]"
      elseif v[1] ~= nil then
        out[#out + 1] = "["
        for i = 1, #v do
          if i > 1 then out[#out + 1] = "," end
          encodeInto(v[i], out)
        end
        out[#out + 1] = "]"
      else
        out[#out + 1] = "{"
        local first = true
        for k, val in pairs(v) do
          if not first then out[#out + 1] = "," end
          first = false
          encodeInto(tostring(k), out)
          out[#out + 1] = ":"
          encodeInto(val, out)
        end
        out[#out + 1] = "}"
      end
    else
      out[#out + 1] = "null"
    end
  end

  function fast.encode(v)
    local out = {}
    encodeInto(v, out)
    return table.concat(out)
  end

  -- Parts of the state kept as encoded text until their signature changes.
  local fragments = {}
  function fast.cached(key, sig, build)
    local f = fragments[key]
    if f and f.sig == sig then return f.value end
    local value = build()
    if value == nil then fragments[key] = nil return nil end
    f = { sig = sig, value = fast.raw(fast.encode(value)) }
    fragments[key] = f
    return f.value
  end

  -- The map's signature. mapEpoch is bumped by anything that can change what
  -- the minimap shows without the room changing (an item, card or pill used, a
  -- room cleared); the map is also rebuilt once a second regardless, so nothing
  -- stays stale for long.
  local mapEpoch = 0
  function fast.mapChanged() mapEpoch = mapEpoch + 1 end
  function fast.mapSignature(level, curses)
    local current = try(function() return level:GetCurrentRoomIndex() end) or -1
    local p = Isaac.GetPlayer(0)
    local items = p and try(function() return p:GetCollectibleCount() end) or 0
    local mirrored = try(function() return game:GetRoom():IsMirrorWorld() end) == true
    return table.concat({ try(function() return level:GetStage() end) or 0,
      try(function() return level:GetStageType() end) or 0, curses, current, mapEpoch, items,
      tostring(mirrored), math.floor(Isaac.GetFrameCount() / 60) }, ":")
  end
end

------------------------------------------------------------------------------
-- How long the mod spends answering the app, for /watch and the app's About page
------------------------------------------------------------------------------
-- Measured in 5-second windows: answers sent, their average and worst time
-- (building and encoding the state), and the worst time the server took in
-- any one frame.
local perf = { n = 0, sum = 0, max = 0, frameMax = 0, start = nil, last = nil, transport = "http",
  clock = (os and os.clock) or function() return Isaac.GetTime() / 1000 end }
function perf.answer(seconds)
  perf.n = perf.n + 1
  perf.sum = perf.sum + seconds
  if seconds > perf.max then perf.max = seconds end
end
function perf.frame(seconds)
  if seconds > perf.frameMax then perf.frameMax = seconds end
  local f = Isaac.GetFrameCount()
  perf.start = perf.start or f
  if f - perf.start >= 5 * 60 then
    local function ms(x) return math.floor(x * 100000 + 0.5) / 100 end
    perf.last = { answers = perf.n, avgMs = perf.n > 0 and ms(perf.sum / perf.n) or 0, maxMs = ms(perf.max),
      frameMaxMs = ms(perf.frameMax), seconds = math.floor((f - perf.start) / 60 + 0.5) }
    perf.n, perf.sum, perf.max, perf.frameMax, perf.start = 0, 0, 0, 0, f
  end
end

------------------------------------------------------------------------------
-- Building the state
------------------------------------------------------------------------------

local nameCache = { item = {}, trinket = {}, card = {}, pill = {} }

local function configName(kind, id)
  local cache = nameCache[kind]
  if cache[id] ~= nil then return cache[id] or nil end
  local config = Isaac.GetItemConfig()
  local entry = try(function()
    if kind == "item" then return config:GetCollectible(id)
    elseif kind == "trinket" then return config:GetTrinket(id)
    elseif kind == "card" then return config:GetCard(id)
    else return config:GetPillEffect(id) end
  end)
  local name = entry and try(function() return entry.Name end) or false
  cache[id] = name
  return name or nil
end

local function stateFlag(key)
  local flag = enum(GameStateFlag, key, nil)
  if flag == nil then return nil end
  return try(function() return game:GetStateFlag(flag) end)
end

local function runInfo()
  return {
    seed = try(function() return game:GetSeeds():GetStartSeedString() end),
    difficulty = game.Difficulty,
    challenge = game.Challenge,
    victoryLap = try(function() return game:GetVictoryLap() end) or 0,
    timeFrames = game.TimeCounter,
    bossRushDone = stateFlag("STATE_BOSSRUSH_DONE"),
    blueWombDone = stateFlag("STATE_BLUEWOMB_DONE"),
    ended = run.ended,
  }
end

local function floorInfo(level, curses)
  local stageType = level:GetStageType()
  local f = {
    stage = level:GetStage(),
    stageType = stageType,
    altPath = stageType == K.STAGE_ALT_A or stageType == K.STAGE_ALT_B,
    name = try(function() return level:GetName() end),
    curses = curses,
  }
  local rooms = level:GetRooms()
  local bosses, cleared = 0, 0
  for i = 0, rooms.Size - 1 do
    local d = rooms:Get(i)
    if d and d.Data and d.Data.Type == K.ROOM_BOSS then
      bosses = bosses + 1
      if d.Clear then cleared = cleared + 1 end
    end
  end
  f.bossCleared = bosses > 0 and cleared == bosses
  if try(function() return game:IsGreedMode() end) then
    f.greedWave = try(function() return level.GreedModeWave end)
    f.greedWaves = try(function() return game:GetGreedWavesNum() end)
  end
  return f
end

local function mapInfo(level, curses)
  local current = level:GetCurrentRoomDesc()
  local currentIndex = current and current.SafeGridIndex or level:GetCurrentRoomIndex()
  local m = { width = 13, current = currentIndex, rooms = {} }
  -- the mirror world (Downpour II / Dross II): the app flips the map, as the game does
  m.mirrored = try(function() return game:GetRoom():IsMirrorWorld() end) == true
  if curses & K.CURSE_LOST ~= 0 then
    m.hidden = true
    m.hiddenReason = "lost"
    return m
  end
  m.hidden = false
  local rooms = level:GetRooms()
  for i = 0, rooms.Size - 1 do
    local d = rooms:Get(i)
    if d and d.GridIndex >= 0 then
      local display = d.DisplayFlags or 0
      local isCurrent = d.SafeGridIndex == currentIndex
      -- only what the game itself shows on the minimap (no spoilers)
      if display ~= 0 or isCurrent then
        m.rooms[#m.rooms + 1] = {
          index = d.SafeGridIndex,
          shape = d.Data and d.Data.Shape or 1,
          type = d.Data and d.Data.Type or 1,
          visited = (d.VisitedCount or 0) > 0,
          clear = d.Clear == true,
          display = display,
        }
      end
    end
  end
  return m
end

local function pedestalIsBlind(pickup, curses)
  if curses & K.CURSE_BLIND ~= 0 then return true end
  return try(function() return pickup:IsBlind() end) == true
end

local function roomInfo(level, curses)
  local room = game:GetRoom()
  local r = { index = level:GetCurrentRoomIndex(), type = room:GetType(), pedestals = {} }
  local found = Isaac.FindByType(K.ENTITY_PICKUP, K.COLLECTIBLE, -1, false, false)
  local list = {}
  for _, e in ipairs(found) do
    local p = e:ToPickup()
    if p and e.SubType > 0 then
      list[#list + 1] = { e = e, p = p }
    end
  end
  -- number pedestals left to right, top to bottom
  table.sort(list, function(a, b)
    local ay, by = math.floor(a.e.Position.Y / 40), math.floor(b.e.Position.Y / 40)
    if ay ~= by then return ay < by end
    return a.e.Position.X < b.e.Position.X
  end)
  for _, it in ipairs(list) do
    -- No prices: the app just numbers the items, in shops and devil deals alike.
    local entry = { group = num(it.p.OptionsPickupIndex) }
    if pedestalIsBlind(it.p, curses) then
      entry.blind = true
    else
      entry.id = it.e.SubType
      entry.name = configName("item", it.e.SubType)
    end
    r.pedestals[#r.pedestals + 1] = entry
  end
  return r
end

local function healthInfo(p)
  local soul = p:GetSoulHearts()
  local bone = p:GetBoneHearts()
  local h = {
    hidden = false,
    maxHearts = p:GetMaxHearts(),
    hearts = p:GetHearts(),
    soulHearts = soul,
    blackMask = p:GetBlackHearts(),
    boneHearts = bone,
    goldenHearts = p:GetGoldenHearts(),
    eternalHearts = p:GetEternalHearts(),
    rottenHearts = try(function() return p:GetRottenHearts() end) or 0,
    brokenHearts = try(function() return p:GetBrokenHearts() end) or 0,
    heartLimit = try(function() return p:GetHeartLimit() end),
  }
  local mask = 0
  local slots = math.ceil(soul / 2) + bone
  for k = 0, math.min(slots, 30) - 1 do
    if try(function() return p:IsBoneHeart(k) end) then mask = mask | (1 << k) end
  end
  h.boneMask = mask
  return h
end

-- Holy Mantle-type shields. The game tracks them as one effect with a count;
-- which item gave which shield is worked out from what the player holds.
-- Returned in draw order: the last entry breaks first.
local function shieldsInfo(p)
  local n = try(function() return p:GetEffects():GetCollectibleEffectNum(K.HOLY_MANTLE) end) or 0
  if n <= 0 then return {} end
  local breakOrder = {}
  if p:HasTrinket(K.WOODEN_CROSS) then breakOrder[#breakOrder + 1] = "woodenCross" end
  if p:HasCollectible(K.BLANKET) and game:GetRoom():GetType() == K.ROOM_BOSS then
    breakOrder[#breakOrder + 1] = "blanket"
  end
  if p:HasCollectible(K.HOLY_MANTLE) then breakOrder[#breakOrder + 1] = "holyMantle" end
  while #breakOrder < n do breakOrder[#breakOrder + 1] = "holyCard" end
  -- shields that already broke are the first ones in break order
  local draw = {}
  for k = #breakOrder, #breakOrder - n + 1, -1 do draw[#draw + 1] = breakOrder[k] end
  return draw
end

local function statsInfo(p)
  return {
    speed = round(p.MoveSpeed),
    tears = round(30 / (num(p.MaxFireDelay, 10) + 1)),
    damage = round(p.Damage),
    range = round(num(try(function() return p.TearRange end), 260) / 40),
    shotSpeed = round(p.ShotSpeed),
    luck = round(p.Luck),
  }
end

local function activesInfo(p)
  local list = {}
  local config = Isaac.GetItemConfig()
  for slot = 0, 3 do
    local id = p:GetActiveItem(slot)
    if id and id > 0 then
      local cfg = config:GetCollectible(id)
      list[#list + 1] = {
        slot = slot,
        id = id,
        name = configName("item", id),
        charge = p:GetActiveCharge(slot),
        maxCharge = cfg and cfg.MaxCharges or 0,
        chargeType = cfg and try(function() return cfg.ChargeType end) or 0,
        battery = p:GetBatteryCharge(slot),
      }
    end
  end
  return list
end

local function trinketsInfo(p)
  local list = {}
  for slot = 0, 1 do
    local raw = p:GetTrinket(slot)
    if raw and raw > 0 then
      local id = raw & (K.GOLDEN_TRINKET - 1)
      list[#list + 1] = {
        slot = slot,
        id = id,
        golden = raw & K.GOLDEN_TRINKET ~= 0,
        name = configName("trinket", id),
      }
    end
  end
  return list
end

local function pocketsInfo(p)
  local list = {}
  local pool = game:GetItemPool()
  for slot = 0, 3 do
    local card = p:GetCard(slot)
    local pill = p:GetPill(slot)
    if card and card > 0 then
      list[#list + 1] = { slot = slot, kind = "card", id = card, name = configName("card", card) }
    elseif pill and pill > 0 then
      local color = pill & (K.HORSE_PILL - 1)
      local entry = {
        slot = slot,
        kind = "pill",
        color = color,
        horse = pill & K.HORSE_PILL ~= 0,
        identified = try(function() return pool:IsPillIdentified(color) end) == true,
      }
      if entry.identified then
        entry.effect = try(function() return pool:GetPillEffect(color, p) end)
        if entry.effect then entry.name = configName("pill", entry.effect) end
      end
      list[#list + 1] = entry
    end
  end
  return list
end

-- Every pill colour the run has identified, with what it does for this player
-- (PHD and False PHD change some effects). Only identified colours are sent, so
-- nothing is spoiled. Gold pills do something different every time, so they're
-- left out.
local function pillsInfo(p)
  local list = {}
  local pool = game:GetItemPool()
  for color = 1, PILL_GOLD - 1 do
    if try(function() return pool:IsPillIdentified(color) end) == true then
      local entry = { color = color, effect = try(function() return pool:GetPillEffect(color, p) end) }
      if entry.effect then entry.name = configName("pill", entry.effect) end
      list[#list + 1] = entry
    end
  end
  return list
end

local function itemsInfo(i)
  local list = {}
  for _, id in ipairs(playerBook(i).order) do
    list[#list + 1] = { id = id, name = configName("item", id) }
  end
  return list
end

local function formsInfo(p)
  local list = {}
  for f = 0, K.NUM_FORMS - 1 do
    list[#list + 1] = try(function() return p:GetPlayerFormCounter(f) end) or 0
  end
  return list
end

-- Tainted ???'s poops: the queue of upcoming poop spells (PoopSpellType
-- numbers, next first) and how many he can hold.
local PLAYER_BLUEBABY_B = enum(PlayerType, "PLAYER_BLUEBABY_B", 25)
-- The game's queue length (its own enum says 6); asking past it isn't safe.
local POOP_QUEUE = enum(PoopSpellType, "SPELL_QUEUE_SIZE", 6)
local POOP_TYPES = enum(PoopSpellType, "SPELL_COUNT", 12) -- types run 1 to this minus 1

local function poopsInfo(p)
  if try(function() return p:GetPlayerType() end) ~= PLAYER_BLUEBABY_B then return nil end
  local queue = {}
  for pos = 0, POOP_QUEUE - 1 do
    local spell = try(function() return p:GetPoopSpell(pos) end)
    if type(spell) ~= "number" or spell <= 0 or spell >= POOP_TYPES then break end
    queue[#queue + 1] = spell
  end
  -- how many he holds is pickups.poopMana
  return { max = try(function() return p:GetMaxPoopMana() end), queue = queue }
end

-- The Forgotten and The Soul are one player with two forms, each with its own
-- hearts. `health` is the form in control; this is the other one.
local PLAYER_THEFORGOTTEN = enum(PlayerType, "PLAYER_THEFORGOTTEN", 16)
local PLAYER_THESOUL = enum(PlayerType, "PLAYER_THESOUL", 17)

local function otherFormInfo(p)
  local t = try(function() return p:GetPlayerType() end)
  if t ~= PLAYER_THEFORGOTTEN and t ~= PLAYER_THESOUL then return nil end
  local sub = try(function() return p:GetSubPlayer() end)
  if not sub then return nil end
  return { type = try(function() return sub:GetPlayerType() end), health = healthInfo(sub) }
end

-- How many people are playing: one per controller. Esau, Tainted Forgotten's
-- Soul, Strawman's Keeper and Found Soul move with their player's controller,
-- so they don't count as another person.
local function humanCount()
  local seen, n = {}, 0
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    local c = p and try(function() return p.ControllerIndex end)
    if c ~= nil and not seen[c] then
      seen[c] = true
      n = n + 1
    end
  end
  return math.max(n, 1)
end

-- Players by entity, so a twin can point at the other's place in `players`.
local playerSlots = {}

local function refreshPlayerSlots()
  playerSlots = {}
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    if p then playerSlots[GetPtrHash(p)] = i end
  end
end

-- Pairs that play as one: Jacob and Esau, Tainted Forgotten and his Soul,
-- Tainted Lazarus and his other form (with Birthright). `main` is false for the
-- second of the pair (Esau...).
local function twinInfo(p)
  local other = try(function() return p:GetOtherTwin() end)
  if not other then return nil end
  local j = playerSlots[GetPtrHash(other)]
  if j == nil then return nil end
  local main = try(function() return p:GetMainTwin() end)
  return { index = j, main = main == nil or GetPtrHash(main) == GetPtrHash(p) }
end

local function playerInfo(i, p, curses)
  local healthHidden = curses & K.CURSE_UNKNOWN ~= 0
  local t = {
    index = i,
    type = p:GetPlayerType(),
    name = try(function() return p:GetName() end),
    lives = p:GetExtraLives(),
    livesMaybe = p:HasCollectible(K.GUPPYS_COLLAR) or p:HasTrinket(K.BROKEN_ANKH),
    pickups = {
      coins = p:GetNumCoins(),
      bombs = p:GetNumBombs(),
      keys = p:GetNumKeys(),
      goldenBomb = p:HasGoldenBomb(),
      goldenKey = p:HasGoldenKey(),
      gigaBombs = try(function() return p:GetNumGigaBombs() end) or 0,
      poopMana = try(function() return p:GetPoopMana() end),
      soulCharge = try(function() return p:GetSoulCharge() end),
      bloodCharge = try(function() return p:GetBloodCharge() end),
    },
    stats = statsInfo(p),
    controller = try(function() return p.ControllerIndex end),
    twin = try(function() return twinInfo(p) end),
    actives = activesInfo(p),
    trinkets = trinketsInfo(p),
    pockets = pocketsInfo(p),
    pills = pillsInfo(p),
    items = fast.cached("items" .. i, table.concat(playerBook(i).order, ","), function() return itemsInfo(i) end),
    forms = formsInfo(p),
    bag = try(function() return bagInfo(i, p) end),
  }
  -- co-op: a dead player's ghost, a co-op baby, and helpers such as Strawman's
  -- Keeper (a player with a parent)
  if try(function() return p:IsCoopGhost() end) == true then t.ghost = true end
  if try(function() return p.Variant end) == 1 then t.baby = true end
  if try(function() return p.Parent end) ~= nil then t.child = true end
  t.poops = try(function() return poopsInfo(p) end)
  if healthHidden then
    t.health = { hidden = true }
    t.shields = {}
  else
    t.health = healthInfo(p)
    t.shields = shieldsInfo(p)
    t.otherForm = try(function() return otherFormInfo(p) end)
  end
  return t
end

local function chancesInfo(level)
  local c = {}
  local devil = try(function() return game:GetRoom():GetDevilRoomChance() end)
  if devil then c.devil = round(math.max(0, math.min(1, devil)), 3) end
  local planet = try(function() return level:GetPlanetariumChance() end)
    or try(function() return game:GetPlanetariumChance() end)
  if planet then c.planetarium = round(math.max(0, math.min(1, planet)), 3) end
  return c
end

local function transitionInfo(p)
  local sprite = p and p:GetSprite()
  if not sprite then return nil end
  if sprite:IsPlaying("Trapdoor") then return "trapdoor" end
  if sprite:IsPlaying("LightTravel") then return "beam" end
  return nil
end

-- Build one part of the state. If it fails (say, a game update renamed
-- something), that part is left out and the rest still goes to the app.
local function section(name, fn)
  local ok, v = pcall(fn)
  if ok then return v end
  problem(name .. " failed: " .. shortErr(v))
  return nil
end

------------------------------------------------------------------------------
-- The game's words, from the app
------------------------------------------------------------------------------
-- The game hands mods codes like "#THE_SAD_ONION_NAME", not the words, and
-- the words live in a game file mods can't open. The app has them (they're
-- bundled with its art), so it sends them with POST /strings; the mod needs
-- them only for the banners it draws while the game's HUD is hidden, which is
-- only while the app is running. Kept in a global so a mod reload keeps them.

SchoolbagStrings = SchoolbagStrings or {}
local strings = SchoolbagStrings
local stringCount = 0
for _ in pairs(strings) do stringCount = stringCount + 1 end

local SMALL_WORDS = { ["of"] = true, ["the"] = true, ["a"] = true, ["an"] = true, ["and"] = true,
  ["to"] = true, ["in"] = true, ["on"] = true, ["for"] = true }

-- The readable text for one of the game's codes; codes the app hasn't sent
-- yet are turned into words ("#THE_SAD_ONION_NAME" -> "The Sad Onion") as a
-- stand-in. Names that aren't codes (modded items) come back as they are.
local function readable(code)
  if type(code) ~= "string" or code == "" then return nil end
  if code:sub(1, 1) ~= "#" then return code end
  local key = code:sub(2)
  if strings[key] then return strings[key] end
  local base = key:match("^(.*)_NAME$")
  if not base then return nil end
  local words, first = {}, true
  for w in base:lower():gmatch("[^_]+") do
    if first or not SMALL_WORDS[w] then w = w:sub(1, 1):upper() .. w:sub(2) end
    words[#words + 1] = w
    first = false
  end
  return table.concat(words, " ")
end

-- POST /strings: a JSON object of the game's string keys (without the "#")
-- and their text, in whatever language the app uses. Merged into what's there.
local function takeStrings(body)
  local ok, data = pcall(json.decode, body)
  if not ok or type(data) ~= "table" then return nil end
  local n = 0
  for k, v in pairs(data) do
    if type(k) == "string" and type(v) == "string" then
      if strings[k] == nil then stringCount = stringCount + 1 end
      strings[k] = v
      n = n + 1
    end
  end
  return n
end

local function buildState()
  local s = {
    protocol = PROTOCOL,
    mod = { name = "Schoolbag", version = VERSION },
    inRun = run.inRun,
    paused = try(function() return game:IsPaused() end) == true,
    pauseMenu = signals.pauseMenu(), -- the game's pause menu is open
    input = { mapAt = signals.mapAt }, -- Isaac.GetFrameCount() of the last Map button press
    perf = perf.last and { answers = perf.last.answers, avgMs = perf.last.avgMs, maxMs = perf.last.maxMs,
      frameMaxMs = perf.last.frameMaxMs, seconds = perf.last.seconds, transport = perf.transport } or nil,
    strings = stringCount, -- 0 means the app should send POST /strings
    hudHidden = SchoolbagHudHidden == true, -- the mod is hiding the game's HUD
    problems = problems, -- parts of the mod that failed (for /watch and testing)
    misses = misses,     -- game calls that didn't answer (often fine)
  }
  if not run.inRun then return s end

  local level = game:GetLevel()
  local curses = level:GetCurses()
  s.run = section("run", runInfo)
  s.floor = section("floor", function() return floorInfo(level, curses) end)
  s.map = fast.cached("map", fast.mapSignature(level, curses), function()
    return section("map", function() return mapInfo(level, curses) end)
  end)
  s.room = section("room", function() return roomInfo(level, curses) end)
  s.players = {}
  s.humans = try(humanCount) or 1
  try(refreshPlayerSlots)
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    local info = p and section("player", function() return playerInfo(i, p, curses) end)
    if info then s.players[#s.players + 1] = info end
  end
  s.chances = section("chances", function() return chancesInfo(level) end)
  s.transition = try(function() return transitionInfo(Isaac.GetPlayer(0)) end)
  s.death = { gameOver = run.ended == "loss", killer = run.lastDamage }
  return s
end

------------------------------------------------------------------------------
-- The local HTTP server
------------------------------------------------------------------------------

local server = nil
local serverProblem = nil -- "luadebug" when sockets aren't allowed, "bind" when the port is taken
-- udp: the UDP socket on the same port, if LuaSocket has UDP; clients: open HTTP
-- connections (see serve()); app: { ip, port, at, keep }, where the app last said
-- hello from over UDP
local net = { udp = nil, clients = {}, app = nil, lastPush = nil, lastPushAt = -1000, pushNow = false }

local function startServer(quiet)
  if server or serverProblem then return end
  -- Keep the socket across `luamod schoolbag` reloads during development.
  if SchoolbagServer then server = SchoolbagServer return end
  local ok, socket = pcall(require, "socket")
  if not ok or type(socket) ~= "table" then
    serverProblem = "luadebug"
    log("LuaSocket isn't available; add --luadebug to the launch options")
    return
  end
  local srv, err = socket.bind(HOST, PORT)
  if not srv then
    serverProblem = "bind"
    if not quiet then log("couldn't listen on " .. HOST .. ":" .. PORT .. " (" .. tostring(err) .. ")") end
    return
  end
  srv:settimeout(0)
  server = srv
  SchoolbagServer = srv
  log("listening on http://" .. HOST .. ":" .. PORT)
  -- the app's faster way in: the state pushed over UDP when it changes
  if SchoolbagUdp then
    net.udp = SchoolbagUdp
  elseif type(socket.udp) == "function" then
    local u = socket.udp()
    if u and u:setsockname(HOST, PORT) then
      u:settimeout(0)
      net.udp, SchoolbagUdp = u, u
    elseif u then
      pcall(function() u:close() end)
    end
  end
end

-- Only listen during a run. On the menus the game runs no mod code to answer,
-- so a request would wait forever; with nothing listening, the app and /watch
-- get a quick "no answer" instead.
local function stopServer()
  for _, c in ipairs(net.clients) do pcall(function() c.sock:close() end) end
  net.clients = {}
  if server then
    pcall(function() server:close() end)
    log("stopped listening until the next run")
  end
  if net.udp then pcall(function() net.udp:close() end) end
  server, SchoolbagServer, net.udp, SchoolbagUdp, net.app = nil, nil, nil, nil, nil
end

local MAX_BODY = 4 * 1024 * 1024 -- the app's strings are well under this
net.MAX_HEAD = 16 * 1024
net.MAX_CLIENTS = 8
net.IDLE_FRAMES = 5 * 60 -- render frames: an open connection with nothing going on is closed

-- Every socket is non-blocking (timeout 0): whatever part of a request has
-- arrived is kept, and the rest is read on later frames; an answer that doesn't
-- fit in the socket's buffer goes out over the next frames. The game never
-- waits on the app. Connections stay open between requests (HTTP keep-alive)
-- unless the client asks to close.

function net.pump(c)
  for _ = 1, 64 do -- up to 512 KB a frame (the app's words, once a run)
    local data, err, partial = c.sock:receive(8192)
    local got = data or partial
    if got and got ~= "" then
      c.buf = c.buf .. got
      c.idle = 0
    end
    if err then
      if err == "closed" then c.closed = true end
      return
    end
  end
end

-- The next complete request in c.buf, or nil while it's still arriving.
function net.nextRequest(c)
  local headEnd = c.buf:find("\r\n\r\n", 1, true)
  if not headEnd then
    if #c.buf > net.MAX_HEAD then c.closed = true end
    return nil
  end
  local head = c.buf:sub(1, headEnd - 1)
  local method, path, version = head:match("^(%u+)%s+(%S+)%s*(%S*)")
  if not method then c.closed = true return nil end
  local lower = head:lower()
  local length = tonumber(lower:match("\r\ncontent%-length:%s*(%d+)")) or 0
  local connection = lower:match("\r\nconnection:%s*([%w%-]+)")
  local close = connection == "close" or (version == "HTTP/1.0" and connection ~= "keep-alive")
  if length > MAX_BODY then
    c.buf = ""
    return { method = method, path = path, length = length, tooBig = true, close = true }
  end
  if #c.buf < headEnd + 3 + length then return nil end
  local body = length > 0 and c.buf:sub(headEnd + 4, headEnd + 3 + length) or nil
  c.buf = c.buf:sub(headEnd + 4 + length)
  return { method = method, path = path, body = body, length = length, close = close }
end

local function respond(c, status, contentType, body, close)
  c.out = table.concat({
    "HTTP/1.1 " .. status,
    "Content-Type: " .. contentType,
    "Content-Length: " .. #body,
    "Cache-Control: no-store",
    "Access-Control-Allow-Origin: *",
    "Connection: " .. (close and "close" or "keep-alive"),
    "", "",
  }, "\r\n") .. body
  c.sent = 0
  c.closeAfter = close
end

function net.flush(c)
  if not c.out then return end
  local last, err, partial = c.sock:send(c.out, c.sent + 1)
  local upto = last or partial
  if upto and upto > c.sent then
    c.sent = upto
    c.idle = 0
  end
  if c.sent >= #c.out then
    c.out = nil
    if c.closeAfter then c.closed = true end
  elseif err == "closed" then
    c.closed = true
  end
end

-- A test page for the Thor's browser, for trying the mod before the app exists:
-- it reads /state twice a second (so the game's HUD stays hidden, as with the
-- app) and shows the values worth checking, with the full state underneath.
local WATCH_PAGE = [==[<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Schoolbag watch</title>
<style>
body { background: #1d1410; color: #e8dccb; font: 15px/1.45 monospace; margin: 12px; }
h1 { font-size: 18px; margin: 0 0 6px; }
label { display: block; margin: 6px 0 10px; }
pre { white-space: pre-wrap; word-break: break-word; margin: 0; }
summary { margin-top: 12px; cursor: pointer; }
button { font: inherit; padding: 6px 14px; }
p { margin: 6px 0 10px; }
</style></head><body>
<h1>Schoolbag watch</h1>
<label><input type="checkbox" id="keep"> Keep the game's HUD (reads with ?hud=keep)</label>
<p><button id="copy" type="button">Copy state</button> <span id="copied"></span></p>
<pre id="sum">Waiting for the mod...</pre>
<details><summary id="missHead">Game calls that didn't answer</summary><pre id="miss"></pre></details>
<details><summary>Full state</summary><pre id="raw"></pre></details>
<textarea id="clip" readonly style="position: absolute; left: -9999px; top: 0"></textarea>
<script>
var el = function (id) { return document.getElementById(id); };
var BAG = ["", "red heart", "soul heart", "black heart", "eternal heart", "gold heart", "bone heart",
  "rotten heart", "penny", "nickel", "dime", "lucky penny", "key", "golden key", "charged key", "bomb",
  "golden bomb", "giga bomb", "micro battery", "lil battery", "mega battery", "card", "pill", "rune",
  "dice shard", "cracked key", "golden penny", "golden pill", "golden battery", "poop"];
var POOPS = ["", "normal", "corny", "burning", "stone", "stinky", "black", "holy", "liquid", "fart",
  "bomb", "diarrhea"];
function hearts(h) {
  if (!h) return "?";
  if (h.hidden) return "hidden (Curse of the Unknown)";
  return "red " + h.hearts + "/" + h.maxHearts + ", soul " + h.soulHearts + " (black mask " + h.blackMask +
    "), bone " + h.boneHearts + ", gold " + h.goldenHearts + ", eternal " + h.eternalHearts +
    ", rotten " + h.rottenHearts + ", broken " + h.brokenHearts;
}
function pct(v) { return v == null ? "?" : Math.round(v * 1000) / 10 + "%"; }
function lines(s) {
  var out = ["mod " + (s.mod ? s.mod.version : "?") + "   time " + new Date().toLocaleTimeString() + "   in run: " + s.inRun +
    "   HUD hidden: " + s.hudHidden + "   words: " + s.strings +
    (s.humans > 1 ? "   CO-OP: " + s.humans + " people (the HUD stays on)" : "")];
  var pf = s.perf;
  out.push("speed: " + (pf ? pf.answers + " answers in " + pf.seconds + " s via " + pf.transport + ", " + pf.avgMs +
    " ms each (worst " + pf.maxMs + " ms), worst frame " + pf.frameMaxMs + " ms" : "measuring (5 s)") +
    "   pause menu: " + s.pauseMenu + "   map button at frame " + (s.input ? s.input.mapAt : "?"));
  if (!s.inRun) return out;
  var f = s.floor;
  out.push(f ? "floor: stage " + f.stage + " type " + f.stageType + ", curses " + f.curses +
    (s.map && s.map.mirrored ? ", MIRROR WORLD" : "") + (s.transition ? ", leaving by " + s.transition : "")
    : "floor: not sent (see PROBLEMS)");
  var c = s.chances || {};
  out.push("chances: devil " + pct(c.devil) + ", planetarium " + pct(c.planetarium));
  (s.players || []).forEach(function (p) {
    var k = p.pickups || {};
    out.push("");
    out.push("player " + p.index + ": type " + p.type + " " + (p.name || "") + ", lives " + p.lives +
      (p.livesMaybe ? "?" : "") + ", controller " + p.controller +
      (p.twin ? (p.twin.main ? ", twin: player " : ", second twin of player ") + p.twin.index : "") +
      (p.ghost ? ", GHOST" : "") + (p.baby ? ", baby" : "") + (p.child ? ", helper (has a parent)" : ""));
    out.push("  hearts: " + hearts(p.health));
    if (p.shields && p.shields.length) out.push("  shields: " + p.shields.join(", "));
    out.push("  coins " + k.coins + ", bombs " + k.bombs + ", keys " + k.keys +
      (k.poopMana != null ? ", poops " + k.poopMana : "") +
      (k.soulCharge != null ? ", soul charge " + k.soulCharge : "") +
      (k.bloodCharge != null ? ", blood charge " + k.bloodCharge : ""));
    if (p.poops) out.push("  next poops (of " + p.poops.max + "): " +
      (p.poops.queue || []).map(function (t) { return POOPS[t] || t; }).join(", "));
    if (p.otherForm) out.push("  other form (type " + p.otherForm.type + "): " + hearts(p.otherForm.health));
    if (p.bag) out.push("  bag" + (p.bag.sure ? "" : " (NOT SURE)") + ": " +
      ((p.bag.items || []).map(function (c) { return BAG[c] || c; }).join(", ") || "empty") +
      " [" + (p.bag.items || []).length + "/8]");
    out.push("  actives: " + (p.actives || []).map(function (a) { return a.slot + ":" + a.id; }).join(" ") +
      "   items: " + (p.items || []).length + ", last " +
      (p.items || []).slice(-3).map(function (i) { return i.id; }).join(" "));
    out.push("  pills known: " + ((p.pills || []).map(function (x) {
      return x.color + "=" + String(x.name || x.effect).replace(/^#/, "").replace(/_NAME$/, "");
    }).join(", ") || "none yet"));
  });
  var r = s.room || {};
  out.push("");
  out.push("room " + r.index + " (type " + r.type + "): " + ((r.pedestals || []).map(function (x, i) {
    return (i + 1) + "=" + (x.blind ? "?" : x.id);
  }).join(" ") || "no items"));
  if (s.death && s.death.gameOver) out.push("game over, killed by " + JSON.stringify(s.death.killer));
  return out;
}
function notes(s) {
  var p = s.problems || [];
  return p.length ? ["", "PROBLEMS (" + p.length + "):"].concat(p.map(function (x) { return "  " + x; }))
    : ["", "problems: none"];
}
var last = null, lastAt = "", stale = false;
function report() {
  if (!last) return "Schoolbag /watch: no answer from the mod yet.";
  var m = last.misses || [];
  return ["Schoolbag /watch report, " + lastAt + (stale ? " (no answer since then)" : ""), ""].concat(lines(last), notes(last),
    ["", "Game calls that didn't answer (" + m.length + "):"], m.map(function (x) { return "  " + x; }),
    ["", "Full state:", JSON.stringify(last)]).join("\n");
}
function copied(msg) {
  el("copied").textContent = msg;
  setTimeout(function () { el("copied").textContent = ""; }, 2500);
}
el("copy").onclick = function () {
  var text = report();
  var fallback = function () {
    var t = el("clip");
    t.value = text;
    t.select();
    try { copied(document.execCommand("copy") ? "Copied" : "Couldn't copy: select Full state instead"); }
    catch (e) { copied("Couldn't copy: select Full state instead"); }
  };
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(function () { copied("Copied"); }, fallback);
  } else {
    fallback();
  }
};
// One read at a time, given up after 1.5 seconds, so a game that can't answer
// shows "No answer" instead of a page that stops moving.
var waiting = false;
function noAnswer() {
  stale = true;
  el("sum").textContent = "No answer at " + new Date().toLocaleTimeString() +
    ": the game is on a menu, or the mod isn't running." +
    (lastAt ? "\nLast answer at " + lastAt + "." : "");
}
function tick() {
  if (waiting) return;
  waiting = true;
  var done = false;
  var ctl = window.AbortController ? new AbortController() : null;
  var finish = function () {
    if (done) return false;
    done = true;
    waiting = false;
    clearTimeout(timer);
    return true;
  };
  var timer = setTimeout(function () {
    if (finish()) { if (ctl) ctl.abort(); noAnswer(); }
  }, 1500);
  var opts = { cache: "no-store" };
  if (ctl) opts.signal = ctl.signal;
  fetch("/state" + (el("keep").checked ? "?hud=keep" : ""), opts)
    .then(function (r) { return r.json(); })
    .then(function (s) {
      if (!finish()) return;
      last = s;
      stale = false;
      lastAt = new Date().toLocaleTimeString();
      el("sum").textContent = lines(s).concat(notes(s)).join("\n");
      var m = s.misses || [];
      el("missHead").textContent = "Game calls that didn't answer: " + m.length + " (often fine)";
      el("miss").textContent = m.join("\n");
      el("raw").textContent = JSON.stringify(s, null, 1);
    })
    .catch(function () { if (finish()) noAnswer(); });
}
setInterval(tick, 500);
tick();
</script></body></html>
]==]

local lastPoll = nil -- Isaac.GetFrameCount() of the app's last /state request

function net.stateJson()
  return fast.encode(buildState())
end

local function handle(c, req)
  local method, path, body, close = req.method, req.path, req.body, req.close
  if req.tooBig then
    respond(c, "413 Payload Too Large", "text/plain", "too big", true)
    return
  end
  local query = path:match("%?(.*)$") or ""
  path = path:gsub("%?.*$", "")
  if method == "POST" and path == "/strings" then
    local n = body and takeStrings(body)
    if n then
      respond(c, "200 OK", "text/plain", "ok " .. n, close)
    else
      respond(c, "400 Bad Request", "text/plain", "expected a JSON object of strings", close)
    end
  elseif method ~= "GET" then
    respond(c, "405 Method Not Allowed", "text/plain", "GET, or POST /strings", close)
  elseif path == "/state" then
    -- the app is watching: see "The game's own HUD". With ?hud=keep (the app's
    -- "Hide the game's HUD" setting off) it reads without hiding anything.
    if query:find("hud=keep", 1, true) then
      lastPoll = nil
    else
      lastPoll = Isaac.GetFrameCount()
    end
    local t0 = perf.clock()
    local ok, text = pcall(net.stateJson)
    if ok then
      respond(c, "200 OK", "application/json", text, close)
      perf.answer(perf.clock() - t0)
    else
      problem("state failed: " .. shortErr(text))
      respond(c, "500 Internal Server Error", "text/plain", tostring(text), close)
    end
  elseif path == "/health" then
    respond(c, "200 OK", "text/plain", "ok", close)
  elseif path == "/watch" then
    respond(c, "200 OK", "text/html; charset=utf-8", WATCH_PAGE, close)
  else
    respond(c, "404 Not Found", "text/plain", "not found", close)
  end
end

-- The UDP side: the app says "hello" about once a second ("hello keep" when it
-- shouldn't hide the game's HUD), and the mod sends it the state as one
-- datagram whenever the state changes, checked up to 10 times a second, and at
-- least once a second as a heartbeat. No hello for 3 seconds: it stops.
net.PUSH_EVERY = 6       -- render frames between checks (10 a second)
net.HEARTBEAT = 60       -- render frames: the state goes out at least once a second
net.APP_GONE = 3 * 60    -- render frames without a hello before the mod stops sending
net.UDP_MAX = 60000      -- bytes; a bigger state tells the app to read over HTTP

function net.serveUdp()
  local udp = net.udp
  if not udp then return end
  for _ = 1, 4 do
    local data, ip, port = udp:receivefrom()
    if not data then break end
    if data:sub(1, 5) == "hello" then
      local keep = data:find("keep", 6, true) ~= nil
      net.app = { ip = ip, port = port, at = Isaac.GetFrameCount(), keep = keep }
      net.pushNow = true
      -- the app is watching: see "The game's own HUD" (a hello counts like a read of /state)
      lastPoll = (not keep) and Isaac.GetFrameCount() or nil
    end
  end
  local app = net.app
  if not app then return end
  local f = Isaac.GetFrameCount()
  if f - app.at > net.APP_GONE then
    net.app, net.lastPush = nil, nil
    perf.transport = "http"
    return
  end
  if not net.pushNow and f % net.PUSH_EVERY ~= 0 then return end
  local t0 = perf.clock()
  local ok, text = pcall(net.stateJson)
  if not ok then
    problem("state failed: " .. shortErr(text))
    return
  end
  -- the run clock changes every update; it alone waits for the heartbeat
  local same = text:gsub('"timeFrames":%d+', "")
  if net.pushNow or same ~= net.lastPush or f - net.lastPushAt >= net.HEARTBEAT then
    if #text > net.UDP_MAX then text = '{"tooBig":true}' end
    udp:sendto(text, app.ip, app.port)
    net.lastPush, net.lastPushAt, net.pushNow = same, f, false
    perf.transport = "udp"
  end
  perf.answer(perf.clock() - t0)
end

local function serve()
  if not server then return end
  local t0 = perf.clock()
  local clients = net.clients
  for _ = 1, 4 do -- a few new connections per frame at most
    if #clients >= net.MAX_CLIENTS then break end
    local sock = server:accept()
    if not sock then break end
    sock:settimeout(0)
    pcall(function() sock:setoption("tcp-nodelay", true) end)
    clients[#clients + 1] = { sock = sock, buf = "", sent = 0, idle = 0 }
  end
  for i = #clients, 1, -1 do
    local c = clients[i]
    local ok, err = pcall(function()
      if not c.out then
        net.pump(c)
        local req = net.nextRequest(c)
        if req then handle(c, req) end
      end
      net.flush(c)
    end)
    if not ok then
      problem("request failed: " .. shortErr(err))
      c.closed = true
    end
    c.idle = c.idle + 1
    if c.closed or c.idle > net.IDLE_FRAMES then
      pcall(function() c.sock:close() end)
      table.remove(clients, i)
    end
  end
  local ok, err = pcall(net.serveUdp)
  if not ok then problem("udp failed: " .. shortErr(err)) end
  perf.frame(perf.clock() - t0)
end

------------------------------------------------------------------------------
-- The game's own HUD
------------------------------------------------------------------------------
-- While the app is showing the HUD on the other screen, the game's own HUD is
-- hidden on the main screen. It comes back by itself a few seconds after the
-- app stops asking, so closing the app or turning the
-- bottom screen off never leaves you without a HUD.

local HUD_GRACE = 3 * 60 -- render frames (60 a second) with no request before the HUD returns
-- kept in a global so a mod reload still knows it hid the HUD
SchoolbagHudHidden = SchoolbagHudHidden or false

local function appWatching()
  return lastPoll ~= nil and Isaac.GetFrameCount() - lastPoll < HUD_GRACE
end

local function setGameHud(visible)
  local hud = try(function() return game:GetHUD() end)
  if not hud then return end
  -- checked every frame: the game can switch its HUD back on by itself
  if try(function() return hud:IsVisible() end) ~= visible then
    try(function() hud:SetVisible(visible) end)
  end
  SchoolbagHudHidden = not visible
end

-- Tainted Cain's crafting preview is part of the game's HUD, and the game
-- doesn't tell mods what the bag will make, so neither the app nor the mod can
-- show it. On Tainted Cain runs only, the game's whole HUD comes back while the
-- bag is full (the only time the game shows a preview), or while the mod isn't
-- sure what's in it. Decided Nov 25 2026; to revisit (see the README's roadmap).
local PLAYER_CAIN_B = enum(PlayerType, "PLAYER_CAIN_B", 23)

local function craftingPreviewShowing()
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    if p and try(function() return p:GetPlayerType() end) == PLAYER_CAIN_B and holdsBag(p) then
      local b = run.bags[i + 1]
      if not b then
        if not run.bagsKnown then return true end
      elseif not b.sure or #b.items >= BAG_SIZE then
        return true
      end
    end
  end
  return false
end

-- In co-op (two or more people) the game's HUD stays on: the others only see
-- the main screen. Jacob & Esau is one person, so it still hides.
local function coopRun()
  return (try(humanCount) or 1) > 1
end

local function updateGameHud()
  if run.inRun and appWatching() and not try(craftingPreviewShowing) and not coopRun() then
    setGameHud(false)
  elseif SchoolbagHudHidden then
    setGameHud(true)
  end
end

------------------------------------------------------------------------------
-- The reminder banner
------------------------------------------------------------------------------
-- While the server can't start, Schoolbag says so on screen for the whole run,
-- drawn the way the game announces a picked-up item: "Schoolbag is offline" in
-- white on the ink streak, and the detail on the little paper strip under it.
-- The sprites and fonts are the game's own, loaded at runtime, so the mod
-- ships no art. It flies in like the game's banners, stays up, and steps
-- aside whenever the game shows a banner of its own in the same spot.

local REMIND_DELAY = 4 * 30 -- game updates to wait before (re)appearing, so the
                            -- game's own banner can finish first
local REMIND_Y = 48         -- banner centre, in screen points (where the game puts its own)
local STREAK_IN, STREAK_OUT = 9, 11 -- frames in the game's TextIn / TextOut animations
local PAPER_TOP = 12        -- the paper strip's top edge, below the banner centre
local PAPER_TEXT_W = 134    -- widest detail line that fits on the strip with some paper
                            -- around it; longer ones get a wider strip
local PAUSE_HIDE = 45       -- render frames (3/4 s): paused that long means the pause
                            -- menu or a floor change, so the banners hide. Room changes
                            -- pause the game more briefly, and the banners stay up.
local BIND_RETRY = 5 * 30   -- how often to try a busy port again
local REMIND_TITLE = "Schoolbag is offline"
local REMIND_DETAIL = { luadebug = "Needs --luadebug", bind = "Port " .. PORT .. " is busy" }
local INK = { 54 / 255, 47 / 255, 45 / 255 } -- the dark ink the game writes on paper with

-- The game writes its banners in Upheaval: the big size on the ink, the small
-- one on the paper. Each font comes with how far down to start writing so its
-- letters sit in the middle (of the ink, or of the paper strip).
local TITLE_FONTS = { { "font/upheaval.fnt", -10 }, { "font/teammeatfont20bold.fnt", -13 } }
local DETAIL_FONTS = { { "font/upheavalmini.fnt", 10 }, { "font/teammeatex/teammeatex10.fnt", 4 },
  { "font/pftempestasevencondensed.fnt", 4 } }

-- phase: nil (off), "wait" (until t0), "in", "stay", "out" (each started at t0)
local remind = { phase = nil, t0 = 0, resumeAt = nil, art = nil }

local function hiddenByPause() return signals.pausedFrames >= PAUSE_HIDE end

local function loadFont(choices)
  for _, c in ipairs(choices) do
    local font = try(function()
      local f = Font()
      f:Load(c[1])
      if f:IsLoaded() then return f end
    end)
    if font then return font, c[2] end
  end
  return nil, 0
end

local function loadSprite(path)
  return try(function()
    local s = Sprite()
    s:Load(path, true)
    if try(function() return s:IsLoaded() end) == false then return nil end
    return s
  end)
end

local function reminderArt()
  if remind.art then return remind.art end
  local art = { streak = loadSprite("gfx/ui/ui_streak.anm2") }
  art.title, art.titleDY = loadFont(TITLE_FONTS)
  art.detail, art.detailDY = loadFont(DETAIL_FONTS)
  remind.art = art
  return art
end

-- The ink streak (layer 0) and its paper strip (layer 1) at x, y, with the
-- title and the detail line on them. A detail line too long for the strip
-- gets a strip stretched to fit, so the words never run off the paper.
local function drawStreak(art, anim, frame, x, y, title, detail, words)
  local zero, pos = Vector(0, 0), Vector(x, y)
  local spr = art.streak
  spr:SetFrame(anim, frame)
  local w = detail and (try(function() return art.detail:GetStringWidth(detail) end) or 0) or 0
  if not detail then
    -- no detail: the ink alone, as the game does for a pill or a card
    if not pcall(spr.RenderLayer, spr, 0, pos, zero, zero) then spr:Render(pos, zero, zero) end
  elseif w > PAPER_TEXT_W and pcall(spr.RenderLayer, spr, 0, pos, zero, zero) then
    local ok = pcall(function()
      spr.Scale = Vector((w + 16) / (PAPER_TEXT_W + 16), 1)
      spr:RenderLayer(1, pos, zero, zero)
    end)
    pcall(function() spr.Scale = Vector(1, 1) end)
    if not ok then spr:Render(pos, zero, zero) end
  else
    spr:Render(pos, zero, zero) -- the ink and its paper strip together
  end
  if words then
    art.title:DrawString(title, x - 200, y + art.titleDY, KColor(1, 1, 1, 1), 400, true)
    if detail then
      local box = math.max(168, math.ceil(w) + 16)
      art.detail:DrawString(detail, x - box / 2, y + PAPER_TOP + art.detailDY, KColor(INK[1], INK[2], INK[3], 1), box, true)
    end
  end
end

local function now() return game:GetFrameCount() end

-- Show the banner after `delay` updates, unless it's already up or on its way.
local function armReminder(delay)
  if not serverProblem then return end
  if remind.phase == nil then
    remind.phase, remind.t0 = "wait", now() + (delay or REMIND_DELAY)
  end
end

-- The game is about to show its own banner in the same spot: get out of the
-- way and come back once it's gone.
local function reminderStepAside()
  if not remind.phase then return end
  local back = now() + REMIND_DELAY
  if remind.phase == "in" or remind.phase == "stay" then
    remind.phase, remind.t0 = "out", now()
  end
  if remind.phase == "wait" then
    remind.t0 = math.max(remind.t0, back)
  else
    remind.resumeAt = math.max(remind.resumeAt or 0, back)
  end
end

-- The problem went away (a busy port came free): fly out for good.
local function reminderDone()
  remind.resumeAt = nil
  if remind.phase == "in" or remind.phase == "stay" then
    remind.phase, remind.t0 = "out", now()
  elseif remind.phase == "wait" then
    remind.phase = nil
  end
end

local function advanceReminder()
  local t = now()
  if remind.phase == "wait" and t >= remind.t0 then remind.phase = "in" end
  if remind.phase == "in" and t - remind.t0 >= STREAK_IN then remind.phase = "stay" end
  if remind.phase == "out" and t - remind.t0 >= STREAK_OUT then
    if serverProblem and remind.resumeAt then
      remind.phase, remind.t0, remind.resumeAt = "wait", remind.resumeAt, nil
    else
      remind.phase, remind.resumeAt = nil, nil
    end
  end
  if remind.phase == "wait" and t >= remind.t0 then remind.phase = "in" end
end

-- Driven by the game's frame count, so it waits while the game is paused.
local function drawReminder()
  advanceReminder()
  local phase = remind.phase
  if phase == nil or phase == "wait" then return end
  if hiddenByPause() or run.ended then return end
  local k = now() - remind.t0
  local anim, frame, words
  if phase == "in" then
    anim, frame, words = "TextIn", k, false
  elseif phase == "stay" then
    anim, frame, words = "TextStay", 0, true
  else
    anim, frame, words = "TextOut", k, k < 5 -- the words leave with the banner
  end

  local art = reminderArt()
  local x, y = Isaac.GetScreenWidth() / 2, REMIND_Y
  local detail = REMIND_DETAIL[serverProblem] or ""
  if not (art.streak and art.title and art.detail) then
    -- the game's art or fonts couldn't be found: plain text instead
    if words then
      local line = REMIND_TITLE .. ": " .. detail
      local w = try(function() return Isaac.GetTextWidth(line) end) or 0
      Isaac.RenderText(line, x - w / 2, y - 6, 1, 1, 1, 1)
    end
    return
  end
  drawStreak(art, anim, frame, x, y, REMIND_TITLE, detail, words) -- ink and paper fly in together
end

-- A busy port may come free during the run: try again every few seconds.
local lastBindTry = 0
local function retryBind()
  if serverProblem ~= "bind" then return end
  local t = now()
  if t - lastBindTry < BIND_RETRY then return end
  lastBindTry = t
  serverProblem = nil
  startServer(true)
  if not serverProblem then reminderDone() end
end

------------------------------------------------------------------------------
-- The game's banners and boss bar, while its HUD is hidden
------------------------------------------------------------------------------
-- Hiding the game's HUD also hides the banners it shows for a new floor (with
-- its curse), a picked-up item or card, a pill or card used, and the boss
-- health bar.
-- While Schoolbag has the HUD hidden, the mod draws them itself with the
-- game's own art, in the same place and with the same timing. Each banner is
-- followed from the moment the game would have started it, so if the HUD is
-- hidden partway through, ours carries on where the game's stopped.

local BANNER_FRAMES = 70 -- the game's "Text" streak animation
local WORDS_FROM, WORDS_TO = 8, 61 -- frames where the streak is still and the words show
local banner = nil -- { title, detail, t0 }

local FLOOR_PAIRS = { -- stage types: 0 normal, 1 and 2 alternates, 4 and 5 the Repentance path
  { [0] = "BASEMENT", [1] = "CELLAR", [2] = "BURNING_BASEMENT", [4] = "DOWNPOUR", [5] = "DROSS" },
  { [0] = "CAVES", [1] = "CATACOMBS", [2] = "FLOODED_CAVES", [4] = "MINES", [5] = "ASHPIT" },
  { [0] = "DEPTHS", [1] = "NECROPOLIS", [2] = "DANK_DEPTHS", [4] = "MAUSOLEUM", [5] = "GEHENNA" },
  { [0] = "WOMB", [1] = "UTERO", [2] = "SCARRED_WOMB", [4] = "CORPSE", [5] = "CORPSE" },
}
local LATE_FLOORS = { [9] = { [0] = "BLUE_WOMB" }, [10] = { [0] = "SHEOL", [1] = "CATHEDRAL" },
  [11] = { [0] = "DARK_ROOM", [1] = "CHEST" }, [12] = { [0] = "THE_VOID" }, [13] = { [0] = "HOME" } }
local GREED_LATE = { [5] = "SHEOL", [6] = "THE_SHOP", [7] = "ULTRA_GREED" }
local CURSE_NAMES = { { 1, "CURSE_OF_DARKNESS" }, { 2, "CURSE_OF_THE_LABYRINTH" }, { 4, "CURSE_OF_THE_LOST" },
  { 8, "CURSE_OF_THE_UNKNOWN" }, { 16, "CURSE_OF_THE_CURSED" }, { 32, "CURSE_OF_THE_MAZE" },
  { 64, "CURSE_OF_THE_BLIND" }, { 128, "CURSE_OF_THE_GIANT" } }

-- "Basement I", "Caves XL", "Sheol": the floor name the game's banner shows
local function floorTitle(level)
  local stage, stageType = level:GetStage(), level:GetStageType()
  local key, numeral
  if try(function() return game:IsGreedMode() end) then
    key = (FLOOR_PAIRS[stage] or {})[stageType] or GREED_LATE[stage]
  elseif stage >= 1 and stage <= 8 then
    key = (FLOOR_PAIRS[math.ceil(stage / 2)] or {})[stageType]
    if level:GetCurses() & K.CURSE_LABYRINTH ~= 0 then
      numeral = "XL"
    else
      numeral = stage % 2 == 1 and "I" or "II"
    end
  else
    local t = LATE_FLOORS[stage]
    key = t and (t[stageType] or t[0])
  end
  local name = key and readable("#" .. key .. "_NAME")
  if not name then -- a floor we don't know: whatever the game calls it
    name = readable(try(function() return level:GetName() end))
    numeral = nil
  end
  if name and numeral then name = name .. " " .. numeral end
  return name
end

local function curseTitle(level)
  local curses = level:GetCurses()
  if curses == 0 then return nil end
  for _, c in ipairs(CURSE_NAMES) do
    if curses & c[1] ~= 0 then return readable("#" .. c[2] .. "_NAME") end
  end
  return readable(try(function() return level:GetCurseName() end))
end

local function showBanner(title, detail)
  if not title or title == "" then return end
  banner = { title = title, detail = detail ~= "" and detail or nil, t0 = now() }
end

-- An item held overhead is the moment the game shows its name.
local heldItem = {} -- per player: "type:id" of the item held overhead, if any
local function watchPickups()
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    local item = p and try(function() return p.QueuedItem.Item end)
    local id = item and try(function() return tostring(item.Type) .. ":" .. tostring(item.ID) end)
    if id and id ~= heldItem[i] then
      showBanner(readable(try(function() return item.Name end)), readable(try(function() return item.Description end)))
    end
    heldItem[i] = id
  end
end

-- Picking up a card, pill or trinket: the game shows its name in the banner
-- spot too. Counted per player, so switching between pockets (the drop
-- button) isn't mistaken for a pickup.
local pockets = {} -- per player: how many of each card, pill and trinket they hold
local function pocketCounts(p)
  local counts = {}
  local function add(key, id)
    if id and id > 0 then counts[key .. id] = (counts[key .. id] or 0) + 1 end
  end
  for slot = 0, 3 do
    add("card", try(function() return p:GetCard(slot) end))
    add("pill", try(function() return p:GetPill(slot) end))
  end
  for slot = 0, 1 do add("trinket", try(function() return p:GetTrinket(slot) end)) end
  return counts
end

local function watchPockets()
  for i = 0, game:GetNumPlayers() - 1 do
    local p = Isaac.GetPlayer(i)
    if p then
      local counts, before = pocketCounts(p), pockets[i]
      if before then -- the first look at a player is what they started with
        for key, n in pairs(counts) do
          if n > (before[key] or 0) then
            reminderStepAside()
            local card = tonumber(key:match("^card(%d+)$"))
            if card then
              local cfg = try(function() return Isaac.GetItemConfig():GetCard(card) end)
              if cfg then
                showBanner(readable(try(function() return cfg.Name end)), readable(try(function() return cfg.Description end)))
              end
            end
            break
          end
        end
      end
      pockets[i] = counts
    end
  end
end

local function drawBanner()
  local k = now() - banner.t0
  if k < 0 or k >= BANNER_FRAMES then banner = nil return end
  if hiddenByPause() then return end
  local art = reminderArt()
  local x, y = Isaac.GetScreenWidth() / 2, REMIND_Y
  local words = k >= WORDS_FROM and k <= WORDS_TO
  if not (art.streak and art.title and art.detail) then
    if words then -- the game's art or fonts couldn't be found: plain text instead
      local line = banner.title .. (banner.detail and (": " .. banner.detail) or "")
      local w = try(function() return Isaac.GetTextWidth(line) end) or 0
      Isaac.RenderText(line, x - w / 2, y - 6, 1, 1, 1, 1)
    end
    return
  end
  drawStreak(art, "Text", k, x, y, banner.title, banner.detail, words)
end

-- The boss bar: every boss in the room added together, as the game does.
local BOSSBAR_FILL = 110 -- columns in the bar's fill (frames 0-110 of "Fill")
local BOSSBAR_FROM_BOTTOM = 22
local boss = { hp = 0, max = 0 }
local bossBar = nil

local function scanBosses()
  local hp, max = 0, 0
  for _, e in ipairs(Isaac.GetRoomEntities()) do
    if e:IsBoss() and not e:IsDead() and e.MaxHitPoints > 0
        and not try(function() return e:HasEntityFlags(EntityFlag.FLAG_FRIENDLY) end) then
      hp = hp + math.max(0, e.HitPoints)
      max = max + e.MaxHitPoints
    end
  end
  boss.hp, boss.max = hp, max
end

local function drawBossBar()
  if boss.max <= 0 or boss.hp <= 0 or hiddenByPause() then return end
  if bossBar == nil then bossBar = loadSprite("gfx/ui/schoolbag_bossbar.anm2") or false end
  if not bossBar then return end
  local pos = Vector(Isaac.GetScreenWidth() / 2, Isaac.GetScreenHeight() - BOSSBAR_FROM_BOTTOM)
  local zero = Vector(0, 0)
  bossBar:SetFrame("Back", 0)
  bossBar:Render(pos, zero, zero)
  local k = math.floor(boss.hp / boss.max * BOSSBAR_FILL + 0.5)
  k = math.max(1, math.min(BOSSBAR_FILL, k))
  bossBar:SetFrame("Fill", k) -- no white flash on a hit (Sean, from the Thor)
  bossBar:Render(pos, zero, zero)
end

local function drawStandIns()
  if banner then
    local ok, err = pcall(drawBanner)
    if not ok then banner = nil logOnce("couldn't draw a banner: " .. shortErr(err)) end
  end
  local ok, err = pcall(drawBossBar)
  if not ok then boss.max = 0 logOnce("couldn't draw the boss bar: " .. shortErr(err)) end
end

------------------------------------------------------------------------------
-- Callbacks
------------------------------------------------------------------------------

local sawLifecycle = false -- false until we see a run start or end

mod:AddCallback(ModCallbacks.MC_POST_GAME_STARTED, function(_, isContinued)
  sawLifecycle = true
  resetRun()
  if isContinued then loadRun() end
  run.inRun = true
  pockets = {}
  if serverProblem == "bind" then serverProblem = nil end -- the port may be free now
  startServer()
  remind.phase, remind.resumeAt = nil, nil
  armReminder()
  scanAllPlayers()
end)

mod:AddCallback(ModCallbacks.MC_PRE_GAME_EXIT, function(_, shouldSave)
  sawLifecycle = true
  if shouldSave then saveRun() end
  run.inRun = false
  remind.phase, remind.resumeAt = nil, nil
  if SchoolbagHudHidden then setGameHud(true) end
  stopServer()
end)

mod:AddCallback(ModCallbacks.MC_POST_GAME_END, function(_, isGameOver)
  run.ended = isGameOver and "loss" or "win"
end)

mod:AddCallback(enum(ModCallbacks, "MC_POST_NEW_ROOM", 19), function() resetBagWatch() end)

-- If the game reports the bag touching a pickup, note it (doesn't change anything).
mod:AddCallback(enum(ModCallbacks, "MC_PRE_PICKUP_COLLISION", 38), function(_, pickup, collider)
  if collider and collider.Type == K.ENTITY_KNIFE and collider.Variant == BAG_KNIFE then
    pcall(markTouched, pickup)
  end
  return nil
end)

-- Save the pickup order every floor too, in case the game is closed without
-- going through the menu.
mod:AddCallback(ModCallbacks.MC_POST_NEW_LEVEL, function()
  if run.inRun then saveRun() end
  reminderStepAside() -- the game shows the floor name where our banner sits
  pcall(function()
    local level = game:GetLevel()
    showBanner(floorTitle(level), curseTitle(level))
  end)
end)

-- The game also uses that spot for item, pill and card names.
mod:AddCallback(ModCallbacks.MC_USE_PILL, function(_, effect)
  reminderStepAside()
  pcall(function() showBanner(readable(Isaac.GetItemConfig():GetPillEffect(effect).Name)) end)
end)
mod:AddCallback(ModCallbacks.MC_USE_CARD, function(_, card)
  reminderStepAside()
  pcall(function() showBanner(readable(Isaac.GetItemConfig():GetCard(card).Name)) end)
end)

mod:AddCallback(ModCallbacks.MC_POST_UPDATE, function()
  if game:GetFrameCount() % SCAN_EVERY == 0 then scanAllPlayers() end
  if remind.phase then
    for i = 0, game:GetNumPlayers() - 1 do
      local p = Isaac.GetPlayer(i)
      -- an item held overhead means its name banner is showing; a trapdoor or
      -- light beam means the floor-change screens are coming
      if p and (try(function() return not p:IsItemQueueEmpty() end) or try(function() return transitionInfo(p) end)) then
        reminderStepAside()
        break
      end
    end
  end
  if run.inRun then
    retryBind()
    local ok, err = pcall(watchPickups)
    if not ok then logOnce("couldn't watch pickups: " .. shortErr(err)) end
    ok, err = pcall(watchPockets)
    if not ok then logOnce("couldn't watch pockets: " .. shortErr(err)) end
    ok, err = pcall(watchBag)
    if not ok then logOnce("couldn't watch the Bag of Crafting: " .. shortErr(err)) end
    if run.bagsDirty then saveRun() end -- the game could be closed before the next floor
    ok, err = pcall(scanBosses)
    if not ok then boss.max = 0 end
  end
end)

mod:AddCallback(ModCallbacks.MC_ENTITY_TAKE_DMG, function(_, entity, amount, flags, source)
  if not source then return nil end
  local t, v, st = source.Type, source.Variant, 0
  local spawnerType = try(function() return source.SpawnerType end) or 0
  local fired = t == K.ENTITY_PROJECTILE or t == K.ENTITY_TEAR or t == K.ENTITY_LASER
    or t == K.ENTITY_KNIFE or t == K.ENTITY_EFFECT
  if fired and spawnerType > 0 then
    t = spawnerType
    v = try(function() return source.SpawnerVariant end) or 0
  else
    st = try(function() return source.Entity and source.Entity.SubType end) or 0
  end
  run.lastDamage = { type = t or 0, variant = v or 0, subtype = st or 0, flags = flags or 0 }
  return nil
end, K.ENTITY_PLAYER)

-- What the minimap shows can change without a new room: items, cards and pills
-- used, a room cleared. Any of these makes the next answer encode the map again.
for _, cb in ipairs({ enum(ModCallbacks, "MC_USE_ITEM", 3), enum(ModCallbacks, "MC_USE_CARD", 5),
  enum(ModCallbacks, "MC_USE_PILL", 10), enum(ModCallbacks, "MC_PRE_SPAWN_CLEAN_AWARD", 70),
  enum(ModCallbacks, "MC_POST_NEW_ROOM", 19), enum(ModCallbacks, "MC_POST_NEW_LEVEL", 18) }) do
  mod:AddCallback(cb, function() fast.mapChanged() end)
end

-- Served from the render callback, which keeps running while the game is
-- paused (the update callback doesn't).
mod:AddCallback(ModCallbacks.MC_POST_RENDER, function()
  if try(function() return game:IsPaused() end) then
    if signals.pausedFrames == 0 then signals.pauseBegan = Isaac.GetFrameCount() end
    signals.pausedFrames = signals.pausedFrames + 1
  else
    signals.pausedFrames = 0
  end
  if not run.inRun and not sawLifecycle then
    -- the mod was loaded mid-run (e.g. `luamod`), so pick the run up
    if game:GetFrameCount() > 0 then
      run.inRun = true
      startServer()
      armReminder()
      scanAllPlayers()
    end
  end
  if run.inRun then pcall(signals.watchMap) end
  serve()
  if run.inRun and not game:IsPaused() then pcall(watchBagCycle) end
  updateGameHud()
  if SchoolbagHudHidden and run.inRun then drawStandIns() end
  if remind.phase and run.inRun then
    local ok, err = pcall(drawReminder)
    if not ok then
      remind.phase = nil
      problem("couldn't draw the reminder: " .. shortErr(err))
    end
  end
end)

-- The server starts with each run (MC_POST_GAME_STARTED, or the render
-- callback above after a mid-run reload), not here: the game loads mods at
-- the title screen, where nothing would answer.
