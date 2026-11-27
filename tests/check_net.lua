-- Checks the mod's server never makes the game wait, keeps connections open,
-- pushes the state over UDP to an app that says hello, measures itself, and
-- reports the game signals the app follows (the Map button, the pause menu).
-- Usage (from the repo root): lua5.3 tests/check_net.lua   (needs LuaSocket)
local socket = require("socket")
local failures = 0
local function check(cond, msg)
  print((cond and "  ok   " or "  FAIL ") .. msg)
  if not cond then failures = failures + 1 end
end

SchoolbagServer, SchoolbagUdp, SchoolbagHudHidden = nil, nil, nil
dofile("tests/stub_isaac.lua")
dofile("mod/schoolbag/main.lua")
local json = require("json")
W.render = 0

-- one render frame (60 a second); a game update every other one unless paused;
-- returns the slowest render callback, in seconds
local function frames(n)
  local worst = 0
  for _ = 1, n do
    W.render = W.render + 1
    if not W.paused and W.render % 2 == 0 then
      W.frame = W.frame + 1
      fire(ModCallbacks.MC_POST_UPDATE)
    end
    local t = socket.gettime()
    fire(ModCallbacks.MC_POST_RENDER)
    worst = math.max(worst, socket.gettime() - t)
  end
  return worst
end

-- reads one HTTP reply by its Content-Length, running frames until it's all there
local function readReply(c)
  c:settimeout(0)
  local buf = ""
  for _ = 1, 200 do
    frames(1)
    local data, _, partial = c:receive(65536)
    buf = buf .. (data or partial or "")
    local headEnd = buf:find("\r\n\r\n", 1, true)
    if headEnd then
      local len = tonumber(buf:sub(1, headEnd):lower():match("content%-length:%s*(%d+)"))
      if len and #buf >= headEnd + 3 + len then
        return buf:sub(1, headEnd + 3), buf:sub(headEnd + 4, headEnd + 3 + len)
      end
    end
  end
  return buf, nil
end

fire(ModCallbacks.MC_POST_GAME_STARTED, false)
frames(10)

print("keep-alive")
local c = assert(socket.connect("127.0.0.1", 8089))
c:send("GET /health HTTP/1.1\r\nHost: x\r\n\r\n")
local head, body = readReply(c)
check(body == "ok" and head:find("Connection: keep%-alive"), "the first request on a connection is answered and it stays open")
c:send("GET /state HTTP/1.1\r\nHost: x\r\n\r\n")
head, body = readReply(c)
local s = body and json.decode(body)
check(s and s.inRun == true, "a second request on the same connection is answered")
c:close()
frames(2)

print("never waiting on a slow request")
c = assert(socket.connect("127.0.0.1", 8089))
c:send("GET /sta")
local worst = frames(5)
check(worst < 0.01, string.format("half a request doesn't hold the game up (slowest frame %.1f ms)", worst * 1000))
c:send("te HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
head, body = readReply(c)
s = body and json.decode(body)
check(s and s.inRun == true and head:find("Connection: close"), "the rest arrives later and gets its answer, then the connection closes")
c:close()
-- a client that connects and never sends anything is dropped after a while
c = assert(socket.connect("127.0.0.1", 8089))
frames(5 * 60 + 5)
c:settimeout(0.2)
local _, err = c:receive(1)
check(err == "closed", "an idle connection is closed after 5 seconds")
c:close()

print("the state pushed over UDP")
local u = assert(socket.udp())
u:setsockname("127.0.0.1", 0)
u:settimeout(0)
local function datagrams(n)
  local got = {}
  for _ = 1, n do
    frames(1)
    while true do
      local d = u:receive()
      if not d then break end
      got[#got + 1] = d
    end
  end
  return got
end
W.hudVisible = true
u:sendto("hello", "127.0.0.1", 8089)
local got = datagrams(2)
s = got[1] and json.decode(got[1])
check(s and s.inRun == true and s.mod.name == "Schoolbag", "a hello gets the state straight back")
frames(1)
check(W.hudVisible == false, "a hello counts as the app watching: the game's HUD is hidden")
datagrams(10) -- the HUD's change itself goes out
got = datagrams(30)
check(#got == 0, "nothing more while nothing changes (half a second)")
W.coins = (W.coins or 0) + 1
got = datagrams(8)
check(#got == 1, "a change goes out within a tenth of a second")
got = datagrams(60)
check(#got >= 1, "a heartbeat at least once a second, even with no change")
u:sendto("hello keep", "127.0.0.1", 8089)
datagrams(2)
check(W.hudVisible ~= false, "\"hello keep\" reads without hiding the game's HUD")
u:sendto("hello", "127.0.0.1", 8089)
datagrams(2)
got = datagrams(3 * 60 + 10)
local after = datagrams(70)
check(#after == 0, "no hello for 3 seconds: the mod stops sending")
frames(3 * 60)
check(W.hudVisible ~= false, "and the game's HUD comes back")
u:close()

print("measuring itself")
c = assert(socket.connect("127.0.0.1", 8089))
c:send("GET /state HTTP/1.1\r\nHost: x\r\n\r\n")
head, body = readReply(c)
s = json.decode(body)
check(type(s.perf) == "table" and s.perf.answers and s.perf.avgMs and s.perf.maxMs and s.perf.frameMaxMs
  and s.perf.transport, "the state reports answers, average and worst times, the worst frame and the transport")
c:close()

print("the game's signals")
local function state()
  local cc = assert(socket.connect("127.0.0.1", 8089))
  cc:send("GET /state HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
  local _, b = readReply(cc)
  cc:close()
  return json.decode(b)
end
local before = state().input.mapAt
W.mapPressed = true
frames(1)
local s1 = state()
check(s1.input.mapAt > before, "pressing the Map button moves input.mapAt")
frames(30)
check(state().input.mapAt == s1.input.mapAt, "holding it down counts once")
W.mapPressed = false
frames(1)
W.mapController, W.mapPressed = 3, true -- a pad the player's index doesn't name
frames(1)
W.mapPressed, W.mapController = false, nil
frames(1)
local s2 = state()
check(s2.input.mapAt > s1.input.mapAt, "a press on any controller counts")
check(s2.pauseMenu == false, "not paused: no pause menu")
W.paused = true
frames(20)
check(state().pauseMenu == false, "a short pause (a room change) isn't the pause menu")
frames(40)
check(state().pauseMenu == true, "paused for 3/4 of a second: counted as the pause menu")
W.paused = false
frames(2)
check(state().pauseMenu == false, "unpaused again")

print("the encoder")
W.playerName = 'Is"aac\\ \n\1'
local cc = assert(socket.connect("127.0.0.1", 8089))
cc:send("GET /state HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
local _, text = readReply(cc)
cc:close()
check(text and text:find('"name":"Is\\"aac\\\\ \\n\\u0001"', 1, true),
  "quotes, backslashes, new lines and control characters are escaped")
W.playerName = nil

fire(ModCallbacks.MC_PRE_GAME_EXIT, false)
print(failures == 0 and "ALL PASSED" or (failures .. " FAILED"))
os.exit(failures == 0 and 0 or 1)
