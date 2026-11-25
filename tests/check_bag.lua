-- Checks the mod's Bag of Crafting tracking against the stubbed game: pickups
-- the swung bag scoops up go in, pickups the player takes don't, a full bag
-- drops its first pickup, crafting empties it, and it survives a continue.
-- Usage (from the repo root): lua5.3 tests/check_bag.lua   (needs LuaSocket)
local socket = require("socket")
local failures = 0
local function check(cond, msg)
  print((cond and "  ok   " or "  FAIL ") .. msg)
  if not cond then failures = failures + 1 end
end

SchoolbagServer, SchoolbagHudHidden = nil, nil
dofile("tests/stub_isaac.lua")
dofile("mod/schoolbag/main.lua")
local json = require("json")
W.render = 0

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
local function update(n) frames(2 * (n or 1)) end

local function state()
  local c = assert(socket.connect("127.0.0.1", 8089))
  c:send("GET /state HTTP/1.1\r\nHost: x\r\n\r\n")
  frames(1)
  c:settimeout(2)
  local reply = c:receive("*a") or ""
  c:close()
  return json.decode(reply:match("\r\n\r\n(.*)$") or "null")
end
local function bag()
  local s = state()
  return s and s.players and s.players[1] and s.players[1].bag
end
local function same(a, b)
  if type(a) ~= "table" or #a ~= #b then return false end
  for i = 1, #b do if a[i] ~= b[i] then return false end end
  return true
end
local function show(t) return t and ("[" .. table.concat(t, ",") .. "]") or "nil" end

local nextHash = 1
local function spawn(variant, subtype, x, y)
  nextHash = nextHash + 1
  local e = { Variant = variant, SubType = subtype, Position = { X = x or 300, Y = y or 300 }, hash = nextHash }
  table.insert(W.pickups, e)
  return e
end
local function vanish(e)
  for i, p in ipairs(W.pickups) do if p == e then table.remove(W.pickups, i) return end end
end
local function swingAt(x, y) W.knives = { { Variant = 4, Position = { X = x, Y = y } } } end
local function stopSwing() W.knives = {} end
local function newRun() fire(ModCallbacks.MC_POST_GAME_STARTED, false) update() end

-- scoop one pickup the way the game does: it's there, the bag swings over it,
-- then it's gone
local function scoop(variant, subtype)
  local e = spawn(variant, subtype)
  update()
  swingAt(e.Position.X + 20, e.Position.Y)
  update()
  vanish(e)
  stopSwing()
  update()
end

fire(ModCallbacks.MC_POST_GAME_STARTED, false)
update(2)

print("not holding the bag")
check(bag() == nil, "no bag in /state")

print("Tainted Cain's pocket bag")
W.actives[2] = 710
update()
local b = bag()
check(b and same(b.items, {}) and b.sure == true, "empty and sure at the start of a run")

scoop(20, 1)
check(same(bag().items, { 8 }), "a penny the bag swung over went in: " .. show(bag().items))

do -- the player walked over it: coins went up as it vanished
  local e = spawn(20, 1)
  update()
  swingAt(e.Position.X, e.Position.Y)
  update()
  stopSwing()
  vanish(e)
  W.coins = W.coins + 1
  update()
  check(same(bag().items, { 8 }), "a penny the player picked up didn't: " .. show(bag().items))
end

do -- the player took it: coins went up at once, the coin lingered for its pickup animation
  local e = spawn(20, 2)
  update()
  swingAt(e.Position.X, e.Position.Y) -- the bag is right there the whole time
  W.coins = W.coins + 5
  update(4)
  vanish(e)
  update()
  stopSwing()
  update()
  check(same(bag().items, { 8 }), "a nickel the player took while the bag was near didn't: " .. show(bag().items))
end

do -- another coin taken long before doesn't stop the bag's coin counting
  W.coins = W.coins + 1
  update(30)
  scoop(20, 1)
  check(same(bag().items, { 8, 8 }), "a penny scooped after an old coin pickup went in: " .. show(bag().items))
  newRun()
  scoop(20, 1)
end

do -- vanished far from the bag
  local e = spawn(30, 1, 600, 600)
  update()
  swingAt(100, 100)
  update()
  stopSwing()
  vanish(e)
  update()
  check(same(bag().items, { 8 }), "a key that vanished far from the bag didn't")
end

