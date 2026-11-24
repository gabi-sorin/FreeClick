package com.freeclick.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Display;

import com.freeclick.core.ScreenFrame;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Reads the screen for colour conditions and image steps WITHOUT the "Share your screen?"
 * dialog: on Android 11+ an accessibility service may take screenshots itself
 * (canTakeScreenshot). Android allows about 3 per second (1 on Android 11), so frames are
 * refreshed at that pace while a run needs them. Older Android versions fall back to
 * MediaProjection (VideoRecorderService, pixel mode). Frames stay in memory only.
 *
 * The screenshot arrives and is converted (≈10 MB) on a background thread, so the main
 * thread (which times the gestures) never stalls; only the finished, immutable frame is
 * handed over. Each frame remembers when it was requested: a frame requested before the
 * last touch may still show the old screen, so conditions ask for frames newer than that.
 * Frames being searched by the image matcher are "acquired" and their pixel buffer is only
 * reused for a later frame after they are released.
 */
final class ScreenSampler {
    /** Android allows one screenshot per 333 ms (Android 12+) or per second (Android 11). */
    static long intervalMs() { return Build.VERSION.SDK_INT >= 31 ? 350 : 1050; }

    private final AccessibilityService svc;
    private final Handler main;
    private final Executor executor;
    private android.os.HandlerThread thread;
    private Handler workerHandler;
    private ScreenFrame latest;
    private final IdentityHashMap<ScreenFrame, Integer> users = new IdentityHashMap<ScreenFrame, Integer>();
    private final Object spareLock = new Object();
    private int[] spare; // a free pixel buffer for the next frame (no 10 MB allocation every time)
    private boolean active;
    private boolean inFlight;
    private long lastRequest;
    private final List<Runnable> waiters = new ArrayList<Runnable>();
    /** Callbacks waiting for a frame newer than a given time. */
    private final List<Object[]> frameWaiters = new ArrayList<Object[]>();

    private final Runnable next = new Runnable() {
        @Override public void run() { request(); }
    };

    ScreenSampler(AccessibilityService svc, final Handler main) {
        this.svc = svc;
        this.main = main;
        this.executor = new Executor() {
            @Override public void execute(Runnable r) { worker().post(r); }
        };
    }

    private synchronized Handler worker() {
        if (thread == null) {
            thread = new android.os.HandlerThread("freeclick-frames");
            thread.start();
            workerHandler = new Handler(thread.getLooper());
        }
        return workerHandler;
    }

    /** Service shutting down: stop the background thread. */
    void release() {
        stop();
        synchronized (this) {
            if (thread != null) { thread.quitSafely(); thread = null; workerHandler = null; }
        }
    }

    /** True if the screen can be read without asking the user (Android 11+). */
    static boolean supported() { return Build.VERSION.SDK_INT >= 30; }

    boolean isActive() { return active; }

    /** Keep refreshing frames until stop(). */
    void start() {
        if (!supported() || active) return;
        active = true;
        request();
    }

    void stop() {
        active = false;
        main.removeCallbacks(next);
        latest = null;
        if (!inFlight) { runWaiters(); failFrameWaiters(); }
    }

    /** Take one fresh frame (even if not active), then run `then` on the main thread. */
    void refreshThen(Runnable then) {
        if (!supported()) { then.run(); return; }
        waiters.add(then);
        scheduleSoon();
    }

    private void scheduleSoon() {
        if (inFlight) return;
        long wait = Math.max(0, lastRequest + intervalMs() - SystemClock.uptimeMillis());
        main.removeCallbacks(next);
        main.postDelayed(next, wait);
    }

    /** Colour 0xRRGGBB at screen pixel (x, y) from the latest frame, or -1 if none yet. */
    int sample(int x, int y) { return sample(x, y, 0); }

    /**
     * Like sample(), but -2 if the latest frame was requested before notBefore (uptime ms):
     * it may still show the screen from before the last touch.
     */
    int sample(int x, int y, long notBefore) {
        ScreenFrame f = latest;
        if (f == null) return -1;
        int c = f.rgb(x, y);
        if (c < 0) return -1;
        if (f.time < notBefore) return -2;
        return c;
    }

    /** The latest frame, if any (main thread only). */
    ScreenFrame latest() { return latest; }

    /** Keep a frame's pixels untouched while it is being searched (main thread). */
    ScreenFrame acquire(ScreenFrame f) {
        if (f == null) return null;
        Integer n = users.get(f);
        users.put(f, n == null ? 1 : n + 1);
        return f;
    }

