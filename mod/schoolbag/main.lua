-- Schoolbag: sends the game's state to the Schoolbag app on the same device.
--
-- It answers HTTP requests on http://127.0.0.1:8089 (see docs/protocol.md).
-- It only reads game state; it never changes the game or hides its HUD.
-- The server needs the game's --luadebug launch option, because that is what
-- lets a mod load LuaSocket. Without it, the mod shows a short reminder at the
-- start of each run, drawn like the game's own item banner, that stays up for
-- the whole run, and does nothing else.
--
-- While the app is reading the run, the mod hides the game's own HUD on the
-- main screen; it comes back a few seconds after the app stops.

local mod = RegisterMod("Schoolbag", 1)

local VERSION = "0.1.0"
local PROTOCOL = 1
local HOST, PORT = "127.0.0.1", 8089
local SCAN_EVERY = 10 -- game updates between inventory scans (30 updates = 1 s)

local json = require("json")
local game = Game()

------------------------------------------------------------------------------
-- Helpers
------------------------------------------------------------------------------

-- Run fn and return its result, or nil if it errors (for API calls that may
-- not exist in every game version).
local function try(fn)
  local ok, v = pcall(fn)
  if ok then return v end
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

local logged = {}
local function logOnce(msg) -- for things that could otherwise repeat every frame
  if not logged[msg] then logged[msg] = true log(msg) end
end

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
  local data = { v = 1, order = {} }
  for i, book in ipairs(run.players) do data.order[i] = book.order end
  local ok, text = pcall(json.encode, data)
  if ok then mod:SaveData(text) end
end

local function loadRun()
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
    actives = activesInfo(p),
    trinkets = trinketsInfo(p),
    pockets = pocketsInfo(p),
    items = itemsInfo(i),
    forms = formsInfo(p),
  }
  if healthHidden then
    t.health = { hidden = true }
    t.shields = {}
  else
    t.health = healthInfo(p)
    t.shields = shieldsInfo(p)
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
local reported = {}
local function section(name, fn)
  local ok, v = pcall(fn)
  if ok then return v end
  if not reported[name] then
    reported[name] = true
    log(name .. " failed: " .. tostring(v))
  end
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
    strings = stringCount, -- 0 means the app should send POST /strings
  }
  if not run.inRun then return s end

  local level = game:GetLevel()
  local curses = level:GetCurses()
  s.run = section("run", runInfo)
  s.floor = section("floor", function() return floorInfo(level, curses) end)
  s.map = section("map", function() return mapInfo(level, curses) end)
  s.room = section("room", function() return roomInfo(level, curses) end)
  s.players = {}
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
end

local MAX_BODY = 4 * 1024 * 1024 -- the app's strings are well under this

local function readRequest(client)
  client:settimeout(0.02)
  local line = client:receive("*l")
  if not line then return nil end
  local method, path = line:match("^(%u+)%s+(%S+)")
  local length = 0
  for _ = 1, 50 do -- the headers; only the body's length matters
    local h = client:receive("*l")
    if not h or h == "" then break end
    local n = h:lower():match("^content%-length:%s*(%d+)")
    if n then length = tonumber(n) end
  end
  local body
  if length > 0 and length <= MAX_BODY then
    client:settimeout(1)
    body = client:receive(length)
  end
  return method, path, body, length
end

local function respond(client, status, contentType, body)
  local head = table.concat({
    "HTTP/1.1 " .. status,
    "Content-Type: " .. contentType,
    "Content-Length: " .. #body,
    "Cache-Control: no-store",
    "Access-Control-Allow-Origin: *",
    "Connection: close",
    "", "",
  }, "\r\n")
  client:settimeout(0.25)
  client:send(head .. body)
end

local lastPoll = nil -- Isaac.GetFrameCount() of the app's last /state request

local function handle(client)
  local method, path, body, length = readRequest(client)
  if not method then return end
  path = path:gsub("%?.*$", "")
  if method == "POST" and path == "/strings" then
    if length > MAX_BODY then
      respond(client, "413 Payload Too Large", "text/plain", "too big")
      return
    end
    local n = body and takeStrings(body)
    if n then
      respond(client, "200 OK", "text/plain", "ok " .. n)
    else
      respond(client, "400 Bad Request", "text/plain", "expected a JSON object of strings")
    end
  elseif method ~= "GET" then
    respond(client, "405 Method Not Allowed", "text/plain", "GET, or POST /strings")
  elseif path == "/state" then
    lastPoll = Isaac.GetFrameCount() -- the app is watching: see "The game's own HUD"
    local ok, body = pcall(function() return json.encode(buildState()) end)
    if ok then
      respond(client, "200 OK", "application/json", body)
    else
      log("state failed: " .. tostring(body))
      respond(client, "500 Internal Server Error", "text/plain", tostring(body))
    end
  elseif path == "/health" then
    respond(client, "200 OK", "text/plain", "ok")
  else
    respond(client, "404 Not Found", "text/plain", "not found")
  end
end

local function serve()
  if not server then return end
  for _ = 1, 4 do -- a few waiting requests per frame at most
    local client = server:accept()
    if not client then return end
    local ok, err = pcall(handle, client)
    if not ok then log("request failed: " .. tostring(err)) end
    pcall(function() client:close() end)
  end
end

------------------------------------------------------------------------------
-- The game's own HUD
------------------------------------------------------------------------------
-- While the app is showing the HUD on the other screen, the game's own HUD is
-- hidden on the main screen, like isaac-hud does. It comes back by itself a
-- few seconds after the app stops asking, so closing the app or turning the
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

