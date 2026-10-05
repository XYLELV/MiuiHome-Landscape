package com.hoshinoriji.miuihomelandscape.overlay;

import android.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDiskIOException;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.hoshinoriji.miuihomelandscape.LandscapeBridge;
import com.hoshinoriji.miuihomelandscape.MiuiHomeLandscapeModule;
import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;
import com.hoshinoriji.miuihomelandscape.overlay.AppPickerDialog;
import com.hoshinoriji.miuihomelandscape.overlay.LandscapeDockView;
import com.hoshinoriji.miuihomelandscape.overlay.LandscapePagedGridView;
import com.hoshinoriji.miuihomelandscape.overlay.recents.LandscapeRecentsController;
import com.hoshinoriji.miuihomelandscape.store.LandscapeStore;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LandscapeController {
    private static final long FOLDER_BLANK_GLOBAL_EDIT_DELAY_MS = 2000;
    private static final int FOLDER_COLS = 4;
    private static final long FOLDER_DRAG_OPEN_DELAY_MS = 850;
    private static final int FOLDER_IME_CUTOUT_COLOR = -1379848;
    private static final long FOLDER_SWAP_ANIM_MS = 140;
    private static final Map<Activity, LandscapeController> INSTANCES = new HashMap();
    private static final String KEY_HIDE_GESTURE_HANDLE = "hide_gesture_handle";
    private static final String SETTINGS_PREFS = "miui_home_landscape_overlay_v4_settings";
    private static final String TAG = "[MiuiHomeLandscape/Controller] ";
    private static boolean sRecentsVisible;
    private final WeakReference<Activity> actRef;
    private long activeFolderBlankDownAtMs;
    private float activeFolderBlankDownRawX;
    private float activeFolderBlankDownRawY;
    private float activeFolderBlankLastRawY;
    private boolean activeFolderBlankLongPressFired;
    private Runnable activeFolderBlankLongPressRunnable;
    private boolean activeFolderBlankTouchAccepted;
    private long activeFolderChildDownAtMs;
    private boolean activeFolderDesktopDragStarted;
    private boolean activeFolderDownOnContent;
    private float activeFolderDownRawX;
    private float activeFolderDownRawY;
    private boolean activeFolderDragHandled;
    private boolean activeFolderDragMoved;
    private boolean activeFolderDragOutside;
    private View activeFolderDragRow;
    private View activeFolderDragSource;
    private boolean activeFolderDragTargetInsert;
    private float activeFolderLastRawX;
    private float activeFolderLastRawY;
    private Runnable activeFolderLongPressRunnable;
    private boolean activeFolderManualDragging;
    private ViewGroup activeFolderRoot;
    private float activeFolderStartTranslationX;
    private float activeFolderStartTranslationY;
    private boolean activeFolderTouchAccepted;
    private BroadcastReceiver commandReceiver;
    private boolean commandReceiverRegistered;
    private final Context ctx;
    private View folderActiveLayer;
    private View folderActivePanel;
    private GridPosition folderActivePos;
    private LinearLayout folderActiveRoot;
    private boolean folderEditActive;
    private boolean folderHadChanges;
    private View folderOverlay;
    private boolean hotseatsHiddenByModule;
    private final IconCache iconCache;
    private LandscapeRecentsController landscapeRecents;
    private String lastExternalDragDesc;
    private boolean memoryFallbackActive;
    private boolean memorySeeded;
    private View nativeDragLayer;
    private View nativeHotseats;
    private View nativeRecentsContainer;
    private View nativeScreenContent;
    private View nativeWorkspace;
    private LandscapeOverlayView overlay;
    private String pendingFolderOpenDragDesc;
    private GridPosition pendingFolderOpenPos;
    private Runnable pendingFolderOpenRunnable;
    private boolean recentsVisible;
    private boolean separationGuardScheduled;
    private boolean workspaceHiddenByModule;
    private long folderActiveId = -1;
    private int folderEditFocusIndex = -1;
    private boolean attached = false;
    private final ArrayList<LandscapeItem> memoryGrid = new ArrayList<>();
    private final ArrayList<LandscapeItem> memoryDock = new ArrayList<>();
    private long activeFolderDragFolderId = -1;
    private int lastExternalDragScreenX = -1;
    private int lastExternalDragScreenY = -1;
    private int activeFolderDragFromIndex = -1;
    private int activeFolderDragTargetIndex = -1;
    private final Handler folderGestureHandler = new Handler(Looper.getMainLooper());
    private int folderSoftInputMode = Integer.MIN_VALUE;
    private final Runnable separationGuardRunnable = new Runnable() {
        @Override
        public void run() {
            LandscapeController.this.separationGuardScheduled = false;
            Activity a = (Activity) LandscapeController.this.actRef.get();
            if (a != null && LandscapeController.this.overlay != null && LandscapeController.isLandscapeNow(a)) {
                LandscapeController.this.enforceLandscapeSeparation("watchdog");
                LandscapeController.this.startLandscapeGuard();
            }
        }
    };
    private final View.OnTouchListener NATIVE_BLOCKER = new View.OnTouchListener() {
        @Override
        public final boolean onTouch(View view, MotionEvent motionEvent) {
            return LandscapeController.lambda$new$0(view, motionEvent);
        }
    };
    private final View.OnDragListener NATIVE_DRAG_BLOCKER = new View.OnDragListener() {
        @Override
        public final boolean onDrag(View view, DragEvent dragEvent) {
            return LandscapeController.lambda$new$1(view, dragEvent);
        }
    };
    private boolean hideGestureHandle = readHideGestureHandle();

    static boolean lambda$new$0(View v, MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == 0 || a == 1 || a == 3) {
            log("[touch] BLOCKED on native view=" + v.getClass().getSimpleName() + " act=" + a + " x=" + ((int) ev.getX()) + " y=" + ((int) ev.getY()));
        }
        return true;
    }

    static boolean lambda$new$1(View v, DragEvent event) {
        int action = event.getAction();
        if (action == 1 || action == 3 || action == FOLDER_COLS) {
            log("[drag] BLOCKED on native view=" + v.getClass().getSimpleName() + " action=" + dragActionName(action) + " x=" + ((int) event.getX()) + " y=" + ((int) event.getY()));
        }
        return true;
    }

    private LandscapeController(Activity a) {
        this.actRef = new WeakReference<>(a);
        this.ctx = a;
        this.iconCache = new IconCache(a);
    }

    public static LandscapeController forActivity(Activity a) {
        LandscapeController c = INSTANCES.get(a);
        if (c == null) {
            LandscapeController c2 = new LandscapeController(a);
            INSTANCES.put(a, c2);
            return c2;
        }
        return c;
    }

    public static void dispose(Activity a) {
        LandscapeController c = INSTANCES.remove(a);
        if (c != null) {
            c.stopLandscapeGuard();
            c.unregisterCommandReceiver();
            c.closeFolderOverlay("dispose");
            if (c.landscapeRecents != null) {
                c.landscapeRecents.dispose();
                c.landscapeRecents = null;
            }
        }
    }

    public static void onRecentsVisibilityChanged(boolean visible, String source) {
        if (sRecentsVisible == visible) {
            return;
        }
        sRecentsVisible = visible;
        logStatic("[recents] visible=" + visible + " source=" + source + " controllers=" + INSTANCES.size());
        for (LandscapeController c : new ArrayList(INSTANCES.values())) {
            if (c != null) {
                c.setRecentsVisible(visible, source);
            }
        }
    }

    private void setRecentsVisible(boolean visible, final String source) {
        if (this.recentsVisible == visible) {
            return;
        }
        this.recentsVisible = visible;
        Activity a = this.actRef.get();
        boolean land = a != null && isLandscapeNow(a);
        if (!land) {
            if (visible) {
                suspendForRecents(source);
                return;
            } else {
                if (a != null) {
                    applyOrientation(a.getResources().getConfiguration());
                    return;
                }
                return;
            }
        }
        if (visible) {
            closeFolderOverlay("recents");
            setHomeCutoutMode(true, "recents-enter:" + source);
            restoreSystemUi("recents-enter:" + source);
            applyRecentsSystemBarStyle(true, "recents-enter:" + source);
            scheduleRecentsNavAutoHide("recents-enter:" + source);
            resolveNativeViews(a);
            setNativeGone(this.nativeWorkspace, true, "workspace-recents");
            setNativeGone(this.nativeHotseats, true, "hotseats-recents");
            setNativeTouchBlocked(this.nativeDragLayer, true, "drag_layer-recents");
            setNativeTouchBlocked(this.nativeScreenContent, true, "screen_content-recents");
            setNativeRecentsMuted(true, "enter-immediate:" + source);
            if (this.overlay != null) {
                this.overlay.setVisibility(8);
            }
            final LandscapeRecentsController r = ensureLandscapeRecents();
            Runnable showTask = new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m76x46487ead(source, r);
                }
            };
            showTask.run();
            return;
        }
        LandscapeRecentsController r2 = this.landscapeRecents;
        resolveNativeViews(a);
        setNativeGone(this.nativeWorkspace, true, "workspace-recents-exit");
        setNativeGone(this.nativeHotseats, true, "hotseats-recents-exit");
        setNativeTouchBlocked(this.nativeDragLayer, true, "drag_layer-recents-exit");
        setNativeTouchBlocked(this.nativeScreenContent, true, "screen_content-recents-exit");
        if (a != null) {
            applyOrientation(a.getResources().getConfiguration());
        }
        scheduleSeparationPulses("recents-exit");
        if (r2 != null) {
            r2.hideDelayed("source=" + source, 420L);
        }
        if (this.overlay == null) {
            setNativeRecentsMuted(false, "hide:" + source);
            applyRecentsSystemBarStyle(false, "recents-exit-no-overlay:" + source);
            applyGestureHandleVisibility(true, "recents-exit-no-overlay:" + source);
            return;
        }
        this.overlay.postDelayed(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m77x39d802ee(source);
            }
        }, 460L);
    }

    void m76x46487ead(String showSource, LandscapeRecentsController recents) {
        Activity act = this.actRef.get();
        if (act == null || !isLandscapeNow(act) || !this.recentsVisible) {
            return;
        }
        resolveNativeViews(act);
        setNativeRecentsMuted(true, "show:" + showSource);
        if (recents != null) {
            recents.show("orientation:landscape source=" + showSource);
        }
    }

    void m77x39d802ee(String source) {
        Activity act = this.actRef.get();
        if (act != null && isLandscapeNow(act) && !this.recentsVisible && !sRecentsVisible) {
            enforceLandscapeSeparation("recents-exit-shield");
        }
        setNativeRecentsMuted(false, "hide-delayed:" + source);
        applyRecentsSystemBarStyle(false, "recents-exit:" + source);
        applyGestureHandleVisibility(true, "recents-exit:" + source);
        schedulePostRecentsNavHide("recents-exit:" + source);
    }

    public void onLauncherCreate() {
        ViewGroup parent;
        String parentTag;
        Activity a = this.actRef.get();
        if (a == null) {
            return;
        }
        registerCommandReceiver(a);
        if (this.attached) {
            return;
        }
        try {
            resolveNativeViews(a);
            View androidContent = a.findViewById(R.id.content);
            if (androidContent instanceof ViewGroup) {
                parent = (ViewGroup) androidContent;
                parentTag = "android.R.id.content";
            } else if (this.nativeDragLayer instanceof ViewGroup) {
                parent = (ViewGroup) this.nativeDragLayer;
                parentTag = "drag_layer";
            } else {
                log("[attach] no suitable parent (android.R.id.content / drag_layer both missing)");
                return;
            }
            log("[parent] overlayParent=" + parentTag + " class=" + parent.getClass().getName() + " dragLayer=" + (this.nativeDragLayer != null) + " screenContent=" + (this.nativeScreenContent != null) + " workspace=" + (this.nativeWorkspace != null) + " hotseats=" + (this.nativeHotseats != null));
            this.overlay = new LandscapeOverlayView(a);
            this.overlay.setVisibility(8);
            parent.addView(this.overlay, new FrameLayout.LayoutParams(-1, -1));
            this.overlay.bringToFront();
            this.overlay.getGrid().setListener(new LandscapePagedGridView.Listener() {
                @Override
                public void onAppClick(LandscapeItem item) {
                    LandscapeController.this.launchItem(item);
                }

                @Override
                public void onAppLongPress(LandscapeItem item, GridPosition pos, View source) {
                    LandscapePagedGridView.startCellDrag(source, "grid:" + pos.pageIndex + ":" + pos.slotIndex);
                }

                @Override
                public void onAppRemoveRequest(LandscapeItem item, GridPosition pos) {
                    LandscapeController.this.confirmRemoveGridOnly(item, pos);
                }

                @Override
                public void onEmptySlotLongPress() {
                    LandscapeController.log("[longClick] empty ignored");
                }

                @Override
                public void onDragHover(String from, GridPosition over, int screenX, int screenY) {
                    LandscapeController.this.handleGridDragHover(from, over, screenX, screenY);
                }

                @Override
                public void onDropOnGrid(String from, GridPosition to) {
                    LandscapeController.this.routeDropOnGrid(from, to);
                }

                @Override
                public void onInsertOnGrid(String from, GridPosition to) {
                    LandscapeController.this.routeInsertOnGrid(from, to);
                }
            });
            this.overlay.getDock().setListener(new LandscapeDockView.Listener() {
                @Override
                public void onAppClick(LandscapeItem item) {
                    LandscapeController.this.launchItem(item);
                }

                @Override
                public void onDropOnDock(String from, DockPosition to) {
                    LandscapeController.this.routeDropOnDock(from, to);
                }
            });
            this.overlay.setOnBlankLongPressListener(null);
            this.attached = true;
            log("[attach] overlay attached under " + parentTag);
            applyOrientation(a.getResources().getConfiguration());
            scheduleSeparationPulses("attach");
            refreshOverlay();
            logSizesDelayed("attach");
        } catch (Throwable t) {
            log("[attach] failed: " + t);
        }
    }

    private void logSizesDelayed(final String tag) {
        if (this.overlay == null) {
            return;
        }
        this.overlay.post(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m49x972c94e7(tag);
            }
        });
    }

    void m49x972c94e7(String tag) {
        try {
            DisplayMetrics dm = this.ctx.getResources().getDisplayMetrics();
            int or = this.ctx.getResources().getConfiguration().orientation;
            log("[" + tag + "/size] orient=" + (or == 2 ? "LAND" : "PORT") + " dm=" + dm.widthPixels + "x" + dm.heightPixels + " overlay=" + this.overlay.getWidth() + "x" + this.overlay.getHeight() + " grid=" + this.overlay.getGrid().getWidth() + "x" + this.overlay.getGrid().getHeight() + " dock=" + this.overlay.getDock().getWidth() + "x" + this.overlay.getDock().getHeight() + " pageWidth=" + this.overlay.getGrid().getWidth() + " pages=" + this.overlay.getGrid().getPageCount());
        } catch (Throwable th) {
        }
    }

    private boolean readHideGestureHandle() {
        return this.ctx.getSharedPreferences(SETTINGS_PREFS, 0).getBoolean("hide_gesture_handle", false);
    }

    private void setHideGestureHandle(boolean hide, String reason) {
        this.hideGestureHandle = hide;
        boolean z = false;
        this.ctx.getSharedPreferences(SETTINGS_PREFS, 0).edit().putBoolean("hide_gesture_handle", hide).apply();
        Activity a = this.actRef.get();
        if (a != null && isLandscapeNow(a)) {
            z = true;
        }
        applyGestureHandleVisibility(z, reason);
    }

    private void applyGestureHandleVisibility(boolean land, String reason) {
        Activity a = this.actRef.get();
        if (a == null || a.getWindow() == null) {
            return;
        }
        View decor = a.getWindow().getDecorView();
        if (decor == null) {
            return;
        }
        boolean recentsActive = this.recentsVisible || sRecentsVisible;
        if (land && !recentsActive) {
            hideSystemUiNow("landscape-home:" + reason);
        } else {
            setHomeCutoutMode(land, "nav-restore");
            restoreSystemUi(reason);
        }
    }

    private void setHomeCutoutMode(boolean land, String reason) {
        Activity a;
        if (Build.VERSION.SDK_INT < 28 || (a = this.actRef.get()) == null || a.getWindow() == null) {
            return;
        }
        WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        boolean landscapeNow = isLandscapeNow(a);
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                a.getWindow().setDecorFitsSystemWindows(!landscapeNow);
            } catch (Throwable th) {
            }
        }
        int mode = landscapeNow ? 1 : 0;
        if (lp.layoutInDisplayCutoutMode != mode) {
            lp.layoutInDisplayCutoutMode = mode;
            a.getWindow().setAttributes(lp);
        }
        try {
            View decor = a.getWindow().getDecorView();
            if (decor != null) {
                decor.requestApplyInsets();
            }
        } catch (Throwable th2) {
        }
        log("[cutout] mode=" + (landscapeNow ? "short-edges" : "default") + " decorFits=" + (!landscapeNow) + " requested=" + land + " reason=" + reason);
    }

    private void restoreSystemUi(String reason) {
        View decor;
        WindowInsetsController c;
        Activity a = this.actRef.get();
        if (a == null || a.getWindow() == null || (decor = a.getWindow().getDecorView()) == null) {
            return;
        }
        int flags = decor.getSystemUiVisibility();
        decor.setSystemUiVisibility(flags & (-3) & (-4097) & (-513));
        if (Build.VERSION.SDK_INT >= 30 && (c = decor.getWindowInsetsController()) != null) {
            c.show(WindowInsets.Type.navigationBars());
        }
        log("[nav] restore system ui reason=" + reason);
    }

    private void applyRecentsSystemBarStyle(boolean recents, String reason) {
        Activity a = this.actRef.get();
        if (a == null || a.getWindow() == null) {
            return;
        }
        Window w = a.getWindow();
        try {
            if (recents) {
                w.setNavigationBarColor(-15724524);
                if (Build.VERSION.SDK_INT >= 28) {
                    w.setNavigationBarDividerColor(-15724524);
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    w.setNavigationBarContrastEnforced(false);
                }
            } else {
                w.setNavigationBarColor(0);
                if (Build.VERSION.SDK_INT >= 28) {
                    w.setNavigationBarDividerColor(0);
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    w.setNavigationBarContrastEnforced(false);
                }
            }
            log("[nav] recents bar style=" + recents + " reason=" + reason);
        } catch (Throwable t) {
            log("[nav] recents bar style failed reason=" + reason + " err=" + t);
        }
    }

    private void hideSystemUiNow(String reason) {
        Activity a = this.actRef.get();
        if (a == null || a.getWindow() == null) {
            return;
        }
        View decor = a.getWindow().getDecorView();
        if (decor == null) {
            return;
        }
        setHomeCutoutMode(true, "nav-auto-hide");
        hideNavigationBarsKeepingCutout(reason);
    }

    private void hideNavigationBarsKeepingCutout(String reason) {
        View decor;
        WindowInsetsController c;
        Activity a = this.actRef.get();
        if (a == null || a.getWindow() == null || (decor = a.getWindow().getDecorView()) == null) {
            return;
        }
        int flags = decor.getSystemUiVisibility() | 256 | 512 | 2 | 4096;
        decor.setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30 && (c = decor.getWindowInsetsController()) != null) {
            c.hide(WindowInsets.Type.navigationBars());
        }
        log("[nav] auto-hide system ui reason=" + reason);
    }

    private void scheduleRecentsNavAutoHide(final String reason) {
        if (this.overlay == null) {
            return;
        }
        this.overlay.postDelayed(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m74x792be161(reason);
            }
        }, 3600L);
    }

    void m74x792be161(String reason) {
        Activity a = this.actRef.get();
        if (a == null || !isLandscapeNow(a)) {
            return;
        }
        if (this.recentsVisible || sRecentsVisible) {
            hideSystemUiNow(reason + "+3600");
        }
    }

    private void schedulePostRecentsNavHide(final String reason) {
        if (!this.hideGestureHandle || this.overlay == null) {
            return;
        }
        int[] delays = {80, 260, 700, 1400};
        for (final int delay : delays) {
            this.overlay.postDelayed(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m73xdacdd593(reason, delay);
                }
            }, delay);
        }
    }

    void m73xdacdd593(String reason, int delay) {
        Activity a = this.actRef.get();
        if (a != null && isLandscapeNow(a) && !this.recentsVisible && !sRecentsVisible) {
            hideSystemUiNow(reason + "+" + delay);
        }
    }

    private void prepareForExternalLaunch() {
        stopLandscapeGuard();
        setHomeCutoutMode(true, "external-launch");
        hideNavigationBarsKeepingCutout("external-launch");
    }

    private void scheduleLandscapeNavHide(final String reason) {
        if (this.overlay == null) {
            return;
        }
        int[] delays = {120, 420, 1000};
        for (final int delay : delays) {
            this.overlay.postDelayed(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m72x52ac371(reason, delay);
                }
            }, delay);
        }
    }

    void m72x52ac371(String reason, int delay) {
        Activity a = this.actRef.get();
        if (a != null && isLandscapeNow(a) && !this.recentsVisible && !sRecentsVisible) {
            hideSystemUiNow(reason + "+" + delay);
        }
    }

    private void registerCommandReceiver(Activity a) {
        if (this.commandReceiverRegistered) {
            return;
        }
        this.commandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LandscapeController.this.handleBridgeCommand(intent);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(LandscapeBridge.ACTION_PING);
        filter.addAction(LandscapeBridge.ACTION_ADD_TO_LANDSCAPE);
        filter.addAction(LandscapeBridge.ACTION_IMPORT_ALL);
        filter.addAction(LandscapeBridge.ACTION_SETTINGS_UPDATE);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                a.registerReceiver(this.commandReceiver, filter, 2);
            } else {
                a.registerReceiver(this.commandReceiver, filter);
            }
            this.commandReceiverRegistered = true;
            log("[bridge] command receiver registered");
        } catch (Throwable t) {
            log("[bridge] register failed: " + t);
        }
    }

    private void unregisterCommandReceiver() {
        if (!this.commandReceiverRegistered || this.commandReceiver == null) {
            return;
        }
        Activity a = this.actRef.get();
        if (a != null) {
            try {
                a.unregisterReceiver(this.commandReceiver);
            } catch (Throwable th) {
            }
        }
        this.commandReceiverRegistered = false;
        this.commandReceiver = null;
    }

    public void handleBridgeCommand(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (LandscapeBridge.ACTION_PING.equals(action)) {
            Intent pong = new Intent(LandscapeBridge.ACTION_PONG);
            pong.setPackage(LandscapeBridge.MODULE_PKG);
            pong.putExtra(LandscapeBridge.EXTRA_VERSION, MiuiHomeLandscapeModule.VERSION);
            pong.putExtra("hide_gesture_handle", this.hideGestureHandle);
            this.ctx.sendBroadcast(pong);
            log("[bridge] pong sent");
            return;
        }
        if (LandscapeBridge.ACTION_SETTINGS_UPDATE.equals(action)) {
            boolean hide = intent.getBooleanExtra("hide_gesture_handle", false);
            setHideGestureHandle(hide, "bridge");
            return;
        }
        if (LandscapeBridge.ACTION_IMPORT_ALL.equals(action)) {
            try {
                LandscapeStore store = LandscapeStore.get(this.ctx);
                int before = store.maxAbsoluteIndexPlusOne();
                seedGridFromInstalledApps(store);
                int after = store.maxAbsoluteIndexPlusOne();
                if (this.overlay != null) {
                    refreshOverlay();
                }
                sendAddResult(true, "已补齐导入全部应用");
                Toast.makeText(this.ctx, "已补齐导入全部应用", 0).show();
                log("[bridge] import-all done before=" + before + " after=" + after);
                return;
            } catch (Throwable t) {
                sendAddResult(false, "导入失败：" + t.getClass().getSimpleName());
                log("[bridge] import-all failed: " + t);
                return;
            }
        }
        if (LandscapeBridge.ACTION_ADD_TO_LANDSCAPE.equals(action)) {
            String pkg = intent.getStringExtra(LandscapeBridge.EXTRA_PACKAGE);
            String cls = intent.getStringExtra(LandscapeBridge.EXTRA_CLASS);
            long serial = intent.getLongExtra(LandscapeBridge.EXTRA_USER_SERIAL, 0L);
            if (pkg == null || cls == null) {
                sendAddResult(false, "添加失败：应用信息不完整");
                log("[bridge] add rejected: missing component");
                return;
            }
            ComponentKey key = new ComponentKey(pkg, cls, serial);
            try {
                List<GridPosition> added = LandscapeStore.get(this.ctx).appendUniqueToGrid(Collections.singletonList(key));
                boolean ok = !added.isEmpty();
                if (ok) {
                    if (this.overlay != null) {
                        refreshOverlay();
                    }
                    sendAddResult(true, "已添加到横屏");
                    Toast.makeText(this.ctx, "已添加到横屏", 0).show();
                    log("[bridge] add ok key=" + key + " pos=" + added.get(0));
                    return;
                }
                sendAddResult(false, "横屏里已经有这个应用");
                Toast.makeText(this.ctx, "横屏里已经有这个应用", 0).show();
                log("[bridge] add skipped duplicate key=" + key);
            } catch (Throwable t2) {
                sendAddResult(false, "添加失败：" + t2.getClass().getSimpleName());
                log("[bridge] add failed key=" + key + " err=" + t2);
            }
        }
    }

    private void sendAddResult(boolean added, String message) {
        Intent result = new Intent(LandscapeBridge.ACTION_ADD_RESULT);
        result.setPackage(LandscapeBridge.MODULE_PKG);
        result.putExtra(LandscapeBridge.EXTRA_ADDED, added);
        result.putExtra(LandscapeBridge.EXTRA_MESSAGE, message);
        this.ctx.sendBroadcast(result);
    }

    public void onConfigurationChanged(Configuration cfg) {
        applyOrientation(cfg);
        if (this.overlay == null) {
            refreshOverlay();
        } else {
            this.overlay.post(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m50x2aa9edce();
                }
            });
        }
    }

    void m50x2aa9edce() {
        scheduleSeparationPulses("cfgChanged");
        refreshOverlay();
        logSizesDelayed("cfgChanged");
    }

    public void onResume() {
        Activity a = this.actRef.get();
        if (a == null) {
            return;
        }
        applyOrientation(a.getResources().getConfiguration());
        if (this.overlay == null) {
            refreshOverlay();
        } else {
            this.overlay.post(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m51x72065e();
                }
            });
        }
    }

    void m51x72065e() {
        scheduleSeparationPulses("resume");
        refreshOverlay();
        logSizesDelayed("resume");
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        Activity a = this.actRef.get();
        if (a == null) {
            return;
        }
        if (!hasFocus) {
            stopLandscapeGuard();
            hideNavigationBarsKeepingCutout("focus-lost");
        } else {
            applyOrientation(a.getResources().getConfiguration());
            scheduleSeparationPulses("focus");
            scheduleLandscapeNavHide("focus");
        }
    }

    private void applyOrientation(Configuration cfg) {
        if (this.overlay == null) {
            return;
        }
        Activity a = this.actRef.get();
        boolean land = cfg != null && cfg.orientation == 2;
        if (land) {
            if (this.recentsVisible || sRecentsVisible) {
                if (this.overlay != null) {
                    this.overlay.setVisibility(8);
                }
                resolveNativeViews(a);
                setNativeGone(this.nativeWorkspace, true, "workspace-recents");
                setNativeGone(this.nativeHotseats, true, "hotseats-recents");
                setNativeTouchBlocked(this.nativeDragLayer, true, "drag_layer-recents");
                setNativeTouchBlocked(this.nativeScreenContent, true, "screen_content-recents");
            } else {
                enforceLandscapeSeparation("orientation");
                startLandscapeGuard();
            }
        } else {
            stopLandscapeGuard();
            this.overlay.setVisibility(8);
        }
        if (!land) {
            closeFolderOverlay("portrait");
            setNativeGone(this.nativeWorkspace, false, "workspace");
            setNativeGone(this.nativeHotseats, false, "hotseats");
            if (this.landscapeRecents != null) {
                this.landscapeRecents.dispose();
                this.landscapeRecents = null;
            }
        }
        if (!land) {
            setNativeTouchBlocked(this.nativeDragLayer, false, "drag_layer");
            setNativeTouchBlocked(this.nativeScreenContent, false, "screen_content");
            setNativeRecentsMuted(false, "portrait");
        }
        applyGestureHandleVisibility(land, "orientation");
        if (land && !this.recentsVisible && !sRecentsVisible) {
            scheduleLandscapeNavHide("orientation");
        }
        log("[orientation] land=" + land + " overlay=" + (land ? "VISIBLE" : "GONE") + " ws=" + viz(this.nativeWorkspace) + " hs=" + viz(this.nativeHotseats) + " dl=" + viz(this.nativeDragLayer) + " sc=" + viz(this.nativeScreenContent));
    }

    public void enforceLandscapeSeparation(String reason) {
        Activity a = this.actRef.get();
        if (a == null || this.overlay == null) {
            return;
        }
        if (this.recentsVisible || sRecentsVisible) {
            resolveNativeViews(a);
            setNativeGone(this.nativeWorkspace, true, "workspace-recents");
            setNativeGone(this.nativeHotseats, true, "hotseats-recents");
            setNativeTouchBlocked(this.nativeDragLayer, true, "drag_layer-recents");
            setNativeTouchBlocked(this.nativeScreenContent, true, "screen_content-recents");
            setNativeRecentsMuted(true, "apply-recents");
            log("[recents] keep native hidden during landscape recents reason=" + reason + " ws=" + viz(this.nativeWorkspace) + " hs=" + viz(this.nativeHotseats) + " overlay=" + viz(this.overlay));
            return;
        }
        resolveNativeViews(a);
        String beforeWs = viz(this.nativeWorkspace);
        String beforeHs = viz(this.nativeHotseats);
        boolean leak = this.overlay.getVisibility() != 0 || isVisible(this.nativeWorkspace) || isVisible(this.nativeHotseats);
        this.overlay.setVisibility(0);
        this.overlay.bringToFront();
        bringFolderToFront("enforce:" + reason);
        setNativeGone(this.nativeWorkspace, true, "workspace");
        setNativeGone(this.nativeHotseats, true, "hotseats");
        setNativeTouchBlocked(this.nativeDragLayer, true, "drag_layer");
        setNativeTouchBlocked(this.nativeScreenContent, true, "screen_content");
        if (leak) {
            log("[separation] repaired reason=" + reason + " beforeWs=" + beforeWs + " beforeHs=" + beforeHs + " afterWs=" + viz(this.nativeWorkspace) + " afterHs=" + viz(this.nativeHotseats));
        }
    }

    private void suspendForRecents(String reason) {
        stopLandscapeGuard();
        if (this.overlay != null) {
            this.overlay.setVisibility(8);
        }
        setNativeTouchBlocked(this.nativeDragLayer, false, "drag_layer");
        setNativeTouchBlocked(this.nativeScreenContent, false, "screen_content");
        restoreSystemUi("recents:" + reason);
        log("[recents] overlay suspended reason=" + reason + " ws=" + viz(this.nativeWorkspace) + " hs=" + viz(this.nativeHotseats) + " overlay=" + (this.overlay == null ? "null" : viz(this.overlay)));
    }

    private void scheduleSeparationPulses(final String reason) {
        if (this.overlay == null) {
            return;
        }
        int[] delays = {0, 60, 180, 420, 900, 1600, 2600};
        for (final int delay : delays) {
            this.overlay.postDelayed(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m75x9773fc0f(reason, delay);
                }
            }, delay);
        }
    }

    void m75x9773fc0f(String reason, int delay) {
        Activity a = this.actRef.get();
        if (a != null && this.overlay != null && isLandscapeNow(a) && !this.recentsVisible && !sRecentsVisible) {
            enforceLandscapeSeparation("pulse:" + reason + "+" + delay);
        }
    }

    public void startLandscapeGuard() {
        if (this.overlay == null || this.separationGuardScheduled || this.recentsVisible || sRecentsVisible) {
            return;
        }
        this.separationGuardScheduled = true;
        this.overlay.postDelayed(this.separationGuardRunnable, 750L);
    }

    private void stopLandscapeGuard() {
        if (this.overlay != null) {
            this.overlay.removeCallbacks(this.separationGuardRunnable);
        }
        this.separationGuardScheduled = false;
    }

    private void resolveNativeViews(Activity a) {
        this.nativeDragLayer = findByName(a, "drag_layer");
        this.nativeScreenContent = findByName(a, "screen_content");
        this.nativeWorkspace = findByName(a, "workspace");
        this.nativeHotseats = findByName(a, "hotseats");
        if (this.nativeHotseats == null) {
            this.nativeHotseats = findByName(a, "hot_seats");
        }
        if (this.nativeHotseats == null) {
            this.nativeHotseats = findByName(a, "hotseat");
        }
        if (this.nativeHotseats == null) {
            this.nativeHotseats = findByName(a, "hotseat_content_screen");
        }
        this.nativeRecentsContainer = findByName(a, "recents_container");
        if (this.nativeRecentsContainer == null) {
            this.nativeRecentsContainer = findByName(a, "recents_view_container");
        }
    }

    private LandscapeRecentsController ensureLandscapeRecents() {
        Activity a;
        if (this.landscapeRecents == null && (a = this.actRef.get()) != null) {
            this.landscapeRecents = new LandscapeRecentsController(a);
        }
        return this.landscapeRecents;
    }

    public static boolean isLandscapeNow(Activity a) {
        return a != null && a.getResources().getConfiguration().orientation == 2;
    }

    private void setNativeGone(View v, boolean land, String name) {
        if (v == null) {
            return;
        }
        boolean shouldRestoreVisibility = true;
        boolean workspace = name != null && name.contains("workspace");
        boolean hotseat = name != null && (name.contains("hotseat") || name.contains("hotseats"));
        if (land) {
            if (workspace) {
                this.workspaceHiddenByModule = true;
            }
            if (hotseat) {
                this.hotseatsHiddenByModule = true;
            }
            v.setVisibility(8);
            v.setAlpha(0.0f);
        } else {
            if ((!workspace || !this.workspaceHiddenByModule) && ((!hotseat || !this.hotseatsHiddenByModule) && (workspace || hotseat))) {
                shouldRestoreVisibility = false;
            }
            if (workspace) {
                this.workspaceHiddenByModule = false;
            }
            if (hotseat) {
                this.hotseatsHiddenByModule = false;
            }
            if (shouldRestoreVisibility) {
                v.setVisibility(0);
                v.setAlpha(1.0f);
            }
        }
        v.setImportantForAccessibility(land ? FOLDER_COLS : 0);
        v.setOnTouchListener(land ? this.NATIVE_BLOCKER : null);
        v.setOnDragListener(land ? this.NATIVE_DRAG_BLOCKER : null);
        v.setOnTouchListener(land ? this.NATIVE_BLOCKER : null);
        v.setOnDragListener(land ? this.NATIVE_DRAG_BLOCKER : null);
    }

    private void setNativeTouchBlocked(View v, boolean land, String name) {
        if (v == null) {
            return;
        }
        v.setOnDragListener(land ? this.NATIVE_DRAG_BLOCKER : null);
        v.setOnTouchListener(land ? this.NATIVE_BLOCKER : null);
    }

    private void setNativeRecentsMuted(boolean muted, String reason) {
        int i;
        if (this.nativeRecentsContainer == null) {
            return;
        }
        this.nativeRecentsContainer.setAlpha(muted ? 0.0f : 1.0f);
        this.nativeRecentsContainer.setOnTouchListener(muted ? this.NATIVE_BLOCKER : null);
        this.nativeRecentsContainer.setOnDragListener(muted ? this.NATIVE_DRAG_BLOCKER : null);
        View view = this.nativeRecentsContainer;
        if (muted) {
            i = FOLDER_COLS;
        } else {
            i = 0;
        }
        view.setImportantForAccessibility(i);
        log("[recents] native container muted=" + muted + " reason=" + reason + " viz=" + viz(this.nativeRecentsContainer));
    }

    private static String dragActionName(int action) {
        switch (action) {
            case 1:
                return "STARTED";
            case 2:
                return "LOCATION";
            case GridPosition.ROWS:
                return "DROP";
            case FOLDER_COLS:
                return "ENDED";
            default:
                return "act#" + action;
        }
    }

    private static String viz(View v) {
        if (v == null) {
            return "null";
        }
        int s = v.getVisibility();
        return s == 0 ? "V" : s == FOLDER_COLS ? "I" : "G";
    }

    private static boolean isVisible(View v) {
        return v != null && v.getVisibility() == 0;
    }

    public void refreshOverlay() {
        if (this.overlay == null) {
            return;
        }
        if (this.memoryFallbackActive) {
            ensureMemorySeeded();
            bindMemorySnapshot("refresh-memory-active");
            return;
        }
        try {
            LandscapeStore store = LandscapeStore.get(this.ctx);
            Activity a = this.actRef.get();
            if (a != null && a.getResources().getConfiguration().orientation == 2 && store.maxAbsoluteIndexPlusOne() == 0) {
                seedGridFromInstalledApps(store);
            }
            List<LandscapeItem> grid = store.listGrid();
            List<LandscapeItem> dock = store.listDock();
            AppRenderer r = this.iconCache;
            syncMemoryFromStore(grid, dock);
            this.memoryFallbackActive = false;
            this.overlay.getGrid().bind(grid, r);
            this.overlay.getDock().bind(dock, r);
        } catch (Throwable t) {
            if (isDiskIo(t) && tryResetAndRebindStore()) {
                return;
            }
            bindMemoryFallback(t);
        }
    }

    private boolean tryResetAndRebindStore() {
        try {
            LandscapeStore store = LandscapeStore.get(this.ctx);
            log("[store] disk I/O detected; resetting landscape overlay database");
            store.resetDatabase();
            seedGridFromInstalledApps(store);
            List<LandscapeItem> grid = store.listGrid();
            List<LandscapeItem> dock = store.listDock();
            syncMemoryFromStore(grid, dock);
            this.memoryFallbackActive = false;
            this.iconCache.invalidate();
            this.overlay.getGrid().bind(grid, this.iconCache);
            this.overlay.getDock().bind(dock, this.iconCache);
            log("[store] reset-rebind ok grid=" + grid.size() + " dock=" + dock.size());
            return true;
        } catch (Throwable resetError) {
            log("[store] reset-rebind failed: " + resetError);
            return false;
        }
    }

    private static boolean isDiskIo(Throwable t) {
        while (t != null) {
            if (t instanceof SQLiteDiskIOException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void seedGridFromInstalledApps(LandscapeStore store) {
        Activity a = this.actRef.get();
        if (a == null) {
            return;
        }
        try {
            prepareForExternalLaunch();
            LauncherApps la = (LauncherApps) a.getSystemService("launcherapps");
            UserManager um = (UserManager) a.getSystemService("user");
            if (la != null && um != null) {
                ArrayList<ComponentKey> keys = new ArrayList<>();
                for (UserHandle u : um.getUserProfiles()) {
                    long serial = um.getSerialNumberForUser(u);
                    List<LauncherActivityInfo> list = la.getActivityList(null, u);
                    if (list != null) {
                        for (LauncherActivityInfo i : list) {
                            ComponentName cn = i.getComponentName();
                            if (cn != null) {
                                keys.add(new ComponentKey(cn, serial));
                            }
                        }
                    }
                }
                if (!keys.isEmpty()) {
                    store.appendUniqueToGrid(keys);
                    this.iconCache.invalidate();
                    log("[seed] first-landscape seeded " + keys.size() + " apps");
                }
            }
        } catch (Throwable t) {
            log("[seed] failed: " + t);
        }
    }

    private void bindMemoryFallback(Throwable cause) {
        this.memoryFallbackActive = true;
        log("[store] refresh failed, using memory fallback: " + cause);
        ensureMemorySeeded();
        bindMemorySnapshot("refresh-fallback");
    }

    private void ensureMemorySeeded() {
        Activity a;
        if ((!this.memorySeeded || (this.memoryGrid.isEmpty() && this.memoryDock.isEmpty())) && (a = this.actRef.get()) != null) {
            try {
                closeFolderOverlay("launch");
                LauncherApps la = (LauncherApps) a.getSystemService("launcherapps");
                UserManager um = (UserManager) a.getSystemService("user");
                if (la != null && um != null) {
                    ArrayList<ComponentKey> keys = new ArrayList<>();
                    for (UserHandle u : um.getUserProfiles()) {
                        long serial = um.getSerialNumberForUser(u);
                        List<LauncherActivityInfo> list = la.getActivityList(null, u);
                        if (list != null) {
                            for (LauncherActivityInfo i : list) {
                                ComponentName cn = i.getComponentName();
                                if (cn != null) {
                                    keys.add(new ComponentKey(cn, serial));
                                }
                            }
                        }
                    }
                    appendMemoryGrid(keys);
                    this.memorySeeded = true;
                    this.iconCache.invalidate();
                    log("[store] memory fallback seeded apps=" + this.memoryGrid.size());
                    return;
                }
                this.memorySeeded = true;
                log("[store] memory fallback cannot seed: launcherApps/userManager missing");
            } catch (Throwable t) {
                this.memorySeeded = true;
                log("[store] memory fallback seed failed: " + t);
            }
        }
    }

    private void bindMemorySnapshot(String reason) {
        if (this.overlay == null) {
            return;
        }
        this.overlay.getGrid().bind(new ArrayList(this.memoryGrid), this.iconCache);
        this.overlay.getDock().bind(new ArrayList(this.memoryDock), this.iconCache);
        log("[store] " + reason + " memory bind grid=" + this.memoryGrid.size() + " dock=" + this.memoryDock.size());
    }

    private void syncMemoryFromStore(List<LandscapeItem> grid, List<LandscapeItem> dock) {
        this.memoryGrid.clear();
        this.memoryDock.clear();
        if (grid != null) {
            this.memoryGrid.addAll(grid);
        }
        if (dock != null) {
            this.memoryDock.addAll(dock);
        }
        this.memorySeeded = true;
        log("[store] synced memory snapshot grid=" + this.memoryGrid.size() + " dock=" + this.memoryDock.size());
    }

    private void appendMemoryGrid(List<ComponentKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        HashSet<Integer> occupied = new HashSet<>();
        HashSet<ComponentKey> existing = new HashSet<>();
        for (LandscapeItem it : this.memoryGrid) {
            if (it != null) {
                occupied.add(Integer.valueOf(GridPosition.toAbsoluteIndex(it.pageIndex, it.slotIndex)));
                if (it.isFolder()) {
                    existing.addAll(it.folderChildren);
                } else if (it.key != null) {
                    existing.add(it.key);
                }
            }
        }
        for (LandscapeItem it2 : this.memoryDock) {
            if (it2 != null && it2.key != null) {
                existing.add(it2.key);
            }
        }
        int cursor = 0;
        for (ComponentKey key : keys) {
            if (key != null && !existing.contains(key)) {
                while (occupied.contains(Integer.valueOf(cursor))) {
                    cursor++;
                }
                GridPosition pos = new GridPosition(GridPosition.pageForAbsoluteIndex(cursor), GridPosition.slotForAbsoluteIndex(cursor));
                this.memoryGrid.add(LandscapeItem.grid(pos, key));
                occupied.add(Integer.valueOf(cursor));
                existing.add(key);
                cursor++;
            }
        }
    }

    private LandscapeItem findMemoryGrid(GridPosition pos) {
        if (pos == null) {
            return null;
        }
        for (LandscapeItem it : this.memoryGrid) {
            if (it != null && it.pageIndex == pos.pageIndex && it.slotIndex == pos.slotIndex) {
                return it;
            }
        }
        return null;
    }

    private LandscapeItem findMemoryDock(DockPosition pos) {
        if (pos == null) {
            return null;
        }
        for (LandscapeItem it : this.memoryDock) {
            if (it != null && it.dockIndex == pos.dockIndex) {
                return it;
            }
        }
        return null;
    }

    private void removeMemoryGrid(GridPosition pos) {
        if (pos == null) {
            return;
        }
        for (int i = this.memoryGrid.size() - 1; i >= 0; i--) {
            LandscapeItem it = this.memoryGrid.get(i);
            if (it != null && it.pageIndex == pos.pageIndex && it.slotIndex == pos.slotIndex) {
                this.memoryGrid.remove(i);
            }
        }
    }

    private void removeMemoryDock(DockPosition pos) {
        if (pos == null) {
            return;
        }
        for (int i = this.memoryDock.size() - 1; i >= 0; i--) {
            LandscapeItem it = this.memoryDock.get(i);
            if (it != null && it.dockIndex == pos.dockIndex) {
                this.memoryDock.remove(i);
            }
        }
    }

    private void putMemoryGrid(GridPosition pos, ComponentKey key) {
        if (pos == null || key == null) {
            return;
        }
        removeMemoryGrid(pos);
        this.memoryGrid.add(LandscapeItem.grid(pos, key));
    }

    private void putMemoryGridItem(GridPosition pos, LandscapeItem item) {
        if (pos == null || item == null) {
            return;
        }
        removeMemoryGrid(pos);
        if (item.isFolder()) {
            this.memoryGrid.add(LandscapeItem.gridFolder(pos, item.folderId, item.folderTitle, item.folderChildren));
        } else if (item.key != null) {
            this.memoryGrid.add(LandscapeItem.grid(pos, item.key));
        }
    }

    private void putMemoryFolder(LandscapeItem folder, List<ComponentKey> children) {
        if (folder == null || !folder.isFolder()) {
            return;
        }
        GridPosition pos = new GridPosition(folder.pageIndex, folder.slotIndex);
        removeMemoryGrid(pos);
        this.memoryGrid.add(LandscapeItem.gridFolder(pos, folder.folderId, folder.folderTitle, children));
    }

    private void addMemoryGridToFolder(GridPosition from, LandscapeItem folder) {
        LandscapeItem src = findMemoryGrid(from);
        if (src == null || src.isFolder() || src.key == null || folder == null || !folder.isFolder()) {
            return;
        }
        ArrayList<ComponentKey> children = new ArrayList<>(folder.folderChildren);
        if (!children.contains(src.key)) {
            children.add(src.key);
        }
        removeMemoryGrid(from);
        putMemoryFolder(folder, children);
    }

    private void addMemoryDockToFolder(DockPosition from, LandscapeItem folder) {
        LandscapeItem src = findMemoryDock(from);
        if (src == null || src.key == null || folder == null || !folder.isFolder()) {
            return;
        }
        ArrayList<ComponentKey> children = new ArrayList<>(folder.folderChildren);
        if (!children.contains(src.key)) {
            children.add(src.key);
        }
        removeMemoryDock(from);
        putMemoryFolder(folder, children);
    }

    private LandscapeItem findMemoryFolder(long folderId) {
        if (folderId < 0) {
            return null;
        }
        for (LandscapeItem it : this.memoryGrid) {
            if (it != null && it.isFolder() && it.folderId == folderId) {
                return it;
            }
        }
        return null;
    }

    private void moveMemoryFolderChild(long folderId, int fromIndex, int toIndex) {
        LandscapeItem folder = findMemoryFolder(folderId);
        if (folder == null || folder.folderChildren.isEmpty() || fromIndex == toIndex || fromIndex < 0 || fromIndex >= folder.folderChildren.size()) {
            return;
        }
        ArrayList<ComponentKey> children = new ArrayList<>(folder.folderChildren);
        int target = Math.max(0, Math.min(children.size() - 1, toIndex));
        ComponentKey moved = children.remove(fromIndex);
        children.add(target, moved);
        putMemoryFolder(folder, children);
    }

    private void swapMemoryFolderChild(long folderId, int fromIndex, int toIndex) {
        LandscapeItem folder = findMemoryFolder(folderId);
        if (folder == null || folder.folderChildren.isEmpty() || fromIndex == toIndex || fromIndex < 0 || toIndex < 0 || fromIndex >= folder.folderChildren.size() || toIndex >= folder.folderChildren.size()) {
            return;
        }
        ArrayList<ComponentKey> children = new ArrayList<>(folder.folderChildren);
        Collections.swap(children, fromIndex, toIndex);
        putMemoryFolder(folder, children);
    }

    private void renameMemoryFolder(long folderId, String title) {
        LandscapeItem folder = findMemoryFolder(folderId);
        if (folder == null) {
            return;
        }
        GridPosition pos = new GridPosition(folder.pageIndex, folder.slotIndex);
        removeMemoryGrid(pos);
        this.memoryGrid.add(LandscapeItem.gridFolder(pos, folder.folderId, title, folder.folderChildren));
    }

    private void removeMemoryFolderChildToGrid(long folderId, int childIndex) {
        LandscapeItem folder = findMemoryFolder(folderId);
        if (folder == null || childIndex < 0 || childIndex >= folder.folderChildren.size()) {
            return;
        }
        ArrayList<ComponentKey> children = new ArrayList<>(folder.folderChildren);
        ComponentKey child = children.remove(childIndex);
        GridPosition folderPos = new GridPosition(folder.pageIndex, folder.slotIndex);
        removeMemoryGrid(folderPos);
        if (children.size() <= 1) {
            if (children.size() == 1) {
                putMemoryGrid(folderPos, children.get(0));
                putMemoryGrid(firstEmptyMemoryGridPosition(), child);
                return;
            } else {
                putMemoryGrid(folderPos, child);
                return;
            }
        }
        this.memoryGrid.add(LandscapeItem.gridFolder(folderPos, folder.folderId, folder.folderTitle, children));
        putMemoryGrid(firstEmptyMemoryGridPosition(), child);
    }

    private GridPosition firstEmptyMemoryGridPosition() {
        HashSet<Integer> occupied = new HashSet<>();
        for (LandscapeItem it : this.memoryGrid) {
            if (it != null) {
                occupied.add(Integer.valueOf(GridPosition.toAbsoluteIndex(it.pageIndex, it.slotIndex)));
            }
        }
        int cursor = 0;
        while (occupied.contains(Integer.valueOf(cursor))) {
            cursor++;
        }
        return posForAbs(cursor);
    }

    private void putMemoryDock(DockPosition pos, ComponentKey key) {
        if (pos == null || key == null) {
            return;
        }
        removeMemoryDock(pos);
        this.memoryDock.add(LandscapeItem.dock(pos, key));
    }

    private void routeDropOnGridMemory(String from, GridPosition to, String reason) {
        ensureMemorySeeded();
        if (from == null || to == null) {
            return;
        }
        try {
            boolean z = true;
            if (from.startsWith("grid:")) {
                GridPosition fromPos = parseGrid(from);
                if (fromPos != null && !fromPos.equals(to)) {
                    LandscapeItem src = findMemoryGrid(fromPos);
                    LandscapeItem dst = findMemoryGrid(to);
                    if (src == null) {
                        log("[drop->grid/memory] missing source from=" + from + " to=" + to);
                        return;
                    }
                    if (!src.isFolder() && dst != null && dst.isFolder()) {
                        addMemoryGridToFolder(fromPos, dst);
                        log("[folder/memory] add grid app to folder from=" + fromPos + " folder=" + dst.folderId);
                        return;
                    }
                    removeMemoryGrid(fromPos);
                    removeMemoryGrid(to);
                    putMemoryGridItem(to, src);
                    if (dst != null) {
                        putMemoryGridItem(fromPos, dst);
                    }
                    StringBuilder sbAppend = new StringBuilder().append("[drop->grid/memory] reason=").append(reason).append(" from=").append(fromPos).append(" to=").append(to).append(" swap=");
                    if (dst == null) {
                        z = false;
                    }
                    log(sbAppend.append(z).toString());
                }
                return;
            }
            if (from.startsWith("dock:")) {
                DockPosition fromDock = parseDock(from);
                LandscapeItem src2 = findMemoryDock(fromDock);
                LandscapeItem dst2 = findMemoryGrid(to);
                if (src2 == null) {
                    log("[drop->grid/memory] missing dock source from=" + from + " to=" + to);
                    return;
                }
                if (dst2 != null && dst2.isFolder()) {
                    addMemoryDockToFolder(fromDock, dst2);
                    log("[folder/memory] add dock app to folder from=" + fromDock + " folder=" + dst2.folderId);
                    return;
                }
                removeMemoryDock(fromDock);
                removeMemoryGrid(to);
                putMemoryGrid(to, src2.key);
                if (dst2 != null && dst2.key != null) {
                    putMemoryDock(fromDock, dst2.key);
                }
                StringBuilder sbAppend2 = new StringBuilder().append("[drop->grid/memory] reason=").append(reason).append(" from=").append(fromDock).append(" to=").append(to).append(" swap=");
                if (dst2 == null) {
                    z = false;
                }
                log(sbAppend2.append(z).toString());
            }
        } finally {
            bindMemorySnapshot("drop-grid");
        }
    }

    private void routeInsertOnGridMemory(String from, GridPosition to, String reason) {
        ensureMemorySeeded();
        if (from == null || to == null) {
            return;
        }
        try {
            if (from.startsWith("grid:")) {
                GridPosition fromPos = parseGrid(from);
                if (fromPos != null && !fromPos.equals(to)) {
                    LandscapeItem src = findMemoryGrid(fromPos);
                    if (src == null) {
                        log("[insert->grid/memory] missing source from=" + from + " to=" + to);
                        return;
                    } else {
                        insertMemoryGrid(fromPos.absoluteIndex(), to.absoluteIndex(), src);
                        log("[insert->grid/memory] reason=" + reason + " from=" + fromPos + " to=" + to);
                    }
                }
                return;
            }
            if (from.startsWith("dock:")) {
                DockPosition fromDock = parseDock(from);
                LandscapeItem src2 = findMemoryDock(fromDock);
                if (src2 != null && src2.key != null) {
                    removeMemoryDock(fromDock);
                    insertMemoryDockToGrid(to.absoluteIndex(), src2.key);
                    log("[insert->grid/memory] reason=" + reason + " from=" + fromDock + " to=" + to);
                }
                log("[insert->grid/memory] missing dock source from=" + from + " to=" + to);
            }
        } finally {
            bindMemorySnapshot("insert-grid");
        }
    }

    private void insertMemoryGrid(int fromAbs, int toAbs, LandscapeItem item) {
        if (item == null || fromAbs == toAbs) {
            return;
        }
        removeMemoryGrid(posForAbs(fromAbs));
        if (fromAbs < toAbs) {
            for (int i = fromAbs + 1; i < toAbs; i++) {
                moveMemoryGridSlot(i, i - 1);
            }
            int i2 = toAbs - 1;
            putMemoryGridItem(posForAbs(i2), item);
            return;
        }
        for (int i3 = fromAbs - 1; i3 >= toAbs; i3--) {
            moveMemoryGridSlot(i3, i3 + 1);
        }
        putMemoryGridItem(posForAbs(toAbs), item);
    }

    private void insertMemoryDockToGrid(int toAbs, ComponentKey key) {
        if (key == null) {
            return;
        }
        int max = Math.max(toAbs, maxMemoryGridAbs());
        for (int i = max; i >= toAbs; i--) {
            moveMemoryGridSlot(i, i + 1);
        }
        putMemoryGrid(posForAbs(toAbs), key);
    }

    private void moveMemoryGridSlot(int fromAbs, int toAbs) {
        GridPosition from = posForAbs(fromAbs);
        LandscapeItem item = findMemoryGrid(from);
        removeMemoryGrid(posForAbs(toAbs));
        removeMemoryGrid(from);
        putMemoryGridItem(posForAbs(toAbs), item);
    }

    private int maxMemoryGridAbs() {
        int abs;
        int max = -1;
        for (LandscapeItem it : this.memoryGrid) {
            if (it != null && (abs = GridPosition.toAbsoluteIndex(it.pageIndex, it.slotIndex)) > max) {
                max = abs;
            }
        }
        return max;
    }

    private static GridPosition posForAbs(int abs) {
        return new GridPosition(GridPosition.pageForAbsoluteIndex(abs), GridPosition.slotForAbsoluteIndex(abs));
    }

    private void routeDropOnDockMemory(String from, DockPosition to, String reason) {
        ensureMemorySeeded();
        if (from == null || to == null) {
            return;
        }
        try {
            boolean z = true;
            if (from.startsWith("grid:")) {
                GridPosition fromPos = parseGrid(from);
                LandscapeItem src = findMemoryGrid(fromPos);
                LandscapeItem dst = findMemoryDock(to);
                if (src == null) {
                    log("[drop->dock/memory] missing grid source from=" + from + " to=" + to);
                    return;
                }
                if (src.isFolder()) {
                    log("[drop->dock/memory] folder ignored from=" + fromPos + " to=" + to);
                    return;
                }
                removeMemoryGrid(fromPos);
                removeMemoryDock(to);
                putMemoryDock(to, src.key);
                if (dst != null) {
                    putMemoryGrid(fromPos, dst.key);
                }
                StringBuilder sbAppend = new StringBuilder().append("[drop->dock/memory] reason=").append(reason).append(" from=").append(fromPos).append(" to=").append(to).append(" swap=");
                if (dst == null) {
                    z = false;
                }
                log(sbAppend.append(z).toString());
            } else if (from.startsWith("dock:")) {
                DockPosition fromDock = parseDock(from);
                if (fromDock != null && !fromDock.equals(to)) {
                    LandscapeItem src2 = findMemoryDock(fromDock);
                    LandscapeItem dst2 = findMemoryDock(to);
                    if (src2 == null) {
                        log("[drop->dock/memory] missing dock source from=" + from + " to=" + to);
                        return;
                    }
                    removeMemoryDock(fromDock);
                    removeMemoryDock(to);
                    putMemoryDock(to, src2.key);
                    if (dst2 != null) {
                        putMemoryDock(fromDock, dst2.key);
                    }
                    StringBuilder sbAppend2 = new StringBuilder().append("[drop->dock/memory] reason=").append(reason).append(" from=").append(fromDock).append(" to=").append(to).append(" swap=");
                    if (dst2 == null) {
                        z = false;
                    }
                    log(sbAppend2.append(z).toString());
                }
            }
        } finally {
            bindMemorySnapshot("drop-dock");
        }
    }

    private void openAppPicker() {
        Activity a = this.actRef.get();
        if (a == null) {
            return;
        }
        AppPickerDialog.show(a, new AppPickerDialog.OnPick() {
            @Override
            public final void onPick(List list) {
                this.f$0.m52x6a9e5bdc(list);
            }
        });
    }

    void m52x6a9e5bdc(List picks) {
        if (picks == null || picks.isEmpty()) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                appendMemoryGrid(picks);
                this.iconCache.invalidate();
                bindMemorySnapshot("picker-memory");
            } else {
                LandscapeStore.get(this.ctx).appendUniqueToGrid(picks);
                this.iconCache.invalidate();
                refreshOverlay();
            }
        } catch (Throwable t) {
            log("[picker] store failed, using memory fallback: " + t);
            this.memoryFallbackActive = true;
            ensureMemorySeeded();
            appendMemoryGrid(picks);
            this.iconCache.invalidate();
            bindMemorySnapshot("picker-fallback");
        }
    }

    public void confirmRemoveGridOnly(LandscapeItem item, final GridPosition pos) {
        CharSequence label;
        if (pos == null) {
            return;
        }
        if (item != null && item.isFolder()) {
            Toast.makeText(this.ctx, "文件夹内应用不走横屏移除", 0).show();
            log("[remove] folder ignored pos=" + pos + " folder=" + item.folderId);
            return;
        }
        Activity a = this.actRef.get();
        if (a == null) {
            removeGridOnly(pos);
            return;
        }
        if (item != null && item.isFolder()) {
            label = item.folderTitle;
        } else {
            label = (item == null || item.key == null) ? null : this.iconCache.getLabel(item.key);
        }
        String name = label == null ? "这个应用" : label.toString();
        log("[remove] confirm show pos=" + pos + " app=" + name);
        new AlertDialog.Builder(a).setTitle("从横屏移除？").setMessage(name + "\n\n只会从横屏桌面移除，不会卸载应用，也不会影响竖屏桌面。").setPositiveButton("移除", new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                this.f$0.m42xbaa88c(pos, dialogInterface, i);
            }
        }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
    }

    void m42xbaa88c(GridPosition pos, DialogInterface d, int which) {
        removeGridOnly(pos);
    }

    private void removeGridOnly(GridPosition pos) {
        if (pos == null) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                removeMemoryGrid(pos);
                bindMemorySnapshot("remove-grid-memory");
                log("[remove] grid memory-only pos=" + pos);
            } else {
                LandscapeStore.get(this.ctx).removeGrid(pos);
                removeMemoryGrid(pos);
                log("[remove] grid store pos=" + pos);
                refreshOverlay();
            }
        } catch (Throwable t) {
            log("[remove] grid store failed, using memory fallback: " + t);
            this.memoryFallbackActive = true;
            ensureMemorySeeded();
            removeMemoryGrid(pos);
            bindMemorySnapshot("remove-grid-fallback");
        }
    }

    public void routeDropOnGrid(String from, GridPosition to) {
        ViewGroup parent;
        Runnable openTask;
        FolderChildRef ref;
        ViewGroup parent2;
        forceClearDesktopEditMode("drop-grid");
        if (routeDropIntoOpenFolderIfNeeded(from)) {
            clearExternalDragPoint();
            return;
        }
        cancelPendingFolderOpen();
        boolean usedMemory = false;
        GridPosition[] openFolderPos = {null};
        if (from == null) {
            return;
        }
        try {
        } catch (Throwable t) {
            try {
                log("[drop→grid] fail: " + t);
                this.memoryFallbackActive = true;
                usedMemory = true;
                routeDropOnGridMemory(from, to, "store-fail");
                if (1 == 0) {
                    refreshOverlay();
                    if (openFolderPos[0] != null) {
                        final GridPosition openAt = openFolderPos[0];
                        openTask = new Runnable() {
                            @Override
                            public final void run() {
                                this.f$0.m69x1d7cba46(openAt);
                            }
                        };
                        Activity a = this.actRef.get();
                        if (a != null) {
                            ViewGroup parent3 = (ViewGroup) a.findViewById(R.id.content);
                            if (parent3 != null) {
                                parent3.post(openTask);
                            } else {
                                openTask.run();
                            }
                        }
                    }
                }
            } finally {
                if (!usedMemory) {
                    refreshOverlay();
                    if (openFolderPos[0] != null) {
                        final GridPosition openAt2 = openFolderPos[0];
                        Runnable openTask2 = new Runnable() {
                            @Override
                            public final void run() {
                                this.f$0.m69x1d7cba46(openAt2);
                            }
                        };
                        Activity a2 = this.actRef.get();
                        if (a2 == null || (parent = (ViewGroup) a2.findViewById(R.id.content)) == null) {
                            openTask2.run();
                        } else {
                            parent.post(openTask2);
                        }
                    }
                }
                clearExternalDragPoint();
            }
        }
        if (this.memoryFallbackActive) {
            routeDropOnGridMemory(from, to, "active");
            if (1 == 0) {
                refreshOverlay();
                if (openFolderPos[0] != null) {
                    final GridPosition openAt3 = openFolderPos[0];
                    Runnable openTask3 = new Runnable() {
                        @Override
                        public final void run() {
                            this.f$0.m69x1d7cba46(openAt3);
                        }
                    };
                    Activity a3 = this.actRef.get();
                    if (a3 == null || (parent2 = (ViewGroup) a3.findViewById(R.id.content)) == null) {
                        openTask3.run();
                    } else {
                        parent2.post(openTask3);
                    }
                }
            }
            clearExternalDragPoint();
            return;
        }
        LandscapeStore store = LandscapeStore.get(this.ctx);
        if (from.startsWith("grid:")) {
            GridPosition fromPos = parseGrid(from);
            if (fromPos != null && !fromPos.equals(to)) {
                LandscapeItem src = store.getGridItem(fromPos);
                LandscapeItem dst = store.getGridItem(to);
                if (src != null && !src.isFolder() && dst != null && dst.isFolder()) {
                    store.addGridToFolder(fromPos, dst.folderId);
                    log("[folder] add grid app to folder direct from=" + fromPos + " folder=" + dst.folderId);
                } else if (src == null || src.isFolder() || dst == null || dst.isFolder()) {
                    store.moveOrSwapGrid(fromPos, to);
                } else {
                    store.createFolderFromGrid(fromPos, to);
                    openFolderPos[0] = to;
                    log("[folder] create from grid from=" + fromPos + " to=" + to);
                }
            }
        } else if (from.startsWith("dock:")) {
            DockPosition fromDock = parseDock(from);
            if (fromDock != null) {
                LandscapeItem dst2 = store.getGridItem(to);
                if (dst2 != null && dst2.isFolder()) {
                    store.addDockToFolder(fromDock, dst2.folderId);
                    log("[folder] add dock app to folder direct from=" + fromDock + " folder=" + dst2.folderId);
                } else if (dst2 == null || dst2.isFolder()) {
                    store.moveDockToGrid(fromDock, to);
                } else {
                    store.createFolderFromDock(fromDock, to);
                    openFolderPos[0] = to;
                    log("[folder] create from dock from=" + fromDock + " to=" + to);
                }
            }
        } else if (from.startsWith("folder:") && (ref = parseFolderChild(from)) != null) {
            store.removeFolderChildToGrid(ref.folderId, ref.childIndex, to, true);
            log("[folder] child drop to grid folder=" + ref.folderId + " index=" + ref.childIndex + " to=" + to);
        }
        if (0 == 0) {
            refreshOverlay();
            if (openFolderPos[0] != null) {
                final GridPosition openAt4 = openFolderPos[0];
                openTask = new Runnable() {
                    @Override
                    public final void run() {
                        this.f$0.m69x1d7cba46(openAt4);
                    }
                };
                Activity a4 = this.actRef.get();
                if (a4 != null) {
                    ViewGroup parent4 = (ViewGroup) a4.findViewById(R.id.content);
                    if (parent4 != null) {
                        parent4.post(openTask);
                    } else {
                        openTask.run();
                    }
                } else {
                    openTask.run();
                }
            }
        }
        clearExternalDragPoint();
    }

    void m69x1d7cba46(GridPosition openAt) {
        try {
            LandscapeItem updated = LandscapeStore.get(this.ctx).getGridItem(openAt);
            if (updated != null && updated.isFolder()) {
                openFolder(updated);
                log("[folder] auto-open after drop pos=" + openAt);
            } else {
                log("[folder] auto-open skipped not-folder pos=" + openAt);
            }
        } catch (Throwable t) {
            log("[folder] open after grid drop failed: " + t);
        }
    }

    public void routeInsertOnGrid(String from, GridPosition to) {
        FolderChildRef ref;
        forceClearDesktopEditMode("insert-grid");
        cancelPendingFolderOpen();
        boolean usedMemory = false;
        if (from == null || to == null) {
            if (0 == 0) {
                refreshOverlay();
                return;
            }
            return;
        }
        try {
        } catch (Throwable t) {
            try {
                log("[insert鈫抔rid] fail: " + t);
                this.memoryFallbackActive = true;
                usedMemory = true;
                routeInsertOnGridMemory(from, to, "store-fail");
                if (1 != 0) {
                    return;
                }
            } finally {
                if (!usedMemory) {
                    refreshOverlay();
                }
            }
        }
        if (this.memoryFallbackActive) {
            usedMemory = true;
            routeInsertOnGridMemory(from, to, "active");
            if (usedMemory) {
                return;
            } else {
                return;
            }
        }
        LandscapeStore store = LandscapeStore.get(this.ctx);
        if (from.startsWith("grid:")) {
            GridPosition fromPos = parseGrid(from);
            if (fromPos != null && !fromPos.equals(to)) {
                LandscapeItem src = store.getGridItem(fromPos);
                LandscapeItem dst = store.getGridItem(to);
                if (src == null || src.isFolder() || dst == null || !dst.isFolder()) {
                    store.insertGrid(fromPos, to);
                    log("[insert->grid] from=" + fromPos + " to=" + to);
                } else {
                    store.addGridToFolder(fromPos, dst.folderId);
                    log("[folder] insert-add grid app to folder direct from=" + fromPos + " folder=" + dst.folderId);
                }
            }
        } else if (from.startsWith("dock:")) {
            DockPosition fromDock = parseDock(from);
            if (fromDock != null) {
                LandscapeItem dst2 = store.getGridItem(to);
                if (dst2 == null || !dst2.isFolder()) {
                    store.insertDockToGrid(fromDock, to);
                    log("[insert->grid] from=" + fromDock + " to=" + to);
                } else {
                    store.addDockToFolder(fromDock, dst2.folderId);
                    log("[folder] insert-add dock app to folder direct from=" + fromDock + " folder=" + dst2.folderId);
                }
            }
        } else if (from.startsWith("folder:") && (ref = parseFolderChild(from)) != null) {
            store.removeFolderChildToGrid(ref.folderId, ref.childIndex, to, true);
            log("[folder] child insert to grid folder=" + ref.folderId + " index=" + ref.childIndex + " to=" + to);
        }
        if (0 != 0) {
            return;
        }
        refreshOverlay();
    }

    public void routeDropOnDock(String from, DockPosition to) {
        DockPosition fromDock;
        forceClearDesktopEditMode("drop-dock");
        cancelPendingFolderOpen();
        boolean usedMemory = false;
        if (from == null) {
            if (usedMemory) {
                return;
            } else {
                return;
            }
        }
        try {
        } catch (Throwable t) {
            try {
                log("[drop→dock] fail: " + t);
                this.memoryFallbackActive = true;
                usedMemory = true;
                routeDropOnDockMemory(from, to, "store-fail");
                if (1 != 0) {
                    return;
                }
            } finally {
                if (!usedMemory) {
                    refreshOverlay();
                }
            }
        }
        if (this.memoryFallbackActive) {
            routeDropOnDockMemory(from, to, "active");
            if (1 == 0) {
                refreshOverlay();
                return;
            }
            return;
        }
        LandscapeStore store = LandscapeStore.get(this.ctx);
        if (from.startsWith("grid:")) {
            GridPosition fromPos = parseGrid(from);
            if (fromPos != null) {
                LandscapeItem src = store.getGridItem(fromPos);
                if (src == null || !src.isFolder()) {
                    store.moveGridToDock(fromPos, to);
                } else {
                    log("[folder] dock drop ignored from=" + fromPos + " to=" + to);
                }
            }
        } else if (from.startsWith("dock:") && (fromDock = parseDock(from)) != null && !fromDock.equals(to)) {
            store.moveOrSwapDock(fromDock, to);
        }
        if (0 != 0) {
            return;
        }
        refreshOverlay();
    }

    public void handleGridDragHover(String from, GridPosition over, int screenX, int screenY) {
        LandscapeItem target;
        if (from == null || over == null || this.overlay == null) {
            cancelPendingFolderOpen();
            return;
        }
        if (!from.startsWith("grid:") && !from.startsWith("dock:")) {
            cancelPendingFolderOpen();
            return;
        }
        this.lastExternalDragDesc = from;
        this.lastExternalDragScreenX = screenX;
        this.lastExternalDragScreenY = screenY;
        try {
            if (this.memoryFallbackActive) {
                target = findMemoryGrid(over);
            } else {
                target = LandscapeStore.get(this.ctx).getGridItem(over);
            }
            if (target != null && target.isFolder()) {
                if (samePosition(this.pendingFolderOpenPos, over) && from.equals(this.pendingFolderOpenDragDesc) && this.pendingFolderOpenRunnable != null) {
                    return;
                }
                cancelPendingFolderOpen();
                this.pendingFolderOpenDragDesc = from;
                this.pendingFolderOpenPos = over;
                this.pendingFolderOpenRunnable = new Runnable() {
                    @Override
                    public final void run() {
                        this.f$0.m46xe2f13dea();
                    }
                };
                log("[folder] schedule drag hover open from=" + from + " pos=" + over);
                this.folderGestureHandler.postDelayed(this.pendingFolderOpenRunnable, FOLDER_DRAG_OPEN_DELAY_MS);
                return;
            }
            cancelPendingFolderOpen();
        } catch (Throwable t) {
            cancelPendingFolderOpen();
            log("[folder] hover check failed: " + t);
        }
    }

    void m46xe2f13dea() {
        LandscapeItem folder;
        String desc = this.pendingFolderOpenDragDesc;
        GridPosition pos = this.pendingFolderOpenPos;
        this.pendingFolderOpenRunnable = null;
        if (desc == null || pos == null) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                folder = findMemoryGrid(pos);
            } else {
                folder = LandscapeStore.get(this.ctx).getGridItem(pos);
            }
            if (folder != null && folder.isFolder()) {
                openFolder(folder);
                setFolderGlobalEdit();
                log("[folder] auto-open on drag hover from=" + desc + " pos=" + pos);
            }
        } catch (Throwable t) {
            log("[folder] auto-open hover failed: " + t);
        }
    }

    private void cancelPendingFolderOpen() {
        if (this.pendingFolderOpenRunnable != null) {
            this.folderGestureHandler.removeCallbacks(this.pendingFolderOpenRunnable);
        }
        this.pendingFolderOpenRunnable = null;
        this.pendingFolderOpenDragDesc = null;
        this.pendingFolderOpenPos = null;
    }

    private void clearExternalDragPoint() {
        this.lastExternalDragDesc = null;
        this.lastExternalDragScreenX = -1;
        this.lastExternalDragScreenY = -1;
    }

    private boolean routeDropIntoOpenFolderIfNeeded(String from) {
        if (from == null || this.folderActiveRoot == null || this.folderActiveId < 0) {
            return false;
        }
        if (!from.startsWith("grid:") && !from.startsWith("dock:")) {
            return false;
        }
        int x = this.lastExternalDragScreenX;
        int y = this.lastExternalDragScreenY;
        if (x < 0 || y < 0 || !from.equals(this.lastExternalDragDesc)) {
            log("[folder] open-folder drop skipped no screen point from=" + from + " last=" + this.lastExternalDragDesc + " x=" + x + " y=" + y);
            return false;
        }
        routeExternalDropIntoFolderAtScreen(from, this.folderActiveId, this.folderActivePos, this.folderActiveRoot, x, y, "grid-drop");
        return true;
    }

    private void routeExternalDropIntoFolderAtScreen(String from, long folderId, GridPosition folderPos, ViewGroup root, int screenX, int screenY, String reason) {
        FolderDropTarget target = findFolderChildDropTargetByScreen(root, screenX, screenY, null);
        int insertIndex = target == null ? folderChildCount(root) : Math.max(0, target.index);
        routeExternalDropIntoFolder(from, folderId, insertIndex, folderPos);
        clearFolderEdit();
        log("[folder] external drop into open folder reason=" + reason + " from=" + from + " folder=" + folderId + " index=" + insertIndex + " x=" + screenX + " y=" + screenY);
    }

    private static boolean samePosition(GridPosition a, GridPosition b) {
        return a == b || (a != null && b != null && a.pageIndex == b.pageIndex && a.slotIndex == b.slotIndex);
    }

    private void forceClearDesktopEditMode(String reason) {
        if (this.overlay == null) {
            return;
        }
        try {
            this.overlay.getGrid().setEditMode(false);
            log("[edit] force-clear grid reason=" + reason);
        } catch (Throwable t) {
            log("[edit] force-clear grid failed reason=" + reason + " err=" + t);
        }
    }

    private boolean isLandscapeGlobalEditMode() {
        try {
            if (this.overlay != null) {
                return this.overlay.getGrid().isGlobalEditMode();
            }
            return false;
        } catch (Throwable t) {
            log("[edit] read landscape global failed: " + t);
            return false;
        }
    }

    private static GridPosition parseGrid(String desc) {
        try {
            String[] parts = desc.split(":");
            if (parts.length != 3) {
                return null;
            }
            int p = Integer.parseInt(parts[1]);
            int s = Integer.parseInt(parts[2]);
            return new GridPosition(p, s);
        } catch (Throwable th) {
            return null;
        }
    }

    private static DockPosition parseDock(String desc) {
        try {
            String[] parts = desc.split(":");
            if (parts.length != 2) {
                return null;
            }
            int i = Integer.parseInt(parts[1]);
            return new DockPosition(i);
        } catch (Throwable th) {
            return null;
        }
    }

    private static FolderChildRef parseFolderChild(String desc) {
        try {
            String[] parts = desc.split(":");
            if (parts.length == 3 && "folder".equals(parts[0])) {
                return new FolderChildRef(Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    private static final class FolderChildRef {
        final int childIndex;
        final long folderId;

        FolderChildRef(long folderId, int childIndex) {
            this.folderId = folderId;
            this.childIndex = childIndex;
        }
    }

    public void launchItem(LandscapeItem it) {
        if (it == null) {
            return;
        }
        if (it.isFolder()) {
            openFolder(it, false);
        } else {
            if (it.key == null) {
                return;
            }
            launchComponent(it.key);
        }
    }

    private void openFolder(LandscapeItem folder) {
        openFolder(folder, false);
    }

    private void openFolder(LandscapeItem folder, boolean editTitle) {
        List<ComponentKey> children;
        final Activity a = this.actRef.get();
        if (a == null || folder == null || !folder.isFolder() || (children = folder.folderChildren) == null || children.isEmpty()) {
            return;
        }
        String title = folder.folderTitle == null ? "文件夹" : folder.folderTitle;
        final GridPosition folderPos = new GridPosition(folder.pageIndex, folder.slotIndex);
        final long folderId = folder.folderId;
        ViewGroup viewGroup = (ViewGroup) a.findViewById(R.id.content);
        if (viewGroup == null) {
            log("[folder] no content parent");
            return;
        }
        boolean inheritLandscapeGlobalEdit = isLandscapeGlobalEditMode();
        closeFolderOverlay("replace");
        prepareFolderEditWindowMode("open");
        this.folderEditActive = inheritLandscapeGlobalEdit;
        this.folderEditFocusIndex = -1;
        if (Build.VERSION.SDK_INT >= 31 && this.overlay != null) {
            try {
                this.overlay.setRenderEffect(RenderEffect.createBlurEffect(dp(a, 48), dp(a, 48), Shader.TileMode.CLAMP));
            } catch (Throwable th) {
                this.overlay.setRenderEffect(null);
            }
        }
        FrameLayout frameLayout = new FrameLayout(a);
        frameLayout.setClipChildren(false);
        frameLayout.setClipToPadding(false);
        frameLayout.setBackgroundColor(335544320);
        frameLayout.setClickable(true);
        frameLayout.setFocusable(true);
        frameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                this.f$0.m53x9e23fef3(view);
            }
        });
        final LinearLayout linearLayout = new LinearLayout(a);
        linearLayout.setOrientation(1);
        linearLayout.setClipChildren(true);
        linearLayout.setClipToPadding(true);
        linearLayout.setClickable(true);
        linearLayout.setLongClickable(true);
        linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                this.f$0.m54x91b38334(view);
            }
        });
        linearLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public final boolean onLongClick(View view) {
                return this.f$0.m55x85430775(view);
            }
        });
        linearLayout.setPadding(dp(a, 24), dp(a, 18), dp(a, 24), dp(a, 20));
        linearLayout.setBackground(roundRectLight(1509949439, dp(a, 30)));
        final EditText titleView = new EditText(a);
        titleView.setSingleLine(true);
        titleView.setText(title);
        titleView.setSelectAllOnFocus(false);
        titleView.setTextColor(-1);
        titleView.setHintTextColor(-1711276033);
        titleView.setShadowLayer(dp(a, 3), 0.0f, dp(a, 1), 1711276032);
        titleView.setGravity(17);
        titleView.setTextAlignment(FOLDER_COLS);
        titleView.setIncludeFontPadding(false);
        titleView.setMinWidth(0);
        titleView.setMinHeight(0);
        titleView.setTextSize(2, 20.0f);
        titleView.setInputType(16385);
        titleView.setPadding(dp(a, 10), 0, dp(a, 10), 0);
        titleView.setBackgroundColor(0);
        titleView.setImeOptions(301989894);
        titleView.setPrivateImeOptions("com.miui.input.force_no_extract=true");
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, dp(a, 42));
        titleLp.bottomMargin = dp(a, 16);
        linearLayout.addView(titleView, titleLp);
        titleView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                return this.f$0.m56x78d28bb6(folderId, folderPos, textView, i, keyEvent);
            }
        });
        titleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                this.f$0.m57x6c620ff7(view);
            }
        });
        titleView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public final void onFocusChange(View view, boolean z) {
                this.f$0.m58x5ab76d8d(folderId, titleView, folderPos, view, z);
            }
        });
        if (editTitle) {
            titleView.postDelayed(new Runnable() {
                @Override
                public final void run() {
                    LandscapeController.lambda$openFolder$21(titleView, a);
                }
            }, 180L);
        }
        final ScrollView scroll = new ScrollView(a);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(1);
        final LinearLayout root = new LinearLayout(a);
        root.setOrientation(1);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(0, dp(a, 2), 0, dp(a, 8));
        root.setClickable(true);
        root.setLongClickable(false);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                this.f$0.m59x41d6760f(view);
            }
        });
        root.setOnLongClickListener(null);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public final boolean onTouch(View view, MotionEvent motionEvent) {
                return this.f$0.m60x3565fa50(scroll, linearLayout, root, view, motionEvent);
            }
        });
        frameLayout.setOnDragListener(new View.OnDragListener() {
            @Override
            public final boolean onDrag(View view, DragEvent dragEvent) {
                return this.f$0.m61x28f57e91(folderId, folderPos, root, view, dragEvent);
            }
        });
        linearLayout.setOnDragListener(new View.OnDragListener() {
            @Override
            public final boolean onDrag(View view, DragEvent dragEvent) {
                return this.f$0.m62x1c8502d2(folderId, folderPos, root, view, dragEvent);
            }
        });
        root.setOnDragListener(new View.OnDragListener() {
            @Override
            public final boolean onDrag(View view, DragEvent dragEvent) {
                return this.f$0.m63x10148713(folderId, folderPos, root, view, dragEvent);
            }
        });
        scroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public final boolean onTouch(View view, MotionEvent motionEvent) {
                return this.f$0.m64x3a40b54(linearLayout, root, view, motionEvent);
            }
        });
        scroll.addView(root, new FrameLayout.LayoutParams(-1, -2));
        populateFolderRows(a, root, frameLayout, linearLayout, children, folderId, folderPos);
        linearLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int screenW = Math.max(dm.widthPixels, dm.heightPixels);
        int screenH = Math.min(dm.widthPixels, dm.heightPixels);
        int panelW = Math.min(dp(a, 820), (int) (screenW * 0.68f));
        int panelH = Math.min(dp(a, 620), (int) (screenH * 0.78f));
        ViewGroup.LayoutParams panelLp = new FrameLayout.LayoutParams(panelW, panelH, 17);
        frameLayout.addView(linearLayout, panelLp);
        installFolderImeCutoutGuard(frameLayout);
        viewGroup.addView(frameLayout, new FrameLayout.LayoutParams(-1, -1));
        this.folderOverlay = frameLayout;
        this.folderActiveLayer = frameLayout;
        this.folderActivePanel = linearLayout;
        this.folderActiveRoot = root;
        this.folderActiveId = folder.folderId;
        this.folderActivePos = folderPos;
        bringFolderToFront("open");
        log("[folder] open id=" + folder.folderId + " size=" + children.size() + " panel=" + panelW + "x" + panelH + " inheritGlobalEdit=" + inheritLandscapeGlobalEdit);
    }

    void m53x9e23fef3(View v) {
        closeFolderOverlay("outside");
    }

    void m54x91b38334(View v) {
        if (isFolderEditing()) {
            clearFolderEdit();
        }
    }

    boolean m55x85430775(View v) {
        setFolderGlobalEdit();
        v.performHapticFeedback(0);
        log("[folder] panel-longPress global-edit");
        return true;
    }

    boolean m56x78d28bb6(long folderId, GridPosition folderPos, TextView v, int actionId, KeyEvent event) {
        commitFolderTitle(folderId, v.getText() == null ? "" : v.getText().toString(), folderPos);
        return false;
    }

    void m57x6c620ff7(View v) {
        prepareFolderEditWindowMode("title-click");
    }

    void m58x5ab76d8d(long folderId, EditText titleView, GridPosition folderPos, View v, boolean hasFocus) {
        if (hasFocus) {
            prepareFolderEditWindowMode("title-focus");
        } else {
            commitFolderTitle(folderId, titleView.getText() == null ? "" : titleView.getText().toString(), folderPos);
        }
    }

    static void lambda$openFolder$21(EditText titleView, Activity a) {
        titleView.requestFocus();
        titleView.setSelection(titleView.getText() == null ? 0 : titleView.getText().length());
        InputMethodManager imm = (InputMethodManager) a.getSystemService("input_method");
        if (imm != null) {
            imm.showSoftInput(titleView, 1);
        }
    }

    void m59x41d6760f(View v) {
        if (isFolderEditing()) {
            clearFolderEdit();
        }
    }

    boolean m60x3565fa50(ScrollView scroll, LinearLayout panel, LinearLayout root, View v, MotionEvent event) {
        return m64x3a40b54(scroll, panel, root, event);
    }

    private void rebuildFolderRowsInPlace(String reason) {
        List<ComponentKey> children;
        Activity a = this.actRef.get();
        LinearLayout root = this.folderActiveRoot;
        View layer = this.folderActiveLayer;
        View panel = this.folderActivePanel;
        long folderId = this.folderActiveId;
        GridPosition folderPos = this.folderActivePos;
        if (a == null || root == null || layer == null || panel == null || folderId < 0 || folderPos == null) {
            long folderId2 = folderId;
            log("[folder] in-place rebuild skip reason=" + reason + " root=" + (root != null) + " layer=" + (layer != null) + " panel=" + (panel != null) + " id=" + folderId2);
            return;
        }
        try {
            if (!this.memoryFallbackActive) {
                LandscapeItem item = LandscapeStore.get(this.ctx).getGridItem(this.folderActivePos);
                if (item != null && item.isFolder()) {
                    children = item.folderChildren;
                }
            } else {
                LandscapeItem item2 = findMemoryFolder(folderId);
                children = item2 != null ? item2.folderChildren : null;
            }
            children = children;
        } catch (Throwable t) {
            log("[folder] in-place fetch failed: " + t);
            children = null;
        }
        if (children == null || children.isEmpty()) {
            closeFolderOverlay("in-place-empty");
            refreshOverlay();
        } else {
            root.removeAllViews();
            populateFolderRows(a, root, layer, panel, children, folderId, folderPos);
            log("[folder] in-place rebuilt reason=" + reason + " size=" + children.size());
        }
    }

    private void populateFolderRows(Activity a, final LinearLayout root, final View layer, final View panel, List<ComponentKey> children, final long folderId, final GridPosition folderPos) {
        LinearLayout row;
        LinearLayout row2 = null;
        int i = 0;
        while (i < children.size()) {
            if (i % FOLDER_COLS == 0) {
                LinearLayout row3 = new LinearLayout(a);
                row3.setOrientation(0);
                row3.setGravity(8388627);
                row3.setClipChildren(false);
                row3.setClipToPadding(false);
                root.addView(row3, new LinearLayout.LayoutParams(-1, -2));
                row = row3;
            } else {
                row = row2;
            }
            final ComponentKey key = children.get(i);
            final int childIndex = i;
            View cell = buildFolderDialogCell(a, key, folderId, childIndex);
            cell.setTag(Integer.valueOf(childIndex));
            cell.setLongClickable(false);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    this.f$0.m65x7bb131c(view);
                }
            });
            cell.setOnLongClickListener(null);
            LinearLayout row4 = row;
            cell.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public final boolean onTouch(View view, MotionEvent motionEvent) {
                    return this.f$0.m66xfb4a975d(layer, panel, root, folderId, childIndex, folderPos, key, view, motionEvent);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            lp.leftMargin = dp(a, 6);
            lp.rightMargin = dp(a, 6);
            lp.topMargin = dp(a, 8);
            lp.bottomMargin = dp(a, 8);
            if (row4 != null) {
                row4.addView(cell, lp);
            }
            i++;
            row2 = row4;
        }
        int last = children.size() % FOLDER_COLS;
        if (last != 0 && row2 != null) {
            for (int k = last; k < FOLDER_COLS; k++) {
                View filler = new View(a);
                filler.setVisibility(FOLDER_COLS);
                filler.setClickable(false);
                filler.setFocusable(false);
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, -2, 1.0f);
                flp.leftMargin = dp(a, 6);
                flp.rightMargin = dp(a, 6);
                flp.topMargin = dp(a, 8);
                flp.bottomMargin = dp(a, 8);
                row2.addView(filler, flp);
            }
        }
    }

    void m65x7bb131c(View v) {
        if (isFolderEditing()) {
            clearFolderEdit();
        }
    }

    private void bringFolderToFront(String reason) {
        if (this.folderOverlay == null) {
            return;
        }
        this.folderOverlay.setElevation(dp(this.ctx, 1000));
        this.folderOverlay.setTranslationZ(dp(this.ctx, 1000));
        this.folderOverlay.bringToFront();
        log("[folder] bringToFront reason=" + reason);
    }

    private void installFolderImeCutoutGuard(final FrameLayout layer) {
        if (layer == null) {
            return;
        }
        final View guard = new View(layer.getContext());
        guard.setBackgroundColor(FOLDER_IME_CUTOUT_COLOR);
        guard.setVisibility(8);
        FrameLayout.LayoutParams initial = new FrameLayout.LayoutParams(0, 0, 8388691);
        layer.addView(guard, initial);
        if (Build.VERSION.SDK_INT >= 30) {
            final int[] lastLeft = {0};
            final Runnable[] pendingInsetsUpdate = {null};
            layer.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                    return this.f$0.m48xae4bdb3e(lastLeft, pendingInsetsUpdate, layer, guard, view, windowInsets);
                }
            });
            layer.setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(1) {
                @Override
                public WindowInsets onProgress(WindowInsets insets, List<WindowInsetsAnimation> runningAnimations) {
                    int imeBottom = 0;
                    int left = lastLeft[0];
                    try {
                        imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
                        DisplayCutout cutout = insets.getDisplayCutout();
                        if (cutout != null) {
                            left = cutout.getSafeInsetLeft();
                            lastLeft[0] = left;
                        }
                    } catch (Throwable th) {
                    }
                    if (pendingInsetsUpdate[0] != null) {
                        layer.removeCallbacks(pendingInsetsUpdate[0]);
                        pendingInsetsUpdate[0] = null;
                    }
                    LandscapeController.this.updateFolderImeCutoutGuard(guard, left, imeBottom, "ime-progress");
                    return insets;
                }
            });
            Objects.requireNonNull(layer);
            layer.post(new Runnable() {
                @Override
                public final void run() {
                    layer.requestApplyInsets();
                }
            });
            return;
        }
        guard.setVisibility(8);
    }

    WindowInsets m48xae4bdb3e(int[] lastLeft, final Runnable[] pendingInsetsUpdate, FrameLayout layer, final View guard, View v, WindowInsets insets) {
        int imeBottom = 0;
        int left = 0;
        try {
            imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                left = cutout.getSafeInsetLeft();
            }
        } catch (Throwable th) {
        }
        final int imeBottom2 = imeBottom;
        final int left2 = left;
        lastLeft[0] = left2;
        if (pendingInsetsUpdate[0] != null) {
            layer.removeCallbacks(pendingInsetsUpdate[0]);
            pendingInsetsUpdate[0] = null;
        }
        if (imeBottom2 <= 0) {
            updateFolderImeCutoutGuard(guard, left2, 0, "insets-hide");
        } else {
            pendingInsetsUpdate[0] = new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m47xbabc56fd(pendingInsetsUpdate, guard, left2, imeBottom2);
                }
            };
            layer.postDelayed(pendingInsetsUpdate[0], FOLDER_SWAP_ANIM_MS);
        }
        return insets;
    }

    void m47xbabc56fd(Runnable[] pendingInsetsUpdate, View guard, int finalLeft, int finalBottom) {
        pendingInsetsUpdate[0] = null;
        updateFolderImeCutoutGuard(guard, finalLeft, finalBottom, "insets-delayed");
    }

    public void updateFolderImeCutoutGuard(View guard, int cutoutLeft, int imeBottom, String reason) {
        if (guard == null) {
            return;
        }
        int width = cutoutLeft > 0 ? dp(this.ctx, 2) + cutoutLeft : 0;
        if (width <= 0 || imeBottom <= 0) {
            guard.animate().cancel();
            guard.setVisibility(8);
            guard.setTranslationY(0.0f);
            guard.setAlpha(1.0f);
            return;
        }
        ViewGroup.LayoutParams raw = guard.getLayoutParams();
        if (raw instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            if (lp.width != width || lp.height != imeBottom) {
                lp.width = width;
                lp.height = imeBottom;
                lp.gravity = 8388691;
                guard.setLayoutParams(lp);
                log("[folder] ime cutout guard width=" + width + " height=" + imeBottom + " reason=" + reason);
            }
            guard.bringToFront();
            guard.animate().cancel();
            guard.setTranslationY(0.0f);
            guard.setAlpha(1.0f);
            guard.setVisibility(0);
        }
    }

    private void closeFolderOverlay(String reason) {
        cancelPendingFolderOpen();
        cancelFolderBlankGesture();
        cancelFolderChildGesture();
        restoreFolderDragSource();
        restoreFolderEditWindowMode("close:" + reason);
        if (Build.VERSION.SDK_INT >= 31 && this.overlay != null) {
            this.overlay.setRenderEffect(null);
        }
        if (this.folderOverlay != null) {
            if (this.folderOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) this.folderOverlay.getParent()).removeView(this.folderOverlay);
            }
            this.folderOverlay = null;
            log("[folder] close reason=" + reason);
        }
        this.folderActiveRoot = null;
        this.folderActivePanel = null;
        this.folderActiveLayer = null;
        this.folderActiveId = -1L;
        this.folderActivePos = null;
        this.folderEditActive = false;
        this.folderEditFocusIndex = -1;
        if (this.folderHadChanges) {
            this.folderHadChanges = false;
            this.folderGestureHandler.post(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.refreshOverlay();
                }
            });
        }
    }

    private void beginFolderChildDrag(View source, long folderId, int fromIndex) {
        restoreFolderDragSource();
        this.activeFolderDragSource = source;
        this.activeFolderDragFolderId = folderId;
        this.activeFolderDragFromIndex = fromIndex;
        this.activeFolderDragTargetIndex = fromIndex;
        this.activeFolderDragTargetInsert = false;
        this.activeFolderDragOutside = false;
        this.activeFolderDragHandled = false;
        this.activeFolderDesktopDragStarted = false;
        this.activeFolderTouchAccepted = false;
        this.activeFolderManualDragging = false;
        this.activeFolderDragMoved = false;
        this.activeFolderRoot = null;
        this.activeFolderDragRow = null;
    }

    public boolean m64x3a40b54(View scroll, final View panel, ViewGroup root, MotionEvent event) {
        int deltaY;
        if (event == null || root == null || scroll == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case 0:
                if (isFolderCellContentHitByScreen(root, Math.round(event.getRawX()), Math.round(event.getRawY()))) {
                    cancelFolderBlankGesture();
                } else {
                    cancelFolderChildGesture();
                    cancelFolderBlankGesture();
                    this.activeFolderBlankTouchAccepted = true;
                    this.activeFolderBlankLongPressFired = false;
                    this.activeFolderBlankDownRawX = event.getRawX();
                    this.activeFolderBlankDownRawY = event.getRawY();
                    this.activeFolderBlankLastRawY = this.activeFolderBlankDownRawY;
                    this.activeFolderBlankDownAtMs = SystemClock.elapsedRealtime();
                    log("[folder] blank-down x=" + Math.round(this.activeFolderBlankDownRawX) + " y=" + Math.round(this.activeFolderBlankDownRawY));
                    if (scroll.getParent() != null) {
                        scroll.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    this.activeFolderBlankLongPressRunnable = new Runnable() {
                        @Override
                        public final void run() {
                            this.f$0.m45x6377af7a(panel);
                        }
                    };
                    this.folderGestureHandler.postDelayed(this.activeFolderBlankLongPressRunnable, FOLDER_BLANK_GLOBAL_EDIT_DELAY_MS);
                }
                break;
            case 1:
                if (this.activeFolderBlankTouchAccepted) {
                    long dt = SystemClock.elapsedRealtime() - this.activeFolderBlankDownAtMs;
                    cancelFolderBlankLongPressOnly();
                    if (!this.activeFolderBlankLongPressFired && isFolderEditing()) {
                        clearFolderEdit();
                    }
                    log("[folder] blank-up dtMs=" + dt + " fired=" + this.activeFolderBlankLongPressFired + " editing=" + isFolderEditing());
                    this.activeFolderBlankTouchAccepted = false;
                    this.activeFolderBlankLongPressFired = false;
                    break;
                }
                break;
            case 2:
                if (this.activeFolderBlankTouchAccepted) {
                    float dx = event.getRawX() - this.activeFolderBlankDownRawX;
                    float dy = event.getRawY() - this.activeFolderBlankDownRawY;
                    int slop = ViewConfiguration.get(scroll.getContext()).getScaledTouchSlop();
                    int cancelSlop = slop * 3;
                    if ((dx * dx) + (dy * dy) > cancelSlop * cancelSlop) {
                        long dt2 = SystemClock.elapsedRealtime() - this.activeFolderBlankDownAtMs;
                        cancelFolderBlankLongPressOnly();
                        log("[folder] blank-cancel by-move dtMs=" + dt2 + " dx=" + Math.round(dx) + " dy=" + Math.round(dy) + " cancelSlop=" + cancelSlop);
                        if ((scroll instanceof ScrollView) && (deltaY = Math.round(this.activeFolderBlankLastRawY - event.getRawY())) != 0) {
                            ((ScrollView) scroll).scrollBy(0, deltaY);
                        }
                        this.activeFolderBlankLastRawY = event.getRawY();
                    }
                    break;
                }
                break;
            case GridPosition.ROWS:
                if (this.activeFolderBlankTouchAccepted) {
                    cancelFolderBlankGesture();
                    break;
                }
                break;
        }
        return false;
    }

    void m45x6377af7a(View panel) {
        if (this.activeFolderBlankTouchAccepted) {
            this.activeFolderBlankLongPressFired = true;
            setFolderGlobalEdit();
            if (panel != null) {
                panel.performHapticFeedback(0);
            }
            long dt = SystemClock.elapsedRealtime() - this.activeFolderBlankDownAtMs;
            log("[folder] blank-longPress dtMs=" + dt + " x=" + Math.round(this.activeFolderBlankDownRawX) + " y=" + Math.round(this.activeFolderBlankDownRawY));
        }
    }

    private boolean isFolderCellContentHitByScreen(ViewGroup root, int screenX, int screenY) {
        if (root == null) {
            return false;
        }
        int[] loc = new int[2];
        for (int i = 0; i < root.getChildCount(); i++) {
            View rowView = root.getChildAt(i);
            if (rowView instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) rowView;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View cell = row.getChildAt(j);
                    if (cell != null && (cell.getTag() instanceof Integer) && cell.getVisibility() == 0) {
                        cell.getLocationOnScreen(loc);
                        int left = loc[0];
                        int top = loc[1];
                        int right = cell.getWidth() + left;
                        int bottom = cell.getHeight() + top;
                        if (screenX >= left && screenX < right && screenY >= top && screenY < bottom) {
                            return isFolderCellContentHit(cell, screenX - left, screenY - top);
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean m63x10148713(View host, DragEvent event, long folderId, GridPosition folderPos, ViewGroup root) {
        boolean ours = false;
        if (event == null) {
            return false;
        }
        switch (event.getAction()) {
            case 1:
                if (event.getClipDescription() != null && LandscapePagedGridView.DRAG_LABEL.contentEquals(event.getClipDescription().getLabel())) {
                    ours = true;
                }
                log("[folder] external-drag started ours=" + ours);
                return ours;
            case 2:
            case 5:
                rememberFolderExternalDragPoint(host, event);
                closeFolderIfExternalDragLeavesPanel(host, event);
                return true;
            case GridPosition.ROWS:
                CharSequence text = dragText(event);
                if (text == null) {
                    return true;
                }
                int[] loc = new int[2];
                if (host != null) {
                    host.getLocationOnScreen(loc);
                }
                int screenX = loc[0] + Math.round(event.getX());
                int screenY = loc[1] + Math.round(event.getY());
                routeExternalDropIntoFolderAtScreen(text.toString(), folderId, folderPos, root, screenX, screenY, "folder-layer-drop");
                cancelPendingFolderOpen();
                clearExternalDragPoint();
                return true;
            case FOLDER_COLS:
            case 6:
                return true;
            default:
                return true;
        }
    }

    private void rememberFolderExternalDragPoint(View host, DragEvent event) {
        CharSequence text = dragText(event);
        if (text == null || host == null) {
            return;
        }
        String from = text.toString();
        if (from.startsWith("grid:") || from.startsWith("dock:")) {
            int[] loc = new int[2];
            host.getLocationOnScreen(loc);
            this.lastExternalDragDesc = from;
            this.lastExternalDragScreenX = loc[0] + Math.round(event.getX());
            this.lastExternalDragScreenY = loc[1] + Math.round(event.getY());
        }
    }

    private void closeFolderIfExternalDragLeavesPanel(View host, DragEvent event) {
        CharSequence text;
        if (host == null || event == null || this.folderActivePanel == null || (text = dragText(event)) == null) {
            return;
        }
        String from = text.toString();
        if (from.startsWith("grid:") || from.startsWith("dock:")) {
            int[] hostLoc = new int[2];
            host.getLocationOnScreen(hostLoc);
            boolean inside = false;
            int screenX = hostLoc[0] + Math.round(event.getX());
            int screenY = hostLoc[1] + Math.round(event.getY());
            int[] panelLoc = new int[2];
            this.folderActivePanel.getLocationOnScreen(panelLoc);
            int margin = dp(this.ctx, 20);
            if (screenX >= panelLoc[0] - margin && screenX < panelLoc[0] + this.folderActivePanel.getWidth() + margin && screenY >= panelLoc[1] - margin && screenY < panelLoc[1] + this.folderActivePanel.getHeight() + margin) {
                inside = true;
            }
            if (inside) {
                return;
            }
            log("[folder] external drag leave panel from=" + from + " x=" + screenX + " y=" + screenY);
            closeFolderOverlay("external-drag-leave");
        }
    }

    private void routeExternalDropIntoFolder(String from, long folderId, int insertIndex, GridPosition folderPos) {
        DockPosition fromDock;
        if (from == null || folderId < 0) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                routeExternalDropIntoFolderMemory(from, folderId, insertIndex);
                rebuildFolderRowsInPlace("external-drop-memory");
                this.folderHadChanges = true;
                return;
            }
            LandscapeStore store = LandscapeStore.get(this.ctx);
            if (from.startsWith("grid:")) {
                GridPosition fromPos = parseGrid(from);
                if (fromPos != null && !fromPos.equals(folderPos)) {
                    store.insertGridToFolder(fromPos, folderId, insertIndex);
                }
            } else if (from.startsWith("dock:") && (fromDock = parseDock(from)) != null) {
                store.insertDockToFolder(fromDock, folderId, insertIndex);
            }
            rebuildFolderRowsInPlace("external-drop");
            this.folderHadChanges = true;
        } catch (Throwable t) {
            log("[folder] external drop failed from=" + from + " folder=" + folderId + " err=" + t);
        }
    }

    private void routeExternalDropIntoFolderMemory(String from, long folderId, int insertIndex) {
        DockPosition dock;
        LandscapeItem src;
        LandscapeItem folder = findMemoryFolder(folderId);
        if (folder == null || folder.folderChildren == null) {
            return;
        }
        ComponentKey key = null;
        if (from.startsWith("grid:")) {
            GridPosition fromPos = parseGrid(from);
            LandscapeItem src2 = findMemoryGrid(fromPos);
            if (src2 != null && !src2.isFolder()) {
                key = src2.key;
                removeMemoryGrid(fromPos);
            }
        } else if (from.startsWith("dock:") && (src = findMemoryDock((dock = parseDock(from)))) != null) {
            key = src.key;
            removeMemoryDock(dock);
        }
        if (key == null || folder.folderChildren.contains(key)) {
            return;
        }
        int target = Math.max(0, Math.min(folder.folderChildren.size(), insertIndex));
        folder.folderChildren.add(target, key);
    }

    public boolean m66xfb4a975d(final View source, MotionEvent event, View layer, final View panel, final ViewGroup root, final long folderId, final int childIndex, GridPosition folderPos, ComponentKey launchKey) {
        boolean z;
        boolean z2;
        if (event == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case 0:
                boolean contentHit = isFolderCellContentHit(source, event.getX(), event.getY());
                cancelFolderChildGesture();
                cancelFolderBlankGesture();
                this.activeFolderDownOnContent = contentHit;
                if (contentHit) {
                    beginFolderChildDrag(source, folderId, childIndex);
                    this.activeFolderTouchAccepted = true;
                    this.activeFolderDownRawX = event.getRawX();
                    this.activeFolderDownRawY = event.getRawY();
                    this.activeFolderLastRawX = this.activeFolderDownRawX;
                    this.activeFolderLastRawY = this.activeFolderDownRawY;
                    this.activeFolderChildDownAtMs = SystemClock.elapsedRealtime();
                    log("[folder] child-down idx=" + childIndex + " contentHit=true x=" + Math.round(this.activeFolderDownRawX) + " y=" + Math.round(this.activeFolderDownRawY));
                    source.setPressed(true);
                    if (source.getParent() != null) {
                        source.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    this.activeFolderLongPressRunnable = new Runnable() {
                        @Override
                        public final void run() {
                            this.f$0.m44x7714b88f(source, folderId, childIndex, root);
                        }
                    };
                    this.folderGestureHandler.postDelayed(this.activeFolderLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                    return true;
                }
                resetFolderDragState();
                this.activeFolderBlankTouchAccepted = true;
                this.activeFolderBlankLongPressFired = false;
                this.activeFolderBlankDownRawX = event.getRawX();
                this.activeFolderBlankDownRawY = event.getRawY();
                this.activeFolderBlankLastRawY = this.activeFolderBlankDownRawY;
                this.activeFolderBlankDownAtMs = SystemClock.elapsedRealtime();
                log("[folder] blank-cell-down idx=" + childIndex + " x=" + Math.round(this.activeFolderBlankDownRawX) + " y=" + Math.round(this.activeFolderBlankDownRawY));
                if (source.getParent() != null) {
                    source.getParent().requestDisallowInterceptTouchEvent(true);
                }
                this.activeFolderBlankLongPressRunnable = new Runnable() {
                    @Override
                    public final void run() {
                        this.f$0.m43x8385344e(panel, childIndex);
                    }
                };
                this.folderGestureHandler.postDelayed(this.activeFolderBlankLongPressRunnable, FOLDER_BLANK_GLOBAL_EDIT_DELAY_MS);
                return true;
            case 1:
                if (this.activeFolderBlankTouchAccepted && !this.activeFolderDownOnContent) {
                    long blankDt = SystemClock.elapsedRealtime() - this.activeFolderBlankDownAtMs;
                    cancelFolderBlankLongPressOnly();
                    if (!this.activeFolderBlankLongPressFired && isFolderEditing()) {
                        clearFolderEdit();
                    }
                    log("[folder] blank-cell-up idx=" + childIndex + " dtMs=" + blankDt + " fired=" + this.activeFolderBlankLongPressFired + " editing=" + isFolderEditing());
                    this.activeFolderBlankTouchAccepted = false;
                    this.activeFolderBlankLongPressFired = false;
                    return true;
                }
                if (this.activeFolderTouchAccepted) {
                    long childDt = SystemClock.elapsedRealtime() - this.activeFolderChildDownAtMs;
                    cancelFolderLongPressOnly();
                    source.setPressed(false);
                    if (this.activeFolderManualDragging) {
                        this.activeFolderLastRawX = event.getRawX();
                        this.activeFolderLastRawY = event.getRawY();
                        updateFolderManualDragLocation(root, panel, event.getRawX(), event.getRawY(), this.activeFolderDragMoved);
                        finishFolderChildDrag(folderPos, root);
                    } else {
                        float upDx = event.getRawX() - this.activeFolderDownRawX;
                        float upDy = event.getRawY() - this.activeFolderDownRawY;
                        int tapSlop = ViewConfiguration.get(source.getContext()).getScaledTouchSlop();
                        boolean moved = (upDx * upDx) + (upDy * upDy) > ((float) (tapSlop * tapSlop));
                        boolean wasEdit = isFolderEditing();
                        int focus = this.folderEditFocusIndex;
                        boolean downOnContent = this.activeFolderDownOnContent;
                        resetFolderDragState();
                        if (moved) {
                            log("[folder] cell up moved-skip-launch dx=" + upDx + " dy=" + upDy);
                        } else if (wasEdit && focus >= 0 && focus != childIndex) {
                            clearFolderEdit();
                            log("[folder] cell up single-other-tap-exit focus=" + focus + " tap=" + childIndex);
                        } else if (downOnContent) {
                            closeFolderOverlay("launch");
                            launchComponent(launchKey);
                        } else if (wasEdit) {
                            clearFolderEdit();
                            log("[folder] cell blank tap exit edit idx=" + childIndex);
                        } else {
                            log("[folder] cell blank tap ignored idx=" + childIndex);
                        }
                    }
                    log("[folder] child-up idx=" + childIndex + " dtMs=" + childDt + " manualDragging=" + this.activeFolderManualDragging + " downOnContent=" + this.activeFolderDownOnContent);
                    return true;
                }
                return false;
            case 2:
                if (this.activeFolderBlankTouchAccepted && !this.activeFolderDownOnContent) {
                    float blankDx = event.getRawX() - this.activeFolderBlankDownRawX;
                    float blankDy = event.getRawY() - this.activeFolderBlankDownRawY;
                    int blankSlop = ViewConfiguration.get(source.getContext()).getScaledTouchSlop();
                    int blankCancelSlop = blankSlop * 3;
                    if ((blankDx * blankDx) + (blankDy * blankDy) <= blankCancelSlop * blankCancelSlop) {
                        return true;
                    }
                    long dt = SystemClock.elapsedRealtime() - this.activeFolderBlankDownAtMs;
                    cancelFolderBlankLongPressOnly();
                    log("[folder] blank-cell-cancel by-move idx=" + childIndex + " dtMs=" + dt + " dx=" + Math.round(blankDx) + " dy=" + Math.round(blankDy) + " cancelSlop=" + blankCancelSlop);
                    if (source.getParent() == null) {
                        z2 = false;
                    } else {
                        z2 = false;
                        source.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    this.activeFolderBlankTouchAccepted = z2;
                    return z2;
                }
                if (!this.activeFolderTouchAccepted) {
                    return false;
                }
                if (!this.activeFolderManualDragging) {
                    float dx = event.getRawX() - this.activeFolderDownRawX;
                    float dy = event.getRawY() - this.activeFolderDownRawY;
                    int cancelSlop = ViewConfiguration.get(source.getContext()).getScaledTouchSlop() * 12;
                    if ((dx * dx) + (dy * dy) <= cancelSlop * cancelSlop) {
                        return true;
                    }
                    long dt2 = SystemClock.elapsedRealtime() - this.activeFolderChildDownAtMs;
                    cancelFolderLongPressOnly();
                    log("[folder] child-cancel by-move idx=" + childIndex + " dtMs=" + dt2 + " dx=" + Math.round(dx) + " dy=" + Math.round(dy) + " cancelSlop=" + cancelSlop);
                    if (source.getParent() == null) {
                        z = false;
                    } else {
                        z = false;
                        source.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    this.activeFolderTouchAccepted = z;
                    return z;
                }
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                this.activeFolderLastRawX = rawX;
                this.activeFolderLastRawY = rawY;
                float dragDx = rawX - this.activeFolderDownRawX;
                float dragDy = rawY - this.activeFolderDownRawY;
                int slop = ViewConfiguration.get(source.getContext()).getScaledTouchSlop();
                if ((dragDx * dragDx) + (dragDy * dragDy) > slop * slop) {
                    this.activeFolderDragMoved = true;
                }
                updateFolderManualDragLocation(root, panel, rawX, rawY, this.activeFolderDragMoved);
                return true;
            case GridPosition.ROWS:
                if (this.activeFolderBlankTouchAccepted && !this.activeFolderDownOnContent) {
                    cancelFolderBlankGesture();
                    return true;
                }
                if (!this.activeFolderTouchAccepted) {
                    return false;
                }
                cancelFolderChildGesture();
                restoreFolderDragSource();
                resetFolderDragState();
                return true;
            default:
                return true;
        }
    }

    void m43x8385344e(View panel, int childIndex) {
        if (this.activeFolderBlankTouchAccepted) {
            this.activeFolderBlankLongPressFired = true;
            setFolderGlobalEdit();
            if (panel != null) {
                panel.performHapticFeedback(0);
            }
            long dt = SystemClock.elapsedRealtime() - this.activeFolderBlankDownAtMs;
            log("[folder] blank-cell-longPress dtMs=" + dt + " idx=" + childIndex);
        }
    }

    void m44x7714b88f(View source, long folderId, int childIndex, ViewGroup root) {
        if (this.activeFolderDragSource != source || this.activeFolderDragFolderId != folderId || this.activeFolderDragFromIndex != childIndex) {
            return;
        }
        this.activeFolderManualDragging = true;
        this.activeFolderRoot = root;
        this.activeFolderStartTranslationX = source.getTranslationX();
        this.activeFolderStartTranslationY = source.getTranslationY();
        source.setPressed(false);
        source.animate().cancel();
        if (source.getParent() instanceof View) {
            this.activeFolderDragRow = (View) source.getParent();
            this.activeFolderDragRow.setTranslationZ(dp(source.getContext(), 20));
        }
        source.setTranslationZ(dp(source.getContext(), 24));
        source.animate().alpha(0.92f).scaleX(1.08f).scaleY(1.08f).setDuration(120L).start();
        long dt = SystemClock.elapsedRealtime() - this.activeFolderChildDownAtMs;
        log("[folder] child manual drag start folder=" + folderId + " index=" + childIndex + " dtMs=" + dt);
        if (!this.folderEditActive) {
            setFolderSingleEdit(childIndex);
        }
    }

    private void cancelFolderLongPressOnly() {
        if (this.activeFolderLongPressRunnable != null) {
            this.folderGestureHandler.removeCallbacks(this.activeFolderLongPressRunnable);
            this.activeFolderLongPressRunnable = null;
        }
    }

    private void cancelFolderBlankLongPressOnly() {
        if (this.activeFolderBlankLongPressRunnable != null) {
            this.folderGestureHandler.removeCallbacks(this.activeFolderBlankLongPressRunnable);
            this.activeFolderBlankLongPressRunnable = null;
        }
    }

    private void cancelFolderBlankGesture() {
        cancelFolderBlankLongPressOnly();
        this.activeFolderBlankTouchAccepted = false;
        this.activeFolderBlankLongPressFired = false;
    }

    private void cancelFolderChildGesture() {
        cancelFolderLongPressOnly();
        this.activeFolderManualDragging = false;
    }

    private void resetFolderDragState() {
        this.activeFolderDragFolderId = -1L;
        this.activeFolderDragFromIndex = -1;
        this.activeFolderDragTargetIndex = -1;
        this.activeFolderDragTargetInsert = false;
        this.activeFolderDragOutside = false;
        this.activeFolderDragHandled = false;
        this.activeFolderDesktopDragStarted = false;
        this.activeFolderTouchAccepted = false;
        this.activeFolderDownOnContent = false;
        this.activeFolderManualDragging = false;
        this.activeFolderDragMoved = false;
        this.activeFolderDragSource = null;
        this.activeFolderDragRow = null;
    }

    private void updateFolderManualDragLocation(ViewGroup root, View panel, float rawX, float rawY) {
        updateFolderManualDragLocation(root, panel, rawX, rawY, true);
    }

    private void updateFolderManualDragLocation(ViewGroup root, View panel, float rawX, float rawY, boolean allowTargetChange) {
        View source = this.activeFolderDragSource;
        if (source != null && this.activeFolderManualDragging) {
            source.setTranslationX((this.activeFolderStartTranslationX + rawX) - this.activeFolderDownRawX);
            source.setTranslationY((this.activeFolderStartTranslationY + rawY) - this.activeFolderDownRawY);
        }
        if (!allowTargetChange) {
            this.activeFolderDragOutside = false;
            this.activeFolderDragTargetIndex = this.activeFolderDragFromIndex;
            this.activeFolderDragTargetInsert = false;
            return;
        }
        if (panel == null) {
            updateFolderDragOutside();
            return;
        }
        int[] panelLoc = new int[2];
        panel.getLocationOnScreen(panelLoc);
        Rect panelRect = new Rect(panelLoc[0], panelLoc[1], panelLoc[0] + panel.getWidth(), panelLoc[1] + panel.getHeight());
        int x = Math.round(rawX);
        int y = Math.round(rawY);
        if (!panelRect.contains(x, y)) {
            updateFolderDragOutside();
            return;
        }
        FolderDropTarget target = findFolderChildDropTargetByScreen(root, x, y, this.activeFolderDragSource);
        if (target != null && target.index >= 0) {
            this.activeFolderDragOutside = false;
            this.activeFolderDragTargetIndex = target.index;
            this.activeFolderDragTargetInsert = target.insert;
        } else {
            this.activeFolderDragOutside = false;
            this.activeFolderDragTargetIndex = this.activeFolderDragFromIndex;
            this.activeFolderDragTargetInsert = false;
        }
    }

    private void updateFolderDragInside(ViewGroup root, View panel, float panelX, float panelY) {
        int target = findFolderChildDropIndex(root, panel, panelX, panelY);
        if (target >= 0) {
            this.activeFolderDragOutside = false;
            this.activeFolderDragTargetIndex = target;
            this.activeFolderDragTargetInsert = false;
        }
    }

    private void updateFolderDragOutside() {
        this.activeFolderDragOutside = true;
        this.activeFolderDragTargetIndex = -1;
        this.activeFolderDragTargetInsert = false;
        maybeStartFolderChildDesktopDrag();
    }

    private void maybeStartFolderChildDesktopDrag() {
        if (this.activeFolderDesktopDragStarted || this.activeFolderDragSource == null || this.activeFolderDragFolderId < 0 || this.activeFolderDragFromIndex < 0) {
            return;
        }
        this.activeFolderDesktopDragStarted = true;
        String descriptor = "folder:" + this.activeFolderDragFolderId + ":" + this.activeFolderDragFromIndex;
        LandscapePagedGridView.startCellDrag(this.activeFolderDragSource, descriptor);
        log("[folder] child handoff to desktop drag " + descriptor);
        closeFolderOverlay("child-out-drag");
    }

    private void finishFolderChildDrag(GridPosition folderPos) {
        finishFolderChildDrag(folderPos, this.activeFolderRoot);
    }

    private void finishFolderChildDrag(GridPosition folderPos, ViewGroup root) {
        boolean routeWillRebuild = this.activeFolderDragHandled;
        if (!this.activeFolderDragMoved) {
            this.activeFolderDragTargetIndex = this.activeFolderDragFromIndex;
            this.activeFolderDragOutside = false;
            this.activeFolderDragTargetInsert = false;
        }
        if (this.activeFolderDragMoved && !this.activeFolderDragHandled && this.activeFolderDragFolderId >= 0 && this.activeFolderDragFromIndex >= 0) {
            this.activeFolderDragHandled = true;
            if (this.activeFolderDragOutside) {
                routeFolderChildOut(this.activeFolderDragFolderId, this.activeFolderDragFromIndex);
                routeWillRebuild = true;
            } else if (this.activeFolderDragTargetIndex >= 0 && this.activeFolderDragTargetIndex != this.activeFolderDragFromIndex) {
                int target = this.activeFolderDragTargetIndex;
                if (this.activeFolderDragTargetInsert) {
                    routeWillRebuild = routeFolderChildMove(root, this.activeFolderDragFolderId, this.activeFolderDragFromIndex, target, folderPos);
                } else if (target != this.activeFolderDragFromIndex) {
                    routeWillRebuild = routeFolderChildSwap(root, this.activeFolderDragFolderId, this.activeFolderDragFromIndex, target, folderPos);
                }
            }
        }
        if (routeWillRebuild) {
            this.activeFolderDragSource = null;
        } else {
            restoreFolderDragSource();
        }
        if (this.activeFolderDragMoved || routeWillRebuild) {
            clearFolderEdit();
        }
        this.activeFolderDragFolderId = -1L;
        this.activeFolderDragFromIndex = -1;
        this.activeFolderDragTargetIndex = -1;
        this.activeFolderDragTargetInsert = false;
        this.activeFolderDragOutside = false;
        this.activeFolderDragHandled = false;
        this.activeFolderDesktopDragStarted = false;
        this.activeFolderTouchAccepted = false;
        this.activeFolderManualDragging = false;
        this.activeFolderDragMoved = false;
        this.activeFolderRoot = null;
        this.activeFolderDragRow = null;
    }

    private void restoreFolderDragSource() {
        final View source = this.activeFolderDragSource;
        this.activeFolderDragSource = null;
        if (source == null) {
            return;
        }
        source.animate().cancel();
        source.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).translationX(this.activeFolderStartTranslationX).translationY(this.activeFolderStartTranslationY).setDuration(120L).withEndAction(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m68x821247d8(source);
            }
        }).start();
    }

    void m68x821247d8(View source) {
        source.setTranslationZ(0.0f);
        if (this.activeFolderDragRow != null) {
            this.activeFolderDragRow.setTranslationZ(0.0f);
        }
        this.activeFolderDragRow = null;
    }

    private boolean isFolderCellContentHit(View source, float x, float y) {
        Rect r;
        if (!(source instanceof ViewGroup)) {
            return true;
        }
        ViewGroup group = (ViewGroup) source;
        if (group.getChildCount() <= 0) {
            return true;
        }
        ViewGroup contentGroup = group;
        int xOff = 0;
        int yOff = 0;
        View first = group.getChildAt(0);
        if (first instanceof ViewGroup) {
            contentGroup = (ViewGroup) first;
            xOff = first.getLeft();
            yOff = first.getTop();
        }
        Rect active = new Rect();
        boolean hasAny = false;
        int pad = dp(source.getContext(), 8);
        for (int i = 0; i < contentGroup.getChildCount(); i++) {
            View child = contentGroup.getChildAt(i);
            if (child != null && child.getVisibility() == 0 && (r = folderChildContentRect(child)) != null) {
                r.offset(xOff, yOff);
                r.inset(-pad, -pad);
                if (!hasAny) {
                    active.set(r);
                    hasAny = true;
                } else {
                    active.union(r);
                }
            }
        }
        if (hasAny) {
            return active.contains(Math.round(x), Math.round(y));
        }
        return true;
    }

    private static Rect folderChildContentRect(View child) {
        TextView tv;
        Layout layout;
        if ((child instanceof TextView) && (layout = (tv = (TextView) child).getLayout()) != null && layout.getLineCount() > 0) {
            float maxLineW = 0.0f;
            for (int i = 0; i < layout.getLineCount(); i++) {
                maxLineW = Math.max(maxLineW, layout.getLineWidth(i));
            }
            int textW = (int) Math.ceil(maxLineW);
            int centerX = (child.getLeft() + child.getRight()) / 2;
            int half = Math.max(1, textW / 2);
            int top = child.getTop() + tv.getPaddingTop();
            int bottom = Math.min(child.getBottom(), layout.getHeight() + top);
            return new Rect(centerX - half, top, centerX + half, bottom);
        }
        return new Rect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
    }

    private int normalizeFolderSwapTarget(int fromIndex, int rawTarget) {
        return rawTarget;
    }

    private boolean routeFolderChildMove(ViewGroup root, final long folderId, final int fromIndex, int insertIndex, final GridPosition folderPos) {
        final int target = normalizeFolderInsertTarget(fromIndex, insertIndex);
        if (target == fromIndex) {
            return false;
        }
        View fromView = findFolderCellView(root, fromIndex);
        View toView = findFolderCellView(root, Math.max(0, Math.min(insertIndex, Math.max(0, folderChildCount(root) - 1))));
        if (fromView == null || toView == null) {
            boolean z = true;
            m70xe131e594(folderId, fromIndex, target, folderPos);
            return z;
        }
        int[] fromLoc = new int[2];
        int[] toLoc = new int[2];
        fromView.getLocationOnScreen(fromLoc);
        toView.getLocationOnScreen(toLoc);
        float fromOrigX = fromLoc[0] - fromView.getTranslationX();
        float fromOrigY = fromLoc[1] - fromView.getTranslationY();
        float toOrigX = toLoc[0] - toView.getTranslationX();
        float toOrigY = toLoc[1] - toView.getTranslationY();
        float dx = toOrigX - fromOrigX;
        float dy = toOrigY - fromOrigY;
        fromView.animate().cancel();
        fromView.animate().translationX(dx).translationY(dy).alpha(0.82f).scaleX(1.02f).scaleY(1.02f).setDuration(FOLDER_SWAP_ANIM_MS).start();
        animateFolderInsertGap(root, fromIndex, target);
        fromView.postDelayed(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m70xe131e594(folderId, fromIndex, target, folderPos);
            }
        }, 170L);
        return true;
    }

    private int normalizeFolderInsertTarget(int fromIndex, int insertIndex) {
        if (fromIndex < 0 || insertIndex < 0) {
            return fromIndex;
        }
        int target = insertIndex;
        if (fromIndex < insertIndex) {
            target = insertIndex - 1;
        }
        return Math.max(0, target);
    }

    private int folderChildCount(ViewGroup root) {
        if (root == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < root.getChildCount(); i++) {
            View rowView = root.getChildAt(i);
            if (rowView instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) rowView;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View cell = row.getChildAt(j);
                    if (cell != null && (cell.getTag() instanceof Integer)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private void animateFolderInsertGap(ViewGroup root, int fromIndex, int targetIndex) {
        View v;
        if (root == null || fromIndex == targetIndex) {
            return;
        }
        int start = Math.min(fromIndex, targetIndex);
        int end = Math.max(fromIndex, targetIndex);
        int dir = fromIndex < targetIndex ? -1 : 1;
        for (int i = start; i <= end; i++) {
            if (i != fromIndex && (v = findFolderCellView(root, i)) != null) {
                v.animate().cancel();
                v.animate().translationX(dp(this.ctx, 18) * dir).scaleX(0.96f).scaleY(0.96f).setDuration(FOLDER_SWAP_ANIM_MS).start();
            }
        }
    }

    private boolean routeFolderChildSwap(ViewGroup root, final long folderId, final int fromIndex, final int toIndex, final GridPosition folderPos) {
        if (fromIndex == toIndex) {
            return false;
        }
        View fromView = findFolderCellView(root, fromIndex);
        View toView = findFolderCellView(root, toIndex);
        if (fromView == null || toView == null) {
            boolean z = true;
            m71xe9465c53(folderId, fromIndex, toIndex, folderPos);
            return z;
        }
        int[] fromLoc = new int[2];
        int[] toLoc = new int[2];
        fromView.getLocationOnScreen(fromLoc);
        toView.getLocationOnScreen(toLoc);
        float fromOrigX = fromLoc[0] - fromView.getTranslationX();
        float fromOrigY = fromLoc[1] - fromView.getTranslationY();
        float toOrigX = toLoc[0] - toView.getTranslationX();
        float toOrigY = toLoc[1] - toView.getTranslationY();
        float dx = toOrigX - fromOrigX;
        float dy = toOrigY - fromOrigY;
        fromView.animate().cancel();
        toView.animate().cancel();
        fromView.animate().translationX(dx).translationY(dy).alpha(0.72f).scaleX(0.96f).scaleY(0.96f).setDuration(FOLDER_SWAP_ANIM_MS).start();
        toView.animate().translationX(-dx).translationY(-dy).setDuration(FOLDER_SWAP_ANIM_MS).start();
        fromView.postDelayed(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m71xe9465c53(folderId, fromIndex, toIndex, folderPos);
            }
        }, 160L);
        return true;
    }

    public void m71xe9465c53(long folderId, int fromIndex, int toIndex, GridPosition folderPos) {
        if (folderId < 0 || fromIndex == toIndex) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                swapMemoryFolderChild(folderId, fromIndex, toIndex);
                bindMemorySnapshot("folder-child-move");
                rebuildFolderRowsInPlace("child-swap-memory");
                this.folderHadChanges = true;
                log("[folder] child swap memory folder=" + folderId + " from=" + fromIndex + " to=" + toIndex);
                return;
            }
            LandscapeStore store = LandscapeStore.get(this.ctx);
            store.swapFolderChild(folderId, fromIndex, toIndex);
            rebuildFolderRowsInPlace("child-swap");
            this.folderHadChanges = true;
            log("[folder] child swap folder=" + folderId + " from=" + fromIndex + " to=" + toIndex);
        } catch (Throwable t) {
            log("[folder] child swap failed folder=" + folderId + " from=" + fromIndex + " to=" + toIndex + " err=" + t);
        }
    }

    public void m70xe131e594(long folderId, int fromIndex, int toIndex, GridPosition folderPos) {
        if (folderId < 0 || fromIndex == toIndex) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                moveMemoryFolderChild(folderId, fromIndex, toIndex);
                bindMemorySnapshot("folder-child-insert");
                rebuildFolderRowsInPlace("child-insert-memory");
                this.folderHadChanges = true;
                log("[folder] child insert memory folder=" + folderId + " from=" + fromIndex + " to=" + toIndex);
                return;
            }
            LandscapeStore store = LandscapeStore.get(this.ctx);
            store.moveFolderChild(folderId, fromIndex, toIndex);
            rebuildFolderRowsInPlace("child-insert");
            this.folderHadChanges = true;
            log("[folder] child insert folder=" + folderId + " from=" + fromIndex + " to=" + toIndex);
        } catch (Throwable t) {
            log("[folder] child insert failed folder=" + folderId + " from=" + fromIndex + " to=" + toIndex + " err=" + t);
        }
    }

    private void routeFolderChildOut(long folderId, int fromIndex) {
        if (folderId < 0 || fromIndex < 0) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                removeMemoryFolderChildToGrid(folderId, fromIndex);
                bindMemorySnapshot("folder-child-out");
                closeFolderOverlay("child-out-memory");
                log("[folder] child out memory folder=" + folderId + " index=" + fromIndex);
                return;
            }
            LandscapeStore.get(this.ctx).removeFolderChildToGrid(folderId, fromIndex);
            refreshOverlay();
            closeFolderOverlay("child-out");
            log("[folder] child out folder=" + folderId + " index=" + fromIndex);
        } catch (Throwable t) {
            log("[folder] child out failed folder=" + folderId + " index=" + fromIndex + " err=" + t);
        }
    }

    private void commitFolderTitle(long folderId, String title, GridPosition folderPos) {
        if (folderId < 0) {
            return;
        }
        String nextTitle = title == null ? "" : title.trim();
        if (nextTitle.isEmpty()) {
            nextTitle = "文件夹";
        }
        try {
            if (this.memoryFallbackActive) {
                renameMemoryFolder(folderId, nextTitle);
                bindMemorySnapshot("folder-rename");
                log("[folder] rename memory folder=" + folderId + " title=" + nextTitle);
            } else {
                LandscapeStore.get(this.ctx).renameFolder(folderId, nextTitle);
                refreshOverlay();
                log("[folder] rename folder=" + folderId + " title=" + nextTitle);
            }
        } catch (Throwable t) {
            log("[folder] rename failed folder=" + folderId + " err=" + t);
        }
    }

    private void prepareFolderEditWindowMode(String reason) {
        Activity a = this.actRef.get();
        if (a == null || a.getWindow() == null) {
            return;
        }
        try {
            setHomeCutoutMode(true, "folder-edit:" + reason);
            WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            if (this.folderSoftInputMode == Integer.MIN_VALUE) {
                this.folderSoftInputMode = lp.softInputMode;
            }
            if (lp.softInputMode != 48) {
                a.getWindow().setSoftInputMode(48);
            }
            log("[folder] edit window mode adjustNothing reason=" + reason);
        } catch (Throwable t) {
            log("[folder] edit window mode failed reason=" + reason + " err=" + t);
        }
    }

    private void restoreFolderEditWindowMode(String reason) {
        Activity a = this.actRef.get();
        if (a == null || a.getWindow() == null) {
            return;
        }
        if (this.folderSoftInputMode == Integer.MIN_VALUE) {
            return;
        }
        try {
            a.getWindow().setSoftInputMode(this.folderSoftInputMode);
            log("[folder] restore window mode reason=" + reason + " mode=" + this.folderSoftInputMode);
        } finally {
            try {
            } finally {
            }
        }
    }

    private static boolean isFolderChildDrag(DragEvent event) {
        CharSequence text;
        if (event == null || event.getClipDescription() == null) {
            return false;
        }
        CharSequence label = event.getClipDescription().getLabel();
        return LandscapePagedGridView.DRAG_LABEL.contentEquals(label) && (text = dragText(event)) != null && text.toString().startsWith("folder:");
    }

    private static int parseFolderChildIndex(DragEvent event, long expectedFolderId) {
        CharSequence text = dragText(event);
        if (text == null) {
            return -1;
        }
        try {
            String[] parts = text.toString().split(":");
            if (parts.length == 3 && "folder".equals(parts[0])) {
                long folderId = Long.parseLong(parts[1]);
                if (folderId != expectedFolderId) {
                    return -1;
                }
                return Integer.parseInt(parts[2]);
            }
            return -1;
        } catch (Throwable th) {
            return -1;
        }
    }

    private static int findFolderChildDropIndex(ViewGroup root, View panel, float panelX, float panelY) {
        if (root == null || panel == null) {
            return -1;
        }
        int[] panelLoc = new int[2];
        panel.getLocationOnScreen(panelLoc);
        int screenX = panelLoc[0] + Math.round(panelX);
        int screenY = panelLoc[1] + Math.round(panelY);
        return findFolderChildDropIndexByScreen(root, screenX, screenY, false);
    }

    /* JADX WARN: Removed duplicated region for block: B:26:0x008a  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private static int findFolderChildDropIndexByScreen(ViewGroup root, int screenX, int screenY, boolean nearest) {
        ViewGroup viewGroup = root;
        int i = screenX;
        int i2 = screenY;
        if (viewGroup == null) {
            return -1;
        }
        int index = 0;
        int bestIndex = -1;
        long bestDistance = Long.MAX_VALUE;
        Rect rect = new Rect();
        int rowIdx = 0;
        while (rowIdx < root.getChildCount()) {
            View rowView = viewGroup.getChildAt(rowIdx);
            if (rowView instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) rowView;
                int col = 0;
                while (col < row.getChildCount()) {
                    View cell = row.getChildAt(col);
                    if (cell == null || cell.getVisibility() != 0) {
                        int bestIndex2 = bestIndex;
                        index++;
                        bestIndex = bestIndex2;
                        col++;
                        i = screenX;
                        i2 = screenY;
                    } else {
                        int[] cellLoc = new int[2];
                        cell.getLocationOnScreen(cellLoc);
                        int i3 = cellLoc[0];
                        int i4 = cellLoc[1];
                        int width = cellLoc[0] + cell.getWidth();
                        int bestIndex3 = bestIndex;
                        int bestIndex4 = cellLoc[1] + cell.getHeight();
                        rect.set(i3, i4, width, bestIndex4);
                        if (rect.contains(i, i2)) {
                            return index;
                        }
                        if (nearest) {
                            int cx = rect.centerX();
                            int cy = rect.centerY();
                            long dx = i - cx;
                            long dy = i2 - cy;
                            long dist = (dx * dx) + (dy * dy);
                            if (dist < bestDistance) {
                                bestDistance = dist;
                                bestIndex = index;
                            } else {
                                bestIndex = bestIndex3;
                            }
                            index++;
                        }
                        col++;
                        i = screenX;
                        i2 = screenY;
                    }
                }
            }
            rowIdx++;
            viewGroup = root;
            i = screenX;
            i2 = screenY;
        }
        if (nearest) {
            return bestIndex;
        }
        return -1;
    }

    private static FolderDropTarget findFolderChildDropTargetByScreen(ViewGroup root, int screenX, int screenY, View excludeView) {
        ViewGroup viewGroup = root;
        int i = screenX;
        if (viewGroup == null) {
            return null;
        }
        Rect rect = new Rect();
        int rowIdx = 0;
        while (rowIdx < root.getChildCount()) {
            View rowView = viewGroup.getChildAt(rowIdx);
            if (rowView instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) rowView;
                int[] rowLoc = new int[2];
                row.getLocationOnScreen(rowLoc);
                int col = 0;
                while (col < row.getChildCount()) {
                    View cell = row.getChildAt(col);
                    if (cell != null) {
                        Object tag = cell.getTag();
                        if ((tag instanceof Integer) && cell.getVisibility() == 0 && cell != excludeView) {
                            int slotIndex = ((Integer) tag).intValue();
                            int left = rowLoc[0] + cell.getLeft();
                            int top = rowLoc[1] + cell.getTop();
                            rect.set(left, top, left + cell.getWidth(), top + cell.getHeight());
                            if (rect.contains(i, screenY)) {
                                int edgeBand = Math.max(1, rect.width() / FOLDER_COLS);
                                if (i < rect.left + edgeBand) {
                                    return new FolderDropTarget(slotIndex, true);
                                }
                                if (i > rect.right - edgeBand) {
                                    return new FolderDropTarget(slotIndex + 1, true);
                                }
                                return new FolderDropTarget(slotIndex, false);
                            }
                        }
                    }
                    col++;
                    i = screenX;
                }
            }
            rowIdx++;
            viewGroup = root;
            i = screenX;
        }
        return null;
    }

    private static View findFolderCellView(ViewGroup root, int targetIndex) {
        if (root == null || targetIndex < 0) {
            return null;
        }
        for (int rowIdx = 0; rowIdx < root.getChildCount(); rowIdx++) {
            View rowView = root.getChildAt(rowIdx);
            if (rowView instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) rowView;
                for (int col = 0; col < row.getChildCount(); col++) {
                    View cell = row.getChildAt(col);
                    if (cell != null) {
                        Object tag = cell.getTag();
                        if ((tag instanceof Integer) && ((Integer) tag).intValue() == targetIndex) {
                            return cell;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void setFolderSingleEdit(int focusIndex) {
        if (focusIndex < 0) {
            return;
        }
        this.folderEditActive = true;
        this.folderEditFocusIndex = focusIndex;
        applyFolderEditModeToBadges();
        log("[folder] edit-mode=single focus=" + focusIndex);
    }

    private void setFolderGlobalEdit() {
        this.folderEditActive = true;
        this.folderEditFocusIndex = -1;
        if (this.overlay != null) {
            this.overlay.getGrid().setEditMode(true);
        }
        applyFolderEditModeToBadges();
        log("[folder] edit-mode=global");
    }

    private void clearFolderEdit() {
        if (this.folderEditActive) {
            this.folderEditActive = false;
            this.folderEditFocusIndex = -1;
            applyFolderEditModeToBadges();
            log("[folder] edit-mode=off");
        }
    }

    private boolean isFolderEditing() {
        return this.folderEditActive;
    }

    private boolean shouldShowBadge(int slotIndex) {
        if (this.folderEditActive) {
            return this.folderEditFocusIndex < 0 || this.folderEditFocusIndex == slotIndex;
        }
        return false;
    }

    private void applyFolderEditModeToBadges() {
        LinearLayout root = this.folderActiveRoot;
        if (root == null) {
            return;
        }
        for (int r = 0; r < root.getChildCount(); r++) {
            View rowView = root.getChildAt(r);
            if (rowView instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) rowView;
                for (int c = 0; c < row.getChildCount(); c++) {
                    View cell = row.getChildAt(c);
                    if (cell instanceof FrameLayout) {
                        Object tag = cell.getTag();
                        if (tag instanceof Integer) {
                            int slotIndex = ((Integer) tag).intValue();
                            FrameLayout fl = (FrameLayout) cell;
                            boolean show = shouldShowBadge(slotIndex);
                            for (int k = 0; k < fl.getChildCount(); k++) {
                                View child = fl.getChildAt(k);
                                if (child instanceof FolderRemoveBadge) {
                                    child.setVisibility(show ? 0 : 8);
                                }
                            }
                            if (cell != this.activeFolderDragSource) {
                                cell.animate().cancel();
                                cell.animate().scaleX(show ? 1.03f : 1.0f).scaleY(show ? 1.03f : 1.0f).alpha(show ? 0.96f : 1.0f).setDuration(120L).start();
                            }
                        }
                    }
                }
            }
        }
    }

    private void requestFolderChildRemove(final long folderId, final int childIndex) {
        String name;
        LandscapeItem item;
        Activity a = this.actRef.get();
        ComponentKey key = null;
        try {
            if (this.memoryFallbackActive) {
                item = findMemoryFolder(folderId);
            } else {
                item = LandscapeStore.get(this.ctx).getGridItem(this.folderActivePos);
            }
            if (item != null && item.folderChildren != null && childIndex >= 0 && childIndex < item.folderChildren.size()) {
                key = item.folderChildren.get(childIndex);
            }
        } catch (Throwable th) {
        }
        CharSequence label = key == null ? null : this.iconCache.getLabel(key);
        if (label == null) {
            name = key == null ? "这个应用" : key.packageName;
        } else {
            name = label.toString();
        }
        if (a == null) {
            performFolderChildRemove(folderId, childIndex);
        } else {
            new AlertDialog.Builder(a).setTitle("从文件夹移出？").setMessage(name + "\n\n应用会移到横屏桌面下一个空位，不会卸载，也不影响竖屏桌面。").setPositiveButton("移出", new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    this.f$0.m67x9cc77e49(folderId, childIndex, dialogInterface, i);
                }
            }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).show();
        }
    }

    void m67x9cc77e49(long folderId, int childIndex, DialogInterface d, int w) {
        performFolderChildRemove(folderId, childIndex);
    }

    private void performFolderChildRemove(long folderId, int childIndex) {
        if (folderId < 0 || childIndex < 0) {
            return;
        }
        try {
            if (this.memoryFallbackActive) {
                removeMemoryFolderChildToGrid(folderId, childIndex);
                bindMemorySnapshot("folder-child-remove-memory");
                rebuildFolderRowsInPlace("child-remove-memory");
                this.folderHadChanges = true;
                log("[folder] child remove memory folder=" + folderId + " idx=" + childIndex);
                return;
            }
            LandscapeStore.get(this.ctx).removeFolderChildToGrid(folderId, childIndex);
            rebuildFolderRowsInPlace("child-remove");
            this.folderHadChanges = true;
            log("[folder] child remove folder=" + folderId + " idx=" + childIndex);
        } catch (Throwable t) {
            log("[folder] child remove failed folder=" + folderId + " idx=" + childIndex + " err=" + t);
        }
    }

    private static class FolderRemoveBadge extends View {
        private final Paint border;
        private final Paint cross;
        private final Paint fill;

        FolderRemoveBadge(Context ctx) {
            super(ctx);
            this.fill = new Paint(1);
            this.border = new Paint(1);
            this.cross = new Paint(1);
            float density = ctx.getResources().getDisplayMetrics().density;
            this.fill.setStyle(Paint.Style.FILL);
            this.fill.setColor(-301989888);
            this.border.setStyle(Paint.Style.STROKE);
            this.border.setStrokeWidth(Math.max(1.0f, density));
            this.border.setColor(-1);
            this.cross.setStyle(Paint.Style.STROKE);
            this.cross.setStrokeCap(Paint.Cap.ROUND);
            this.cross.setStrokeWidth(Math.max(2.0f, density * 2.0f));
            this.cross.setColor(-1);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2.0f;
            float cy = getHeight() / 2.0f;
            float borderHalf = this.border.getStrokeWidth() / 2.0f;
            float radius = Math.max(0.0f, (Math.min(getWidth(), getHeight()) / 2.0f) - borderHalf);
            canvas.drawCircle(cx, cy, radius, this.fill);
            canvas.drawCircle(cx, cy, radius, this.border);
            float arm = radius * 0.42f;
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, this.cross);
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, this.cross);
        }
    }

    private static final class FolderDropTarget {
        final int index;
        final boolean insert;

        FolderDropTarget(int index, boolean insert) {
            this.index = index;
            this.insert = insert;
        }
    }

    private static CharSequence dragText(DragEvent event) {
        if (event == null || event.getClipData() == null || event.getClipData().getItemCount() <= 0) {
            return LandscapePagedGridView.currentDragDescriptor();
        }
        CharSequence text = event.getClipData().getItemAt(0).getText();
        return text != null ? text : LandscapePagedGridView.currentDragDescriptor();
    }

    private static GradientDrawable roundRect(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        g.setStroke(1, 587202559);
        return g;
    }

    private static GradientDrawable roundRectLight(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        g.setStroke(1, 872415231);
        return g;
    }

    private View buildFolderDialogCell(Context ctx, ComponentKey key, final long folderId, final int childIndex) {
        FrameLayout frameLayout = new FrameLayout(ctx);
        frameLayout.setClipChildren(false);
        frameLayout.setClipToPadding(false);
        frameLayout.setClickable(true);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(1);
        content.setGravity(17);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setDuplicateParentStateEnabled(true);
        ImageView icon = new ImageView(ctx);
        Drawable d = this.iconCache.getIcon(key);
        if (d != null) {
            icon.setImageDrawable(d);
        }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = MiuiStyleResolver.resolveDimenPx(ctx, 52, "app_icon_size", "config_icon_size", "workspace_icon_size");
        content.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView label = new TextView(ctx);
        CharSequence text = this.iconCache.getLabel(key);
        label.setText(text == null ? key.packageName : text);
        label.setGravity(17);
        label.setMaxLines(1);
        label.setTextColor(-1);
        label.setShadowLayer(dp(ctx, 3), 0.0f, dp(ctx, 1), 1711276032);
        label.setTextSize(0, MiuiStyleResolver.resolveTextSizePx(ctx, 12, "workspace_icon_text_size", "icon_text_size"));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.topMargin = dp(ctx, FOLDER_COLS);
        content.addView(label, labelLp);
        frameLayout.addView(content, new FrameLayout.LayoutParams(-1, -2, 17));
        FolderRemoveBadge badge = new FolderRemoveBadge(ctx);
        int badgeSize = Math.max(dp(ctx, 22), iconSize / 3);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(badgeSize, badgeSize);
        blp.gravity = 8388659;
        blp.leftMargin = dp(ctx, 14);
        blp.topMargin = dp(ctx, 0);
        badge.setVisibility(shouldShowBadge(childIndex) ? 0 : 8);
        badge.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                this.f$0.m40x188aa341(folderId, childIndex, view);
            }
        });
        frameLayout.addView(badge, blp);
        return frameLayout;
    }

    void m40x188aa341(long fid, int idx, View v) {
        requestFolderChildRemove(fid, idx);
    }

    private void launchComponent(ComponentKey key) {
        Activity a;
        if (key == null || (a = this.actRef.get()) == null) {
            return;
        }
        try {
            prepareForExternalLaunch();
            LauncherApps la = (LauncherApps) a.getSystemService("launcherapps");
            UserManager um = (UserManager) a.getSystemService("user");
            UserHandle user = um.getUserForSerialNumber(key.userSerial);
            if (user == null) {
                user = Process.myUserHandle();
            }
            la.startMainActivity(key.toComponentName(), user, null, null);
        } catch (Throwable t) {
            log("[launch] failed: " + t);
        }
    }

    private static View findByName(Activity a, String name) {
        try {
            int id = a.getResources().getIdentifier(name, "id", "com.miui.home");
            if (id == 0) {
                return null;
            }
            return a.findViewById(id);
        } catch (Throwable th) {
            return null;
        }
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static class IconCache implements AppRenderer {
        private final Context ctx;
        private final Map<ComponentKey, Drawable> iconMap = new HashMap();
        private final Map<ComponentKey, CharSequence> labelMap = new HashMap();
        private boolean loaded = false;

        IconCache(Context c) {
            this.ctx = c;
        }

        void invalidate() {
            this.loaded = false;
            this.iconMap.clear();
            this.labelMap.clear();
        }

        private void ensureLoaded() {
            if (this.loaded) {
                return;
            }
            LauncherApps la = (LauncherApps) this.ctx.getSystemService("launcherapps");
            UserManager um = (UserManager) this.ctx.getSystemService("user");
            if (la == null || um == null) {
                this.loaded = true;
                return;
            }
            for (UserHandle u : um.getUserProfiles()) {
                long serial = um.getSerialNumberForUser(u);
                List<LauncherActivityInfo> list = la.getActivityList(null, u);
                if (list != null) {
                    for (LauncherActivityInfo i : list) {
                        ComponentName cn = i.getComponentName();
                        ComponentKey k = new ComponentKey(cn, serial);
                        this.iconMap.put(k, i.getBadgedIcon(0));
                        this.labelMap.put(k, i.getLabel());
                    }
                }
            }
            this.loaded = true;
        }

        @Override
        public Drawable getIcon(ComponentKey key) {
            ensureLoaded();
            return this.iconMap.get(key);
        }

        @Override
        public CharSequence getLabel(ComponentKey key) {
            ensureLoaded();
            CharSequence l = this.labelMap.get(key);
            return l == null ? key.packageName : l;
        }
    }

    private void confirmRemoveFallback(final LandscapeItem it) {
        Activity a = this.actRef.get();
        if (a == null || it == null) {
            return;
        }
        CharSequence label = this.iconCache.getLabel(it.key);
        new AlertDialog.Builder(a).setTitle(label == null ? it.key.packageName : label.toString()).setItems(new CharSequence[]{"移除"}, new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                this.f$0.m41x653ead7(it, dialogInterface, i);
            }
        }).show();
    }

    void m41x653ead7(LandscapeItem it, DialogInterface d, int which) {
        if (it.kind == LandscapeItem.Kind.GRID) {
            LandscapeStore.get(this.ctx).removeGrid(new GridPosition(it.pageIndex, it.slotIndex));
        } else if (it.kind == LandscapeItem.Kind.DOCK) {
            LandscapeStore.get(this.ctx).removeDock(new DockPosition(it.dockIndex));
        }
        refreshOverlay();
    }

    public static void log(String msg) {
        String line = TAG + msg;
        XposedBridge.log(line);
        try {
            Log.i("MiuiHomeLandscape", line);
        } catch (Throwable th) {
        }
    }

    private static void logStatic(String msg) {
        String line = TAG + msg;
        XposedBridge.log(line);
        try {
            Log.i("MiuiHomeLandscape", line);
        } catch (Throwable th) {
        }
    }
}
