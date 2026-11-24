package com.freeclick.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 🌙 AFK mode (2.2.1): what to do when the game leaves the screen during a long run. No Android
 * here, so every decision is tested; the app layer only reports what happens and does what this says.
 *
 * <pre>
 *  IN_GAME ──another app in front──▶ AWAY (run paused, nothing is tapped outside the game)
 *     ▲                                │ back in time → RESUME
 *     │                                ▼ after awayMs
 *     │                             ASKING ("I reopen the game in 10 s · Cancel")
 *     │                                │ Cancel → HELD (waits for you; back in the game → RESUME)
 *     │                                ▼ after askMs
 *     │                             REOPENING (the game is launched; again after loadTimeoutMs, 3 tries)
 *     │                                ▼ game in front
 *     └────────────RESTART─────────  SETTLING (settleMs for loading, then the run starts over)
 * </pre>
 * Apps the run itself opened (an "open app" step), or a home / recents / back step, are not "away".
 * More than {@link #maxRestartsPerHour} reopenings in an hour → GIVE_UP (something is really wrong).
 */
public final class AfkWatch {
    public static final int NONE = 0, PAUSE = 1, RESUME = 2, ASK = 3, REOPEN = 4, RESTART = 5, GIVE_UP = 6;
    public static final int IN_GAME = 0, AWAY = 1, ASKING = 2, HELD = 3, REOPENING = 4, SETTLING = 5, GAVE_UP = 6;

    public long awayMs = 20000, askMs = 10000, loadTimeoutMs = 45000, settleMs = 8000, macroSwitchMs = 6000;
    public int maxRestartsPerHour = 5, maxTries = 3;

    private String game;
    private final Set<String> allowed = new HashSet<String>();
    private int phase = IN_GAME;
    private long since;
    private long switchUntil = -1;
    private boolean pausedByUs;
    private int tries;
    private final List<Long> restarts = new ArrayList<Long>();
    private int restartCount;

    /** A run started in {@code game} (null/empty: unknown, then nothing is watched). */
    public void start(String game) {
        this.game = game == null || game.isEmpty() ? null : game;
        allowed.clear();
        phase = IN_GAME;
        pausedByUs = false;
        switchUntil = -1;
        tries = 0;
    }

    public boolean watching() { return game != null; }
    public String game() { return game; }
    public int phase() { return phase; }
    public int restartCount() { return restartCount; }
    public boolean pausedByUs() { return pausedByUs; }

    /** The run opened this app itself (an "open app" step): it is part of the run, not "away". */
    public void runOpened(String pkg, long now) {
        if (pkg != null && !pkg.isEmpty()) allowed.add(pkg);
        switchUntil = now + macroSwitchMs;
    }

    /** The run pressed home / recents / back: the app that comes up next is part of the run. */
    public void runSwitched(long now) { switchUntil = now + macroSwitchMs; }

    /** Another app is in front (system panels, the keyboard and FreeClick itself are not reported). */
    public int onForeground(String pkg, long now) {
        if (game == null || pkg == null || phase == GAVE_UP) return NONE;
        boolean ours = pkg.equals(game) || allowed.contains(pkg);
        if (!ours && now <= switchUntil) { // the run went there itself
            allowed.add(pkg);
            return NONE;
        }
        if (ours) {
            if (phase == REOPENING) {
                if (!pkg.equals(game)) return NONE;
                phase = SETTLING;
                since = now;
                return NONE;
            }
            if (phase == AWAY || phase == ASKING || phase == HELD) {
                phase = IN_GAME;
                boolean resume = pausedByUs;
                pausedByUs = false;
                return resume ? RESUME : NONE;
            }
            if (phase == SETTLING && !pkg.equals(game)) return NONE;
            return NONE;
        }
        if (phase == IN_GAME) {
            phase = AWAY;
            since = now;
            pausedByUs = true;
            return PAUSE;
        }
        if (phase == SETTLING) { // the game went away again while loading
            phase = REOPENING;
            since = now;
        }
        return NONE;
    }

    /** Call about once a second while the screen is on. */
    public int tick(long now) {
        if (game == null) return NONE;
        switch (phase) {
            case AWAY:
                if (now - since < awayMs) return NONE;
                phase = ASKING;
                since = now;
                return ASK;
            case ASKING:
                if (now - since < askMs) return NONE;
                if (recentRestarts(now) >= maxRestartsPerHour) { phase = GAVE_UP; return GIVE_UP; }
                phase = REOPENING;
                since = now;
                tries = 1;
                return REOPEN;
            case REOPENING:
                if (now - since < loadTimeoutMs) return NONE;
                if (tries >= maxTries) { phase = GAVE_UP; return GIVE_UP; }
                tries++;
                since = now;
                return REOPEN;
            case SETTLING:
                if (now - since < settleMs) return NONE;
                phase = IN_GAME;
                pausedByUs = false;
                tries = 0;
                restarts.add(now);
                restartCount++;
                return RESTART;
            default:
                return NONE;
        }
    }

    /** "Cancel" on the countdown: wait for the user (the run stays paused). */
    public void hold() {
        if (phase == ASKING || phase == AWAY) phase = HELD;
    }

    /** The user pressed ▶ (or stopped / started again): whatever was waiting is over. */
    public void userTookOver() {
        if (phase != GAVE_UP) phase = IN_GAME;
        pausedByUs = false;
    }

    /** Waiting for the game to come back after FreeClick itself was restarted by Android. */
    public void expectReopen(long now) {
        if (game == null) return;
        phase = REOPENING;
        since = now;
        tries = 1;
    }

    private int recentRestarts(long now) {
        int n = 0;
        for (Long t : restarts) if (now - t < 3600000L) n++;
        return n;
    }

    // ------------------------------------------------------------------ battery and heat
    public static final int BATTERY_OK = 0, BATTERY_LOW = 1, BATTERY_HOT = 2;
    public static final float HOT_C = 45f, COOL_C = 40f;

    /**
     * Whether the run should wait for the battery: too hot (≥ 45 °C, until ≤ 40 °C) or low (≤ minLevel %
     * and not charging, until charging or 5 % above). {@code current} is the previous answer (hysteresis).
     */
    public static int battery(int level, boolean plugged, float tempC, int minLevel, int current) {
        if (tempC >= HOT_C || (current == BATTERY_HOT && tempC > COOL_C)) return BATTERY_HOT;
        if (minLevel > 0 && !plugged && level >= 0
                && (level <= minLevel || (current == BATTERY_LOW && level < minLevel + 5))) return BATTERY_LOW;
        return BATTERY_OK;
    }

    /** "1 h 05 min", "12 min", "45 s": how long the session has been running. */
    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 60) return s + " s";
        long m = s / 60;
        if (m < 60) return m + " min";
        long h = m / 60;
        m %= 60;
        return h + " h " + (m < 10 ? "0" : "") + m + " min";
    }
}
