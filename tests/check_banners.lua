-- Checks the banners and boss bar the mod draws while the game's HUD is
-- hidden: floor names and curses, picked-up items, pills, cards, the boss
-- bar, and the game's words arriving from the app over POST /strings.
-- Usage (from the repo root): lua5.3 tests/check_banners.lua   (needs LuaSocket)
local socket = require("socket")
local failures = 0
local function check(cond, msg)
  print((cond and "  ok   " or "  FAIL ") .. msg .. (cond and "" or ("   [" .. tostring(DIAG) .. "]")))
  if not cond then failures = failures + 1 end
end

local calls = {}
local function record(kind, a, b, c) calls[#calls + 1] = { kind = kind, a = a, b = b, c = c } end

SchoolbagServer, SchoolbagHudHidden, SchoolbagStrings = nil, nil, nil
dofile("tests/stub_isaac.lua")
local json = require("json") -- the stub's stand-in for the game's json
W.render, W.curses = 0, 0
-- the game's drawing API, recorded
Vector = function(x, y) return { X = x, Y = y } end
KColor = function(r, g, b, a) return { r, g, b, a } end
Isaac.GetScreenWidth = function() return 480 end
Isaac.GetScreenHeight = function() return 270 end
Isaac.GetTextWidth = function(s) return #s * 6 end
Sprite = function()
  local spr = {}
  function spr:Load(path) self.path = path end
  function spr:IsLoaded() return true end
  function spr:SetFrame(anim, f) self.anim, self.frame = anim, f end
  function spr:Render(pos) record("sprite", self.path, self.anim .. ":" .. self.frame, pos) end
  function spr:RenderLayer(layer, pos)
    record("layer", self.path, self.anim .. ":" .. self.frame, layer)
    if self.Scale and self.Scale.X ~= 1 then record("scale", self.path, self.Scale.X, layer) end
  end
  return spr
end
Font = function()
  local f = {}
  function f:Load(path) self.path = path end
  function f:IsLoaded() return true end
  function f:DrawString(s, x, y, col, box) record("font", s, self.path, { x = x, y = y, box = box }) end
  function f:GetStringWidth(s) return #s * 7 end -- about Upheaval mini's
  return f
end
-- items held overhead, pills, cards and bosses
local P = Isaac.GetPlayer(0)
local itemConfig = Isaac.GetItemConfig()
function itemConfig:GetPillEffect(e) return { Name = e == 2 and "#BAD_TRIP_NAME" or "#PILL_NAME" } end
function itemConfig:GetCard(c) return { Name = c == 1 and "#THE_FOOL_NAME" or "#CARD_NAME" } end
EntityFlag = { FLAG_FRIENDLY = 1 << 29 }
W.bosses = {}
Isaac.GetRoomEntities = function()
  local out = {}
  for _, b in ipairs(W.bosses) do
    out[#out + 1] = {
      HitPoints = b.hp, MaxHitPoints = b.max,
      IsBoss = function() return true end,
      IsDead = function() return b.dead == true end,
      HasEntityFlags = function(_, f) return b.friendly == true and f == EntityFlag.FLAG_FRIENDLY end,
    }
  end
  return out
end
local level = Game():GetLevel()
function level:GetName() return "Somewhere" end

dofile("mod/schoolbag/main.lua")

local function frames(n)
  for _ = 1, n do
    calls = {}
    W.render = W.render + 1
    if not W.paused then
      W.frame = W.frame + 1
      fire(ModCallbacks.MC_POST_UPDATE)
    end
    fire(ModCallbacks.MC_POST_RENDER)
  end
  return calls
end
local function request(method, path, body)
  local c = assert(socket.connect("127.0.0.1", 8089))
  local head = method .. " " .. path .. " HTTP/1.1\r\nHost: x\r\nConnection: close\r\n"
  if body then head = head .. "Content-Type: application/json\r\nContent-Length: " .. #body .. "\r\n" end
  c:send(head .. "\r\n" .. (body or ""))
  local saved = calls
  frames(1)
  c:settimeout(2)
  local reply = c:receive("*a") or ""
  c:close()
  calls = saved
  return reply
end
local function state()
  local reply = request("GET", "/state")
  return json.decode(reply:match("\r\n\r\n(.*)$"))
end
-- n frames with the app reading twice a second, like it would (keeps the HUD hidden)
local function run(n)
  local c
  for i = 1, n do
    if W.render % 30 == 0 then request("GET", "/state") end
    c = frames(1)
  end
  return c
end
local function find(c, kind, a)
  DIAG = nil
  for _, x in ipairs(c) do if x.kind == kind and (a == nil or x.a == a) then return x end end
end
local function words(c)
  local out = {}
  for _, x in ipairs(c) do if x.kind == "font" then out[#out + 1] = x.a end end
  return table.concat(out, " / ")
end
local function streak(c)
  local s = find(c, "sprite", "gfx/ui/ui_streak.anm2") or find(c, "layer", "gfx/ui/ui_streak.anm2")
  local out = s and (s.kind .. " " .. s.b)
  local w = {}
  for _, x in ipairs(c) do if x.kind == "font" then w[#w + 1] = x.a end end
  DIAG = tostring(out) .. " | " .. table.concat(w, " / ")
  return out
end
local function newFloor(stage, stageType, curses)
  W.stage, W.stageType, W.curses = stage, stageType or 0, curses or 0
  fire(ModCallbacks.MC_POST_NEW_LEVEL)
end

fire(ModCallbacks.MC_POST_GAME_STARTED, false)
state()
check(SchoolbagHudHidden, "the app is reading, so the game's HUD is hidden")

print("floor banners")
newFloor(1)
local c = run(9)
check(words(c) == "Basement I" and streak(c) == "layer Text:9",
  "Basement I on the ink alone (no curse, no paper), in the game's words where the codes allow")
c = run(60)
check(streak(c) == nil, "gone after the game's 70 frames")
newFloor(2, 0, 64)
c = run(20)
check(words(c) == "Basement II / Curse of the Blind" and (streak(c) or ""):match("^sprite Text:"),
  "Basement II with the curse on the paper strip")
newFloor(3, 0, 2)
c = run(20)
check(words(c):match("^Caves XL / Curse of the Labyrinth"), "Curse of the Labyrinth: Caves XL")
local wide = find(c, "scale")
local line = find(c, "font", "Curse of the Labyrinth")
check(find(c, "layer") and wide and wide.c == 1 and math.abs(wide.b - (154 + 16) / 150) < 0.001
  and line and line.c.box == 170,
  "a line too long for the paper strip gets a wider strip, and stays on it")
newFloor(2, 0, 64)
c = run(20)
check(not find(c, "scale") and (streak(c) or ""):match("^sprite Text:"), "a line that fits: the strip as it is")
newFloor(1, 4)
c = run(20)
check(words(c) == "Downpour I", "the Repentance path: Downpour I")
newFloor(10, 1)
c = run(20)
check(words(c) == "Cathedral", "late floors have no number: Cathedral")
W.greed = true
newFloor(6)
c = run(20)
check(words(c) == "The Shop", "Greed mode: The Shop")
W.greed = nil
newFloor(99)
c = run(20)
check(words(c) == "Somewhere", "a floor it doesn't know: the game's own name for it")

print("the game's words from the app")
check(state().strings == 0, "/state says no words yet")
check(request("POST", "/strings", "[1, 2"):match("^HTTP/1.1 400"), "bad JSON is refused")
local reply = request("POST", "/strings", json.encode({
  BASEMENT_NAME = "Sous-sol", CURSE_OF_THE_BLIND_NAME = "Curse of the Blind!",
  THE_SAD_ONION_NAME = "The Sad Onion", THE_SAD_ONION_DESCRIPTION = "Tears up", MOMS_KNIFE_NAME = "Mom's Knife",
}))
check(reply:match("^HTTP/1.1 200") and reply:match("ok 5"), "POST /strings takes them")
check(state().strings == 5, "/state counts them")
newFloor(2, 0, 64)
c = run(20)
check(words(c) == "Sous-sol II / Curse of the Blind!", "banners use the app's words (any language)")

print("items, pills and cards")
P.QueuedItem = { Item = { Type = 1, ID = 1, Name = "#THE_SAD_ONION_NAME", Description = "#THE_SAD_ONION_DESCRIPTION" } }
c = run(10)
check(words(c) == "The Sad Onion / Tears up" and streak(c) == "sprite Text:9", "item held overhead: name and its line")
local before = tonumber(streak(c):match("%d+$"))
c = run(30)
check(tonumber((streak(c) or ""):match("%d+$") or -1) > before + 25, "holding it doesn't restart the banner")
P.QueuedItem = { Item = nil }
run(5)
P.QueuedItem = { Item = { Type = 1, ID = 114, Name = "#MOMS_KNIFE_NAME", Description = "#MOMS_KNIFE_DESCRIPTION" } }
c = run(10)
check(words(c) == "Mom's Knife" and tonumber((streak(c) or "layer Text:99"):match("^layer Text:(%d+)$")) <= 11,
  "the next item restarts it; no line known, so no paper")
P.QueuedItem = { Item = { Type = 2, ID = 5, Name = "Modded Trinket", Description = "Does a thing" } }
c = run(10)
check(words(c) == "Modded Trinket / Does a thing", "modded names are shown as they are")
P.QueuedItem = nil
fire(ModCallbacks.MC_USE_PILL, 2, P, 0)
c = run(10)
check(words(c) == "Bad Trip", "pill used: its name")
fire(ModCallbacks.MC_USE_CARD, 1, P, 0)
c = run(10)
check(words(c) == "The Fool", "card used: its name")
local was = tonumber(streak(c):match("%d+$"))
W.cards = { [0] = 3 } -- picked up a card
c = run(10)
check(words(c) == "Card" and streak(c), "card picked up: its name (the game shows one then too)")
W.cards = { [0] = 3, [1] = 1 }
run(80)
W.cards = { [0] = 1, [1] = 3 } -- the drop button swaps them
c = run(10)
check(streak(c) == nil, "switching between pockets isn't a pickup")
W.cards = nil
run(80)
fire(ModCallbacks.MC_USE_CARD, 1, P, 0)
c = run(10)
was = tonumber(streak(c):match("%d+$"))
W.paused = true
c = frames(10)
check(streak(c) == "layer Text:" .. was, "a short pause (a room change): stays up, holding still")
c = frames(40)
check(streak(c) == nil, "hidden once paused for longer (the pause menu)")
W.paused = false
c = frames(1)
check(streak(c) == "layer Text:" .. (was + 1), "and carries on where it was")

print("boss bar")
local function bar(c)
  local back = find(c, "sprite", "gfx/ui/schoolbag_bossbar.anm2")
  if not back then return nil end
  local out = {}
  for _, x in ipairs(c) do if x.a == "gfx/ui/schoolbag_bossbar.anm2" then out[#out + 1] = x.b end end
  return table.concat(out, " + "), back.c
end
c = run(2)
check(bar(c) == nil, "no bosses, no bar")
W.bosses = { { hp = 30, max = 60 }, { hp = 25, max = 40 } }
c = run(2)
local b, pos = bar(c)
check(b == "Back:0 + Fill:61", "two bosses: one bar for both (55 of 100 HP)")
check(pos and pos.X == 240 and pos.Y == 248, "centred at the bottom of the screen")
W.bosses[1].hp = 20
c = run(1)
check(bar(c) == "Back:0 + Fill:50", "a hit just shortens it, with no white flash")
W.bosses[3] = { hp = 500, max = 500, friendly = true }
c = run(1)
check(bar(c) == "Back:0 + Fill:50", "friendly bosses don't count")
W.bosses = { { hp = 1, max = 300 } }
c = run(3)
check(bar(c) == "Back:0 + Fill:1", "a sliver stays until the boss is dead")
W.bosses[1].dead = true
c = run(1)
check(bar(c) == nil, "gone when the boss dies")

print("only while the HUD is hidden")
W.bosses = { { hp = 30, max = 60 } }
newFloor(4)
frames(3 * 60 + 5) -- the app stops reading
c = frames(1)
check(not SchoolbagHudHidden and streak(c) == nil and bar(c) == nil,
  "with the game's HUD back, the game draws its own and the mod draws nothing")

SchoolbagServer:close()
print(failures == 0 and "\nALL PASSED" or ("\n" .. failures .. " FAILED"))
os.exit(failures == 0 and 0 or 1)
