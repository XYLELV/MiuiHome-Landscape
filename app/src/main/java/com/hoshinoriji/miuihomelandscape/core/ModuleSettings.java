package com.hoshinoriji.miuihomelandscape.core;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.hoshinoriji.miuihomelandscape.LandscapeBridge;

/**
 * Process-local settings snapshot. When called from the injected controller, values are stored
 * in com.miui.home's private SharedPreferences rather than in a world-readable file.
 */
public final class ModuleSettings {
    public static final int ROTATION_AUTO = 0;
    public static final int ROTATION_FORCE_LANDSCAPE = 1;
    public static final int ROTATION_FOLLOW_SYSTEM = 2;

    public static final int DOCK_APPEARANCE_AUTO = 0;
    public static final int DOCK_APPEARANCE_REGULAR = 1;
    public static final int DOCK_APPEARANCE_CLEAR = 2;

    public static final int DOCK_SIZE_COMPACT = 0;
    public static final int DOCK_SIZE_STANDARD = 1;
    public static final int DOCK_SIZE_LARGE = 2;

    public static final String PREFS_NAME = "miui_home_landscape_v5_settings";

    private static final String KEY_ROTATION_MODE = "rotation_mode";
    private static final String KEY_GRID_COLUMNS = "grid_columns";
    private static final String KEY_GRID_ROWS = "grid_rows";
    private static final String KEY_DOCK_SLOTS = "dock_slots";
    private static final String KEY_RECENTS_ENABLED = "recents_enabled";
    private static final String KEY_HIDE_GESTURE_HANDLE = "hide_gesture_handle";
    private static final String KEY_DIM_WALLPAPER = "dim_wallpaper";
    private static final String KEY_LABELS_ENABLED = "labels_enabled";
    private static final String KEY_ALLOW_UNSUPPORTED = "allow_unsupported";
    private static final String KEY_DOCK_ENABLED = "dock_enabled";
    private static final String KEY_DOCK_GLASS = "dock_glass";
    private static final String KEY_DOCK_OPACITY = "dock_opacity";
    private static final String KEY_DOCK_APPEARANCE = "dock_appearance";
    private static final String KEY_DOCK_SIZE = "dock_size";

    private final int rotationMode;
    private final int gridColumns;
    private final int gridRows;
    private final int dockSlots;
    private final boolean recentsEnabled;
    private final boolean hideGestureHandle;
    private final boolean dimWallpaper;
    private final boolean labelsEnabled;
    private final boolean allowUnsupported;
    private final boolean dockEnabled;
    private final boolean dockGlass;
    private final int dockOpacity;
    private final int dockAppearance;
    private final int dockSize;

    private ModuleSettings(SharedPreferences prefs) {
        rotationMode = sanitizeRotation(prefs.getInt(KEY_ROTATION_MODE, ROTATION_AUTO));
        gridColumns = clamp(prefs.getInt(KEY_GRID_COLUMNS, LandscapeBridge.GRID_COLUMNS), 6, 10);
        gridRows = clamp(prefs.getInt(KEY_GRID_ROWS, LandscapeBridge.GRID_ROWS), 2, 5);
        dockSlots = clamp(prefs.getInt(KEY_DOCK_SLOTS, LandscapeBridge.DOCK_SLOTS), 5, 12);
        recentsEnabled = prefs.getBoolean(KEY_RECENTS_ENABLED, true);
        hideGestureHandle = prefs.getBoolean(KEY_HIDE_GESTURE_HANDLE, false);
        dimWallpaper = prefs.getBoolean(KEY_DIM_WALLPAPER, true);
        labelsEnabled = prefs.getBoolean(KEY_LABELS_ENABLED, true);
        allowUnsupported = prefs.getBoolean(KEY_ALLOW_UNSUPPORTED, false);
        dockEnabled = prefs.getBoolean(KEY_DOCK_ENABLED, true);
        dockGlass = prefs.getBoolean(KEY_DOCK_GLASS, true);
        dockOpacity = clamp(prefs.getInt(KEY_DOCK_OPACITY, 52), 20, 90);
        dockAppearance = sanitizeDockAppearance(prefs.getInt(
                KEY_DOCK_APPEARANCE, DOCK_APPEARANCE_AUTO));
        dockSize = sanitizeDockSize(prefs.getInt(KEY_DOCK_SIZE, DOCK_SIZE_STANDARD));
    }

    public static ModuleSettings load(Context context) {
        return new ModuleSettings(prefs(context));
    }

