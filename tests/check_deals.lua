-- Checks the devil and angel chances: the game's deal chance split the way its
-- Found HUD splits it (see dealShare in main.lua), and what's sent when the
-- game won't say.
-- Usage (from the repo root): lua5.3 tests/check_deals.lua   (needs LuaSocket)
local failures = 0
local function check(cond, msg)
  print((cond and "  ok   " or "  FAIL ") .. msg)
  if not cond then failures = failures + 1 end
end

SchoolbagServer, SchoolbagHudHidden = nil, nil
dofile("tests/stub_isaac.lua")
dofile("mod/schoolbag/main.lua")

-- the game's numbers (the stub leaves these enums out, so the mod's fallbacks are used)
local SPAWNED, VISITED, ANGEL_SPAWNED = 5, 6, 0
local EVIL_BUM_KILLED, BUM_LEFT, EVIL_BUM_LEFT = 1, 3, 4
local KEY1, KEY2, EUCHARIST, VIRTUES, CONTRITION, ROSARY = 238, 239, 499, 584, 643, 7

local function reset()
  W.flags, W.levelFlags, W.angelBonus, W.deals, W.donated = {}, {}, 0, 0, 0
  W.dealChance, W.breakAngel = nil, nil
  W.items = { [45] = 1, [105] = 1 }
  W.trinkets = { [0] = 0, [1] = 0 }
end

local function chances() return SchoolbagChances(Game():GetLevel()) end

local function expect(devil, angel, msg)
  local c = chances()
  check(c.devil == devil and c.angel == angel,
    msg .. string.format(" (devil %s, angel %s)", tostring(c.devil), tostring(c.angel)))
end

-- the stub's deal chance is a third
reset()
expect(0.333, 0, "no deal seen yet: all devil")

reset() W.flags[SPAWNED] = true
expect(0, 0.333, "the first deal seen and never walked into: the next is an angel room")

reset() W.flags[SPAWNED] = true W.flags[ANGEL_SPAWNED] = true
expect(0.167, 0.167, "after that angel room: even")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true
expect(0.167, 0.167, "walked into a devil room, took nothing: even")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.deals = 1
expect(0.333, 0, "a devil deal taken: no more angels")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.deals = 2 W.items[CONTRITION] = 1
expect(0.167, 0.167, "a deal taken, with Act of Contrition: even again")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.deals = 1 W.items[VIRTUES] = 1
expect(0.125, 0.208, "a deal taken, with Book of Virtues: even, then a quarter more to the angel")

reset() W.items[EUCHARIST] = 1 W.flags[SPAWNED] = true W.flags[VISITED] = true W.deals = 3
expect(0, 0.333, "The Eucharist: all angel, deals or not")

reset() W.angelBonus = 0.25
expect(0, 0.333, "a sacrifice room blessing before any deal: an angel room")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.angelBonus = 0.5
expect(0.083, 0.25, "blessed after a devil room: the bonus comes off the devil's half")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.angelBonus = -0.5
expect(0.167, 0.167, "a negative bonus doesn't count on its own")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.deals = 1 W.items[CONTRITION] = 1 W.angelBonus = -0.2
expect(0.2, 0.133, "a negative bonus counts with Act of Contrition")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.trinkets[1] = ROSARY
expect(0.083, 0.25, "Rosary Bead halves the devil's half")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.trinkets[0] = ROSARY | 32768
expect(0.083, 0.25, "a golden Rosary Bead too")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.donated = 10
expect(0.083, 0.25, "10 coins in the donation machine halve it")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.donated = 9
expect(0.167, 0.167, "9 coins don't")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.items[KEY1] = 1 W.items[KEY2] = 1
expect(0.094, 0.24, "both key pieces: three quarters each")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.levelFlags[EVIL_BUM_KILLED] = true
expect(0.125, 0.208, "a devil beggar killed on this floor")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.levelFlags[BUM_LEFT] = true
expect(0.15, 0.183, "a beggar paid off and gone")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.levelFlags[EVIL_BUM_LEFT] = true
expect(0.183, 0.15, "a devil beggar paid off and gone tips it the other way")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true W.levelFlags[BUM_LEFT] = true W.levelFlags[EVIL_BUM_LEFT] = true
expect(0.167, 0.167, "both beggars gone: they cancel out")

reset() W.items[KEY1] = 1 W.trinkets[0] = ROSARY
expect(0.333, 0, "angel boosts don't matter before any deal")

reset() W.flags[SPAWNED] = true W.flags[VISITED] = true
W.trinkets[0] = ROSARY W.donated = 10 W.items[KEY1] = 1 W.items[KEY2] = 1 W.angelBonus = 0.9
expect(0.002, 0.331, "everything at once: nearly all angel")

reset() W.dealChance = 1.35 W.flags[SPAWNED] = true W.flags[VISITED] = true
expect(0.5, 0.5, "a deal chance over 100% counts as 100%")

reset() W.dealChance = 0
expect(0, 0, "no deal chance: both 0")

-- co-op: anyone's items count
reset() W.flags[SPAWNED] = true W.flags[VISITED] = true
W.extra = { MakePlayer({ HasCollectible = function(_, id) return id == EUCHARIST end }) }
expect(0, 0.333, "a second player's Eucharist counts")
W.extra = nil

-- a game update that renames a call: the whole chance as the devil's, no angel
reset() W.flags[SPAWNED] = true W.breakAngel = true
local logged, debugString = {}, Isaac.DebugString
Isaac.DebugString = function(s) logged[#logged + 1] = s end
local c = chances()
Isaac.DebugString = debugString
check(c.devil == 0.333 and c.angel == nil, "the game won't say: the total as the devil's, angel left out")
check(#logged == 1 and logged[1]:find("angel chance failed", 1, true) ~= nil,
  "and it's logged as a problem: " .. tostring(logged[1]))
reset()

if SchoolbagServer then SchoolbagServer:close() end
print(failures == 0 and "ALL PASSED" or (failures .. " FAILED"))
os.exit(failures == 0 and 0 or 1)
