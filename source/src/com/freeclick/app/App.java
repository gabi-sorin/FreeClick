package com.freeclick.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public final class App extends Application {
    public static final String CHANNEL_RECORDING = "recording";
    public static final String CHANNEL_INFO = "info";

    private static App instance;
    private MacroRepository repository;
    private Prefs prefs;
    private ImageStore images;

    public static App get() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        images = new ImageStore(this);
        repository = new MacroRepository(this);
        prefs = new Prefs(this);
        // Pictures of deleted image steps are cleaned up a little after changes (never right away).
        repository.addListener(new MacroRepository.ChangeListener() {
            @Override public void onMacrosChanged() { scheduleImageCleanup(60000); }
        });
        scheduleImageCleanup(30000);
        // Saved items, drafts and settings live in the app's private storage and survive updates;
        // alarms are the one thing Android may drop, so re-arm schedules on every start (idempotent).
        try { Schedules.armAll(this); } catch (RuntimeException ignored) { }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel rec = new NotificationChannel(CHANNEL_RECORDING, "Filmare ecran",
                    NotificationManager.IMPORTANCE_LOW);
            rec.setDescription("Afișată cât timp se filmează ecranul");
            nm.createNotificationChannel(rec);
            NotificationChannel info = new NotificationChannel(CHANNEL_INFO, "Programări și stare",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(info);
        }
    }

    public MacroRepository repo() { return repository; }

    private Letters letters;

    /** The letters the text reader knows (taught ones are loaded from their file on first use). */
    public synchronized Letters letters() {
        if (letters == null) letters = new Letters(this);
        return letters;
    }

    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable cleanup = new Runnable() {
        @Override public void run() {
            new Thread(new Runnable() { public void run() { cleanUpImages(); } }, "freeclick-gc").start();
            scheduleImageCleanup(24L * 3600 * 1000); // the service keeps the app alive for days: also daily
        }
    };

    void scheduleImageCleanup(long delayMs) {
        main.removeCallbacks(cleanup);
        main.postDelayed(cleanup, delayMs);
    }

    /**
     * Delete pictures no saved item and no draft uses (and not written in the last day).
     * If anything cannot be read, nothing is deleted.
     */
    void cleanUpImages() {
        try {
            java.util.Set<String> used = repository.usedImages();
            boolean allRead = repository.allReadable();
            for (String kind : new String[]{com.freeclick.core.Macro.KIND_MANUAL, com.freeclick.core.Macro.KIND_RECORD,
                    com.freeclick.core.Macro.KIND_COMBO}) {
                String d = prefs.draft(kind);
                if (d == null) continue;
                try {
                    MacroRepository.addImages(com.freeclick.core.MacroCodec.fromJson(d), used);
                } catch (RuntimeException unreadable) {
                    allRead = false;
                }
            }
            int n = images.gc(used, allRead);
            if (n > 0) android.util.Log.i("FreeClick", "deleted " + n + " unused pictures");
        } catch (RuntimeException e) {
            android.util.Log.w("FreeClick", "picture cleanup skipped", e);
        }
    }
    public Prefs prefs() { return prefs; }
    ImageStore images() { return images; }

    private ShotStore shots;

    /** Screenshots kept with scripts (code editor, 2.0). */
    synchronized ShotStore shots() {
        if (shots == null) shots = new ShotStore(this);
        return shots;
    }
}
