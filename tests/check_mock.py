"""Run the fake mod through every scenario and check each state's shape."""
import json, subprocess, sys, time, urllib.request, urllib.error
sys.path.insert(0, "tests")
from schema import validate

BASE = "http://127.0.0.1:8099"
srv = subprocess.Popen([sys.executable, "tools/mock_server.py", "--port", "8099"], stdout=subprocess.DEVNULL)
bad = 0
try:
    time.sleep(0.6)
    names = json.load(urllib.request.urlopen(BASE + "/scenarios"))["all"]
    for name in names:
        urllib.request.urlopen(f"{BASE}/scenario/{name}").read()
        if name == "offline":
            try:
                urllib.request.urlopen(BASE + "/state", timeout=2).read()
                print(f"  FAIL {name}: answered"); bad += 1
            except Exception:
                print(f"  ok   {name}: no answer, like the title screen")
            continue
        s = json.load(urllib.request.urlopen(BASE + "/state"))
        errs = validate(s)
        print(("  ok   " if not errs else "  FAIL ") + name + ("" if not errs else f": {errs}"))
        bad += bool(errs)
    # the app sends the game's words for the banners; the count shows up in /state
    urllib.request.urlopen(f"{BASE}/scenario/normal").read()
    req = urllib.request.Request(BASE + "/strings", data=json.dumps({"THE_SAD_ONION_NAME": "The Sad Onion"}).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    ok = urllib.request.urlopen(req).read() == b"ok 1" and json.load(urllib.request.urlopen(BASE + "/state"))["strings"] == 1
    print(("  ok   " if ok else "  FAIL ") + "POST /strings")
    bad += not ok
finally:
    srv.terminate()
print("ALL PASSED" if not bad else f"{bad} FAILED")
sys.exit(1 if bad else 0)
