package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts macros to/from JSON. Single macros are stored one per file; exports
 * use a "bundle" that also carries every sub-macro referenced in combination
 * mode, so a shared file is self-contained.
 */
public final class MacroCodec {
    private MacroCodec() {}

    /**
     * File format version. Raised by every release that adds step types or fields an older
     * release could lose (1 = 1.0-1.5, 2 = 1.6: open-app step, random pause, 3 = 1.7: image step,
     * 4 = 1.8: print / set variable / read steps, 5 = 1.9: scripts).
     * Files from a newer version are refused instead of being half-read and saved back broken.
     */
    public static final int VERSION = 5;
    public static final String BUNDLE_FORMAT = "freeclick-bundle";
    /** Deepest block nesting accepted from a file (protects against hostile files). */
    static final int MAX_FILE_DEPTH = 64;

    /** The file was written by a newer FreeClick. */
    public static final class NewerVersionException extends IllegalArgumentException {
        public final int fileVersion;
        NewerVersionException(int v) {
            super("Fișierul e făcut cu o versiune mai nouă de FreeClick. Actualizează aplicația ca să-l deschizi.");
            fileVersion = v;
        }
    }

    static void checkVersion(Map<String, Object> o) {
        int v = Json.getInt(o, "version", 1);
        if (v > VERSION) throw new NewerVersionException(v);
    }

    // ------------------------------------------------------------ single macro
    public static String toJson(Macro m) { return Json.stringify(toMap(m), true); }

