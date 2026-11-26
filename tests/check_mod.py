"""End-to-end test: run the mod against the stubbed game API and check /state.

Usage (from the repo root): python3 tests/check_mod.py
Needs lua5.3 and lua-socket.
"""
import json, os, subprocess, sys, time, urllib.request, urllib.error
sys.path.insert(0, os.path.dirname(__file__))
from schema import validate

BASE = "http://127.0.0.1:8089"
CTL, ACK = "/tmp/schoolbag_ctl", "/tmp/schoolbag_ack"
failures = []


def check(cond, msg):
    print(("  ok   " if cond else "  FAIL ") + msg)
    if not cond:
        failures.append(msg)


def step(cmd, settle=0.45):
    if os.path.exists(ACK):
        os.remove(ACK)
    with open(CTL, "w") as f:
        f.write(cmd + "\n")
    for _ in range(200):
        if os.path.exists(ACK):
            break
        time.sleep(0.01)
    time.sleep(settle)  # let a few inventory scans run


def get(path):
    try:
        with urllib.request.urlopen(BASE + path, timeout=3) as r:
            return r.status, r.headers, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read().decode()


def state():
    status, headers, body = get("/state")
    assert status == 200, (status, body)
    s = json.loads(body)
    errs = validate(s)
    check(not errs, "state matches the protocol shape" + (f": {errs}" if errs else ""))
    return s, headers