do -- touched too long ago
  local e = spawn(30, 1)
  update()
  swingAt(e.Position.X, e.Position.Y)
  update()
  stopSwing()
  update(20)
  vanish(e)
  update()
  check(same(bag().items, { 8 }), "a key the bag touched 20 updates earlier didn't")
end

do -- the game reports the bag touching it, though the bag's position is far off
  local e = spawn(30, 4)
  update()
  fire(ModCallbacks.MC_PRE_PICKUP_COLLISION, e, { Type = 8, Variant = 4 }, false)
  update()
  vanish(e)
  update()
  check(same(bag().items, { 8, 14 }), "a charged key the game said the bag touched went in: " .. show(bag().items))
end

print("what each pickup counts as")
newRun()
scoop(10, 5) -- double heart
scoop(10, 10) -- blended heart
scoop(70, 14 | 2048) -- golden horse pill
check(same(bag().items, { 1, 1, 1, 2, 27 }), "double heart = 2 red, blended = red + soul, golden horse pill = golden pill: " .. show(bag().items))
newRun()
scoop(300, 49)
scoop(300, 33)
scoop(300, 1)
scoop(300, 78)
scoop(42, 0)
scoop(40, 7)
scoop(90, 2)
check(same(bag().items, { 24, 23, 21, 25, 29, 17, 18 }), "dice shard, rune, card, cracked key, poop, giga bomb, micro battery: " .. show(bag().items))
scoop(40, 3) -- a troll bomb can't go in the bag
check(#bag().items == 7, "a troll bomb isn't counted")

print("a full bag")
scoop(20, 3)
check(same(bag().items, { 24, 23, 21, 25, 29, 17, 18, 10 }), "8th pickup fills it: " .. show(bag().items))
scoop(20, 7)
check(same(bag().items, { 23, 21, 25, 29, 17, 18, 10, 26 }), "9th pushes the first out: " .. show(bag().items))

print("cycling")
W.dropPressed = true
frames(1)
W.dropPressed = false
update()
check(same(bag().items, { 21, 25, 29, 17, 18, 10, 26, 23 }), "the drop button cycles the bag: " .. show(bag().items))

print("crafting")
W.collectibles = W.collectibles + 1
update()
b = bag()
check(b and #b.items == 0 and b.sure == true, "a new item with a full bag empties it")
for _ = 1, 8 do scoop(20, 1) end
W.collectibles = W.collectibles + 1
W.actives[0] = 118 -- crafted an active item over the D6
update()
b = bag()
check(b and #b.items == 0 and b.sure == false, "crafting over an active item marks the bag unsure (its pickups go in unseen)")
W.actives[0] = 105
for _ = 1, 8 do scoop(20, 1) end
W.collectibles = W.collectibles + 1
update()
check(bag().sure == true, "the next craft makes it sure again")
scoop(20, 1)
W.collectibles = W.collectibles + 1
update()
check(#bag().items == 1, "a new item with a part-full bag doesn't empty it")

print("rooms")
do
  local e = spawn(20, 2)
  update()
  swingAt(e.Position.X, e.Position.Y)
  update()
  stopSwing()
  W.roomIndex = 46
  fire(ModCallbacks.MC_POST_NEW_ROOM)
  W.pickups = {}
  update()
  check(#bag().items == 1, "pickups left behind in the last room don't count")
end

print("save and continue")
scoop(10, 3)
check(same(bag().items, { 8, 2 }), "a soul heart went in: " .. show(bag().items))
update(2)
fire(ModCallbacks.MC_PRE_GAME_EXIT, true)
fire(ModCallbacks.MC_POST_GAME_STARTED, true)
update()
b = bag()
check(b and same(b.items, { 8, 2 }) and b.sure == true, "continuing the same run brings the bag back: " .. show(b and b.items))
fire(ModCallbacks.MC_PRE_GAME_EXIT, true)
W.seed = "WXYZ 1234"
fire(ModCallbacks.MC_POST_GAME_STARTED, true)
update()
b = bag()
check(b and #b.items == 0 and b.sure == false, "continuing a different run: empty and unsure")
fire(ModCallbacks.MC_POST_GAME_STARTED, false)
update()
b = bag()
check(b and #b.items == 0 and b.sure == true, "a new run: empty and sure")

print("the bag in the state")
local s = state()
check(s.players[1].bag ~= nil and type(s.players[1].bag.items) == "table", "players[].bag has items and sure")

if SchoolbagServer then SchoolbagServer:close() end
print(failures == 0 and "ALL PASSED" or (failures .. " FAILED"))
os.exit(failures == 0 and 0 or 1)
