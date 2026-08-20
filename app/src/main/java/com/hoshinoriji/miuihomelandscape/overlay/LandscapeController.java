package com.hoshinoriji.miuihomelandscape.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoshinoriji.miuihomelandscape.LandscapeBridge;
import com.hoshinoriji.miuihomelandscape.MiuiHomeLandscapeModule;
import com.hoshinoriji.miuihomelandscape.core.ModuleSettings;
import com.hoshinoriji.miuihomelandscape.core.NativeViewLease;
import com.hoshinoriji.miuihomelandscape.core.WindowLease;
import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;
import com.hoshinoriji.miuihomelandscape.overlay.recents.LandscapeRecentsController;
import com.hoshinoriji.miuihomelandscape.store.LandscapeStore;

import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XposedBridge;

/**
 * One reversible landscape session for one MIUI Launcher Activity.
 *
 * <p>The controller owns only the module overlay.  MIUI remains authoritative
 * for portrait and for the Recents state machine.  There are no global state
 * flags, no watchdog pulses, no native listener replacement, and no automatic
 * data reset.</p>
 */
public final class LandscapeController {
    private static final String TAG = "[MIHL5/Controller] ";
    private static final Map<Activity, LandscapeController> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final WeakReference<Activity> activityRef;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mihl-vili-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final NativeViewLease nativeViews = new NativeViewLease();
    private final WindowLease window = new WindowLease();
    private final IconCatalog icons;
    private final LandscapeStore store;

    private ViewGroup parent;
    private LandscapeOverlayView overlay;
    private LandscapeRecentsController recents;
    private View nativeWorkspace;
    private View nativeHotseat;
    private View nativeDragLayer;
    private View nativeScreenContent;
    private View nativeRecentsView;
    private ModuleSettings settings;
    private BroadcastReceiver commandReceiver;
    private View folderPanel;

    private boolean attached;
    private boolean resumed;
    private boolean destroyed;
    private boolean nativeRecentsVisible;
    private boolean customRecentsVisible;
    private boolean refreshPosted;
    private boolean refreshAgain;
    private boolean seedRunning;
    private boolean storeWarningShown;
    private boolean storeHealthKnown;
    private boolean storeReadable;
    private boolean layoutMutationPending;
    private boolean desktopEditMode;
    private long pendingRefreshRevision = -1L;
    private boolean orientationLeased;
    private int attachAttempts;
    private int originalRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private volatile long generation;

    private LandscapeController(Activity activity) {
        activityRef = new WeakReference<>(activity);
        store = LandscapeStore.get(activity);
        icons = new IconCatalog(activity);
        settings = ModuleSettings.load(activity);
    }

    public static LandscapeController forActivity(Activity activity) {
        if (activity == null) throw new IllegalArgumentException("activity == null");
        if (activity.isFinishing() || activity.isDestroyed()) {
            throw new IllegalStateException("Launcher Activity is finishing or destroyed");
        }
        synchronized (INSTANCES) {
            LandscapeController controller = INSTANCES.get(activity);
            if (controller == null) {
                controller = new LandscapeController(activity);
                INSTANCES.put(activity, controller);
            }
            return controller;
        }
    }

    public static void dispose(Activity activity) {
        dispose(activity, true);
    }

    /** Activity recreation must not briefly restore MIUI's portrait manifest request. */
    public static void disposeForActivityDestroy(Activity activity) {
        dispose(activity, false);
    }

    private static void dispose(Activity activity, boolean restoreOrientation) {
        LandscapeController controller;
        synchronized (INSTANCES) {
            controller = INSTANCES.remove(activity);
        }
        if (controller != null) controller.disposeInternal(restoreOrientation);
    }

