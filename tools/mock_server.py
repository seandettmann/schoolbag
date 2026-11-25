#!/usr/bin/env python3
"""Fake Schoolbag mod: serves made-up game state so the app can be built and
tested without the game running.

    python3 tools/mock_server.py                 # normal run on 127.0.0.1:8089
    python3 tools/mock_server.py --scenario dead
    python3 tools/mock_server.py --host 0.0.0.0  # reachable from a phone/Thor on Wi-Fi

Switch scenarios while it runs: GET /scenario/<name>. GET /scenarios lists them.
It speaks the same protocol as the real mod (docs/protocol.md). Only the
standard library is used.
"""
import argparse
import copy
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PROTOCOL = 1
NAMES = {
    1: "#THE_SAD_ONION_NAME", 2: "#THE_INNER_EYE_NAME", 3: "#SPOON_BENDER_NAME",
    4: "#CRICKETS_HEAD_NAME", 5: "#MY_REFLECTION_NAME", 7: "#BLOOD_OF_THE_MARTYR_NAME",
    8: "#BROTHER_BOBBY_NAME", 10: "#HALO_OF_FLIES_NAME", 11: "#1UP_NAME",
    12: "#MAGIC_MUSHROOM_NAME", 13: "#THE_VIRUS_NAME", 14: "#ROID_RAGE_NAME",
    15: "#HEART_NAME", 16: "#RAW_LIVER_NAME", 17: "#SKELETON_KEY_NAME",
    18: "#A_DOLLAR_NAME", 19: "#BOOM_NAME", 20: "#TRANSCENDENCE_NAME",
    21: "#THE_COMPASS_NAME", 22: "#LUNCH_NAME", 27: "#WOODEN_SPOON_NAME",
    45: "#YUM_HEART_NAME", 48: "#CUPIDS_ARROW_NAME", 105: "#THE_D6_NAME",
    114: "#MOMS_KNIFE_NAME", 118: "#BRIMSTONE_NAME", 212: "#GUPPYS_COLLAR_NAME",
    313: "#HOLY_MANTLE_NAME",
}
TRINKETS = {1: "#SWALLOWED_PENNY_NAME", 19: "#PAPER_CLIP_NAME", 121: "#WOODEN_CROSS_NAME"}
RUN_ITEMS = [1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 27]

# Basement map from the mock-ups (grid width 13; index = y * 13 + x)
MAP_LAYOUT = ["...B...", "...v...", ".$vvv..", "..?vT..", "...v..."]
MAP_TYPES = {"v": 1, "$": 2, "B": 5, "T": 4, "?": 7}


def item(i):
    return {"id": i, "name": NAMES.get(i, f"#ITEM_{i}_NAME")}


def base_map():
    rooms, current = [], None
    for y, row in enumerate(MAP_LAYOUT):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            index = (y + 2) * 13 + (x + 3)
            visited = ch in "v$BT"
            rooms.append({
                "index": index, "shape": 1, "type": MAP_TYPES[ch], "visited": visited,
                "clear": visited, "display": 5 if ch in "$B?T" else 1,
            })
            if ch == "T":
                current = index
    return {"hidden": False, "width": 13, "current": current, "rooms": rooms}


def base_player():
    return {
        "index": 0, "type": 0, "name": "Isaac",
        "health": {
            "hidden": False, "maxHearts": 12, "hearts": 9, "soulHearts": 6, "blackMask": 4,
            "boneHearts": 0, "boneMask": 0, "goldenHearts": 0, "eternalHearts": 0,
            "rottenHearts": 0, "brokenHearts": 0, "heartLimit": 24,
        },
        "lives": 0, "livesMaybe": False, "shields": [],
        "pickups": {"coins": 7, "bombs": 2, "keys": 1, "goldenBomb": False, "goldenKey": False, "gigaBombs": 0},
        "stats": {"speed": 1.0, "tears": 2.73, "damage": 5.25, "range": 6.5, "shotSpeed": 1.0, "luck": 1.0},
        "actives": [
            {"slot": 0, "id": 105, "name": NAMES[105], "charge": 4, "maxCharge": 6, "chargeType": 0, "battery": 0},
            {"slot": 1, "id": 45, "name": NAMES[45], "charge": 2, "maxCharge": 4, "chargeType": 0, "battery": 0},
        ],
        "trinkets": [
            {"slot": 0, "id": 1, "golden": False, "name": TRINKETS[1]},
            {"slot": 1, "id": 19, "golden": False, "name": TRINKETS[19]},
        ],
        "pockets": [{"slot": 0, "kind": "card", "id": 1, "name": "#THE_FOOL_NAME"}],
        "items": [item(i) for i in RUN_ITEMS],
        "forms": [0] * 16,
    }


