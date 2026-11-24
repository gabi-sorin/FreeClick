package com.freeclick.app;

import com.freeclick.core.T;

import java.util.Calendar;

/** Pure helpers from the Android layer (run on the JVM with android.jar stubs on the classpath). */
public final class AppLogicTests {

    static void testSpeedSteps() {
        T.near(1.25, ClickService.nextSpeed(1.0, +1), 1e-9, "1 -> 1.25");
        T.near(0.75, ClickService.nextSpeed(1.0, -1), 1e-9, "1 -> 0.75");
        T.near(10, ClickService.nextSpeed(10, +1), 1e-9, "max stays");
        T.near(0.1, ClickService.nextSpeed(0.1, -1), 1e-9, "min stays");
        T.near(2, ClickService.nextSpeed(1.7, +1), 1e-9, "custom 1.7 up -> 2");
        T.near(1.5, ClickService.nextSpeed(1.7, -1), 1e-9, "custom 1.7 down -> 1.5");
    }

    static void testSpeedLabel() {
        T.eq("1×", FloatingPanel.formatSpeed(1.0), "1x");
        T.eq("0.25×", FloatingPanel.formatSpeed(0.25), "0.25x");
        T.eq("1.5×", FloatingPanel.formatSpeed(1.5), "1.5x");
    }

    static void testScheduleNextTime() {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 21, 10, 30, 0);
        c.set(Calendar.MILLISECOND, 0);
        long now = c.getTimeInMillis();
        Schedules.Entry later = new Schedules.Entry();
        later.hour = 18; later.minute = 5;
        Calendar r = Calendar.getInstance();
        r.setTimeInMillis(Schedules.nextTime(later, now));
        T.eq(21, r.get(Calendar.DAY_OF_MONTH), "later today");
        T.eq(18, r.get(Calendar.HOUR_OF_DAY), "hour");
        Schedules.Entry past = new Schedules.Entry();
        past.hour = 10; past.minute = 30; // exactly now -> tomorrow
        r.setTimeInMillis(Schedules.nextTime(past, now));
        T.eq(22, r.get(Calendar.DAY_OF_MONTH), "same minute goes to tomorrow");
    }

    static void testSafeFileNames() {
        T.eq("abc-123_X", MacroRepository.safeName("abc-123_X"), "plain id unchanged");
        T.eq("______etc_passwd", MacroRepository.safeName("../../etc/passwd"), "traversal neutralised");
    }

    static void testSystemPanelsPauseButToastsAndVolumeDoNot() {
        T.ok(ClickService.isShadeOrSystemPanel("android.widget.FrameLayout", ""), "unknown System UI window pauses (safe side)");
        T.ok(!ClickService.isShadeOrSystemPanel("android.widget.Toast$TN", ""), "toasts never pause");
        T.ok(!ClickService.isShadeOrSystemPanel("com.android.systemui.volume.VolumeDialogImpl$CustomDialog", ""), "volume never pauses");
        T.ok(!ClickService.isShadeOrSystemPanel("com.android.systemui.statusbar.HeadsUpView", "Mesaj nou"), "incoming-message banners never pause");
        T.ok(ClickService.isSystemUiPackage("com.android.systemui"), "AOSP");
        T.ok(ClickService.isSystemUiPackage("miui.systemui.plugin"), "MIUI control center");
        T.ok(!ClickService.isSystemUiPackage("com.supercell.clashofclans"), "apps are not System UI");
    }

    static void testServiceIntervalsFollowAndroidLimits() {
        T.ok(ScreenSampler.intervalMs() >= 334, "never faster than Android allows (3/s)");
    }

    static void testPictureNamesAreContentHashes() {
        String a = ImageStore.hash(new byte[]{1, 2, 3}), b = ImageStore.hash(new byte[]{1, 2, 4});
        T.eq(32, a.length(), "32 hex chars");
        T.ok(com.freeclick.core.ImageSpec.isSafeId(a), "usable as a file name");
        T.ok(!a.equals(b), "different pictures, different names");
        T.eq(a, ImageStore.hash(new byte[]{1, 2, 3}), "same picture, same name");
    }

    static void testCleanupNeverDeletesWhatIsUsedOrRecent() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("fcimg").toFile();
        ImageStore st = new ImageStore(dir);
        long old = System.currentTimeMillis() - 3L * 24 * 3600 * 1000;
        String used = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", unused = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", fresh = "cccccccccccccccccccccccccccccccc";
        for (String id : new String[]{used, unused, fresh}) {
            java.io.File f = new java.io.File(dir, id + ".png");
            java.nio.file.Files.write(f.toPath(), new byte[]{1});
            if (!id.equals(fresh)) T.ok(f.setLastModified(old), "aged");
        }
        java.util.Set<String> inUse = new java.util.HashSet<String>();
        inUse.add(used);
        T.eq(0, st.gc(inUse, false), "an unreadable saved item: nothing deleted");
        T.eq(1, st.gc(inUse, true), "only the old unused picture goes");
        T.ok(new java.io.File(dir, used + ".png").exists(), "used kept");
        T.ok(new java.io.File(dir, fresh + ".png").exists(), "recent kept (an undo may still need it)");
        T.ok(!new java.io.File(dir, unused + ".png").exists(), "unused deleted");
        T.eq(2, st.all().length, "two left");
    }
}
