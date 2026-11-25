-- Checks the "Schoolbag is offline" banner under plain Lua 5.3: it stays up
-- for the whole run, steps aside for the game's own banners, waits while
-- paused, handles a busy port, and falls back to plain text without the art.
-- Usage (from the repo root): lua5.3 tests/check_reminder.lua
local failures = 0
local function check(cond, msg)
  print((cond and "  ok   " or "  FAIL ") .. msg)
  if not cond then failures = failures + 1 end
end

local calls
local function record(kind, a, b, c) calls[#calls + 1] = { kind = kind, a = a, b = b, c = c } end
local bindOk -- for the busy-port case: flips to true when the port "comes free"

local function fresh(opts)
  package.loaded.socket = nil
  package.preload.socket = nil
  if opts.noSocket then
    package.preload.socket = function() error("module 'socket' not found (no --luadebug)") end
  elseif opts.busy then
    bindOk = false
    package.preload.socket = function()
      return { bind = function()
        if not bindOk then return nil, "address already in use" end
        return { settimeout = function() end, accept = function() return nil end, close = function() end }
      end }
    end
  end
  SchoolbagServer = nil
  dofile("tests/stub_isaac.lua")
  calls = {}
  -- the game's drawing API, recorded
  Vector = function(x, y) return { X = x, Y = y } end
  KColor = function(r, g, b, a) return { r, g, b, a } end
  Isaac.GetScreenWidth = function() return 480 end
  Isaac.GetTextWidth = function(s) return #s * 6 end
  Isaac.RenderText = function(s, x, y) record("text", s, x, y) end
  Sprite = function()
    local spr = { loaded = false }
    function spr:Load(path) self.path = path self.loaded = not opts.noArt end
    function spr:IsLoaded() return self.loaded end
    function spr:SetFrame(anim, f) self.anim, self.frame = anim, f end
    function spr:Render(pos, tl, br) record("sprite", self.path, self.anim .. ":" .. self.frame, { pos = pos, tl = tl, br = br }) end
    function spr:RenderLayer(layer, pos) record("layer", self.path, self.anim .. ":" .. self.frame, { layer = layer, pos = pos }) end
    return spr
  end
  Font = function()
    local f = { loaded = false }
    function f:Load(path) self.path = path self.loaded = not opts.noArt end
    function f:IsLoaded() return self.loaded end
    function f:DrawString(s, x, y, col, box, center) record("font", s, self.path, { x = x, y = y, box = box, center = center }) end
    return f
  end
  dofile("mod/schoolbag/main.lua")
end

-- one game update plus one render, like the game at 30 fps
local function frames(n)
  for _ = 1, n do
    calls = {}
    if not W.paused then
      W.frame = W.frame + 1
      fire(ModCallbacks.MC_POST_UPDATE)
    end
    fire(ModCallbacks.MC_POST_RENDER)
  end
  return calls
end

local function find(list, kind, a)
  for _, c in ipairs(list) do if c.kind == kind and (a == nil or c.a == a) then return c end end
end
local function ink(c) local l = find(c, "sprite", "gfx/ui/ui_streak.anm2") return l and l.b end

print("no --luadebug")
fresh({ noSocket = true })
fire(ModCallbacks.MC_POST_GAME_STARTED, false)
fire(ModCallbacks.MC_POST_NEW_LEVEL)
local c = frames(4 * 30 - 1)
check(#c == 0, "nothing for the first 4 seconds (the floor-name banner has the spot)")
c = frames(1)
check(ink(c) == "TextIn:0" and not find(c, "font"), "the game's banner flies in, words not yet")
c = frames(9)
check(ink(c) == "TextStay:0", "then holds")
local whole = find(c, "sprite", "gfx/ui/ui_streak.anm2")
check(whole and whole.c.pos.X == 240 and whole.c.pos.Y == 48 and not find(c, "layer"),
  "the whole banner is drawn, ink and its own paper strip, at the game's spot")
local title = find(c, "font", "Schoolbag is offline")
local detail = find(c, "font", "Needs --luadebug")
check(title and detail, "both lines drawn")
check(title and title.b == "font/teammeatfont20bold.fnt" and detail and detail.b == "font/teammeatex/teammeatex10.fnt",
  "game fonts: Team Meat 20 bold title, Team Meat Extended 10 detail")
check(title and title.c.center and title.c.box == 400 and title.c.x == 40 and title.c.y == 35, "title centred on the ink")
check(detail and detail.c.center and detail.c.box == 168 and detail.c.x == 156 and detail.c.y == 64,
  "detail centred on the paper strip")
c = frames(30 * 60 * 10)
check(ink(c) == "TextStay:0" and find(c, "font", "Needs --luadebug"), "still up ten minutes later")

W.paused = true
c = frames(300)
check(#c == 0, "hidden while paused")
W.paused = false
c = frames(1)
check(ink(c) == "TextStay:0", "back as soon as the game resumes")

print("stepping aside for the game's banners")
W.queued = true
c = frames(1)
check(ink(c) == "TextOut:0", "item held overhead: flies out")
c = frames(30)
check(#c == 0, "stays away while the item is held")
W.queued = nil
c = frames(4 * 30 - 1)
check(#c == 0, "and while the item's name banner shows")
c = frames(1)
check(ink(c) == "TextIn:0", "flies back in 4 seconds later")
c = frames(20)
fire(ModCallbacks.MC_USE_PILL, 1, PLAYER, 0)
c = frames(1)
check(ink(c) == "TextOut:1", "pill used: steps aside")
c = frames(4)
check(ink(c) == "TextOut:5" and not find(c, "font"), "the words leave as the banner stretches away")
c = frames(4 * 30 + 9)
check(ink(c) == "TextStay:0", "and comes back")
fire(ModCallbacks.MC_USE_CARD, 1, PLAYER, 0)
c = frames(11)
check(#c == 0, "card used: steps aside")
c = frames(4 * 30)
check(ink(c) ~= nil, "and comes back")
c = frames(20)
W.playing = "Trapdoor"
c = frames(1)
check(ink(c) == "TextOut:0", "trapdoor: steps aside before the floor-change screens")
W.playing = nil
fire(ModCallbacks.MC_POST_NEW_LEVEL)
c = frames(4 * 30 - 1)
check(#c == 0, "new floor: waits for the floor-name banner")
c = frames(10)
check(ink(c) == "TextStay:0", "then holds again")

print("run over and continued")
fire(ModCallbacks.MC_POST_GAME_END, true)
c = frames(30)
check(#c == 0, "hidden once the run is over")
fire(ModCallbacks.MC_PRE_GAME_EXIT, true)
c = frames(30)
check(#c == 0, "nothing on the menus")
fire(ModCallbacks.MC_POST_GAME_STARTED, true)
c = frames(4 * 30 + 20)
check(find(c, "font", "Needs --luadebug") ~= nil, "back when the run is continued")

print("port taken")
fresh({ busy = true })
fire(ModCallbacks.MC_POST_GAME_STARTED, false)
c = frames(4 * 30 + 20)
check(find(c, "font", "Port 8089 is busy") ~= nil, "says the port is busy")
c = frames(30 * 60)
check(find(c, "font", "Port 8089 is busy") ~= nil, "still up a minute later")
bindOk = true
local sawOut = false
for _ = 1, 5 * 30 + 12 do
  c = frames(1)
  if ink(c) and ink(c):sub(1, 7) == "TextOut" then sawOut = true end
end
check(sawOut, "port frees up within 5 seconds: the retry succeeds and the banner flies out")
c = frames(30 * 30)
check(#c == 0, "and doesn't come back")
fire(ModCallbacks.MC_POST_NEW_LEVEL)
c = frames(10 * 30)
check(#c == 0, "not even on the next floor")

print("game art missing")
fresh({ noSocket = true, noArt = true })
fire(ModCallbacks.MC_POST_GAME_STARTED, false)
c = frames(4 * 30 + 20)
local t = find(c, "text")
check(t and t.a == "Schoolbag is offline: Needs --luadebug" and not find(c, "sprite"), "falls back to plain text")
c = frames(30 * 60)
check(find(c, "text") ~= nil, "and that stays up too")

print("server running")
fresh({})
fire(ModCallbacks.MC_POST_GAME_STARTED, false)
c = frames(4 * 30 + 40)
check(#c == 0, "no banner when everything works")
if SchoolbagServer then SchoolbagServer:close() end

print(failures == 0 and "\nALL PASSED" or ("\n" .. failures .. " FAILED"))
os.exit(failures == 0 and 0 or 1)