def base_state():
    return {
        "protocol": PROTOCOL,
        "mod": {"name": "Schoolbag", "version": "mock"},
        "inRun": True,
        "paused": False,
        "run": {"seed": "ABCD EFGH", "difficulty": 0, "challenge": 0, "victoryLap": 0,
                "timeFrames": 37260, "bossRushDone": False, "blueWombDone": False},
        "floor": {"stage": 2, "stageType": 0, "altPath": False, "name": "Basement II",
                  "curses": 1, "bossCleared": False},
        "map": base_map(),
        "room": {"index": 58, "type": 4, "pedestals": [
            {"id": 48, "name": NAMES[48], "group": 0},
            {"id": 114, "name": NAMES[114], "group": 0},
            {"id": 118, "name": NAMES[118], "group": 0},
        ]},
        "players": [base_player()],
        "chances": {"devil": 0.333, "planetarium": 0.01},
        "death": {"gameOver": False},
    }


def scenario(name):
    """Return a fresh state for a scenario. Each matches a board in the mock-ups."""
    s = base_state()
    p = s["players"][0]
    if name == "normal":
        pass
    elif name == "notinrun":
        return {"protocol": PROTOCOL, "mod": {"name": "Schoolbag", "version": "mock"},
                "inRun": False, "paused": False}
    elif name == "paused":
        s["paused"] = True
    elif name == "lost":
        s["floor"]["curses"] = 4
        s["map"] = {"hidden": True, "hiddenReason": "lost", "width": 13, "current": 58, "rooms": []}
    elif name == "unknown":
        s["floor"]["curses"] = 8
        p["health"] = {"hidden": True}
        p["lives"] = 1
    elif name == "blind":
        s["floor"]["curses"] = 64
        s["room"]["pedestals"] = [{"blind": True, "group": 0} for _ in range(3)]
    elif name == "loading":
        s["transition"] = "trapdoor"
    elif name == "dead":
        p["health"].update({"hearts": 0, "soulHearts": 0, "blackMask": 0})
        s["run"]["ended"] = "loss"
        s["death"] = {"gameOver": True, "killer": {"type": 10, "variant": 1, "subtype": 0, "flags": 0}}
    elif name == "active0":
        p["actives"] = []
    elif name == "active1":
        p["actives"] = p["actives"][:1]
    elif name == "greed":
        s["run"]["difficulty"] = 3
        s["floor"].update({"stage": 2, "name": "Caves", "curses": 0, "greedWave": 6, "greedWaves": 11})
    elif name == "challenge":
        s["run"]["challenge"] = 1  # Pitch Black
        s["floor"]["curses"] = 1
    elif name == "shields":
        p["shields"] = ["holyMantle", "woodenCross"]
        p["lives"], p["livesMaybe"] = 1, True
        p["items"].append(item(313))
        p["items"].append(item(212))
        p["trinkets"][1] = {"slot": 1, "id": 121, "golden": False, "name": TRINKETS[121]}
    elif name == "shieldsall":
        p["shields"] = ["holyCard", "holyMantle", "blanket", "woodenCross"]
        p["lives"] = 1
    elif name == "bossrush":
        s["run"]["timeFrames"] = (16 * 60 + 5) * 30
        s["floor"].update({"stage": 6, "name": "Depths II"})
        p["lives"] = 1
        p["pickups"]["coins"] = 157
    elif name == "hush":
        s["run"]["timeFrames"] = (29 * 60 + 12) * 30
        s["run"]["bossRushDone"] = True
        s["floor"].update({"stage": 8, "name": "Womb II", "curses": 2})
    elif name == "devil":
        s["room"] = {"index": -1, "type": 14, "pedestals": [
            {"id": 48, "name": NAMES[48], "group": 0},
            {"id": 114, "name": NAMES[114], "group": 0},
            {"id": 118, "name": NAMES[118], "group": 0},
            {"blind": True, "group": 0},
        ]}
    elif name == "emptyroom":
        s["room"]["pedestals"] = []
    elif name == "newitem":
        # an item the app has no art for yet (added by a game update or a mod)
        p["items"].append({"id": 999, "name": "Mystery Item"})
        s["room"]["pedestals"].append({"id": 999, "name": "Mystery Item", "group": 0})
    elif name == "pills":
        p["pockets"] = [
            {"slot": 0, "kind": "pill", "color": 3, "horse": False, "identified": False},
            {"slot": 1, "kind": "pill", "color": 5, "horse": True, "identified": True, "effect": 7,
             "name": "#FULL_HEALTH_NAME"},
        ]
    elif name == "tcain":
        # Tainted Cain: the Bag of Crafting in the pocket and what the mod saw go in
        p["type"], p["name"] = 23, "Cain"
        p["actives"].append({"slot": 2, "id": 710, "name": "#BAG_OF_CRAFTING_NAME", "charge": 0,
                             "maxCharge": 0, "chargeType": 0, "battery": 0})
        p["bag"] = {"items": [8, 8, 1, 12, 15, 2, 22, 21], "sure": True}
    elif name == "tbluebaby":
        # Tainted ???: 2 poops to place; the queue shows what comes next (PoopSpellType)
        p["type"], p["name"] = 25, "???"
        p["health"].update({"maxHearts": 0, "hearts": 0, "soulHearts": 6, "blackMask": 0})
        p["pickups"]["poopMana"] = 2
        p["poops"] = {"max": 9, "queue": [4, 1, 3, 1, 7, 2]}
    elif name == "forgotten":
        # The Forgotten in control (bone hearts); The Soul holds the soul hearts
        p["type"], p["name"] = 16, "The Forgotten"
        p["health"].update({"maxHearts": 0, "hearts": 5, "soulHearts": 0, "blackMask": 0,
                            "boneHearts": 4, "boneMask": 15})
        p["otherForm"] = {"type": 17, "health": {
            "hidden": False, "maxHearts": 0, "hearts": 0, "soulHearts": 5, "blackMask": 0,
            "boneHearts": 0, "boneMask": 0, "goldenHearts": 0, "eternalHearts": 0,
            "rottenHearts": 0, "brokenHearts": 0, "heartLimit": 12}}
    else:
        raise KeyError(name)
    return s


