package com.freeclick.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Screen capture through MediaProjection, in one of two modes:
 *  - VIDEO:  MP4 (H.264) recording of the screen;
 *  - PIXELS: keeps the latest frame (half resolution) so colour conditions can
 *            read pixels. Nothing is stored or sent anywhere.
 * Android allows one capture at a time, so the two modes are exclusive.
 * Order required by Android 14: consent -> startForeground(mediaProjection)
 * -> getMediaProjection -> registerCallback -> createVirtualDisplay.
 */
public final class VideoRecorderService extends Service {
    private static final String TAG = "FreeClick";
    static final String ACTION_START = "com.freeclick.video.START";
    static final String ACTION_STOP = "com.freeclick.video.STOP";
    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    static final String EXTRA_MODE = "mode";
    static final String MODE_VIDEO = "video";
    static final String MODE_PIXELS = "pixels";
    private static final int NOTIF_ID = 42;
    private static final int PIXEL_DOWNSCALE = 2;

    private static VideoRecorderService running;
    private static Runnable afterStart;

    private final Handler main = new Handler(Looper.getMainLooper());
    private String mode = MODE_VIDEO;
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaRecorder recorder;
    private ImageReader reader;
    private Image lastImage;
    private ParcelFileDescriptor pfd;
    private Uri mediaUri;
    private File legacyFile;
    private boolean started;
    private boolean stopFailed;
    private int videoW, videoH;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() { main.post(new Runnable() { public void run() { finish(true); } }); }
    };

    // ------------------------------------------------------------------ static API
    /** True while a screen VIDEO is being recorded. */
    public static boolean isRecording() { return running != null && running.started && MODE_VIDEO.equals(running.mode); }

    /** True while frames are captured for colour conditions. */
    public static boolean isCapturingPixels() { return running != null && running.started && MODE_PIXELS.equals(running.mode); }

    public static boolean isActive() { return running != null && running.started; }

    /** Ask for consent (system dialog) and start video; `then` runs on the main thread when done (or if it failed/was denied). */
    public static void requestStart(Context c, Runnable then) { request(c, MODE_VIDEO, then); }

    /** Same, for reading pixels (colour conditions). */
    public static void requestPixels(Context c, Runnable then) { request(c, MODE_PIXELS, then); }

    private static void request(Context c, String mode, Runnable then) {
        if (running != null && running.started) {
            if (!mode.equals(running.mode)) {
                Toast.makeText(c, MODE_VIDEO.equals(running.mode)
                        ? "Oprește întâi filmarea ecranului" : "Ecranul e deja citit pentru condițiile de culoare",
                        Toast.LENGTH_LONG).show();
            }
            if (then != null) then.run();
            return;
        }
        afterStart = then;
        c.startActivity(new Intent(c, CaptureRequestActivity.class).putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Called by CaptureRequestActivity when the user said no. */
    static void consentDenied(Context c) {
        Toast.makeText(c, "Captura ecranului nu a fost permisă", Toast.LENGTH_SHORT).show();
        runAfterStart();
    }

    private static void runAfterStart() {
        Runnable r = afterStart;
        afterStart = null;
        if (r != null) r.run();
    }

    public static void stop(Context c) {
        if (running != null) running.finish(true);
    }

    /** Colour 0xRRGGBB of the screen pixel (screen coordinates), or -1 if not capturing. Main thread. */
    public static int samplePixel(int x, int y) {
        VideoRecorderService s = running;
        if (s == null || !s.started || !MODE_PIXELS.equals(s.mode)) return -1;
        return s.readPixel(x, y);
    }

    // ------------------------------------------------------------------ service
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finish(true);
            return START_NOT_STICKY;
        }
        if (intent == null || !ACTION_START.equals(intent.getAction()) || started) return START_NOT_STICKY;
        String m = intent.getStringExtra(EXTRA_MODE);
        mode = MODE_PIXELS.equals(m) ? MODE_PIXELS : MODE_VIDEO;

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIF_ID, n);
        running = this;

        try {
            int code = intent.getIntExtra(EXTRA_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) throw new IllegalStateException("projection null");
            projection.registerCallback(projectionCallback, main);
            if (MODE_PIXELS.equals(mode)) startPixels(); else startRecording();
            started = true;
            toast(MODE_PIXELS.equals(mode) ? "Citesc culorile ecranului (pentru condiții)" : "Filmarea a început");
        } catch (Exception e) {
            Log.e(TAG, "capture start failed", e);
            toast("Nu pot porni captura: " + e.getMessage());
            finish(false);
        }
        QuickTileService.requestUpdate(this);
        runAfterStart();
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------------ pixels
    private void startPixels() {
        Point real = Overlays.screenSize(this);
        int w = Math.max(1, real.x / PIXEL_DOWNSCALE), h = Math.max(1, real.y / PIXEL_DOWNSCALE);
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
        int dpi = Math.max(1, getResources().getDisplayMetrics().densityDpi / PIXEL_DOWNSCALE);
        display = projection.createVirtualDisplay("FreeClick-pixels", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, main);
    }

    private int readPixel(int x, int y) {
        if (reader == null) return -1;
        try {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                if (lastImage != null) lastImage.close();
                lastImage = img;
            }
        } catch (IllegalStateException tooMany) {
            // all buffers held: keep using the last frame
        }
        Image img = lastImage;
        if (img == null) return -1;
        int px = x / PIXEL_DOWNSCALE, py = y / PIXEL_DOWNSCALE;
        if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) return -1;
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int off = py * plane.getRowStride() + px * plane.getPixelStride();
        if (off + 2 >= buf.limit()) return -1;
        int r = buf.get(off) & 0xFF, g = buf.get(off + 1) & 0xFF, b = buf.get(off + 2) & 0xFF;
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ video
    private void startRecording() throws Exception {
        Point real = Overlays.screenSize(this);
        int dpi = getResources().getDisplayMetrics().densityDpi;
        int[] maxSides = {960, 1280, 1920};
        int q = Math.max(0, Math.min(2, App.get().prefs().videoQuality()));
        Exception last = null;
        // Try the chosen quality, then fall back to smaller sizes if the encoder refuses (fails in prepare()).
        for (int maxSide : new int[]{maxSides[q], 1280, 720}) {
            try {
                prepareRecorder(real.x, real.y, maxSide, q);
            } catch (Exception e) {
                last = e;
                Log.w(TAG, "recorder config " + maxSide + " failed", e);
                releaseRecorder(false);
                continue;
            }
            display = projection.createVirtualDisplay("FreeClick", videoW, videoH, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.getSurface(), null, main);
            recorder.start();
            return;
        }
        throw last;
    }

    private void prepareRecorder(int w, int h, int maxSide, int quality) throws Exception {
        float scale = Math.min(1f, maxSide / (float) Math.max(w, h));
        videoW = Math.max(16, (int) (w * scale) / 16 * 16);
        videoH = Math.max(16, (int) (h * scale) / 16 * 16);
        recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        openOutput();
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(videoW, videoH);
        recorder.setVideoFrameRate(30);
        int[] bppf = {3, 5, 8};
        recorder.setVideoEncodingBitRate(Math.max(1_000_000, videoW * videoH * bppf[quality]));
        recorder.prepare();
    }

    private void openOutput() throws Exception {
        String name = "FreeClick_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        if (Build.VERSION.SDK_INT >= 29) {
            if (mediaUri == null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FreeClick");
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                mediaUri = getContentResolver().insert(
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                if (mediaUri == null) throw new IllegalStateException("MediaStore insert failed");
            }
            if (pfd != null) pfd.close();
            pfd = getContentResolver().openFileDescriptor(mediaUri, "rw");
            recorder.setOutputFile(pfd.getFileDescriptor());
        } else {
            File dir = VideoStore.legacyDir(this);
            if (legacyFile == null) legacyFile = new File(dir, name);
            recorder.setOutputFile(legacyFile.getAbsolutePath());
        }
    }

    private void releaseRecorder(boolean stopFirst) {
        if (display != null) { display.release(); display = null; }
        if (recorder != null) {
            if (stopFirst) {
                try { recorder.stop(); } catch (RuntimeException e) { Log.w(TAG, "recorder.stop", e); stopFailed = true; }
            }
            recorder.reset();
            recorder.release();
            recorder = null;
        }
    }

    // ------------------------------------------------------------------ stop
    /** Stop capturing; a finished video is published (or discarded on failure); then end the service. */
    private void finish(boolean keep) {
        boolean wasStarted = started;
        started = false;
        stopFailed = false;
        releaseRecorder(wasStarted && MODE_VIDEO.equals(mode));
        if (lastImage != null) { lastImage.close(); lastImage = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) {
            try { projection.unregisterCallback(projectionCallback); } catch (RuntimeException ignored) { }
            try { projection.stop(); } catch (RuntimeException ignored) { }
            projection = null;
        }
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        pfd = null;
        boolean ok = keep && wasStarted && !stopFailed;
        ContentResolver cr = getContentResolver();
        if (mediaUri != null) {
            try {
                if (ok) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    cr.update(mediaUri, v, null, null);
                } else {
                    cr.delete(mediaUri, null, null);
                }
            } catch (RuntimeException e) { Log.w(TAG, "finalize video", e); }
            mediaUri = null;
        }
        if (legacyFile != null && !ok && legacyFile.exists() && !legacyFile.delete()) Log.w(TAG, "cannot delete");
        legacyFile = null;
        if (ok && MODE_VIDEO.equals(mode)) toast("Filmare salvată în Movies/FreeClick");
        if (running == this) running = null;
        stopForeground(true);
        stopSelf();
        QuickTileService.requestUpdate(this);
    }

    @Override public void onDestroy() {
        if (started) finish(true);
        if (running == this) running = null;
        super.onDestroy();
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, VideoRecorderService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, App.CHANNEL_RECORDING) : new Notification.Builder(this);
        boolean pixels = MODE_PIXELS.equals(mode);
        return b.setSmallIcon(pixels ? android.R.drawable.ic_menu_view : android.R.drawable.presence_video_online)
                .setContentTitle(pixels ? "FreeClick urmărește culorile ecranului" : "FreeClick filmează ecranul")
                .setContentText(pixels ? "Pentru condițiile de culoare. Nimic nu e salvat sau trimis."
                        : "Atinge „Oprește” pentru a salva filmarea")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Oprește", pi).build())
                .build();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