    /** The search is done with this frame (main thread). */
    void releaseFrame(ScreenFrame f) {
        if (f == null) return;
        Integer n = users.get(f);
        if (n == null || n <= 1) {
            users.remove(f);
            if (f != latest) recycle(f);
        } else {
            users.put(f, n - 1);
        }
    }

    private void recycle(ScreenFrame f) {
        synchronized (spareLock) { spare = f.argb; }
    }

    interface FrameCallback { void onFrame(ScreenFrame f); }

    /**
     * Call `then` (main thread) with a frame requested at or after notBefore, or with null if
     * screen capture stopped or keeps failing. Frames keep coming while waiting.
     */
    void whenFrame(long notBefore, FrameCallback then) {
        if (!supported()) { then.onFrame(null); return; }
        ScreenFrame f = latest;
        if (f != null && f.time >= notBefore) { then.onFrame(f); return; }
        frameWaiters.add(new Object[]{notBefore, then});
        scheduleSoon();
    }

    private void request() {
        if (inFlight || !supported()) return;
        if (!active && waiters.isEmpty() && frameWaiters.isEmpty()) return;
        inFlight = true;
        final long requestedAt = SystemClock.uptimeMillis();
        lastRequest = requestedAt;
        try {
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, executor, new AccessibilityService.TakeScreenshotCallback() {
                // Runs on the background thread: the heavy conversion happens here.
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    HardwareBuffer hb = result.getHardwareBuffer();
                    ScreenFrame frame = null;
                    try {
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                        if (hw != null) {
                            // Hardware bitmaps cannot be read pixel by pixel: copy, then take the pixels.
                            Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                            hw.recycle();
                            if (sw != null) {
                                int w = sw.getWidth(), h = sw.getHeight();
                                int[] buf;
                                synchronized (spareLock) {
                                    buf = spare != null && spare.length == w * h ? spare : null;
                                    spare = null;
                                }
                                if (buf == null) buf = new int[w * h];
                                sw.getPixels(buf, 0, w, 0, 0, w, h);
                                sw.recycle();
                                frame = new ScreenFrame(buf, w, h, requestedAt);
                            }
                        }
                    } catch (RuntimeException ignored) {
                    } catch (OutOfMemoryError oom) {
                        frame = null;
                    } finally {
                        hb.close();
                    }
                    final ScreenFrame done = frame;
                    main.post(new Runnable() { public void run() { deliver(done); } });
                }

                @Override public void onFailure(int errorCode) {
                    main.post(new Runnable() { public void run() { done(false); } });
                }
            });
        } catch (RuntimeException e) { // service not ready / screenshots not allowed
            done(false);
        }
    }

    /** Main thread: swap in the new frame (readers only ever touch frames on this thread). */
    private void deliver(ScreenFrame frame) {
        if (frame == null) { done(false); return; }
        if (!active && waiters.isEmpty() && frameWaiters.isEmpty()) { // stopped meanwhile: drop it
            recycle(frame);
            inFlight = false;
            return;
        }
        ScreenFrame old = latest;
        latest = frame;
        if (old != null && !users.containsKey(old)) recycle(old);
        done(true);
    }

    private int failures;

    private void done(boolean ok) {
        inFlight = false;
        if (ok) {
            failures = 0;
            runWaiters(); // they read the fresh frame synchronously
            runFrameWaiters();
        } else if (++failures >= 3) {
            failures = 0;
            runWaiters(); // give up: sample() returns -1 and the caller reports it
            failFrameWaiters();
        }
        if (active || !waiters.isEmpty() || !frameWaiters.isEmpty()) main.postDelayed(next, intervalMs());
    }

    private void runWaiters() {
        if (waiters.isEmpty()) return;
        List<Runnable> copy = new ArrayList<Runnable>(waiters);
        waiters.clear();
        for (Runnable r : copy) r.run();
    }

    private void runFrameWaiters() {
        ScreenFrame f = latest;
        if (f == null || frameWaiters.isEmpty()) return;
        List<Object[]> ready = new ArrayList<Object[]>();
        for (Object[] w : frameWaiters) if (f.time >= (Long) w[0]) ready.add(w);
        frameWaiters.removeAll(ready);
        for (Object[] w : ready) ((FrameCallback) w[1]).onFrame(f);
    }

    private void failFrameWaiters() {
        if (frameWaiters.isEmpty()) return;
        List<Object[]> copy = new ArrayList<Object[]>(frameWaiters);
        frameWaiters.clear();
        for (Object[] w : copy) ((FrameCallback) w[1]).onFrame(null);
    }
}
