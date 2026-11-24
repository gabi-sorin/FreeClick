"""Static checks for the Android layer (things unit tests on the JVM cannot run).

Usage: python3 tools/check.py   (from the project root; exit code 1 on problems)
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "src", "com", "freeclick", "app")
A = "{http://schemas.android.com/apk/res/android}"
problems = []
passed = []


def ok(cond, what):
    (passed if cond else problems).append(what)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def statements(src):
    """Small Java statement splitter: splits at ';' and at block braces outside parentheses
    (anonymous classes inside a call stay part of that call's statement)."""
    out, cur, depth, i, in_str = [], [], 0, 0, False
    while i < len(src):
        ch = src[i]
        if in_str:
            cur.append(ch)
            if ch == "\\":
                cur.append(src[i + 1]); i += 2; continue
            if ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True; cur.append(ch)
        elif ch in "([":
            depth += 1; cur.append(ch)
        elif ch in ")]":
            depth -= 1; cur.append(ch)
        elif depth == 0 and ch in ";{}":
            cur.append(ch)
            out.append("".join(cur)); cur = []
        else:
            cur.append(ch)
        i += 1
    out.append("".join(cur))
    return out


sources = {f[:-5]: read(os.path.join(APP, f)) for f in os.listdir(APP) if f.endswith(".java")}
manifest = ET.parse(os.path.join(ROOT, "AndroidManifest.xml")).getroot()
app = manifest.find("application")

# 1. every component class is declared, every declared class exists
declared = {}
for tag in ("activity", "service", "receiver", "provider"):
    for el in app.findall(tag):
        declared[el.get(A + "name").split(".")[-1]] = (tag, el)
bases = {"Activity": "activity", "AccessibilityService": "service", "Service": "service",
         "TileService": "service", "BroadcastReceiver": "receiver", "ContentProvider": "provider"}
for name, src in sources.items():
    m = re.search(r"public final class \w+ extends (\w+)", src)
    if m and m.group(1) in bases:
        ok(name in declared and declared[name][0] == bases[m.group(1)],
           "%s (%s) declared in the manifest" % (name, m.group(1)))
for name in list(declared):
    outer, _, inner = name.partition("$")
    ok(outer in sources and (not inner or re.search(r"class %s extends" % inner, sources[outer]) is not None),
       "manifest component %s exists in code" % name)
    if inner:
        declared[inner] = declared[name]  # nested classes are referenced as Inner.class


# 2. intents target declared components
for name, src in sources.items():
    for target in set(re.findall(r"new Intent\([^;]*?,\s*(\w+)\.class\)", src)):
        ok(target in declared, "%s: Intent target %s is declared" % (name, target))

# 3. exported components are exactly the expected, protected ones
# ImportActivity (2.3): "Share → FreeClick"; it only shows what a code brings and imports after the user confirms
expected_exported = {"MainActivity", "ClickService", "QuickTileService", "TriggerReceiver", "ShortcutRunActivity", "ImportActivity"}
exported = {n for n, (t, el) in declared.items() if el.get(A + "exported") == "true" and "$" not in n}
ok(exported == expected_exported, "exported components = %s" % sorted(exported))
for n, (t, el) in declared.items():
    ok(el.get(A + "exported") in ("true", "false"), "%s has an explicit android:exported" % n)
ok(declared["ClickService"][1].get(A + "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE",
   "accessibility service protected by BIND_ACCESSIBILITY_SERVICE")
ok(declared["QuickTileService"][1].get(A + "permission") == "android.permission.BIND_QUICK_SETTINGS_TILE",
   "tile protected by BIND_QUICK_SETTINGS_TILE")
ok("allowExternalTriggers()" in sources["TriggerReceiver"], "Tasker receiver gated by the setting")
ok("shortcutToken()" in sources["ShortcutRunActivity"] and "MessageDigest.isEqual" in sources["ShortcutRunActivity"],
   "shortcut activity checks the secret token (constant time)")
ok(declared["FileShareProvider"][1].get(A + "exported") == "false", "file provider not exported")

# 4. PendingIntents are immutable
for name, src in sources.items():
    for st in statements(src):
        if re.search(r"PendingIntent\.get(Activity|Service|Broadcast)\(", st):
            ok("FLAG_IMMUTABLE" in st, "%s: PendingIntent is FLAG_IMMUTABLE" % name)

# 5. activities started from non-activity contexts use NEW_TASK
def starts_new_task(st, src):
    if "FLAG_ACTIVITY_NEW_TASK" in st:
        return True
    m = re.search(r"startActivity\((\w+)\)", st)
    if not m:
        return False
    var = m.group(1)
    # the flag may be set where the Intent variable is built or later on it
    return bool(re.search(r"\b%s\b[^;]*FLAG_ACTIVITY_NEW_TASK" % re.escape(var), src))


non_activity = [n for n, s2 in sources.items()
                if re.search(r"extends (AccessibilityService|Service|BroadcastReceiver|TileService)\b", s2)]
for name in non_activity + ["VideoStore"]:
    for st in statements(sources[name]):
        if re.search(r"\bstartActivity\(", st) and "startActivityAndCollapse" not in st:
            ok(starts_new_task(st, sources[name]),
               "%s: startActivity uses NEW_TASK: %s" % (name, " ".join(st.split())[:80]))

# 6. every Toast is shown
for name, src in sources.items():
    for st in statements(src):
        if "Toast.makeText(" in st:
            ok(".show()" in st, "%s: Toast is shown" % name)

# 7. dialogs from the service are overlay dialogs (never plain .show())
svc = sources["ClickService"]
for st in statements(svc):
    if "AlertDialog.Builder" in st:
        ok(".create()" in st and ".show()" not in st, "ClickService: dialog shown as overlay")
ok("isAttachedToWindow()" not in "".join(s for n, s in sources.items() if n != "Overlays"),
   "no isAttachedToWindow() state checks (race: true only on the next frame)")

# 8. resources referenced from code/xml exist
strings = set(re.findall(r'<string name="(\w+)"', read(os.path.join(ROOT, "res", "values", "strings.xml"))))
styles_xml = read(os.path.join(ROOT, "res", "values", "styles.xml"))
styles = {n.replace(".", "_") for n in re.findall(r'<style name="([\w.]+)"', styles_xml)}
all_text = read(os.path.join(ROOT, "AndroidManifest.xml")) + "".join(
    read(os.path.join(dp, f)) for dp, _, fs in os.walk(os.path.join(ROOT, "res")) for f in fs if f.endswith(".xml"))
for s in set(re.findall(r"@string/(\w+)", all_text)) | set(re.findall(r"R\.string\.(\w+)", "".join(sources.values()))):
    ok(s in strings, "string resource %s exists" % s)
for s in set(re.findall(r"R\.style\.(\w+)", "".join(sources.values()))):
    ok(s in styles, "style %s exists" % s)

# 9. accessibility config asks only for what is needed
cfg = read(os.path.join(ROOT, "res", "xml", "accessibility_service_config.xml"))
ok('canRetrieveWindowContent="false"' in cfg, "service does not read window content")
ok('canPerformGestures="true"' in cfg and 'canTakeScreenshot="true"' in cfg, "gestures + screenshots declared")

# 10. API levels: newer calls only inside version checks (reviewed list of files)
guarded_files = {"App", "CaptureRequestActivity", "MainActivity", "Overlays", "QuickTileService", "ScheduleReceiver",
                 "Schedules", "SettingsActivity", "ShortcutRunActivity", "VideoRecorderService", "VideoStore",
                 "ScreenSampler", "ControlNotification", "ScriptSession", "AfkGuard", "SessionService"}
api24 = os.environ.get("API24_ERRORS_FILE")
if api24 and os.path.exists(api24):
    files = {os.path.basename(l.split(":")[0])[:-5] for l in read(api24).splitlines() if l.strip()}
    ok(files <= guarded_files, "newer-than-Android-7 APIs only in reviewed, version-checked files: %s"
       % sorted(files - guarded_files))

# 11. steps can live inside blocks: nobody iterates only the top-level list (ActionTree knows the tree)
core_all = {}
for dp, _, fs in os.walk(os.path.join(ROOT, "src")):
    for f in fs:
        if f.endswith(".java"):
            core_all[os.path.join(dp, f)] = read(os.path.join(dp, f))
for path, src in core_all.items():
    name = os.path.basename(path)
    for n, line in enumerate(src.splitlines(), 1):
        if "tree-ok" in line or name == "ActionTree.java":
            continue
        m_for = re.search(r"for\s*\(\s*[\w.]*Action\s+\w+\s*:\s*([\w().]*\.actions)\s*\)", line)
        if m_for and "ActionTree" not in m_for.group(1):
            ok(False, "%s:%d loops over .actions directly (use ActionTree)" % (name, n))
        elif re.search(r"\.actions\.size\(\)", line) and name != "EditorActivity.java":
            ok(False, "%s:%d counts .actions.size() (use ActionTree.count)" % (name, n))
ok(True, "no direct loops over the top-level step list")

# 12. screenshots are processed off the main thread
sampler = sources.get("ScreenSampler", "")
ok("HandlerThread" in sampler and "worker().post" in sampler, "screenshot callbacks run on a background thread")
ok("main.post(r)" not in sampler, "no screenshot executor on the main thread")

# 13. the notification controls receiver is private
man = read(os.path.join(ROOT, "AndroidManifest.xml"))
mrec = re.search(r'android:name="com\.freeclick\.app\.ControlNotification\$Receiver"\s+android:exported="(\w+)"', man)
ok(mrec is not None and mrec.group(1) == "false", "notification buttons receiver is not exported")

print("static checks: %d passed, %d problems" % (len(passed), len(problems)))
for p in problems:
    print("  PROBLEM: " + p)
sys.exit(1 if problems else 0)