local function updateGameHud()
  if run.inRun and appWatching() then
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
local DETAIL_DY = 4         -- detail text below the strip's top edge (centred on the strip)
local BIND_RETRY = 5 * 30   -- how often to try a busy port again
local REMIND_TITLE = "Schoolbag is offline"
local REMIND_DETAIL = { luadebug = "Needs --luadebug", bind = "Port " .. PORT .. " is busy" }
local INK = { 54 / 255, 47 / 255, 45 / 255 } -- the dark ink the game writes on paper with

-- phase: nil (off), "wait" (until t0), "in", "stay", "out" (each started at t0)
local remind = { phase = nil, t0 = 0, resumeAt = nil, art = nil }

local function loadFont(paths)
  for _, path in ipairs(paths) do
    local font = try(function()
      local f = Font()
      f:Load(path)
      if f:IsLoaded() then return f end
    end)
    if font then return font end
  end
  return nil
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
  remind.art = {
    streak = loadSprite("gfx/ui/ui_streak.anm2"),
    title = loadFont({ "font/teammeatfont20bold.fnt", "font/upheaval.fnt" }),
    -- the strip is small, so the detail uses the small Team Meat size to keep
    -- some paper around the words
    detail = loadFont({ "font/teammeatex/teammeatex10.fnt", "font/teammeatfont10.fnt",
      "font/pftempestasevencondensed.fnt" }),
  }
  return remind.art
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
  if game:IsPaused() or run.ended then return end
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
  local zero = Vector(0, 0)
  art.streak:SetFrame(anim, frame)
  art.streak:Render(Vector(x, y), zero, zero) -- the ink and its paper strip fly in together
  if words then
    art.title:DrawString(REMIND_TITLE, x - 200, y - 13, KColor(1, 1, 1, 1), 400, true)
    art.detail:DrawString(detail, x - 84, y + PAPER_TOP + DETAIL_DY, KColor(INK[1], INK[2], INK[3], 1), 168, true)
  end
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
-- its curse), a picked-up item, a pill or a card, and the boss health bar.
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

local function drawBanner()
  local k = now() - banner.t0
  if k < 0 or k >= BANNER_FRAMES then banner = nil return end
  if game:IsPaused() then return end
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
  local zero = Vector(0, 0)
  art.streak:SetFrame("Text", k)
  if banner.detail then
    art.streak:Render(Vector(x, y), zero, zero) -- the ink and its paper strip
  elseif not pcall(art.streak.RenderLayer, art.streak, 0, Vector(x, y), zero, zero) then
    art.streak:Render(Vector(x, y), zero, zero)
  end
  if words then
    art.title:DrawString(banner.title, x - 200, y - 13, KColor(1, 1, 1, 1), 400, true)
    if banner.detail then
      art.detail:DrawString(banner.detail, x - 84, y + PAPER_TOP + DETAIL_DY, KColor(INK[1], INK[2], INK[3], 1), 168, true)
    end
  end
end

-- The boss bar: every boss in the room added together, as the game does.
local BOSSBAR_FILL = 110 -- columns in the bar's fill (frames 0-110 of "Fill")
local BOSSBAR_FROM_BOTTOM = 22
local boss = { hp = 0, max = 0, flashUntil = 0 }
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
  if boss.max > 0 and hp < boss.hp then boss.flashUntil = now() + 2 end -- a hit flashes the bar
  boss.hp, boss.max = hp, max
end

local function drawBossBar()
  if boss.max <= 0 or boss.hp <= 0 or game:IsPaused() then return end
  if bossBar == nil then bossBar = loadSprite("gfx/ui/schoolbag_bossbar.anm2") or false end
  if not bossBar then return end
  local pos = Vector(Isaac.GetScreenWidth() / 2, Isaac.GetScreenHeight() - BOSSBAR_FROM_BOTTOM)
  local zero = Vector(0, 0)
  bossBar:SetFrame("Back", 0)
  bossBar:Render(pos, zero, zero)
  local k = math.floor(boss.hp / boss.max * BOSSBAR_FILL + 0.5)
  k = math.max(1, math.min(BOSSBAR_FILL, k))
  bossBar:SetFrame(now() < boss.flashUntil and "FillHit" or "Fill", k)
  bossBar:Render(pos, zero, zero)
end

local function drawStandIns()
  if banner then
    local ok, err = pcall(drawBanner)
    if not ok then banner = nil logOnce("couldn't draw a banner: " .. tostring(err)) end
  end
  local ok, err = pcall(drawBossBar)
  if not ok then boss.max = 0 logOnce("couldn't draw the boss bar: " .. tostring(err)) end
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
end)

mod:AddCallback(ModCallbacks.MC_POST_GAME_END, function(_, isGameOver)
  run.ended = isGameOver and "loss" or "win"
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
    if not ok then logOnce("couldn't watch pickups: " .. tostring(err)) end
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

-- Served from the render callback, which keeps running while the game is
-- paused (the update callback doesn't).
mod:AddCallback(ModCallbacks.MC_POST_RENDER, function()
  if not run.inRun and not sawLifecycle then
    -- the mod was loaded mid-run (e.g. `luamod`), so pick the run up
    if game:GetFrameCount() > 0 then
      run.inRun = true
      startServer()
      armReminder()
      scanAllPlayers()
    end
  end
  serve()
  updateGameHud()
  if SchoolbagHudHidden and run.inRun then drawStandIns() end
  if remind.phase and run.inRun then
    local ok, err = pcall(drawReminder)
    if not ok then
      remind.phase = nil
      log("couldn't draw the reminder: " .. tostring(err))
    end
  end
end)

startServer()