    /** Entry point used only by the precise MIUI RecentsContainer hook. */
    public static void onNativeRecentsVisibility(Activity activity, View nativeView,
            boolean visible, String source) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        LandscapeController controller = INSTANCES.get(activity);
        if (controller != null) {
            controller.handleNativeRecentsVisibility(nativeView, visible, source);
        }
    }

    /** Exact MIUI gesture-complete signal; custom Recents must not take over mid-swipe. */
    public static void onNativeRecentsGestureSettled(
            Activity activity, View nativeView, String source) {
        if (activity == null || nativeView == null
                || activity.isFinishing() || activity.isDestroyed()) return;
        LandscapeController controller = INSTANCES.get(activity);
        if (controller != null && controller.recents != null) {
            controller.recents.onNativeGestureSettled(nativeView, source);
        }
    }

    public static boolean isInternalNativeRecentsMutation(Activity activity, View nativeView) {
        if (activity == null || nativeView == null) return false;
        LandscapeController controller = INSTANCES.get(activity);
        return controller != null && controller.recents != null
                && controller.recents.isInternalNativeMutation(nativeView);
    }

    public void onLauncherCreate() {
        runOnMain(() -> {
            if (destroyed) return;
            registerCommandReceiver();
            attachIfReady();
            reconcile("create");
        });
    }

    public void onConfigurationChanged(Configuration ignored) {
        runOnMain(() -> {
            if (destroyed) return;
            generation++;
            reconcile("configuration");
        });
    }

    public void onResume() {
        runOnMain(() -> {
            if (destroyed) return;
            resumed = true;
            settings = ModuleSettings.load(context());
            attachIfReady();
            if (recents != null && nativeRecentsView != null
                    && nativeRecentsView.isAttachedToWindow()) {
                // During custom takeover the native authority is intentionally INVISIBLE. Do not
                // mistake our own mutation for MIUI closing Recents across pause/resume.
                boolean currentlyVisible = customRecentsVisible
                        || nativeRecentsView.getVisibility() == View.VISIBLE;
                nativeRecentsVisible = currentlyVisible;
                recents.onNativeVisibilityChanged(nativeRecentsView, currentlyVisible,
                        "host-resume-authority-check");
            }
            reconcile("resume");
        });
    }

    public void onPause() {
        runOnMain(() -> {
            resumed = false;
            generation++;
            Activity activity = activity();
            boolean landscapeNow = activity != null
                    && activity.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            boolean preserveGestureRecents = landscapeNow && nativeRecentsVisible
                    && recents != null && settings.recentsEnabled();
            if (!preserveGestureRecents) {
                nativeRecentsVisible = false;
                customRecentsVisible = false;
                if (recents != null) recents.onHostPaused();
            }
            reconcile("pause");
        });
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        runOnMain(() -> {
            if (destroyed || !hasFocus) return;
            reconcile("focus");
        });
    }

    private void attachIfReady() {
        Activity activity = activity();
        if (activity == null || attached || destroyed) return;
        View content = activity.findViewById(android.R.id.content);
        resolveNativeViews(activity);
        if (!(content instanceof ViewGroup)) {
            if (++attachAttempts <= 8 && activity.getWindow() != null) {
                activity.getWindow().getDecorView().post(this::attachIfReady);
            } else {
                log("attach failed: android.R.id.content missing");
            }
            return;
        }

        parent = (ViewGroup) content;
        overlay = new LandscapeOverlayView(activity);
        overlay.setVisibility(View.GONE);
        overlay.setOnBlankLongPressListener(this::enterDesktopEditMode);
        overlay.setEditListener(new LandscapeOverlayView.EditListener() {
            @Override public void onDone() {
                exitDesktopEditMode();
            }
        });
        bindOverlayCallbacks();
        parent.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        recents = new LandscapeRecentsController(activity, parent,
                showing -> {
                    customRecentsVisible = showing;
                    reconcile("custom-recents=" + showing);
                });
        attached = true;
        attachAttempts = 0;
        overlay.requestApplyInsets();
        log("attached parent=" + parent.getClass().getName());
    }

    private void bindOverlayCallbacks() {
        overlay.getGrid().setListener(new LandscapePagedGridView.Listener() {
            @Override public void onAppClick(LandscapeItem item) {
                if (item == null) return;
                if (item.isFolder()) openFolder(item);
                else launch(item.key);
            }

            @Override public void onAppLongPress(LandscapeItem item, GridPosition pos,
                    View source) {
                if (item == null || pos == null) return;
                LandscapePagedGridView.startCellDrag(source,
                        "grid:" + pos.pageIndex + ":" + pos.slotIndex);
            }

            @Override public void onAppRemoveRequest(LandscapeItem item, GridPosition pos) {
                confirmRemoveGrid(item, pos);
            }

            @Override public void onEmptySlotLongPress() {
                enterDesktopEditMode();
            }

            @Override public void onEditModeExitRequest() {
                exitDesktopEditMode();
            }

            @Override public void onDragHover(String fromDescriptor, GridPosition over,
                    int screenX, int screenY) {
                // Center-drop onto a folder is handled atomically on DROP.  We do
                // not open another Window while Android owns an active drag.
            }

            @Override public void onDropOnGrid(String fromDescriptor, GridPosition to) {
                routeGridDrop(fromDescriptor, to, false);
            }

            @Override public void onInsertOnGrid(String fromDescriptor, GridPosition to) {
                routeGridDrop(fromDescriptor, to, true);
            }
        });

        overlay.getDock().setListener(new LandscapeDockView.Listener() {
            @Override public void onAppClick(LandscapeItem item) {
                if (item != null && item.key != null) launch(item.key);
            }

            @Override public void onAppRemoveRequest(
                    LandscapeItem item, DockPosition position) {
                confirmRemoveDock(item, position);
            }

            @Override public void onDropOnDock(String fromDescriptor, DockPosition to) {
                routeDockDrop(fromDescriptor, to);
            }
        });
    }

    private void reconcile(String reason) {
        Activity activity = activity();
        if (activity == null || destroyed) return;
        if (!attached) {
            attachIfReady();
            if (!attached) return;
        }

        settings = ModuleSettings.load(activity);
        applyRequestedOrientation(activity);

        boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean active = resumed && landscape;
        if (!active) {
            exitDesktopEditMode();
            if (overlay != null) overlay.setVisibility(View.GONE);
            // A gesture-driven Recents state can legitimately pause Launcher while remaining in
            // the same ViewRoot. Only an actual portrait configuration ends landscape Recents.
            if (recents != null) recents.onLandscapeChanged(landscape);
            nativeViews.restoreAll();
            window.restore(activity);
            dismissFolder();
            return;
        }

        if (!storeHealthKnown || !storeReadable) {
            exitDesktopEditMode();
            overlay.setVisibility(View.GONE);
            if (recents != null) recents.onLandscapeChanged(false);
            nativeViews.restoreAll();
            window.restore(activity);
            dismissFolder();
            if (storeHealthKnown && !storeWarningShown) {
                storeWarningShown = true;
                showToast("横屏布局数据损坏，已保留原数据；请在设置页确认后重置");
            }
            if (!storeHealthKnown) {
                refreshOverlay("layout-health-probe");
            } else {
                log("layout unreadable; native MIUI Home retained");
            }
            return;
        }
        storeWarningShown = false;

        resolveNativeViews(activity);
        nativeViews.hide(nativeWorkspace, nativeHotseat);
        window.apply(activity, settings.hideGestureHandle());
        overlay.setDimWallpaper(settings.dimWallpaper());
        overlay.getGrid().setLabelsEnabled(settings.labelsEnabled());
        overlay.applyDockStyle(settings.dockEnabled(), settings.dockAppearance(),
                settings.dockSize(), isWallpaperBright(activity));
        if (recents != null) recents.onLandscapeChanged(true);

        boolean showHome = !nativeRecentsVisible && !customRecentsVisible;
        overlay.setVisibility(showHome ? View.VISIBLE : View.GONE);
        if (!showHome) {
            exitDesktopEditMode();
            dismissFolder();
        }
        if (showHome) {
            overlay.bringToFront();
            overlay.requestApplyInsets();
            scheduleRefresh(reason);
        }
        log("state reason=" + reason + " home=" + showHome
                + " nativeRecents=" + nativeRecentsVisible
                + " customRecents=" + customRecentsVisible);
    }

    private static boolean isWallpaperBright(Activity activity) {
        try {
            WallpaperColors colors = WallpaperManager.getInstance(activity)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors == null || colors.getPrimaryColor() == null) return true;
            int color = colors.getPrimaryColor().toArgb();
            double red = linearChannel(Color.red(color) / 255.0);
            double green = linearChannel(Color.green(color) / 255.0);
            double blue = linearChannel(Color.blue(color) / 255.0);
            return 0.2126 * red + 0.7152 * green + 0.0722 * blue >= 0.42;
        } catch (Throwable error) {
            log("wallpaper colors unavailable: " + error);
            return true;
        }
    }

    private static double linearChannel(double channel) {
        return channel <= 0.04045
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private void handleNativeRecentsVisibility(View nativeView, boolean visible, String source) {
        runOnMain(() -> {
            if (destroyed) return;
            if (recents != null && recents.isInternalNativeMutation(nativeView)) {
                return;
            }
            nativeRecentsView = nativeView;
            nativeRecentsVisible = visible;
            if (recents != null && settings.recentsEnabled()) {
                recents.onNativeVisibilityChanged(nativeView, visible, source);
                // A native GONE emitted by removeTask is not a user exit. Recents filters that
                // signal synchronously and exposes the effective authority back to the reducer.
                nativeRecentsVisible = recents.hasNativeAuthority();
            } else if (recents != null) {
                recents.onNativeVisibilityChanged(nativeView, false, "disabled");
            }
            reconcile("native-recents=" + visible + "/" + source);
        });
    }

    /**
     * MIUI Home declares portrait in its manifest.  Apply an explicit runtime
     * request once per session so merely intercepting later portrait locks is
     * not required for the initial transition.
     */
    private void applyRequestedOrientation(Activity activity) {
        if (!orientationLeased) {
            originalRequestedOrientation =
                    MiuiHomeLandscapeModule.originalRequestedOrientation(activity);
            orientationLeased = true;
        }
        int desired;
        if (settings.rotationMode() == ModuleSettings.ROTATION_FORCE_LANDSCAPE) {
            desired = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        } else if (settings.rotationMode() == ModuleSettings.ROTATION_FOLLOW_SYSTEM) {
            desired = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        } else {
            desired = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        }
        if (activity.getRequestedOrientation() != desired) {
            activity.setRequestedOrientation(desired);
        }
    }

    private void restoreRequestedOrientation(Activity activity) {
        if (!orientationLeased || activity == null) return;
        orientationLeased = false;
        if (activity.getRequestedOrientation() != originalRequestedOrientation) {
            MiuiHomeLandscapeModule.setRequestedOrientationInternal(
                    activity, originalRequestedOrientation);
        }
    }

    private void scheduleRefresh(String reason) {
        refreshOverlay(reason);
    }

    private void refreshOverlay(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> refreshOverlay(reason));
            return;
        }
        if (destroyed || overlay == null) return;
        if (refreshPosted) {
            refreshAgain = true;
            return;
        }
        refreshPosted = true;
        final long token = generation;
        try {
            worker.execute(() -> {
                LandscapeStore.LayoutRead layout = store.readLayout();
                if (layout.isReadable()) icons.ensureWarm();
                main.post(() -> {
                    refreshPosted = false;
                    if (destroyed || overlay == null) return;
                    boolean healthWasKnown = storeHealthKnown;
                    storeHealthKnown = true;
                    storeReadable = layout.isReadable();
                    if (pendingRefreshRevision >= 0L
                            && layout.isReadable()
                            && layout.revision() >= pendingRefreshRevision) {
                        pendingRefreshRevision = -1L;
                        layoutMutationPending = false;
                    }
                    if (!layout.isReadable()) {
                        // Never bind an unreadable snapshot as an empty desktop.
                        reconcile("layout-became-unreadable");
                    } else if (!healthWasKnown
                            && overlay.getVisibility() != View.VISIBLE) {
                        reconcile("layout-health-ready");
                    } else if (token == generation
                            && overlay.getVisibility() == View.VISIBLE) {
                        try {
                            overlay.getGrid().bind(layout.grid(), icons);
                            overlay.getDock().bind(layout.dock(), icons);
                            if (!layout.isInitialized() && !seedRunning) seedAllApps(false);
                            log("bind reason=" + reason + " revision=" + layout.revision()
                                    + " grid=" + layout.grid().size()
                                    + " dock=" + layout.dock().size());
                        } catch (Throwable error) {
                            log("bind failed, preserving last layout: " + error);
                        }
                    }
                    if (refreshAgain) {
                        refreshAgain = false;
                        refreshOverlay("coalesced-after-" + reason);
                    }
                });
            });
        } catch (RuntimeException rejected) {
            refreshPosted = false;
            log("refresh worker rejected: " + rejected);
        }
    }

    private void seedAllApps(boolean explicitImport) {
        if (seedRunning || destroyed) return;
        seedRunning = true;
        if (explicitImport) icons.invalidate();
        final long token = generation;
        worker.execute(() -> {
            try {
                List<ComponentKey> keys = icons.enumerateAll();
                if (!destroyed && (explicitImport || token == generation)) {
                    store.appendUniqueToGrid(keys);
                }
            } catch (Throwable error) {
                log("import failed: " + error);
            } finally {
                main.post(() -> {
                    seedRunning = false;
                    if (!destroyed && (explicitImport || token == generation)) {
                        refreshOverlay(explicitImport ? "import" : "first-seed");
                    }
                });
            }
        });
    }

    private void enterDesktopEditMode() {
        if (destroyed || overlay == null || overlay.getVisibility() != View.VISIBLE) return;
        dismissFolder();
        desktopEditMode = true;
        overlay.setEditMode(true);
        showToast("已进入横屏桌面编辑");
        log("desktop edit entered");
    }

    private void exitDesktopEditMode() {
        if (!desktopEditMode && (overlay == null || !overlay.isEditMode())) return;
        desktopEditMode = false;
        if (overlay != null) overlay.setEditMode(false);
        log("desktop edit exited");
    }

    private void confirmRemoveGrid(LandscapeItem item, GridPosition position) {
        Activity activity = activity();
        if (activity == null || item == null || position == null) return;
        String name = item.isFolder() ? safeTitle(item.folderTitle) : icons.getLabel(item.key).toString();
        new AlertDialog.Builder(activity)
                .setTitle("从横屏桌面移除")
                .setMessage(name + "\n不会卸载应用，也不会修改竖屏桌面。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    mutateLayout("remove", () -> store.removeGrid(position),
                            "移除失败，原布局已保留");
                })
                .show();
    }

    private void confirmRemoveDock(LandscapeItem item, DockPosition position) {
        Activity activity = activity();
        if (activity == null || item == null || position == null) return;
        String name = item.key == null ? "应用" : icons.getLabel(item.key).toString();
        new AlertDialog.Builder(activity)
                .setTitle("从横屏 Dock 移除")
                .setMessage(name + "\n不会卸载应用，也不会修改竖屏桌面。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    mutateLayout("remove-dock", () -> store.removeDock(position),
                            "移除失败，原布局已保留");
                })
                .show();
    }

    private void routeGridDrop(String descriptor, GridPosition to, boolean insert) {
        DragSource source = DragSource.parse(descriptor);
        if (source == null || to == null) return;
        dismissFolder();
        mutateLayout(insert ? "insert-grid" : "drop-grid", () -> {
            LandscapeItem target = store.getGridItem(to);
            switch (source.kind) {
                case GRID: {
                    GridPosition from = new GridPosition(source.first, source.second);
                    if (from.equals(to)) return;
                    LandscapeItem moving = store.getGridItem(from);
                    if (insert) {
                        store.insertGrid(from, to);
                    } else if (moving != null && moving.isFolder()) {
                        store.moveOrSwapGrid(from, to);
                    } else if (target != null && target.isFolder()) {
                        store.addGridToFolder(from, target.folderId);
                    } else if (target != null && !target.isFolder()) {
                        store.createFolderFromGrid(from, to);
                    } else {
                        store.moveOrSwapGrid(from, to);
                    }
                    break;
                }
                case DOCK: {
                    DockPosition from = new DockPosition(source.first);
                    if (insert) {
                        store.insertDockToGrid(from, to);
                    } else if (target != null && target.isFolder()) {
                        store.addDockToFolder(from, target.folderId);
                    } else if (target != null && !target.isFolder()) {
                        store.createFolderFromDock(from, to);
                    } else {
                        store.moveDockToGrid(from, to);
                    }
                    break;
                }
                case FOLDER:
                    if (insert) {
                        store.removeFolderChildToGrid(source.folderId, source.second, to, true);
                    } else if (target != null && target.isFolder()) {
                        store.moveFolderChildToFolder(
                                source.folderId, source.second, target.folderId);
                    } else if (target != null) {
                        store.createFolderFromFolderChild(
                                source.folderId, source.second, to);
                    } else {
                        store.removeFolderChildToGrid(
                                source.folderId, source.second, to, false);
                    }
                    break;
            }
        }, "移动未完成，原布局已保留");
    }

    private void routeDockDrop(String descriptor, DockPosition to) {
        DragSource source = DragSource.parse(descriptor);
        if (source == null || to == null) return;
        dismissFolder();
        mutateLayout("drop-dock", () -> {
            switch (source.kind) {
                case GRID: {
                    GridPosition from = new GridPosition(source.first, source.second);
                    LandscapeItem moving = store.getGridItem(from);
                    if (moving != null && moving.isFolder()) {
                        return;
                    }
                    store.moveGridToDock(from, to);
                    break;
                }
                case DOCK:
                    store.moveOrSwapDock(new DockPosition(source.first), to);
                    break;
                case FOLDER:
                    store.moveFolderChildToDock(source.folderId, source.second, to);
                    break;
            }
        }, "Dock 移动未完成，文件夹不能放入 Dock");
    }

    private void openFolder(LandscapeItem requested) {
        Activity activity = activity();
        if (activity == null || requested == null || !requested.isFolder()) return;
        LandscapeItem folder = requested;
        dismissFolder();

        if (parent == null) return;
        FrameLayout panel = new FrameLayout(activity);
        panel.setBackgroundColor(0x33000000);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setOnClickListener(v -> dismissFolder());

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(18), dp(24), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEE202124);
        background.setCornerRadius(dp(30));
        background.setStroke(dp(1), 0x44FFFFFF);
        root.setBackground(background);
        // Consume taps inside the card so only its dimmed outer area closes it.
        root.setClickable(true);
        root.setOnClickListener(v -> { });
        Rect folderCardBounds = new Rect();
        panel.setOnDragListener((view, event) -> {
            if (event.getClipDescription() == null
                    || !LandscapePagedGridView.DRAG_LABEL.contentEquals(
                    event.getClipDescription().getLabel())) {
                return false;
            }
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                root.getHitRect(folderCardBounds);
                if (!folderCardBounds.contains(
                        Math.round(event.getX()), Math.round(event.getY()))) {
                    // The source and destination remain in one ViewRoot. Hiding only the
                    // panel exposes the Grid/Dock targets without turning this into a global drag.
                    panel.setVisibility(View.INVISIBLE);
                }
            } else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                restoreFolderPanelIfPresent();
            }
            return true;
        });

        EditText title = new EditText(activity);
        title.setSingleLine(true);
        title.setText(safeTitle(folder.folderTitle));
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(Color.TRANSPARENT);
        title.setSelectAllOnFocus(true);
        title.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Runnable saveTitle = () -> {
            String nextTitle = title.getText().toString();
            mutateLayout("rename-folder",
                    () -> store.renameFolder(folder.folderId, nextTitle), null);
        };
        title.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveTitle.run();
                title.clearFocus();
                return true;
            }
            return false;
        });
        title.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) saveTitle.run(); });
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(true);
        for (int index = 0; index < folder.folderChildren.size(); index++) {
            ComponentKey key = folder.folderChildren.get(index);
            View child = buildFolderChild(folder.folderId, index, key);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(104);
            params.height = dp(112);
            grid.addView(child, params);
        }
        ScrollView folderScroll = new ScrollView(activity);
        folderScroll.setFillViewport(true);
        folderScroll.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(folderScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = new Button(activity);
        add.setText("添加应用");
        add.setOnClickListener(v -> AppPickerDialog.show(activity, picks -> {
            if (picks == null || picks.isEmpty()) return;
            dismissFolder();
            mutateLayout("folder-add",
                    () -> store.addComponentsToFolder(folder.folderId, picks),
                    "没有可加入此文件夹的新应用");
        }));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        addParams.topMargin = dp(10);
        root.addView(add, addParams);

        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(48),
                dp(560));
        int height = Math.min(activity.getResources().getDisplayMetrics().heightPixels - dp(48),
                dp(420));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                Math.max(dp(320), width), Math.max(dp(240), height), Gravity.CENTER);
        panel.addView(root, cardParams);
        parent.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        folderPanel = panel;
        panel.bringToFront();
    }

    private View buildFolderChild(long folderId, int index, ComponentKey key) {
        Activity activity = activity();
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(4), dp(6), dp(4), dp(4));

        ImageView icon = new ImageView(activity);
        Drawable drawable = icons.getIcon(key);
        if (drawable != null) icon.setImageDrawable(drawable);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView label = new TextView(activity);
        label.setText(icons.getLabel(key));
        label.setTextColor(Color.WHITE);
        label.setTextSize(11f);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        cell.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        cell.setOnClickListener(v -> launch(key));
        cell.setOnLongClickListener(v -> {
            LandscapePagedGridView.startCellDrag(v,
                    "folder:" + folderId + ":" + index);
            return true;
        });
        cell.setOnDragListener((v, event) -> handleFolderChildDrag(
                folderId, index, event));
        return cell;
    }

    private boolean handleFolderChildDrag(long folderId, int targetIndex,
            DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return event.getClipDescription() != null
                    && LandscapePagedGridView.DRAG_LABEL.contentEquals(
                    event.getClipDescription().getLabel());
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            restoreFolderPanelIfPresent();
            return true;
        }
        if (event.getAction() != DragEvent.ACTION_DROP) return true;
        DragSource source = DragSource.fromEvent(event);
        if (source == null) return true;
        dismissFolder();
        mutateLayout("folder-reorder", () -> {
            if (source.kind == SourceKind.FOLDER && source.folderId == folderId) {
                store.moveFolderChild(folderId, source.second, targetIndex);
            } else if (source.kind == SourceKind.GRID) {
                store.insertGridToFolder(new GridPosition(source.first, source.second),
                        folderId, targetIndex);
            } else if (source.kind == SourceKind.DOCK) {
                store.insertDockToFolder(new DockPosition(source.first), folderId, targetIndex);
            }
        }, "文件夹排列未改变");
        return true;
    }

    private void launch(ComponentKey key) {
        Activity activity = activity();
        if (activity == null || key == null) return;
        try {
            icons.launch(key);
        } catch (Throwable error) {
            log("launch " + key + ": " + error);
            showToast("无法启动这个应用");
        }
    }

    private void resolveNativeViews(Activity activity) {
        nativeWorkspace = findByName(activity, "workspace");
        nativeHotseat = firstNonNull(findByName(activity, "hotseat"),
                findByName(activity, "hotseats"));
        nativeDragLayer = findByName(activity, "drag_layer");
        nativeScreenContent = findByName(activity, "screen_content");
    }

    private static View findByName(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", LandscapeBridge.HOME_PKG);
        return id == 0 ? null : activity.findViewById(id);
    }

    private static View firstNonNull(View first, View second) {
        return first != null ? first : second;
    }

    private void registerCommandReceiver() {
        Activity activity = activity();
        if (activity == null || commandReceiver != null) return;
        commandReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                if (LandscapeBridge.ACTION_IMPORT_ALL.equals(action)) {
                    seedAllApps(true);
                } else if (LandscapeBridge.ACTION_QUERY_APP_STATES.equals(action)) {
                    sendAppStates(intent.getStringExtra(LandscapeBridge.EXTRA_NONCE));
                } else if (LandscapeBridge.ACTION_SET_APP_ENABLED.equals(action)) {
                    setAppEnabledFromSettings(intent);
                } else if (LandscapeBridge.ACTION_RESET_LAYOUT.equals(action)) {
                    worker.execute(() -> {
                        boolean reset = store.resetLayout();
                        main.post(() -> {
                            if (destroyed) return;
                            if (reset) {
                                storeWarningShown = false;
                                storeHealthKnown = true;
                                storeReadable = true;
                                layoutMutationPending = false;
                                pendingRefreshRevision = -1L;
                                icons.invalidate();
                                reconcile("explicit-reset");
                            } else {
                                showToast("重置失败，原布局仍被保留");
                            }
                        });
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(LandscapeBridge.ACTION_IMPORT_ALL);
        filter.addAction(LandscapeBridge.ACTION_RESET_LAYOUT);
        filter.addAction(LandscapeBridge.ACTION_QUERY_APP_STATES);
        filter.addAction(LandscapeBridge.ACTION_SET_APP_ENABLED);
        activity.registerReceiver(commandReceiver, filter,
                LandscapeBridge.PERMISSION_CONTROL, main, Context.RECEIVER_EXPORTED);
    }

    private void sendAppStates(String nonce) {
        if (nonce == null || nonce.length() > 128 || destroyed) return;
        worker.execute(() -> {
            Set<ComponentKey> keys = store.listComponentKeys();
            ArrayList<String> encoded = new ArrayList<>();
            if (keys != null) {
                for (ComponentKey key : keys) {
                    if (key != null) encoded.add(key.encode());
                }
            }
            main.post(() -> {
                Activity activity = activity();
                if (destroyed || activity == null) return;
                Intent result = new Intent(LandscapeBridge.ACTION_APP_STATES_RESULT)
                        .setPackage(LandscapeBridge.MODULE_PKG)
                        .putExtra(LandscapeBridge.EXTRA_NONCE, nonce)
                        .putExtra(LandscapeBridge.EXTRA_SUCCESS, keys != null)
                        .putStringArrayListExtra(LandscapeBridge.EXTRA_COMPONENT_KEYS, encoded);
                activity.sendBroadcast(result);
            });
        });
    }

    private void setAppEnabledFromSettings(Intent command) {
        String nonce = command.getStringExtra(LandscapeBridge.EXTRA_NONCE);
        ComponentKey key = ComponentKey.decode(command.getStringExtra(
                LandscapeBridge.EXTRA_COMPONENT_KEYS));
        boolean enabled = command.getBooleanExtra(LandscapeBridge.EXTRA_ENABLED, false);
        if (nonce == null || nonce.length() > 128 || key == null || destroyed) return;
        worker.execute(() -> {
            long before = store.getRevision();
            boolean success = store.setComponentEnabled(key, enabled);
            Set<ComponentKey> current = store.listComponentKeys();
            boolean actual = current != null && current.contains(key);
            long after = store.getRevision();
            main.post(() -> {
                Activity activity = activity();
                if (destroyed || activity == null) return;
                if (success && after > before) refreshOverlay("app-toggle");
                Intent result = new Intent(LandscapeBridge.ACTION_APP_TOGGLE_RESULT)
                        .setPackage(LandscapeBridge.MODULE_PKG)
                        .putExtra(LandscapeBridge.EXTRA_NONCE, nonce)
                        .putExtra(LandscapeBridge.EXTRA_COMPONENT_KEYS, key.encode())
                        .putExtra(LandscapeBridge.EXTRA_ENABLED, actual)
                        .putExtra(LandscapeBridge.EXTRA_SUCCESS, success && actual == enabled);
                activity.sendBroadcast(result);
            });
        });
    }

    private void unregisterCommandReceiver() {
        Activity activity = activity();
        if (activity == null || commandReceiver == null) return;
        try { activity.unregisterReceiver(commandReceiver); }
        catch (Throwable ignored) {}
        commandReceiver = null;
    }

    private void mutateLayout(String reason, LayoutMutation mutation, String noChangeMessage) {
        if (destroyed || mutation == null) return;
        if (layoutMutationPending) {
            showToast("正在保存上一项布局操作，请稍候");
            return;
        }
        layoutMutationPending = true;
        pendingRefreshRevision = -1L;
        try {
            worker.execute(() -> {
                long before = store.getRevision();
                Throwable failure = null;
                try {
                    mutation.run();
                } catch (Throwable error) {
                    failure = error;
                }
                long after = store.getRevision();
                boolean changed = before >= 0L && after > before;
                Throwable finalFailure = failure;
                main.post(() -> {
                    if (destroyed) return;
                    if (finalFailure != null) {
                        layoutMutationPending = false;
                        log(reason + " failed: " + finalFailure);
                        showToast(noChangeMessage == null
                                ? "布局操作失败，原数据已保留" : noChangeMessage);
                        return;
                    }
                    if (!changed) {
                        layoutMutationPending = false;
                        if (noChangeMessage != null) showToast(noChangeMessage);
                    } else {
                        pendingRefreshRevision = after;
                        refreshOverlay(reason);
                    }
                });
            });
        } catch (RuntimeException rejected) {
            layoutMutationPending = false;
            log(reason + " worker rejected: " + rejected);
            if (noChangeMessage != null) showToast(noChangeMessage);
        }
    }

    private void disposeInternal(boolean restoreOrientation) {
        runOnMain(() -> {
            if (destroyed) return;
            destroyed = true;
            generation++;
            Activity activity = activity();
            desktopEditMode = false;
            if (overlay != null) overlay.setEditMode(false);
            unregisterCommandReceiver();
            dismissFolder();
            if (recents != null) recents.dispose();
            recents = null;
            nativeViews.restoreAll();
            window.restore(activity);
            if (restoreOrientation) restoreRequestedOrientation(activity);
            if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
            overlay = null;
            icons.invalidate();
            worker.shutdownNow();
            log("disposed");
        });
    }

    private void dismissFolder() {
        View panel = folderPanel;
        folderPanel = null;
        if (panel != null && panel.getParent() instanceof ViewGroup) {
            try { ((ViewGroup) panel.getParent()).removeView(panel); }
            catch (Throwable ignored) {}
        }
    }

    private void restoreFolderPanelIfPresent() {
        View panel = folderPanel;
        if (panel != null && panel.getParent() != null
                && panel.getVisibility() != View.VISIBLE) {
            panel.setVisibility(View.VISIBLE);
        }
    }

    private Activity activity() {
        return activityRef.get();
    }

    private Context context() {
        return activity();
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(action);
    }

    private void showToast(String text) {
        Activity activity = activity();
        if (activity != null) Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        Activity activity = activity();
        if (activity == null) return value;
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String safeTitle(String title) {
        String clean = title == null ? "" : title.trim();
        return clean.isEmpty() ? "文件夹" : clean;
    }

    private static void log(String message) {
        String line = TAG + message;
        XposedBridge.log(line);
        android.util.Log.i("MIHL5", line);
    }

    private enum SourceKind { GRID, DOCK, FOLDER }

    private interface LayoutMutation {
        void run();
    }

    private static final class DragSource {
        final SourceKind kind;
        final int first;
        final int second;
        final long folderId;

        DragSource(SourceKind kind, int first, int second, long folderId) {
            this.kind = kind;
            this.first = first;
            this.second = second;
            this.folderId = folderId;
        }

        static DragSource fromEvent(DragEvent event) {
            if (event == null || event.getClipData() == null
                    || event.getClipData().getItemCount() == 0) return null;
            CharSequence value = event.getClipData().getItemAt(0).getText();
            return value == null ? null : parse(value.toString());
        }

        static DragSource parse(String descriptor) {
            if (descriptor == null || descriptor.length() > 128) return null;
            String[] parts = descriptor.split(":");
            try {
                if (parts.length == 3 && "grid".equals(parts[0])) {
                    int page = Integer.parseInt(parts[1]);
                    int slot = Integer.parseInt(parts[2]);
                    if (page < 0 || !GridPosition.isValidSlotIndex(slot)) return null;
                    return new DragSource(SourceKind.GRID, page, slot, -1L);
                }
                if (parts.length == 2 && "dock".equals(parts[0])) {
                    int index = Integer.parseInt(parts[1]);
                    if (index < 0 || index >= DockPosition.SLOTS) return null;
                    return new DragSource(SourceKind.DOCK, index, -1, -1L);
                }
                if (parts.length == 3 && "folder".equals(parts[0])) {
                    long id = Long.parseLong(parts[1]);
                    int index = Integer.parseInt(parts[2]);
                    if (id < 0 || index < 0) return null;
                    return new DragSource(SourceKind.FOLDER, -1, index, id);
                }
            } catch (NumberFormatException ignored) {}
            return null;
        }
    }

    /** LauncherApps-backed icon/label/launch adapter with profile support. */
    private static final class IconCatalog implements AppRenderer {
        private final Context context;
        private final LauncherApps launcherApps;
        private final UserManager userManager;
        private final Map<ComponentKey, LauncherActivityInfo> activities = new HashMap<>();
        private final Map<ComponentKey, Drawable> drawables = new HashMap<>();
        private final Map<ComponentKey, CharSequence> labels = new HashMap<>();
        private final List<ComponentKey> knownKeys = new ArrayList<>();
        private volatile boolean catalogWarmed;

        IconCatalog(Context context) {
            Context appContext = context.getApplicationContext();
            this.context = appContext == null ? context : appContext;
            launcherApps = (LauncherApps) this.context.getSystemService(
                    Context.LAUNCHER_APPS_SERVICE);
            userManager = (UserManager) this.context.getSystemService(Context.USER_SERVICE);
        }

        @Override public synchronized Drawable getIcon(ComponentKey key) {
            if (key == null) return UniformIconDrawable.wrap(context,
                    context.getDrawable(android.R.drawable.sym_def_app_icon));
            Drawable cached = drawables.get(key);
            if (cached != null) return cached;
            LauncherActivityInfo info = resolve(key);
            Drawable icon = null;
            try {
                if (info != null) icon = info.getBadgedIcon(context.getResources()
                        .getDisplayMetrics().densityDpi);
            } catch (Throwable ignored) {}
            if (icon == null) icon = context.getDrawable(android.R.drawable.sym_def_app_icon);
            icon = UniformIconDrawable.wrap(context, icon);
            if (icon != null) drawables.put(key, icon);
            return icon;
        }

        @Override public synchronized CharSequence getLabel(ComponentKey key) {
            if (key == null) return "";
            CharSequence cached = labels.get(key);
            if (cached != null) return cached;
            LauncherActivityInfo info = resolve(key);
            CharSequence label = null;
            try { if (info != null) label = info.getLabel(); }
            catch (Throwable ignored) {}
            if (label == null || label.length() == 0) label = key.packageName;
            labels.put(key, label);
            return label;
        }

        synchronized void invalidate() {
            activities.clear();
            drawables.clear();
            labels.clear();
            knownKeys.clear();
            catalogWarmed = false;
        }

        List<ComponentKey> enumerateAll() {
            synchronized (this) {
                if (catalogWarmed) return new ArrayList<>(knownKeys);
            }
            ArrayList<ComponentKey> result = new ArrayList<>();
            if (launcherApps == null || userManager == null) return result;
            Map<ComponentKey, LauncherActivityInfo> discovered = new HashMap<>();
            Map<ComponentKey, CharSequence> discoveredLabels = new HashMap<>();
            Map<ComponentKey, Drawable> discoveredIcons = new HashMap<>();
            int density = context.getResources().getDisplayMetrics().densityDpi;
            for (UserHandle user : userManager.getUserProfiles()) {
                long serial = userManager.getSerialNumberForUser(user);
                List<LauncherActivityInfo> list;
                try { list = launcherApps.getActivityList(null, user); }
                catch (Throwable ignored) { continue; }
                if (list == null) continue;
                for (LauncherActivityInfo info : list) {
                    if (info == null || info.getComponentName() == null) continue;
                    ComponentKey key = new ComponentKey(info.getComponentName(), serial);
                    discovered.put(key, info);
                    CharSequence label;
                    try { label = info.getLabel(); }
                    catch (Throwable ignored) { label = null; }
                    if (label == null || label.length() == 0) label = key.packageName;
                    discoveredLabels.put(key, label);
                    try {
                        Drawable icon = info.getBadgedIcon(density);
                        if (icon != null) discoveredIcons.put(
                                key, UniformIconDrawable.wrap(context, icon));
                    } catch (Throwable ignored) {}
                    result.add(key);
                }
            }
            Collator collator = Collator.getInstance();
            result.sort(Comparator.comparing(
                    k -> discoveredLabels.get(k).toString(), collator));
            synchronized (this) {
                activities.putAll(discovered);
                labels.putAll(discoveredLabels);
                drawables.putAll(discoveredIcons);
                knownKeys.clear();
                knownKeys.addAll(result);
                catalogWarmed = true;
            }
            return result;
        }

        void ensureWarm() {
            if (!catalogWarmed) enumerateAll();
        }

        void launch(ComponentKey key) {
            if (launcherApps == null || userManager == null) {
                throw new IllegalStateException("LauncherApps unavailable");
            }
            UserHandle user = userManager.getUserForSerialNumber(key.userSerial);
            if (user == null) throw new IllegalArgumentException("profile removed");
            launcherApps.startMainActivity(key.toComponentName(), user, null, null);
        }

        private LauncherActivityInfo resolve(ComponentKey key) {
            LauncherActivityInfo cached = activities.get(key);
            if (cached != null) return cached;
            if (launcherApps == null || userManager == null) return null;
            UserHandle user = userManager.getUserForSerialNumber(key.userSerial);
            if (user == null) return null;
            List<LauncherActivityInfo> list;
            try { list = launcherApps.getActivityList(key.packageName, user); }
            catch (Throwable ignored) { return null; }
            if (list == null) return null;
            for (LauncherActivityInfo info : list) {
                if (info != null && key.toComponentName().equals(info.getComponentName())) {
                    activities.put(key, info);
                    return info;
                }
            }
            return null;
        }
    }
}