    public static Macro fromJson(String json) {
        Object o = Json.parse(json);
        if (!(o instanceof Map)) throw new IllegalArgumentException("Macro JSON must be an object");
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) o;
        return fromMap(map);
    }

    static Map<String, Object> toMap(Macro m) {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("version", (long) VERSION);
        o.put("id", m.id);
        o.put("name", m.name);
        o.put("kind", m.kind);
        o.put("createdAt", m.createdAt);
        o.put("updatedAt", m.updatedAt);
        o.put("screenWidth", (long) m.screenWidth);
        o.put("screenHeight", (long) m.screenHeight);

        Macro.Settings s = m.settings;
        Map<String, Object> so = new LinkedHashMap<String, Object>();
        so.put("loops", (long) s.loops);
        so.put("loopDelay", s.loopDelay);
        so.put("speed", s.speed);
        so.put("jitterPx", (long) s.jitterPx);
        so.put("jitterTimePct", (long) s.jitterTimePct);
        so.put("stopAfterMs", s.stopAfterMs);
        so.put("scaleToScreen", s.scaleToScreen);
        so.put("safeMode", s.safeMode);
        so.put("recordVideo", s.recordVideo);
        so.put("lockToPackage", s.lockToPackage == null ? "" : s.lockToPackage);
        so.put("launchPackage", s.launchPackage == null ? "" : s.launchPackage);
        so.put("tapInterval", s.tapInterval);
        so.put("tapDuration", s.tapDuration);
        so.put("swipeDuration", s.swipeDuration);
        if (s.syncTaps) so.put("syncTaps", Boolean.TRUE); // 2.3; older versions simply ignore it
        o.put("settings", so);

        if (Macro.KIND_SCRIPT.equals(m.kind) || (m.script != null && m.script.length() > 0)) o.put("script", m.script == null ? "" : m.script);
        o.put("actions", listToJson(m.actions));
        return o;
    }

    private static List<Object> listToJson(List<Action> list) {
        List<Object> acts = new ArrayList<Object>();
        for (Action a : list) acts.add(actionToMap(a)); // tree-ok: actionToMap recurses into blocks
        return acts;
    }

    static Map<String, Object> actionToMap(Action a) {
        if (a.type == Action.Type.UNKNOWN && a.raw != null) return Json.deepCopy(a.raw);
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("type", a.type.name());
        o.put("delay", a.delay);
        o.put("duration", a.duration);
        o.put("x", round1(a.x));
        o.put("y", round1(a.y));
        if (a.type == Action.Type.SWIPE || (a.type == Action.Type.READ && a.readMode != Action.READ_COLOR)) {
            o.put("x2", round1(a.x2));
            o.put("y2", round1(a.y2));
        }
        if (a.type == Action.Type.PRINT || a.type == Action.Type.SET_VAR) o.put("text", a.text == null ? "" : a.text);
        if (a.type == Action.Type.SET_VAR || a.type == Action.Type.READ) o.put("var", a.varName == null ? "" : a.varName);
        if (a.type == Action.Type.READ) o.put("read", (long) a.readMode);
        if (a.type == Action.Type.GESTURE) {
            List<Object> strokes = new ArrayList<Object>();
            for (Stroke s : a.strokes) {
                List<Object> flat = new ArrayList<Object>();
                for (int i = 0; i < s.size(); i++) {
                    flat.add(round1(s.x(i)));
                    flat.add(round1(s.y(i)));
                    flat.add(s.times.get(i));
                }
                strokes.add(flat);
            }
            o.put("strokes", strokes);
        }
        if (a.isZoom()) o.put("zoom", (long) a.zoomSpan);
        if (a.type == Action.Type.GLOBAL) o.put("global", (long) a.globalAction);
        if (a.type == Action.Type.MACRO) o.put("macroId", a.macroId);
        if (a.type == Action.Type.WAIT_COLOR) {
            o.put("color", ColorMatch.toHex(a.color));
            o.put("tolerance", (long) a.tolerance);
            o.put("invert", a.invert);
            o.put("stopOnTimeout", a.stopOnTimeout);
        }
        if (a.type == Action.Type.WAIT && a.durationMax > a.duration) o.put("durationMax", a.durationMax);
        if (a.type == Action.Type.LAUNCH_APP) o.put("package", a.packageName == null ? "" : a.packageName);
        if (a.type == Action.Type.IMAGE && a.img != null) {
            o.put("image", imageToMap(a.img));
            o.put("press", (long) a.press);
            o.put("wait", (long) a.waitMode);
            if (a.allMatches) o.put("all", true);
            if (a.maxHits > 0) o.put("maxHits", (long) a.maxHits);
            o.put("pressMs", a.pressMs);
            o.put("stopOnTimeout", a.stopOnTimeout);
        }
        o.put("repeat", (long) a.repeat);
        o.put("interval", a.interval);
        o.put("enabled", a.enabled);
        if (a.label != null && a.label.length() > 0) o.put("label", a.label);
        if (a.children != null) o.put("children", listToJson(a.children));
        if (a.elseChildren != null) o.put("else", listToJson(a.elseChildren));
        return o;
    }

    private static Double round1(float v) { return Math.round(v * 10.0) / 10.0; }

    static Map<String, Object> imageToMap(ImageSpec s) {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("id", s.id);
        o.put("w", (long) s.w);
        o.put("h", (long) s.h);
        o.put("capX", round1(s.capX));
        o.put("capY", round1(s.capY));
        o.put("capScreenW", (long) s.capScreenW);
        o.put("capScreenH", (long) s.capScreenH);
        o.put("tapDx", round1(s.tapDx));
        o.put("tapDy", round1(s.tapDy));
        if (s.hasZone()) {
            o.put("zx1", round1(s.zx1)); o.put("zy1", round1(s.zy1));
            o.put("zx2", round1(s.zx2)); o.put("zy2", round1(s.zy2));
        }
        o.put("precision", (long) s.precision);
        if (s.tight) o.put("tight", true);
        if (s.name != null && s.name.length() > 0) o.put("name", s.name);
        return o;
    }

    static ImageSpec imageFromMap(Map<String, Object> o) {
        if (o == null) return null;
        ImageSpec s = new ImageSpec();
        s.id = Json.getString(o, "id", "");
        if (!ImageSpec.isSafeId(s.id)) return null; // ids become file names
        s.w = clamp(Json.getInt(o, "w", 0), 0, 4096);
        s.h = clamp(Json.getInt(o, "h", 0), 0, 4096);
        s.capX = (float) Json.getDouble(o, "capX", 0);
        s.capY = (float) Json.getDouble(o, "capY", 0);
        s.capScreenW = clamp(Json.getInt(o, "capScreenW", 0), 0, 20000);
        s.capScreenH = clamp(Json.getInt(o, "capScreenH", 0), 0, 20000);
        s.tapDx = (float) Json.getDouble(o, "tapDx", s.w / 2.0);
        s.tapDy = (float) Json.getDouble(o, "tapDy", s.h / 2.0);
        s.zx1 = (float) Json.getDouble(o, "zx1", 0); s.zy1 = (float) Json.getDouble(o, "zy1", 0);
        s.zx2 = (float) Json.getDouble(o, "zx2", 0); s.zy2 = (float) Json.getDouble(o, "zy2", 0);
        s.precision = ImageMatcher.clampPrecision(Json.getInt(o, "precision", ImageSpec.PRECISION_NORMAL));
        s.tight = Json.getBool(o, "tight", false);
        s.name = Json.getString(o, "name", "");
        return s;
    }

    static Macro fromMap(Map<String, Object> o) {
        checkVersion(o);
        String name = Json.getString(o, "name", "Macro");
        Macro m = new Macro(name);
        String id = Json.getString(o, "id", null);
        if (id != null && id.length() > 0) m.id = id;
        m.createdAt = Json.getLong(o, "createdAt", m.createdAt);
        m.updatedAt = Json.getLong(o, "updatedAt", m.createdAt);
        m.screenWidth = Json.getInt(o, "screenWidth", 0);
        m.screenHeight = Json.getInt(o, "screenHeight", 0);

        Map<String, Object> so = Json.getObject(o, "settings");
        if (so != null) {
            Macro.Settings s = m.settings;
            s.loops = Math.max(0, Json.getInt(so, "loops", s.loops));
            s.loopDelay = Math.max(0, Json.getLong(so, "loopDelay", s.loopDelay));
            s.speed = Macro.clampSpeed(Json.getDouble(so, "speed", s.speed));
            s.jitterPx = clamp(Json.getInt(so, "jitterPx", s.jitterPx), 0, 500);
            s.jitterTimePct = clamp(Json.getInt(so, "jitterTimePct", s.jitterTimePct), 0, 100);
            s.stopAfterMs = Math.max(0, Json.getLong(so, "stopAfterMs", s.stopAfterMs));
            s.scaleToScreen = Json.getBool(so, "scaleToScreen", s.scaleToScreen);
            s.safeMode = Json.getBool(so, "safeMode", s.safeMode);
            s.recordVideo = Json.getBool(so, "recordVideo", s.recordVideo);
            s.lockToPackage = Json.getString(so, "lockToPackage", "");
            s.launchPackage = Json.getString(so, "launchPackage", "");
            s.tapInterval = Math.max(0, Json.getLong(so, "tapInterval", s.tapInterval));
            s.tapDuration = Math.max(1, Math.min(60000, Json.getLong(so, "tapDuration", s.tapDuration)));
            s.swipeDuration = Math.max(1, Math.min(60000, Json.getLong(so, "swipeDuration", s.swipeDuration)));
            s.syncTaps = Json.getBool(so, "syncTaps", false);
        }

        List<Action> read = listFromJson(Json.getArray(o, "actions"), 0);
        if (read != null) m.actions.addAll(read);
        String kind = Json.getString(o, "kind", null);
        m.kind = Macro.isValidKind(kind) ? kind : Macro.inferKind(m);
        m.script = limit(Json.getString(o, "script", ""), 1000000);
        return m;
    }

    private static List<Action> listFromJson(List<Object> arr, int depth) {
        if (arr == null) return null;
        if (depth > MAX_FILE_DEPTH) throw new IllegalArgumentException("Blocuri prea adânci în fișier");
        List<Action> out = new ArrayList<Action>();
        for (Object ao : arr) {
            if (!(ao instanceof Map)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> am = (Map<String, Object>) ao;
            Action a = actionFromMap(am, depth);
            if (a != null) out.add(a);
        }
        return out;
    }

    static Action actionFromMap(Map<String, Object> o) { return actionFromMap(o, 0); }

    static Action actionFromMap(Map<String, Object> o, int depth) {
        Action.Type type;
        try {
            type = Action.Type.valueOf(Json.getString(o, "type", ""));
        } catch (IllegalArgumentException unknown) {
            type = null;
        }
        if (type == null || type == Action.Type.UNKNOWN) {
            // A step this version does not know: keep it exactly as written, never run it.
            Action u = new Action(Action.Type.UNKNOWN);
            u.raw = Json.deepCopy(o);
            u.enabled = false;
            return u;
        }
        Action a = new Action(type);
        a.delay = Math.max(0, Json.getLong(o, "delay", 0));
        a.duration = Math.max(0, Json.getLong(o, "duration", 0));
        a.x = (float) Json.getDouble(o, "x", 0);
        a.y = (float) Json.getDouble(o, "y", 0);
        a.x2 = (float) Json.getDouble(o, "x2", 0);
        a.y2 = (float) Json.getDouble(o, "y2", 0);
        a.zoomSpan = (int) Math.max(-4000, Math.min(4000, Json.getLong(o, "zoom", 0)));
        List<Object> strokes = Json.getArray(o, "strokes");
        if (strokes != null) {
            for (Object so : strokes) {
                if (!(so instanceof List)) continue;
                List<?> flat = (List<?>) so;
                Stroke s = new Stroke();
                for (int i = 0; i + 2 < flat.size(); i += 3) {
                    Object px = flat.get(i), py = flat.get(i + 1), pt = flat.get(i + 2);
                    if (px instanceof Number && py instanceof Number && pt instanceof Number) {
                        s.add(((Number) px).floatValue(), ((Number) py).floatValue(), ((Number) pt).longValue());
                    }
                }
                if (s.size() > 0) a.strokes.add(s);
            }
        }
        a.globalAction = Json.getInt(o, "global", 0);
        a.macroId = Json.getString(o, "macroId", null);
        a.repeat = Math.max(1, Json.getInt(o, "repeat", 1));
        a.interval = Math.max(0, Json.getLong(o, "interval", 100));
        a.enabled = Json.getBool(o, "enabled", true);
        a.label = Json.getString(o, "label", "");
        a.color = ColorMatch.parseHex(Json.getString(o, "color", "#000000"));
        a.tolerance = clamp(Json.getInt(o, "tolerance", 24), 0, 255);
        a.invert = Json.getBool(o, "invert", false);
        a.stopOnTimeout = Json.getBool(o, "stopOnTimeout", false);
        a.durationMax = Math.max(0, Json.getLong(o, "durationMax", 0));
        if (a.durationMax <= a.duration) a.durationMax = 0;
        a.packageName = Json.getString(o, "package", "");
        a.text = limit(Json.getString(o, "text", ""), 2000);
        a.varName = limit(Json.getString(o, "var", ""), 32);
        a.readMode = clamp(Json.getInt(o, "read", Action.READ_NUMBER), 0, 2);
        if (type == Action.Type.IMAGE) {
            a.img = imageFromMap(Json.getObject(o, "image"));
            if (a.img == null) { // a broken image step is kept as an unknown one rather than lost
                Action u = new Action(Action.Type.UNKNOWN);
                u.raw = Json.deepCopy(o);
                u.enabled = false;
                return u;
            }
            a.press = clamp(Json.getInt(o, "press", Action.PRESS_TAP), 0, 3);
            a.waitMode = clamp(Json.getInt(o, "wait", Action.MISSING_CONTINUE), 0, 1);
            a.allMatches = Json.getBool(o, "all", false);
            a.maxHits = clamp(Json.getInt(o, "maxHits", 0), 0, 1000000);
            a.pressMs = Math.max(1, Math.min(60000, Json.getLong(o, "pressMs", Action.DEFAULT_TAP_MS)));
        }
        a.children = listFromJson(Json.getArray(o, "children"), depth + 1);
        a.elseChildren = listFromJson(Json.getArray(o, "else"), depth + 1);
        if (type == Action.Type.GESTURE && a.strokes.isEmpty()) return null;
        return a;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String limit(String s, int max) { return s == null ? "" : s.length() > max ? s.substring(0, max) : s; }

    /** Ids become file names, so imported ids must be plain (no '/', '..', etc.). */
    public static boolean isSafeId(String id) {
        return id != null && id.length() > 0 && id.length() <= 64 && id.matches("[A-Za-z0-9_-]+");
    }

    // ------------------------------------------------------------ bundles
    /** Reads the PNG of an image step (null if missing). */
    public interface ImageSource { byte[] png(String id); }

    /** Largest PNG accepted from a file (cut-outs are at most 400×400). */
    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    /** Export a macro plus every sub-macro it (transitively) references. */
    public static String exportBundle(Macro root, MacroLibrary lib) { return exportBundle(root, lib, null); }

    /** Same, with the pictures of its image steps inside (base64), so the file is self-contained. */
    public static String exportBundle(Macro root, MacroLibrary lib, ImageSource images) {
        List<Macro> all = new ArrayList<Macro>();
        collect(root, lib, all, new java.util.HashSet<String>());
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("format", BUNDLE_FORMAT);
        o.put("version", (long) VERSION);
        o.put("root", root.id);
        List<Object> list = new ArrayList<Object>();
        for (Macro m : all) list.add(toMap(m));
        o.put("macros", list);
        if (images != null) {
            Map<String, Object> im = new LinkedHashMap<String, Object>();
            MacroLibrary.InMemory l = new MacroLibrary.InMemory();
            for (Macro m : all) l.add(m);
            for (String id : MacroGraph.imageIds(root, l)) {
                byte[] png = images.png(id);
                if (png != null) im.put(id, B64.encode(png));
            }
            if (!im.isEmpty()) o.put("images", im);
        }
        return Json.stringify(o, true);
    }

    /**
     * 📤 Several saves in one file / share code (2.3): each with everything it uses (sub-macros, pictures).
     * Importing it adds them all, never replacing anything.
     */
    public static String exportPack(List<Macro> roots, MacroLibrary lib, ImageSource images) {
        List<Macro> all = new ArrayList<Macro>();
        Set<String> seen = new java.util.HashSet<String>();
        for (Macro r : roots) collect(r, lib, all, seen);
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("format", BUNDLE_FORMAT);
        o.put("version", (long) VERSION);
        if (roots.size() == 1) o.put("root", roots.get(0).id);
        List<Object> list = new ArrayList<Object>();
        for (Macro m : all) list.add(toMap(m));
        o.put("macros", list);
        if (images != null) {
            Map<String, Object> im = new LinkedHashMap<String, Object>();
            MacroLibrary.InMemory l = new MacroLibrary.InMemory();
            for (Macro m : all) l.add(m);
            for (Macro r : roots) {
                for (String id : MacroGraph.imageIds(r, l)) {
                    if (im.containsKey(id)) continue;
                    byte[] png = images.png(id);
                    if (png != null) im.put(id, B64.encode(png));
                }
            }
            if (!im.isEmpty()) o.put("images", im);
        }
        return Json.stringify(o, false);
    }

    /** Pictures carried by a bundle: id → PNG bytes (unsafe ids and oversized data dropped). */
    public static Map<String, byte[]> importImages(String json) {
        Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map)) return out;
        @SuppressWarnings("unchecked") Map<String, Object> o = (Map<String, Object>) parsed;
        Map<String, Object> im = Json.getObject(o, "images");
        if (im == null) return out;
        for (Map.Entry<String, Object> e : im.entrySet()) {
            if (!ImageSpec.isSafeId(e.getKey()) || !(e.getValue() instanceof String)) continue;
            String b = (String) e.getValue();
            if (b.length() > MAX_IMAGE_BYTES * 4 / 3 + 8) continue;
            byte[] png = B64.decode(b);
            if (png != null && png.length > 0) out.put(e.getKey(), png);
        }
        return out;
    }

    /** After importing pictures under other names (their real content hash), point the steps at them. */
    public static void renameImages(List<Macro> macros, Map<String, String> renamed) {
        if (renamed.isEmpty()) return;
        for (Macro m : macros) {
            for (Action a : ActionTree.all(m.actions)) {
                if (a.img != null && renamed.containsKey(a.img.id)) a.img.id = renamed.get(a.img.id);
            }
        }
    }

    private static void collect(Macro m, MacroLibrary lib, List<Macro> out, Set<String> seen) {
        if (m == null || !seen.add(m.id)) return;
        out.add(m);
        for (Action a : ActionTree.all(m.actions)) {
            if (a.type == Action.Type.MACRO && a.macroId != null && lib != null) {
                collect(lib.find(a.macroId), lib, out, seen);
            }
        }
    }

    /** Full backup: every saved item in one file. Restoring it keeps the original ids. */
    public static String exportAll(List<Macro> all) {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("format", BUNDLE_FORMAT);
        o.put("version", (long) VERSION);
        o.put("backup", Boolean.TRUE);
        List<Object> list = new ArrayList<Object>();
        for (Macro m : all) list.add(toMap(m));
        o.put("macros", list);
        return Json.stringify(o, true);
    }

    /** True if the text is a full backup made with exportAll. */
    public static boolean isBackup(String json) {
        try {
            Object parsed = Json.parse(json);
            if (!(parsed instanceof Map)) return false;
            @SuppressWarnings("unchecked") Map<String, Object> o = (Map<String, Object>) parsed;
            return BUNDLE_FORMAT.equals(Json.getString(o, "format", "")) && Json.getBool(o, "backup", false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Read a bundle (or a single macro file). Macros whose ids already exist
     * get fresh ids and all references inside the bundle are rewritten, so an
     * import never overwrites or hijacks an existing macro.
     */
    public static List<Macro> importBundle(String json, Set<String> existingIds) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("Fișier invalid");
        @SuppressWarnings("unchecked") Map<String, Object> o = (Map<String, Object>) parsed;
        checkVersion(o);
        List<Macro> macros = new ArrayList<Macro>();
        if (BUNDLE_FORMAT.equals(Json.getString(o, "format", ""))) {
            List<Object> list = Json.getArray(o, "macros");
            if (list != null) {
                for (Object mo : list) {
                    if (mo instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) mo;
                        macros.add(fromMap(mm));
                    }
                }
            }
        } else if (o.containsKey("actions")) {
            macros.add(fromMap(o));
        } else {
            throw new IllegalArgumentException("Fișierul nu conține macro-uri");
        }

        // A full backup restores items under their own ids (replacing the same items),
        // so restoring twice never creates duplicates. Other files never overwrite anything.
        boolean backup = Json.getBool(o, "backup", false);
        Map<String, String> remap = new HashMap<String, String>();
        Set<String> taken = backup ? new java.util.HashSet<String>() : new java.util.HashSet<String>(existingIds);
        for (Macro m : macros) {
            if (taken.contains(m.id) || !isSafeId(m.id)) {
                String fresh = UUID.randomUUID().toString();
                remap.put(m.id, fresh);
                m.id = fresh;
            }
            taken.add(m.id);
        }
        for (Macro m : macros) {
            for (Action a : ActionTree.all(m.actions)) {
                if (a.type == Action.Type.MACRO && a.macroId != null && remap.containsKey(a.macroId)) {
                    a.macroId = remap.get(a.macroId);
                }
            }
        }
        return macros;
    }
}
