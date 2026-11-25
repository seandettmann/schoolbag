-- Runs the Schoolbag mod under plain Lua 5.3 with the stubbed game API.
-- A test client steers it by writing a scenario name to /tmp/schoolbag_ctl.
dofile("tests/stub_isaac.lua")
dofile("mod/schoolbag/main.lua")
local socket = require("socket")

local S = {}
local started = false
function S.start() started = true fire(ModCallbacks.MC_POST_GAME_STARTED, false) end
function S.add4() W.items[4] = 1 end
function S.add1() W.items[1] = 1 end
function S.normal()
  W.items[313] = 1
  W.items[212] = 1
  W.effectsMantle = 2
  W.trinkets[0] = 121
  W.trinkets[1] = 32768 | 2
  W.pedestals = {
    { id = 118, x = 200, y = 200 },
    { id = 4, x = 100, y = 200 },
  }
end
function S.cursed() W.curses = 1 | 4 | 8 | 64 end
function S.uncursed() W.curses = 1 end
function S.dead()
  fire(ModCallbacks.MC_ENTITY_TAKE_DMG, PLAYER, 1, 0,
    { Type = 9, Variant = 0, SpawnerType = 10, SpawnerVariant = 1 })
  fire(ModCallbacks.MC_POST_GAME_END, true)
  W.playing = "Trapdoor"
end
function S.exit() fire(ModCallbacks.MC_PRE_GAME_EXIT, true) end
function S.continue()
  W.playing = nil
  fire(ModCallbacks.MC_POST_GAME_STARTED, true)
end
function S.reorder() -- lose one copy of item 4, gain a second item 1
  W.items[4] = 0
  W.items[1] = 2
end
function S.tcain() -- the Bag of Crafting in the pocket, with a penny on the floor next to it
  W.actives[2] = 710
  W.pickups = { { Variant = 20, SubType = 1, Position = { X = 300, Y = 300 }, hash = "penny" } }
  W.knives = { { Variant = 4, Position = { X = 320, Y = 300 } } }
end
function S.scooped() W.pickups = {} W.knives = {} end
function S.bluebaby() -- Tainted ???: poop spells queued (stone, normal, burning)
  W.playerType = 25
  local queue = { 4, 1, 3 }
  PLAYER.GetPoopMana = function() return 2 end
  PLAYER.GetMaxPoopMana = function() return 9 end
  PLAYER.GetPoopSpell = function(_, pos) return queue[pos + 1] or 0 end
end
function S.forgotten() -- The Forgotten in control; The Soul holds 2 and a half soul hearts
  W.playerType = 16
  local soul = setmetatable({}, { __index = PLAYER })
  function soul:GetPlayerType() return 17 end
  function soul:GetMaxHearts() return 0 end
  function soul:GetHearts() return 0 end
  function soul:GetSoulHearts() return 5 end
  function soul:GetBlackHearts() return 0 end
  PLAYER.GetSubPlayer = function() return soul end
end
function S.plain() W.playerType = 0 end
function S.mirror() W.mirror = true end
function S.unmirror() W.mirror = false end

local stop = socket.gettime() + 60
while socket.gettime() < stop do
  if started then -- frame count stays 0 until a run starts, like the title screen
    W.frame = W.frame + 1
    W.time = W.time + 1
    fire(ModCallbacks.MC_POST_UPDATE)
  end
  fire(ModCallbacks.MC_POST_RENDER)
  local f = io.open("/tmp/schoolbag_ctl", "r")
  if f then
    local cmd = f:read("*l")
    f:close()
    os.remove("/tmp/schoolbag_ctl")
    if cmd == "quit" then break end
    assert(S[cmd], "unknown scenario " .. tostring(cmd))()
    local done = io.open("/tmp/schoolbag_ack", "w") done:write(cmd) done:close()
  end
  socket.sleep(0.004)
end
