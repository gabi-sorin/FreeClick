package com.freeclick.core;

/**
 * System actions. The numeric values are exactly those of
 * android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_* so they
 * can be passed straight to performGlobalAction(). A unit test checks them.
 */
public final class GlobalActions {
    private GlobalActions() {}

    public static final int BACK = 1;
    public static final int HOME = 2;
    public static final int RECENTS = 3;
    public static final int NOTIFICATIONS = 4;
    public static final int QUICK_SETTINGS = 5;
    public static final int POWER_DIALOG = 6;
    public static final int TOGGLE_SPLIT_SCREEN = 7; // API 24
    public static final int LOCK_SCREEN = 8;         // API 28
    public static final int TAKE_SCREENSHOT = 9;     // API 28

    public static final int[] ALL = {BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS,
            POWER_DIALOG, TOGGLE_SPLIT_SCREEN, LOCK_SCREEN, TAKE_SCREENSHOT};

    /** Minimum Android API level that supports the action. */
    public static int minApi(int action) {
        switch (action) {
            case LOCK_SCREEN:
            case TAKE_SCREENSHOT: return 28;
            case TOGGLE_SPLIT_SCREEN: return 24;
            default: return 16;
        }
    }

    /** Romanian display name. */
    public static String name(int action) {
        switch (action) {
            case BACK: return "Înapoi";
            case HOME: return "Acasă";
            case RECENTS: return "Aplicații recente";
            case NOTIFICATIONS: return "Notificări";
            case QUICK_SETTINGS: return "Setări rapide";
            case POWER_DIALOG: return "Meniu pornire";
            case TOGGLE_SPLIT_SCREEN: return "Ecran împărțit";
            case LOCK_SCREEN: return "Blochează ecranul";
            case TAKE_SCREENSHOT: return "Captură de ecran";
            default: return "Acțiune " + action;
        }
    }
}
