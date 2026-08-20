package com.hoshinoriji.miuihomelandscape;

/**
 * The only IPC contract between the module application and the code injected into MIUI Home.
 * Commands are explicit broadcasts and the receiver in MIUI Home must require
 * {@link #PERMISSION_CONTROL} from the sender.
 */
public final class LandscapeBridge {
    public static final String MODULE_PKG = "com.hoshinoriji.miuihomelandscape.vili";
    public static final String HOME_PKG = "com.miui.home";
    public static final String PERMISSION_CONTROL = MODULE_PKG + ".permission.CONTROL";
    public static final String CONTROL_PERMISSION = PERMISSION_CONTROL;

    private static final String ACTION_PREFIX = MODULE_PKG + ".action.";

    public static final String ACTION_PING = ACTION_PREFIX + "PING_HOME";
    public static final String ACTION_PONG = ACTION_PREFIX + "PONG_HOME";
    public static final String ACTION_ADD_TO_LANDSCAPE = ACTION_PREFIX + "ADD_TO_LANDSCAPE";
    public static final String ACTION_IMPORT_ALL = ACTION_PREFIX + "IMPORT_ALL";
    public static final String ACTION_RESET_LAYOUT = ACTION_PREFIX + "RESET_LAYOUT";
    public static final String ACTION_ADD_RESULT = ACTION_PREFIX + "COMMAND_RESULT";
    public static final String ACTION_SETTINGS_UPDATE = ACTION_PREFIX + "SETTINGS_UPDATE";
    public static final String ACTION_QUERY_APP_STATES = ACTION_PREFIX + "QUERY_APP_STATES";
    public static final String ACTION_APP_STATES_RESULT = ACTION_PREFIX + "APP_STATES_RESULT";
    public static final String ACTION_SET_APP_ENABLED = ACTION_PREFIX + "SET_APP_ENABLED";
    public static final String ACTION_APP_TOGGLE_RESULT = ACTION_PREFIX + "APP_TOGGLE_RESULT";

    public static final String EXTRA_PACKAGE = "package_name";
    public static final String EXTRA_CLASS = "class_name";
    public static final String EXTRA_USER_SERIAL = "user_serial";
    public static final String EXTRA_ADDED = "added";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_VERSION = "version";
    public static final String EXTRA_NONCE = "nonce";
    public static final String EXTRA_PROFILE_ENABLED = "profile_enabled";
    public static final String EXTRA_PROFILE_SUMMARY = "profile_summary";

    public static final String EXTRA_ROTATION_MODE = "rotation_mode";
    public static final String EXTRA_GRID_COLUMNS = "grid_columns";
    public static final String EXTRA_GRID_ROWS = "grid_rows";
    public static final String EXTRA_DOCK_SLOTS = "dock_slots";
    public static final String EXTRA_RECENTS_ENABLED = "recents_enabled";
    public static final String EXTRA_HIDE_GESTURE_HANDLE = "hide_gesture_handle";
    public static final String EXTRA_DIM_WALLPAPER = "dim_wallpaper";
    public static final String EXTRA_LABELS_ENABLED = "labels_enabled";
    public static final String EXTRA_ALLOW_UNSUPPORTED = "allow_unsupported";
    public static final String EXTRA_DOCK_ENABLED = "dock_enabled";
    public static final String EXTRA_DOCK_GLASS = "dock_glass";
    public static final String EXTRA_DOCK_OPACITY = "dock_opacity";
    public static final String EXTRA_DOCK_APPEARANCE = "dock_appearance";
    public static final String EXTRA_DOCK_SIZE = "dock_size";
    public static final String EXTRA_COMPONENT_KEYS = "component_keys";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_SUCCESS = "success";

    public static final int GRID_COLUMNS = 8;
    public static final int GRID_ROWS = 3;
    public static final int DOCK_SLOTS = 9;

    private LandscapeBridge() {
    }
}
