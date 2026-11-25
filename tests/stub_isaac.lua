-- A tiny stand-in for the parts of Isaac's Lua API the Schoolbag mod uses, so
-- the mod can run under plain Lua 5.3 + LuaSocket for testing. Values are
-- made up; only the shapes matter. Missing methods raise errors, like the
-- real userdata does, so the mod's `try` guards get exercised.

----------------------------------------------------------------------------
-- json (the game ships its own json.lua; this one only needs to be close)
----------------------------------------------------------------------------
local json = {}
local function isArray(t)
  local n = 0
  for k in pairs(t) do
    if type(k) ~= "number" then return false end
    n = n + 1
  end
  for i = 1, n do if t[i] == nil then return false end end
  return true, n
end
local function esc(s)
  return '"' .. s:gsub('[%c"\\]', function(c)
    local m = { ['"'] = '\\"', ['\\'] = '\\\\', ['\n'] = '\\n', ['\r'] = '\\r', ['\t'] = '\\t' }
    return m[c] or string.format("\\u%04x", c:byte())
  end) .. '"'
end
function json.encode(v)
  local t = type(v)
  if t == "nil" then return "null"
  elseif t == "boolean" then return tostring(v)
  elseif t == "number" then
    assert(v == v and v ~= math.huge and v ~= -math.huge, "unexpected number " .. tostring(v))
    if math.type(v) == "integer" then return tostring(v) end
    return string.format("%.14g", v)
  elseif t == "string" then return esc(v)
  elseif t == "table" then
    local arr, n = isArray(v)
    local parts = {}
    if arr then
      for i = 1, n do parts[i] = json.encode(v[i]) end
      return "[" .. table.concat(parts, ",") .. "]"
    end
    for k, val in pairs(v) do
      assert(type(k) == "string", "mixed table key " .. tostring(k))
      parts[#parts + 1] = esc(k) .. ":" .. json.encode(val)
    end
    table.sort(parts)
    return "{" .. table.concat(parts, ",") .. "}"
  end
  error("can't encode " .. t)
end
function json.decode(s)
  local pos = 1
  local function ws() pos = s:find("%S", pos) or #s + 1 end
  local value
  local function str()
    local out, i = {}, pos + 1
    while true do
      local c = s:sub(i, i)
      if c == '"' then pos = i + 1 return table.concat(out) end
      if c == "\\" then i = i + 1 c = s:sub(i, i) end
      out[#out + 1] = c
      i = i + 1
    end
  end
  function value()
    ws()
    local c = s:sub(pos, pos)
    if c == "{" then
      local o = {} pos = pos + 1 ws()
      if s:sub(pos, pos) == "}" then pos = pos + 1 return o end
      while true do
        ws() local k = str() ws() pos = pos + 1
        o[k] = value() ws()
        local d = s:sub(pos, pos) pos = pos + 1
        if d == "}" then return o end
      end
    elseif c == "[" then
      local a = {} pos = pos + 1 ws()
      if s:sub(pos, pos) == "]" then pos = pos + 1 return a end
      while true do
        a[#a + 1] = value() ws()
        local d = s:sub(pos, pos) pos = pos + 1
        if d == "]" then return a end
      end
    elseif c == '"' then return str()
    elseif s:sub(pos, pos + 3) == "true" then pos = pos + 4 return true
    elseif s:sub(pos, pos + 4) == "false" then pos = pos + 5 return false
    elseif s:sub(pos, pos + 3) == "null" then pos = pos + 4 return nil
    else
      local num = s:match("^-?[%d%.eE+-]+", pos)
      pos = pos + #num
      return tonumber(num)
    end
  end
  return value()
end
package.preload["json"] = function() return json end

----------------------------------------------------------------------------
-- enums (only what the mod looks up; values from the game's enums.lua)
----------------------------------------------------------------------------
ModCallbacks = {
  MC_POST_UPDATE = 1, MC_POST_RENDER = 2, MC_ENTITY_TAKE_DMG = 11,
  MC_POST_GAME_STARTED = 15, MC_POST_GAME_END = 16, MC_PRE_GAME_EXIT = 17,
  MC_POST_NEW_LEVEL = 18, MC_USE_CARD = 5, MC_USE_PILL = 10,
  MC_POST_NEW_ROOM = 19, MC_PRE_PICKUP_COLLISION = 38,
}
CollectibleType = { COLLECTIBLE_HOLY_MANTLE = 313, COLLECTIBLE_GUPPYS_COLLAR = 212, COLLECTIBLE_BLANKET = 535,
  COLLECTIBLE_BAG_OF_CRAFTING = 710 }
ButtonAction = { ACTION_DROP = 11 }
-- KnifeVariant, Card and PillColor.PILL_GOLD left out: the mod has fallbacks
TrinketType = { TRINKET_BROKEN_ANKH = 28, TRINKET_WOODEN_CROSS = 121, TRINKET_GOLDEN_FLAG = 32768 }
PillColor = { PILL_GIANT_FLAG = 2048 }
LevelCurse = { CURSE_OF_THE_LOST = 4, CURSE_OF_THE_UNKNOWN = 8, CURSE_OF_BLIND = 64 }
EntityType = { ENTITY_PLAYER = 1, ENTITY_TEAR = 2, ENTITY_PICKUP = 5, ENTITY_LASER = 7,
  ENTITY_KNIFE = 8, ENTITY_PROJECTILE = 9, ENTITY_EFFECT = 1000 }
PickupVariant = { PICKUP_COLLECTIBLE = 100, PICKUP_HEART = 10, PICKUP_COIN = 20, PICKUP_KEY = 30,
  PICKUP_BOMB = 40, PICKUP_POOP = 42, PICKUP_PILL = 70, PICKUP_LIL_BATTERY = 90, PICKUP_TAROTCARD = 300 }
RoomType = { ROOM_BOSS = 5 }
StageType = { STAGETYPE_REPENTANCE = 4, STAGETYPE_REPENTANCE_B = 5 }
GameStateFlag = { STATE_BOSSRUSH_DONE = 3, STATE_BLUEWOMB_DONE = 4 }
-- PlayerForm intentionally left out: the mod must cope with a missing enum.

----------------------------------------------------------------------------
-- world state the test can change between requests
----------------------------------------------------------------------------
W = {
  frame = 0, time = 0, curses = 1, stage = 2, stageType = 0, paused = false,
  roomType = 4, greed = false,
  items = { [45] = 1, [105] = 1 },
  effectsMantle = 0,
  trinkets = { [0] = 0, [1] = 0 },
  playing = nil,
  pedestals = {},
  saved = nil,
  -- Bag of Crafting tests
  actives = { [0] = 105, [1] = 45 },
  pickups = {},   -- { Variant, SubType, Position = {X, Y}, hash }
  knives = {},    -- { Variant, Position = {X, Y} }
  coins = 157, keys = 1, bombs = 2, collectibles = 2,
  roomIndex = 45, seed = "ABCD EFGH",
}

local NAMES = { [1] = "#THE_SAD_ONION_NAME", [4] = "#CRICKETS_HEAD_NAME", [45] = "#YUM_HEART_NAME",
  [105] = "#THE_D6_NAME", [118] = "#BRIMSTONE_NAME", [212] = "#GUPPYS_COLLAR_NAME",
  [313] = "#HOLY_MANTLE_NAME", [535] = "#BLANKET_NAME" }
local CHARGES = { [45] = 4, [105] = 6 }

local itemConfig = {
  GetCollectibles = function() return { Size = 733 } end,
  GetCollectible = function(_, id)
    if NAMES[id] or (id > 0 and id < 733 and id % 7 ~= 0) then
      return { Name = NAMES[id] or ("#ITEM_" .. id), MaxCharges = CHARGES[id] or 0, ChargeType = 0 }
    end
    return nil
  end,
  GetTrinket = function(_, id) return { Name = "#TRINKET_" .. id } end,
  GetCard = function(_, id) return { Name = id == 1 and "#THE_FOOL_NAME" or "#CARD_" .. id } end,
  GetPillEffect = function(_, id) return { Name = "#PILL_" .. id } end,
}

local function newRooms()
  local list = {
    { GridIndex = 45, SafeGridIndex = 45, DisplayFlags = 5, VisitedCount = 1, Clear = true, Data = { Shape = 1, Type = 4 } },
    { GridIndex = 44, SafeGridIndex = 44, DisplayFlags = 1, VisitedCount = 1, Clear = true, Data = { Shape = 1, Type = 1 } },
    { GridIndex = 32, SafeGridIndex = 32, DisplayFlags = 5, VisitedCount = 0, Clear = false, Data = { Shape = 1, Type = 5 } },
    { GridIndex = 58, SafeGridIndex = 58, DisplayFlags = 0, VisitedCount = 0, Clear = false, Data = { Shape = 1, Type = 7 } },
    { GridIndex = -1, SafeGridIndex = -1, DisplayFlags = 0, VisitedCount = 0, Clear = false, Data = { Shape = 1, Type = 14 } },
  }
  return { Size = #list, Get = function(_, i) return list[i + 1] end }
end

local level = {
  GetCurses = function() return W.curses end,
  GetStage = function() return W.stage end,
  GetStageType = function() return W.stageType end,
  GetName = function() error("needs arguments in this version") end,
  GetRooms = function() return newRooms() end,
  GetCurrentRoomDesc = function() return { SafeGridIndex = 45 } end,
  GetCurrentRoomIndex = function() return W.roomIndex end,
  GreedModeWave = 6,
}

local room = {
  GetType = function() return W.roomType end,
  GetDevilRoomChance = function() return 0.3333333 end,
}

local pool = {
  IsPillIdentified = function(_, color) return color == 5 end,
  GetPillEffect = function(_, color) return color + 10 end,
}

local player = {
  MoveSpeed = 1.0, MaxFireDelay = 10, Damage = 5.25, TearRange = 260, ShotSpeed = 1.0, Luck = 1.0,
}
function player:GetPlayerType() return W.playerType or 0 end
function player:GetName() return "Isaac" end
function player:GetExtraLives() return 1 end
function player:HasCollectible(id) return (W.items[id] or 0) > 0 end
function player:GetCollectibleNum(id) return W.items[id] or 0 end
function player:HasTrinket(id) return W.trinkets[0] & 32767 == id or W.trinkets[1] & 32767 == id end
function player:GetTrinket(slot) return W.trinkets[slot] or 0 end
function player:GetNumCoins() return W.coins end
function player:GetNumBombs() return W.bombs end
function player:GetNumKeys() return W.keys end
function player:GetCollectibleCount() return W.collectibles end
player.ControllerIndex = 0
function player:HasGoldenBomb() return false end
function player:HasGoldenKey() return true end
function player:GetNumGigaBombs() return 0 end
-- GetPoopMana / GetSoulCharge / GetBloodCharge left out on purpose
function player:GetMaxHearts() return 12 end
function player:GetHearts() return 9 end
function player:GetSoulHearts() return 5 end
function player:GetBlackHearts() return 4 end
function player:GetBoneHearts() return 0 end
function player:GetGoldenHearts() return 0 end
function player:GetEternalHearts() return 0 end
function player:GetRottenHearts() return 0 end
function player:GetBrokenHearts() return 0 end
function player:GetHeartLimit() return 24 end
function player:IsBoneHeart() return false end
function player:GetEffects()
  return { GetCollectibleEffectNum = function(_, id) return id == 313 and W.effectsMantle or 0 end }
end
function player:GetActiveItem(slot) return W.actives[slot] or 0 end
function player:GetActiveCharge(slot) return slot == 0 and 4 or 2 end
function player:GetBatteryCharge() return 0 end
function player:GetCard(slot) return slot == 0 and 1 or 0 end
function player:GetPill(slot) return slot == 1 and (3 | 2048) or (slot == 2 and 5 or 0) end
function player:GetPlayerFormCounter(f) return f == 1 and 2 or 0 end
function player:IsItemQueueEmpty() return not W.queued end
function player:GetSprite()
  return { IsPlaying = function(_, anim) return anim == W.playing end }
end

local game = {
  Difficulty = 0, Challenge = 0,
}
setmetatable(game, { __index = function(_, k)
  if k == "TimeCounter" then return W.time end
end })
function game:GetLevel() return level end
function game:GetRoom() return room end
function game:GetNumPlayers() return 1 end
function game:IsPaused() return W.paused end
function game:GetSeeds() return { GetStartSeedString = function() return W.seed end } end
function game:GetVictoryLap() return 0 end
function game:GetStateFlag(f) return f == 3 end
function game:IsGreedMode() return W.greed end
function game:GetGreedWavesNum() return 11 end
function game:GetItemPool() return pool end
function game:GetFrameCount() return W.frame end
-- the game's own HUD on the main screen
local hud = {}
function hud:IsVisible() return W.hudVisible ~= false end
function hud:SetVisible(v) W.hudVisible = v W.hudSets = (W.hudSets or 0) + 1 end
function game:GetHUD() return hud end

function Game() return game end
function GetPtrHash(e) return e.hash or e end
Input = { IsActionTriggered = function(action) return action == ButtonAction.ACTION_DROP and W.dropPressed == true end }

Isaac = {
  GetPlayer = function(i) if i == 0 then return player end end,
  GetItemConfig = function() return itemConfig end,
  FindByType = function(t, v)
    local out = {}
    if t == EntityType.ENTITY_KNIFE then
      for _, k in ipairs(W.knives) do
        if v == nil or v == -1 or k.Variant == v then out[#out + 1] = k end
      end
      return out
    end
    if t == EntityType.ENTITY_PICKUP and v == -1 then
      for _, e in ipairs(W.pickups) do out[#out + 1] = e end
    end
    for _, ped in ipairs(W.pedestals) do
      local e = { Variant = 100, SubType = ped.id, Position = { X = ped.x, Y = ped.y } }
      local pk = { Price = ped.price or -2, OptionsPickupIndex = ped.group or 0 } -- the game has prices; the mod must not send them
      function e:ToPickup() return pk end
      out[#out + 1] = e
    end
    return out
  end,
  GetFrameCount = function() return W.render or 0 end, -- render frames, 60 a second, run while paused
  DebugString = function(s) io.stderr:write(s, "\n") end,
  RenderText = function() end,
}

----------------------------------------------------------------------------
-- RegisterMod
----------------------------------------------------------------------------
CALLBACKS = {}
function RegisterMod(name)
  local m = { Name = name }
  function m:AddCallback(id, fn, filter) CALLBACKS[id] = CALLBACKS[id] or {} table.insert(CALLBACKS[id], { fn = fn, filter = filter }) end
  function m:SaveData(s) W.saved = s end
  function m:LoadData() return W.saved end
  function m:HasData() return W.saved ~= nil end
  MOD = m
  return m
end

function fire(id, ...)
  local r
  for _, cb in ipairs(CALLBACKS[id] or {}) do r = cb.fn(MOD, ...) end
  return r
end

PLAYER = player
