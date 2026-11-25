"""A light shape check for protocol v1 states (shared by the mod and mock tests)."""


def _is_list(v):
    return isinstance(v, list) or v == {}  # Lua may send an empty list as {}


def validate(s):
    """Return a list of problems with a /state payload (empty when it's fine)."""
    errs = []

    def need(obj, key, types, where):
        if key not in obj:
            errs.append(f"{where}.{key} missing")
        elif not isinstance(obj[key], types):
            errs.append(f"{where}.{key} is {type(obj[key]).__name__}")

    need(s, "protocol", int, "state")
    need(s, "inRun", bool, "state")
    need(s, "paused", bool, "state")
    need(s, "strings", int, "state")  # how many of the game's words the app has sent
    if not s.get("inRun"):
        return errs
    for key in ("run", "floor", "map", "room", "death"):
        need(s, key, dict, "state")
    if errs:
        return errs
    for k in ("difficulty", "challenge", "timeFrames"):
        need(s["run"], k, int, "run")
    for k in ("stage", "stageType", "curses"):
        need(s["floor"], k, int, "floor")
    need(s["map"], "hidden", bool, "map")
    if not _is_list(s["map"].get("rooms")):
        errs.append("map.rooms not a list")
    for r in s["map"].get("rooms") or []:
        for k in ("index", "shape", "type", "display"):
            need(r, k, int, "map.rooms[]")
    for ped in s["room"].get("pedestals") or []:
        need(ped, "group", int, "pedestal")
        if ped.get("blind"):
            if "id" in ped or "name" in ped:
                errs.append("blind pedestal leaks its item")
        else:
            need(ped, "id", int, "pedestal")
    players = s.get("players")
    if not isinstance(players, list) or not players:
        errs.append("players missing")
        return errs
    for p in players:
        for k in ("index", "type", "lives"):
            need(p, k, int, "player")
        need(p, "health", dict, "player")
        if p.get("health", {}).get("hidden"):
            if len(p["health"]) != 1 or p.get("shields") not in ([], {}):
                errs.append("hidden health leaks values")
        else:
            for k in ("maxHearts", "hearts", "soulHearts", "blackMask", "boneMask"):
                need(p["health"], k, int, "health")
        for k in ("pickups", "stats"):
            need(p, k, dict, "player")
        for k in ("shields", "actives", "trinkets", "pockets", "items", "forms"):
            if not _is_list(p.get(k)):
                errs.append(f"player.{k} not a list")
        for pk in p.get("pockets") or []:
            if pk.get("kind") == "pill" and not pk.get("identified") and "effect" in pk:
                errs.append("unidentified pill leaks its effect")
        if "bag" in p:  # only while the player holds the Bag of Crafting
            bag = p["bag"]
            if not isinstance(bag, dict):
                errs.append("player.bag not an object")
            else:
                need(bag, "sure", bool, "bag")
                if not _is_list(bag.get("items")):
                    errs.append("bag.items not a list")
                else:
                    items = bag["items"] or []
                    if len(items) > 8:
                        errs.append("bag holds more than 8")
                    if any(not isinstance(c, int) or not 1 <= c <= 29 for c in items):
                        errs.append("bag has an unknown component")
    return errs
