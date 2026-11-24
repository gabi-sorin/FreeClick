package com.freeclick.core.script;

import com.freeclick.core.Action;
import com.freeclick.core.ImageSpec;

import java.util.List;

/**
 * What a script needs from the phone. Every call happens on the script's own thread and may
 * block until done (a tap until it was performed, a read until a fresh frame was read). Calls
 * must give up promptly with InterruptedException when the script is stopped.
 * The app implements it with the accessibility service; the tests with a virtual clock.
 */
public interface ScriptHost {
    /** Monotonic time in ms. */
    long now();
    /** Wall-clock time in ms (for la_ora / ora()). */
    long wallTime();
    void sleep(long ms) throws InterruptedException;
    /** Perform a gesture (tap, long press, double tap, swipe, free gesture). False if cancelled (the user touched the screen). */
    boolean gesture(Action a) throws InterruptedException;
    boolean global(int globalAction) throws InterruptedException;
    boolean openApp(String pkg) throws InterruptedException;
    /** Colour 0xRRGGBB at x,y on a frame taken after the last touch settled; -1 = the screen cannot be read. */
    int pixel(int x, int y) throws InterruptedException;
    /**
     * A fingerprint of what the zone shows now (a coarse grid of its colours): equal while nothing in it
     * changes. -1 if the screen cannot be read.
     */
    long zoneHash(int x1, int y1, int x2, int y2) throws InterruptedException;
    /**
     * 2.4: cols × rows colour samples at the centres of equal cells of the zone (script coordinates), row by row
     * (see ColorZones.grid); null if the screen cannot be read.
     */
    int[] grid(int x1, int y1, int x2, int y2, int cols, int rows) throws InterruptedException;
    /** Saves what is on screen now as a picture in the gallery (Pictures/FreeClick). */
    boolean saveScreenshot(String name) throws InterruptedException;
    /** Puts text on the clipboard. */
    void copyText(String text);
    /** Opens a web link or a deep link (an app's own page). */
    boolean openUrl(String url) throws InterruptedException;
    /** Text read in the zone (number = only digits and number signs); null = the screen cannot be read. */
    String readText(int x1, int y1, int x2, int y2, boolean number) throws InterruptedException;
    /** Where the image is, top-most first ({x, y} tap points); null = the screen cannot be read. */
    List<int[]> findImage(ImageSpec spec, boolean all) throws InterruptedException;
    /** A console line (kind: PlaybackEngine.PRINT_USER / PRINT_INFO / PRINT_WARN). */
    void print(String text, int kind);
    void toast(String text);
    void vibrate(long ms);
    int screenWidth();
    int screenHeight();
    /** Play steps with the normal engine (images, colours, sub-macros all work); returns when done. */
    void runSteps(List<Action> steps, int times) throws InterruptedException;
    /** The steps of a saved item, by name (null if there is none). */
    List<Action> stepsOf(String name);
    /** Variables kept between runs ("pastreaza"), as written by ScriptRunner (JSON); null if none yet. */
    String loadKept();
    void saveKept(String json);

    // ------------------------------------------------------------------ 2.3: your fingers
    /** What FreeClick hears of your fingers (screen pixels, times on the same clock as now()). */
    com.freeclick.core.TouchTracker touches();
    /**
     * The script listens (a wait for your touch, degete(), asculta(true)…) or stops listening. Where the
     * phone cannot just observe touches, the app puts up its listening layer, which passes each gesture on
     * to the game when {@code forward} (after your fingers lift).
     */
    void listen(boolean on, boolean forward);
    /** Screen pixels → the script's own coordinates (its design screen). */
    int[] toScript(int x, int y);

    // ------------------------------------------------------------------ 2.4: showing, telling, the phone
    /** Text over the game (cannot be touched); x < 0: at the top in the middle; "" hides it. */
    void hud(String text, int x, int y);
    /** Battery 0…100 (-1 unknown) and whether it charges. */
    int battery();
    boolean charging();
    /** True if the screen is wider than tall now. */
    boolean landscape();
    /** A phone notification (with the title). */
    void notifyUser(String title, String text);

    // ------------------------------------------------------------------ 2.5: the script's own buttons
    /** A button over the game (script coordinates); presses never reach the game. Returns its number (1, 2…). */
    int addButton(String text, int x1, int y1, int x2, int y2);
    /** 0 = all of them. */
    void removeButton(int id);
    /** Takes one press of this button if there is one. */
    boolean takePress(int id);
    /** Takes the oldest press of any button: its number, or -1. */
    int takeAnyPress();
    /** Only gestures that start in this zone are heard (x1 < 0: the whole screen); the listening layer covers only it. */
    void listenZone(int x1, int y1, int x2, int y2);

    // ------------------------------------------------------------------ 2.5: files of the scripts (FreeClick's own folder)
    /** The text of a file, or null if there is none. */
    String readFile(String name);
    /** Writes (or adds to) a file; false if it could not. */
    boolean writeFile(String name, String text, boolean append);

    // ------------------------------------------------------------------ 2.5: asking you
    /** A question over the game; waits for the answer. null = "Anulează". {@code number}: digits only. */
    String ask(String question, String preset, boolean number) throws InterruptedException;
    /** A list to pick from; waits. The index, or -1 for "Anulează". */
    int choose(String title, List<String> options) throws InterruptedException;
}
