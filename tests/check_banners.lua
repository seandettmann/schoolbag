-- Checks the banners and boss bar the mod draws while the game's HUD is
-- hidden: floor names and curses, picked-up items, pills, cards, the boss
-- bar, and the game's words arriving from the app over POST /strings.
-- Usage (from the repo root): lua5.3 tests/check_banners.lua   (needs LuaSocket)
--
-- Every card's banner is checked too: that its line stays on the paper strip.
-- With SCHOOLBAG_GAME (a folder of the game's unpacked resources, with
-- font/upheavalmini.fnt and pocketitems.xml) and SCHOOLBAG_WORDS (the art
-- bundle's strings/en.json), that's the game's own cards, measured in the
-- game's own font. Without them (as on GitHub, which has no game files), it's
-- made-up lines from short to very long, measured at 7 points a letter.
local socket = require("socket")
local failures = 0
local function check(cond, msg)
  print((cond and "  ok   " or "  FAIL ") .. msg .. (cond and "" or ("   [" .. tostring(DIAG) .. "]")))
  if not cond then failures = failures + 1 end
end

local calls = {}
local function record(kind, a, b, c) calls[#calls + 1] = { kind = kind, a = a, b = b, c = c } end

SchoolbagServer, SchoolbagHudHidden, SchoolbagStrings = nil, nil, nil
SchoolbagBannerPaper = true -- most checks here are of the paper strip; titles only is checked near the end
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
  function spr:Render(pos)
    record("sprite", self.path, self.anim .. ":" .. self.frame, pos)
    calls[#calls].d = self.Scale and self.Scale.X or 1
  end
  function spr:RenderLayer(layer, pos)
    record("layer", self.path, self.anim .. ":" .. self.frame, layer)
    if self.Scale and self.Scale.X ~= 1 then record("scale", self.path, self.Scale.X, layer) end
  end
  return spr
end
MEASURE = function(s) return #s * 7 end -- about Upheaval mini's; the game's own below, if it's here
Font = function()
  local f = {}
  function f:Load(path) self.path = path end
  function f:IsLoaded() return true end
  function f:DrawString(s, x, y, col, box) record("font", s, self.path, { x = x, y = y, box = box, scale = 1 }) end
  function f:DrawStringScaled(s, x, y, sx, sy, col, box)
    record("font", s, self.path, { x = x, y = y, box = box, scale = sx })
  end
  function f:GetStringWidth(s) return MEASURE(s) end
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
-- the long strip drawn this frame, if any: its left and right edges, and whether its parts meet
local function longPaper(c)
  local parts = {}
  for _, x in ipairs(c) do
    if x.a == "gfx/ui/schoolbag_paper.anm2" then parts[x.b] = { x = x.c.X, y = x.c.Y, scale = x.d } end
  end
  if not (parts["Left:0"] and parts["Middle:0"] and parts["Right:0"]) then return nil end
  local l, m, r = parts["Left:0"], parts["Middle:0"], parts["Right:0"]
  local joined = l.scale == 1 and r.scale == 1 and m.x == l.x + 30
    and math.abs(m.x + m.scale * 108 - r.x) < 0.001 and l.y == 48 and m.y == 48 and r.y == 48
  return l.x, r.x + 30, joined
end
-- whether a detail line sits on its strip: the strip (the game's 168 points,
-- or a long one) reaches at least 22 points past the line at each end, clear
-- of its torn ends
local function onPaper(c, line, w)
  local text = find(c, "font", line)
  if not text then return false, "not written" end
  local l, r, joined = longPaper(c)
  if not l then l, r, joined = 240 - 84, 240 + 84, true end
  local box = text.c.box
  w = w * text.c.scale -- a line too long even for the screen is written narrower
  if math.abs(text.c.x + box / 2 - 240) > 0.001 then return false, "not centred" end
  if not joined then return false, "the strip's parts don't meet" end
  if w + 44 > r - l + 0.001 then return false, ("strip %.0f for a %.0f-point line"):format(r - l, w) end
  if box > r - l + 0.001 then return false, "the text box runs past the strip" end
  return true
end
local ok, why = onPaper(c, "Curse of the Labyrinth", 154)
local l, r = longPaper(c)
check(ok and l == 240 - 99 and r == 240 + 99,
  "a line too long for the paper strip gets a longer one, and stays on it (" .. tostring(why or "ok") .. ")")
check((streak(c) or ""):match("^sprite Text:%d+$"), "the game's own strip is still drawn underneath")
c = run(45)
check(words(c) == "" and not longPaper(c) and (streak(c) or ""):match("^sprite Text:6"),
  "as the strip flies out, it's the game's size again and the words have gone")
newFloor(2, 0, 4)
c = run(20)
check(not longPaper(c) and (streak(c) or ""):match("^sprite Text:") and words(c) == "Basement II / Curse of the Lost",
  "a line that fits: the strip as it is")
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

print("every card's line stays on the paper")
-- the game's font: its letters' widths, from the .fnt file (BMFont, binary)
local function gameFont(path)
  local f = io.open(path, "rb")
  if not f then return nil end
  local d = f:read("a")
  f:close()
  if d:sub(1, 4) ~= "BMF\3" then return nil end
  local adv, i = {}, 5
  while i <= #d do
    local kind, size = string.unpack("<BI4", d, i)
    if kind == 4 then
      for k = i + 5, i + 4 + size, 20 do
        local id, _, _, _, _, _, _, xadv = string.unpack("<I4I2I2I2I2i2i2i2", d, k)
        adv[id] = xadv
      end
    end
    i = i + 5 + size
  end
  return function(s)
    local w = 0
    for _, ch in utf8.codes(s) do
      w = w + (adv[ch] or adv[utf8.codepoint(utf8.char(ch):upper())] or 0)
    end
    return w
  end
end
local cards, source = {}, nil
local game, wordsFile = os.getenv("SCHOOLBAG_GAME"), os.getenv("SCHOOLBAG_WORDS")
local measure = game and gameFont(game .. "/font/upheavalmini.fnt")
local xmlFile = game and io.open(game .. "/other/pocketitems.xml") or (game and io.open(game .. "/pocketitems.xml"))
local wf = wordsFile and io.open(wordsFile)
if measure and xmlFile and wf then
  local gameWords = json.decode(wf:read("a"))
  for kind, tag in xmlFile:read("a"):gmatch("<(%a+)(%s[^>]-)/>") do
    local id, name, desc = tag:match('%sid="(%d+)"'), tag:match('%sname="#?([^"]+)"'), tag:match('%sdescription="#?([^"]+)"')
    if (kind == "card" or kind == "rune") and id and desc and tonumber(id) > 0 then
      cards[#cards + 1] = { id = tonumber(id), name = gameWords[name] or name, line = gameWords[desc] or desc }
    end
  end
  MEASURE, source = measure, "the game's " .. #cards .. " cards, in the game's own font"
else
  local words = { "May", "you", "see", "the", "value", "of", "all", "things", "in", "life", "and", "beyond" }
  for n = 1, #words * 2 do
    local t = {}
    for k = 1, n do t[k] = words[(k - 1) % #words + 1] end
    cards[#cards + 1] = { id = 200 + n, name = "Card " .. n, line = table.concat(t, " ") }
  end
  source = #cards .. " made-up lines, 3 to 120 letters (no game files here)"
end
if xmlFile then xmlFile:close() end
if wf then wf:close() end
local byId = {}
for _, card in ipairs(cards) do byId[card.id] = card end
local getCard = itemConfig.GetCard
function itemConfig:GetCard(id)
  local card = byId[id]
  if card then return { Name = card.name, Description = card.line } end
  return getCard(self, id)
end
local bad, long, widest, squeezed = {}, 0, nil, 0
for _, card in ipairs(cards) do
  W.cards = {}
  run(1)
  W.cards = { [0] = card.id }
  c = run(20)
  local w = MEASURE(card.line)
  local fine, why = onPaper(c, card.line, w)
  if not fine then bad[#bad + 1] = card.name .. " (" .. why .. ")" end
  local text = find(c, "font", card.line)
  if text and text.c.scale < 1 then squeezed = squeezed + 1 end
  if w > 124 then long = long + 1 end
  if not widest or w > widest.w then widest = { w = w, name = card.name } end
end
W.cards = nil
run(80)
check(#bad == 0, ("%s: all on the paper (%d needed a longer strip, %d written narrower to fit the screen; the longest, %s, is %d points)")
  :format(source, long, squeezed, widest.name, widest.w) .. (#bad > 0 and ("; off it: " .. table.concat(bad, ", ")) or ""))
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

print("titles only (the default since Nov 27 2026)")
SchoolbagBannerPaper = nil
W.bosses = nil
newFloor(2, 0, 64)
c = run(20)
check(words(c) == "Sous-sol II" and (streak(c) or ""):match("^layer Text:"),
  "a floor with a curse: its name on the ink alone, no curse strip")
P.QueuedItem = { Item = { Type = 1, ID = 1, Name = "#THE_SAD_ONION_NAME", Description = "#THE_SAD_ONION_DESCRIPTION" } }
c = run(10)
check(words(c) == "The Sad Onion" and (streak(c) or ""):match("^layer Text:"), "an item: its name alone, no pickup line")
P.QueuedItem = nil
W.cards = { [0] = 3 }
c = run(10)
check(words(c) == "Card" and (streak(c) or ""):match("^layer Text:"), "a card picked up: its name alone")
W.cards = nil
run(80)
SchoolbagBannerPaper = true

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