    public static ModuleSettings updateFromIntent(Context context, Intent intent) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (intent != null) {
            if (intent.hasExtra(LandscapeBridge.EXTRA_ROTATION_MODE)) {
                editor.putInt(KEY_ROTATION_MODE, sanitizeRotation(intent.getIntExtra(
                        LandscapeBridge.EXTRA_ROTATION_MODE, ROTATION_AUTO)));
            }
            if (intent.hasExtra(LandscapeBridge.EXTRA_GRID_COLUMNS)) {
                editor.putInt(KEY_GRID_COLUMNS, clamp(intent.getIntExtra(
                        LandscapeBridge.EXTRA_GRID_COLUMNS, LandscapeBridge.GRID_COLUMNS), 6, 10));
            }
            if (intent.hasExtra(LandscapeBridge.EXTRA_GRID_ROWS)) {
                editor.putInt(KEY_GRID_ROWS, clamp(intent.getIntExtra(
                        LandscapeBridge.EXTRA_GRID_ROWS, LandscapeBridge.GRID_ROWS), 2, 5));
            }
            if (intent.hasExtra(LandscapeBridge.EXTRA_DOCK_SLOTS)) {
                editor.putInt(KEY_DOCK_SLOTS, clamp(intent.getIntExtra(
                        LandscapeBridge.EXTRA_DOCK_SLOTS, LandscapeBridge.DOCK_SLOTS), 5, 12));
            }
            putBooleanIfPresent(editor, intent,
                    LandscapeBridge.EXTRA_RECENTS_ENABLED, KEY_RECENTS_ENABLED);
            putBooleanIfPresent(editor, intent,
                    LandscapeBridge.EXTRA_HIDE_GESTURE_HANDLE, KEY_HIDE_GESTURE_HANDLE);
            putBooleanIfPresent(editor, intent,
                    LandscapeBridge.EXTRA_DIM_WALLPAPER, KEY_DIM_WALLPAPER);
            putBooleanIfPresent(editor, intent,
                    LandscapeBridge.EXTRA_LABELS_ENABLED, KEY_LABELS_ENABLED);
            putBooleanIfPresent(editor, intent,
                    LandscapeBridge.EXTRA_ALLOW_UNSUPPORTED, KEY_ALLOW_UNSUPPORTED);
            putBooleanIfPresent(editor, intent,
                    LandscapeBridge.EXTRA_DOCK_ENABLED, KEY_DOCK_ENABLED);
            putBooleanIfPresent(editor, intent,
                    LandscapeBridge.EXTRA_DOCK_GLASS, KEY_DOCK_GLASS);
            if (intent.hasExtra(LandscapeBridge.EXTRA_DOCK_OPACITY)) {
                editor.putInt(KEY_DOCK_OPACITY, clamp(intent.getIntExtra(
                        LandscapeBridge.EXTRA_DOCK_OPACITY, 52), 20, 90));
            }
            if (intent.hasExtra(LandscapeBridge.EXTRA_DOCK_APPEARANCE)) {
                editor.putInt(KEY_DOCK_APPEARANCE, sanitizeDockAppearance(intent.getIntExtra(
                        LandscapeBridge.EXTRA_DOCK_APPEARANCE, DOCK_APPEARANCE_AUTO)));
            }
            if (intent.hasExtra(LandscapeBridge.EXTRA_DOCK_SIZE)) {
                editor.putInt(KEY_DOCK_SIZE, sanitizeDockSize(intent.getIntExtra(
                        LandscapeBridge.EXTRA_DOCK_SIZE, DOCK_SIZE_STANDARD)));
            }
        }
        editor.apply();
        return load(context);
    }

    public int rotationMode() {
        return rotationMode;
    }

    public int gridColumns() {
        return gridColumns;
    }

    public int gridRows() {
        return gridRows;
    }

    public int dockSlots() {
        return dockSlots;
    }

    public boolean recentsEnabled() {
        return recentsEnabled;
    }

    public boolean hideGestureHandle() {
        return hideGestureHandle;
    }

    public boolean dimWallpaper() {
        return dimWallpaper;
    }

    public boolean labelsEnabled() {
        return labelsEnabled;
    }

    public boolean allowUnsupported() {
        return allowUnsupported;
    }

    public boolean dockEnabled() {
        return dockEnabled;
    }

    public boolean dockGlass() {
        return dockGlass;
    }

    public int dockOpacity() {
        return dockOpacity;
    }

    public int dockAppearance() {
        return dockAppearance;
    }

    public int dockSize() {
        return dockSize;
    }

    public void putInto(Intent intent) {
        intent.putExtra(LandscapeBridge.EXTRA_ROTATION_MODE, rotationMode);
        intent.putExtra(LandscapeBridge.EXTRA_GRID_COLUMNS, gridColumns);
        intent.putExtra(LandscapeBridge.EXTRA_GRID_ROWS, gridRows);
        intent.putExtra(LandscapeBridge.EXTRA_DOCK_SLOTS, dockSlots);
        intent.putExtra(LandscapeBridge.EXTRA_RECENTS_ENABLED, recentsEnabled);
        intent.putExtra(LandscapeBridge.EXTRA_HIDE_GESTURE_HANDLE, hideGestureHandle);
        intent.putExtra(LandscapeBridge.EXTRA_DIM_WALLPAPER, dimWallpaper);
        intent.putExtra(LandscapeBridge.EXTRA_LABELS_ENABLED, labelsEnabled);
        intent.putExtra(LandscapeBridge.EXTRA_ALLOW_UNSUPPORTED, allowUnsupported);
        intent.putExtra(LandscapeBridge.EXTRA_DOCK_ENABLED, dockEnabled);
        intent.putExtra(LandscapeBridge.EXTRA_DOCK_GLASS, dockGlass);
        intent.putExtra(LandscapeBridge.EXTRA_DOCK_OPACITY, dockOpacity);
        intent.putExtra(LandscapeBridge.EXTRA_DOCK_APPEARANCE, dockAppearance);
        intent.putExtra(LandscapeBridge.EXTRA_DOCK_SIZE, dockSize);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void putBooleanIfPresent(
            SharedPreferences.Editor editor, Intent intent, String extra, String key) {
        if (intent.hasExtra(extra)) {
            editor.putBoolean(key, intent.getBooleanExtra(extra, false));
        }
    }

    private static int sanitizeRotation(int value) {
        if (value == ROTATION_FORCE_LANDSCAPE || value == ROTATION_FOLLOW_SYSTEM) {
            return value;
        }
        return ROTATION_AUTO;
    }

    private static int sanitizeDockAppearance(int value) {
        if (value == DOCK_APPEARANCE_REGULAR || value == DOCK_APPEARANCE_CLEAR) {
            return value;
        }
        return DOCK_APPEARANCE_AUTO;
    }

    private static int sanitizeDockSize(int value) {
        if (value == DOCK_SIZE_COMPACT || value == DOCK_SIZE_LARGE) {
            return value;
        }
        return DOCK_SIZE_STANDARD;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