SCENARIOS = ["normal", "notinrun", "offline", "paused", "lost", "unknown", "blind", "loading",
             "dead", "active0", "active1", "greed", "challenge", "shields", "shieldsall",
             "bossrush", "hush", "devil", "emptyroom", "newitem", "pills", "tcain",
             "tbluebaby", "forgotten"]


class Handler(BaseHTTPRequestHandler):
    current = "normal"
    started = time.time()
    strings = {}  # what the app sent with POST /strings

    def log_message(self, fmt, *args):  # keep the console quiet
        pass

    def send(self, status, body, ctype="application/json"):
        data = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path.startswith("/scenario/"):
            name = path.split("/", 2)[2]
            if name not in SCENARIOS:
                return self.send(404, f"unknown scenario; try one of: {', '.join(SCENARIOS)}", "text/plain")
            Handler.current = name
            Handler.started = time.time()
            print(f"scenario: {name}")
            return self.send(200, name, "text/plain")
        if path == "/scenarios":
            return self.send(200, json.dumps({"current": Handler.current, "all": SCENARIOS}))
        if Handler.current == "offline":
            # behave like the game on its title screen: no answer at all
            self.close_connection = True
            return
        if path == "/health":
            return self.send(200, "ok", "text/plain")
        if path != "/state":
            return self.send(404, "not found", "text/plain")
        state = scenario(Handler.current)
        state["strings"] = len(Handler.strings)
        # like the mod: reading hides the game's HUD unless the app asks ?hud=keep
        state["hudHidden"] = bool(state.get("inRun")) and "hud=keep" not in self.path
        if state.get("inRun") and state.get("run") and not state.get("paused"):
            # the run clock keeps ticking (30 frames per second)
            state["run"]["timeFrames"] += int((time.time() - Handler.started) * 30)
        self.send(200, json.dumps(state))


    def do_POST(self):
        # like the mod: the app sends the game's words for the banners
        path = self.path.split("?")[0]
        if Handler.current == "offline":
            self.close_connection = True
            return
        if path != "/strings":
            return self.send(405, "GET, or POST /strings", "text/plain")
        length = int(self.headers.get("Content-Length") or 0)
        try:
            data = json.loads(self.rfile.read(length) or b"null")
        except ValueError:
            data = None
        if not isinstance(data, dict):
            return self.send(400, "expected a JSON object of strings", "text/plain")
        got = {k: v for k, v in data.items() if isinstance(k, str) and isinstance(v, str)}
        Handler.strings.update(got)
        print(f"strings: got {len(got)}, {len(Handler.strings)} in all")
        return self.send(200, f"ok {len(got)}", "text/plain")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=8089)
    ap.add_argument("--scenario", default="normal", choices=SCENARIOS)
    a = ap.parse_args()
    Handler.current = a.scenario
    srv = ThreadingHTTPServer((a.host, a.port), Handler)
    print(f"Fake Schoolbag mod on http://{a.host}:{a.port}/state (scenario: {a.scenario})")
    print("Switch with /scenario/<name>; list with /scenarios")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
