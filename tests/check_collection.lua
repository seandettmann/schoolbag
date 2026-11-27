-- Checks reading the save's item collection (the Room tab names and describes
-- only items collected before): the Repentance+ save format, the slot from the
-- game's log, where the file is looked for, and the pedestals' `known` flag.
-- Usage (from the repo root): lua5.3 tests/check_collection.lua   (needs LuaSocket)
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
local C = SchoolbagCollection
W.render = 0

-- A Repentance+ save: the header, a checksum, chunks 1-3 and the collection
-- (item IDs 0-9; 1, 4 and 7 collected), then the chunks that follow
local function save(collected)
  local function chunk(id, entries, width)
    local body = {}
    for _, v in ipairs(entries) do body[#body + 1] = width == 1 and string.char(v) or string.pack("<i4", v) end
    return string.pack("<I4I4I4", id, #entries * (id == 1 and 1 or 4), #entries) .. table.concat(body)
  end
  local col = {}
  for i = 0, 9 do col[#col + 1] = collected[i] and 1 or 0 end
  return "ISAACNGSAVE09R  " .. string.pack("<I4", 12345)
    .. chunk(1, {1, 0, 1}, 1) .. chunk(2, {5, 6}, 4) .. chunk(3, {7}, 4)
    .. chunk(4, col, 1) .. chunk(5, {0, 1}, 1)
end
local SAVE = save({ [1] = true, [4] = true, [7] = true })

print("the save format")
local known, n = C.parse(SAVE)
check(known and n == 3 and known[1] and known[4] and known[7] and not known[2], "items 1, 4 and 7 collected, the rest not")
check(C.parse("ISAACNGSAVE06R  rest") == nil, "an older (Rebirth) save is refused")
check(C.parse(SAVE:sub(1, 60)) == nil, "a save cut short is refused")

print("the game's log")
local slot, cloud = C.fromLog("[INFO] - Loading PersistentGameData from Steam Cloud: rep+persistentgamedata2.dat.\r\n[INFO] - Reading Collectibles\r\n")
check(slot == 2 and cloud, "slot 2, from the Steam Cloud")
slot, cloud = C.fromLog("Loading PersistentGameData from Steam Cloud: rep+persistentgamedata1.dat.\nLoading PersistentGameData from Steam Cloud: rep+persistentgamedata3.dat.\n")
check(slot == 3, "the last save loaded wins")
check(C.fromLog("nothing here") == nil, "no slot without the line")

-- a pretend disk
local disk = {}
C.read = function(path) return disk[path] end
C.env = function(k)
  return ({ USERPROFILE = "C:\\users\\xuser", APPDATA = "C:\\users\\xuser\\AppData\\Roaming",
    ["ProgramFiles(x86)"] = "C:\\Program Files (x86)" })[k]
end
local DOCS = "C:\\users\\xuser\\Documents\\My Games\\Binding of Isaac Repentance+\\"

print("finding the save")
disk[DOCS .. "log.txt"] = "Loading PersistentGameData from Steam Cloud: rep+persistentgamedata1.dat.\n"
disk["C:\\Program Files (x86)\\Steam\\config\\loginusers.vdf"] = '"users"\n{\n\t"76561197960265738"\n\t{\n\t}\n}\n'
disk["C:\\Program Files (x86)\\Steam\\userdata\\10\\250900\\remote\\rep+persistentgamedata1.dat"] = SAVE
C.load()
check(C.status == "read" and C.count == 3 and C.path:find("userdata\\10\\", 1, true), "the cloud copy, under the account from loginusers.vdf")
disk["C:\\Program Files (x86)\\Steam\\userdata\\10\\250900\\remote\\rep+persistentgamedata1.dat"] = nil
disk["C:\\users\\xuser\\AppData\\Roaming\\GSE Saves\\250900\\remote\\rep+persistentgamedata1.dat"] = SAVE
C.load()
check(C.status == "read" and C.path:find("GSE Saves", 1, true), "or a Steam stand-in's folder")
disk["C:\\users\\xuser\\AppData\\Roaming\\GSE Saves\\250900\\remote\\rep+persistentgamedata1.dat"] = nil
disk[DOCS .. "log.txt"] = "Loading PersistentGameData from file: persistentgamedata1.dat.\n"
disk[DOCS .. "persistentgamedata1.dat"] = SAVE
C.load()
check(C.status == "read" and C.path == DOCS .. "persistentgamedata1.dat", "the Documents copy when Steam Cloud is off")
disk[DOCS .. "persistentgamedata1.dat"] = nil
C.load()
check(C.status == "save not found" and C.known == nil and #C.tried >= 2, "not found: everything described, and where it looked")
disk[DOCS .. "log.txt"] = nil
C.load()
check(C.known == nil and C.status:find("log", 1, true), "no log: everything described")

print("the Room tab's items")
disk[DOCS .. "log.txt"] = "Loading PersistentGameData from Steam Cloud: rep+persistentgamedata1.dat.\n"
disk["C:\\Program Files (x86)\\Steam\\userdata\\10\\250900\\remote\\rep+persistentgamedata1.dat"] = SAVE
local function frames(n)
  for _ = 1, n do
    W.render = W.render + 1
    if not W.paused and W.render % 2 == 0 then W.frame = W.frame + 1; fire(ModCallbacks.MC_POST_UPDATE) end
    fire(ModCallbacks.MC_POST_RENDER)
  end
end
local function state()
  local c = assert(socket.connect("127.0.0.1", 8089))
  c:send("GET /state HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
  frames(1)
  c:settimeout(2)
  local reply = c:receive("*a") or ""
  c:close()
  return json.decode(reply:match("\r\n\r\n(.*)$") or "{}")
end
fire(ModCallbacks.MC_POST_GAME_STARTED, false)
frames(2)
W.pedestals = { { id = 4, x = 100, y = 200 }, { id = 2, x = 200, y = 200 }, { id = 8, x = 300, y = 200 } }
W.items[8] = 1 -- held now: collected this run, before the game has saved
local s = state()
local p = s.room and s.room.pedestals or {}
check(p[1] and p[1].known == nil, "a collected item: named as usual")
check(p[2] and p[2].known == false and p[2].id == 2, "not collected yet: known = false (its picture still shows)")
check(p[3] and p[3].known == nil, "held this run: named")
check(s.collection and s.collection.status == "read" and s.collection.slot == 1 and s.collection.items == 3, "the state says how the save was read")
W.items[8] = 0
W.pedestals = {}

if SchoolbagServer then SchoolbagServer:close() end
print(failures == 0 and "ALL PASSED" or (failures .. " FAILED"))
os.exit(failures == 0 and 0 or 1)