lua = subprocess.Popen(["lua5.3", "tests/run_mod.lua"], stderr=subprocess.PIPE, text=True)
try:
    time.sleep(0.5)
    print("before a run")
    s, h = state()
    check(s["protocol"] == 1 and s["mod"]["name"] == "Schoolbag", "protocol and mod name")
    check(s["inRun"] is False and "players" not in s, "no run data before a run starts")
    check(h.get("Access-Control-Allow-Origin") == "*", "CORS header")
    check(get("/health")[0] == 200 and get("/health")[2] == "ok", "/health answers ok")
    check(get("/nope")[0] == 404, "unknown path is 404")

    print("normal run")
    step("start"); step("add4"); step("add1"); step("normal")
    s, _ = state()
    p = s["players"][0]
    order = [it["id"] for it in p["items"]]
    check(order[:4] == [45, 105, 4, 1], f"pickup order kept: {order}")
    check(p["items"][2]["name"] == "#CRICKETS_HEAD_NAME", "item names are the game's keys")
    check(p["shields"] == ["holyMantle", "woodenCross"], f"shields, Wooden Cross breaks first: {p['shields']}")
    check(p["lives"] == 1 and p["livesMaybe"] is True, "x1? lives with Guppy's Collar")
    check(p["health"]["hidden"] is False and p["health"]["hearts"] == 9, "health sent")
    check(p["pickups"]["coins"] == 157 and p["pickups"]["goldenKey"] is True, "pickups")
    check("poopMana" not in p["pickups"], "missing API calls are left out, not errors")
    check(p["stats"]["tears"] == 2.73 and p["stats"]["range"] == 6.5, f"stats converted: {p['stats']}")
    check([a["slot"] for a in p["actives"]] == [0, 1] and p["actives"][0]["maxCharge"] == 6, "both actives with charge")
    gold = [t for t in p["trinkets"] if t["slot"] == 1][0]
    check(gold["id"] == 2 and gold["golden"] is True, "golden trinket id without the golden bit")
    pill = [x for x in p["pockets"] if x["kind"] == "pill" and x["slot"] == 1][0]
    check(pill["color"] == 3 and pill["horse"] is True and pill["identified"] is False and "effect" not in pill,
          "unidentified horse pill doesn't spoil its effect")
    known = [x for x in p["pockets"] if x["kind"] == "pill" and x["slot"] == 2][0]
    check(known["identified"] is True and known["effect"] == 15, "identified pill sends its effect")
    check(p["pockets"][0] == {"slot": 0, "kind": "card", "id": 1, "name": "#THE_FOOL_NAME"}, "card")
    check(p["pills"] == [{"color": 5, "effect": 15, "name": "#PILL_15"}],
          f"only identified pill colours are listed, with effect and name: {p['pills']}")
    check(len(p["forms"]) == 16 and p["forms"][1] == 2, "transformation progress (enum missing, fallback used)")
    peds = s["room"]["pedestals"]
    check([x["id"] for x in peds] == [4, 118] and all("price" not in x for x in peds),
          f"pedestals left to right, no prices: {peds}")
    check(s["map"]["hidden"] is False and sorted(r["index"] for r in s["map"]["rooms"]) == [32, 44, 45],
          "map only has rooms the game shows (no spoilers)")
    check(s["floor"]["bossCleared"] is False and "name" not in s["floor"], "floor info; failing GetName left out")
    check(s["run"]["seed"] == "ABCD EFGH" and s["run"]["bossRushDone"] is True, "run info")
    check(s["chances"]["devil"] == 0.333, "devil chance")

    print("cursed floor")
    step("cursed")
    s, _ = state()
    p = s["players"][0]
    check(p["health"] == {"hidden": True} and p["shields"] in ([], {}), "Curse of the Unknown hides health and shields")
    check(s["map"]["hidden"] is True and s["map"]["hiddenReason"] == "lost", "Curse of the Lost hides the map")
    check(all(x.get("blind") and "id" not in x for x in s["room"]["pedestals"]), "Curse of the Blind hides pedestals")
    step("uncursed")

    print("death")
    step("dead")
    s, _ = state()
    check(s["death"]["gameOver"] is True and s["death"]["killer"]["type"] == 10
          and s["death"]["killer"]["variant"] == 1, f"killer is whatever fired the shot: {s['death']}")
    check(s["run"]["ended"] == "loss" and s["transition"] == "trapdoor", "run ended, transition seen")

    print("exit and continue")
    step("exit")
    s, _ = state()
    check(s["inRun"] is False, "not in a run after exiting")
    step("continue")
    s, _ = state()
    order2 = [it["id"] for it in s["players"][0]["items"]]
    check(order2 == order, f"order survives continuing a run: {order2}")
    step("reorder")
    s, _ = state()
    order3 = [it["id"] for it in s["players"][0]["items"]]
    check(4 not in order3 and order3.count(1) == 2 and order3[-1] == 1, f"lost and duplicate items: {order3}")

    print("Bag of Crafting")
    check("bag" not in s["players"][0], "no bag without the Bag of Crafting")
    step("tcain")
    step("scooped")
    s, _ = state()
    bag = s["players"][0].get("bag")
    check(bag == {"items": [8], "sure": True}, f"the penny the bag scooped is in it: {bag}")

    print("Tainted ??? and The Forgotten")
    check("poops" not in s["players"][0] and "otherForm" not in s["players"][0], "no poops or other form for other characters")
    step("bluebaby")
    s, _ = state()
    poops = s["players"][0].get("poops")
    check(poops == {"max": 9, "queue": [4, 1, 3]}, f"Tainted ???'s poop queue, next first: {poops}")
    step("forgotten")
    s, _ = state()
    other = s["players"][0].get("otherForm")
    check(other and other["type"] == 17 and other["health"]["soulHearts"] == 5 and other["health"]["maxHearts"] == 0,
          f"The Forgotten's other form (The Soul) and its hearts: {other}")
    step("cursed")
    s, _ = state()
    check("otherForm" not in s["players"][0], "Curse of the Unknown hides the other form's hearts too")
    step("uncursed")
    step("plain")

    print("one person, co-op and twins")
    check(s["humans"] == 1 and s["players"][0].get("controller") == 0, "solo: 1 person, controller sent")
    check("twin" not in s["players"][0] and "ghost" not in s["players"][0], "solo: no twin, not a ghost")
    step("jacobesau")
    s, _ = state()
    j, e = s["players"][0], s["players"][1]
    check(len(s["players"]) == 2 and s["humans"] == 1, f"Jacob & Esau: two players, one person: {s['humans']}")
    check(j["twin"] == {"index": 1, "main": True} and e["twin"] == {"index": 0, "main": False},
          f"twins point at each other, Jacob first: {j.get('twin')} {e.get('twin')}")
    check(e["type"] == 20 and e["name"] == "Esau", "Esau has his own type and name")
    check(s["hudHidden"] is True, "Jacob & Esau: the HUD is still hidden")
    step("coop")
    s, _ = state()
    p2 = s["players"][1]
    check(s["humans"] == 2 and p2["controller"] == 1, f"co-op: 2 people: {s['humans']}")
    check(p2.get("ghost") is True and p2.get("baby") is True, "a dead co-op player is sent as a ghost")
    check(s["hudHidden"] is False, "co-op: the mod leaves the game's HUD on")
    step("solo")

    print("pill tracker")
    step("pills")
    s, _ = state()
    got = s["players"][0]["pills"]
    check([x["color"] for x in got] == [1, 5, 13], f"identified colours in colour order, gold pill left out: {got}")
    check(all(x["effect"] == x["color"] + 10 for x in got), "each colour's effect for this player")

    print("mirror world")
    check(s["map"].get("mirrored") is False, "map.mirrored is false outside the mirror world")
    step("mirror")
    s, _ = state()
    check(s["map"].get("mirrored") is True, "map.mirrored is true in the mirror world")
    step("unmirror")

    print("the /watch test page")
    status, headers, body = get("/watch")
    check(status == 200 and "text/html" in headers.get("Content-Type", "") and "fetch(\"/state\"" in body,
          "/watch is an HTML page that reads /state")

    print("reading without hiding the HUD")
    status, _, body = get("/state?hud=keep")
    check(status == 200 and json.loads(body)["inRun"] is True, "/state?hud=keep answers like /state")
    s, _ = state()
    check(isinstance(s.get("hudHidden"), bool), "state says whether the mod is hiding the HUD")

    step("quit", settle=0.1)
finally:
    time.sleep(0.2)
    if lua.poll() is None:
        lua.kill()
    err = lua.stderr.read()
    if err.strip():
        print("mod log:\n" + err)

print(f"\n{'ALL PASSED' if not failures else str(len(failures)) + ' FAILED'}")
sys.exit(1 if failures else 0)
