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
