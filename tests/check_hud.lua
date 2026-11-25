-- Checks that the mod hides the game's own HUD only while the app is reading
-- the run, and gives it back when the app stops or the run ends.
-- Usage (from the repo root): lua5.3 tests/check_hud.lua   (needs LuaSocket)
local socket = require("socket")
local failures = 0
local function check(cond, msg)
  print((cond and "  ok   " or "  FAIL ") .. msg)
  if not cond then failures = failures + 1 end
end

SchoolbagServer, SchoolbagHudHidden = nil, nil
dofile("tests/stub_isaac.lua")
dofile("mod/schoolbag/main.lua")
W.render = 0

-- one render frame (60 a second); a game update every other one unless paused
local function frames(n)
  for _ = 1, n do
    W.render = W.render + 1
    if not W.paused and W.render % 2 == 0 then
      W.frame = W.frame + 1
      fire(ModCallbacks.MC_POST_UPDATE)
    end
    fire(ModCallbacks.MC_POST_RENDER)
  end
end

-- what the app does: ask for /state and read the answer
local function poll(path)
  local c = assert(socket.connect("127.0.0.1", 8089))
  c:send("GET " .. (path or "/state") .. " HTTP/1.1\r\nHost: x\r\n\r\n")
  frames(1) -- the mod answers during the next render frame
  c:settimeout(2)
  local reply = c:receive("*a")
  c:close()
  return reply or ""
end

local function visible() return W.hudVisible ~= false end

fire(ModCallbacks.MC_POST_GAME_STARTED, false)
frames(30)
check(visible(), "HUD stays on while no app is reading")
check(poll("/health"):find("ok") and visible(), "a /health check alone doesn't hide it")

check(poll():find("200 OK"), "the app reads /state")
check(not visible(), "HUD hidden as soon as the app reads the run")
for _ = 1, 20 do frames(6) poll() end -- ten reads a second for two seconds
check(not visible(), "stays hidden while the app keeps reading")

poll("/state?hud=keep")
frames(1)
check(visible(), "the app's setting off (?hud=keep): the HUD comes straight back")
for _ = 1, 10 do frames(6) poll("/state?hud=keep") end
check(visible(), "and stays while the app keeps reading that way")
poll()
check(not visible(), "the setting back on: hidden again")

W.hudVisible = true -- the game turns its HUD back on by itself
frames(1)
check(not visible(), "hidden again if the game switches it back on")

W.paused = true
for _ = 1, 10 do frames(6) poll() end
check(not visible(), "stays hidden while paused (the app still reads)")
W.paused = false

frames(3 * 60 - 10)
check(not visible(), "still hidden just under 3 seconds after the last read")
frames(20)
check(visible(), "back on its own 3 seconds after the app stops reading")

poll()
check(not visible(), "hidden again when the app comes back")
fire(ModCallbacks.MC_PRE_GAME_EXIT, true)
check(visible(), "given back when you leave the run")
frames(10)
poll()
frames(5)
check(visible(), "not hidden on the menus, even with the app reading")

fire(ModCallbacks.MC_POST_GAME_STARTED, true)
frames(2)
poll()
check(not visible(), "hidden again in a continued run")

-- Tainted Cain: the game's HUD comes back while the Bag of Crafting is full,
-- since its crafting preview is part of the HUD
local hashes = 0
local function scoop()
  hashes = hashes + 1
  local e = { Variant = 20, SubType = 1, Position = { X = 300, Y = 300 }, hash = "coin" .. hashes }
  W.pickups = { e }
  frames(2)
  W.knives = { { Variant = 4, Position = { X = 310, Y = 300 } } }
  frames(2)
  W.pickups, W.knives = {}, {}
  frames(2)
end
local function reading() for _ = 1, 3 do frames(6) poll() end end
W.actives[2] = 710
for _ = 1, 8 do scoop() end
reading()
check(not visible(), "a full Bag of Crafting on another character: still hidden")
W.playerType = 23
reading()
check(visible(), "Tainted Cain with a full bag: the game's HUD shows (its crafting preview)")
W.collectibles = W.collectibles + 1 -- crafted
frames(2)
reading()
check(not visible(), "hidden again once he crafts and the bag empties")
for _ = 1, 3 do scoop() end
reading()
check(not visible(), "a part-full bag: stays hidden")
for _ = 1, 5 do scoop() end
W.collectibles = W.collectibles + 1
W.actives[0] = 118 -- crafted an active item over the D6: unseen pickups went in
frames(2)
reading()
check(visible(), "shown while the mod isn't sure what's in the bag")
W.actives[0], W.actives[2], W.playerType = 105, nil, 0
reading()
check(not visible(), "hidden again without the bag")
SchoolbagServer:close()
SchoolbagServer = nil
-- a mod reload (luamod) while hidden: the new copy still knows to give it back
dofile("tests/stub_isaac.lua")
W.hudVisible, W.render = false, 0
dofile("mod/schoolbag/main.lua")
fire(ModCallbacks.MC_POST_GAME_STARTED, true)
frames(10)
check(visible(), "after a mod reload with no app reading, the HUD comes back")
if SchoolbagServer then SchoolbagServer:close() end

print(failures == 0 and "\nALL PASSED" or ("\n" .. failures .. " FAILED"))
os.exit(failures == 0 and 0 or 1)
