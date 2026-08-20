package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lifecycle owner for the vili landscape task switcher.
 *
 * <p>MIUI Recents remains authoritative. The native View is changed only after a successful
 * task read and a completed custom-View layout pass. Any reflection/action failure immediately
 * restores the exact native state captured before takeover.</p>
 */
public final class LandscapeRecentsController {
    public interface Callback {
        void onCustomRecentsVisibilityChanged(boolean visible);
    }

    private static final String TAG = "MIHL/Recents";
    private static final long OPERATION_TIMEOUT_MS = 3000L;
    private static final long TAKEOVER_FALLBACK_MS = 520L;
    private static final long TASK_MUTATION_GUARD_MS = 1200L;

    private final WeakReference<Activity> activityRef;
    private final WeakReference<ViewGroup> parentRef;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService worker = newWorker();

    private static ExecutorService newWorker() {
        return Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mihl-recents");
        thread.setDaemon(true);
        return thread;
        });
    }
    private final AtomicInteger generation = new AtomicInteger();
    private final RecentTaskGateway gateway;
    private final LandscapeRecentsView customView;

    private volatile boolean showing;
    private volatile boolean disposed;
    private Future<?> activeWork;
    private View nativeRecents;
    private NativeViewState nativeState;
    private boolean nativeVisible;
    private boolean landscape;
    private boolean attemptedCurrentVisibilityEpoch;
    private int nativeMutationDepth;
    private Runnable operationTimeout;
    private Runnable takeoverFallback;
    private Runnable taskMutationGuard;
    private boolean taskMutationInFlight;

    public LandscapeRecentsController(Activity activity, ViewGroup overlayParent,
                                      Callback callback) {
        if (activity == null) throw new IllegalArgumentException("activity == null");
        if (overlayParent == null) throw new IllegalArgumentException("overlayParent == null");
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("LandscapeRecentsController must be created on main");
        }

        activityRef = new WeakReference<>(activity);
        parentRef = new WeakReference<>(overlayParent);
        this.callback = callback;
        gateway = new RecentTaskGateway(activity);
        landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        customView = new LandscapeRecentsView(activity);
        customView.setVisibility(View.GONE);
        customView.setAlpha(0f);
        customView.setListener(new LandscapeRecentsView.Listener() {
            @Override public void onLaunch(RecentTaskItem task) {
                launch(task);
            }

            @Override public void onDismiss(RecentTaskItem task) {
                dismiss(task);
            }

            @Override public void onClear() {
                clear();
            }

            @Override public void onRefresh() {
                loadTasks("manual-refresh");
            }
        });
        overlayParent.addView(customView, createLayoutParams(overlayParent));
    }

    /** Called from the MIUI visibility hook. The supplied native View is the source of truth. */
    public void onNativeVisibilityChanged(View nativeView, boolean visible, String source) {
        runOnMain(() -> handleNativeVisibility(nativeView, visible, source));
    }

    /** Called after MIUI posts FsGestureEnterRecentsCompleteEvent. */
    public void onNativeGestureSettled(View nativeView, String source) {
        runOnMain(() -> {
            if (disposed || !landscape || !nativeVisible || nativeView == null
                    || nativeView != nativeRecents) return;
            cancelTakeoverFallback();
            attemptTakeover("gesture-settled:" + source);
        });
    }

    public void onLandscapeChanged(boolean isLandscape) {
        runOnMain(() -> {
            if (disposed || landscape == isLandscape) return;
            landscape = isLandscape;
            if (!landscape) {
                attemptedCurrentVisibilityEpoch = false;
                cancelTakeoverFallback();
                exitOverviewToHome("portrait");
            } else if (nativeVisible && nativeRecents != null) {
                scheduleTakeoverFallback("entered-landscape");
            }
        });
    }

    /** A paused Launcher must wait for a fresh MIUI visibility event on resume. */
    public void onHostPaused() {
        runOnMain(() -> {
            if (disposed) return;
            nativeVisible = false;
            attemptedCurrentVisibilityEpoch = false;
            cancelTakeoverFallback();
            releaseToNative("host-paused");
        });
    }

    public boolean isShowing() {
        return showing;
    }

    /** Logical MIUI authority after filtering controller-owned task mutations. */
    public boolean hasNativeAuthority() {
        return !disposed && nativeVisible;
    }

    /** True only during this controller's synchronous save/restore mutation. */
    public boolean isInternalNativeMutation(View nativeView) {
        return Looper.myLooper() == Looper.getMainLooper()
                && nativeMutationDepth > 0
                && nativeView != null
                && nativeView == nativeRecents;
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        generation.incrementAndGet();
        Future<?> work = activeWork;
        if (work != null) work.cancel(true);
        worker.shutdownNow();
        runOnMain(this::disposeOnMain);
    }

    private void handleNativeVisibility(View nativeView, boolean visible, String source) {
        if (disposed) return;
        // setVisibility() is hooked by the module. Ignore the synchronous callback generated
        // by our own temporary hide/restore, otherwise takeover would immediately undo itself.
        if (nativeMutationDepth > 0 && nativeView == nativeRecents) return;
        if (nativeView == null) {
            nativeVisible = visible;
            attemptedCurrentVisibilityEpoch = false;
            cancelTakeoverFallback();
            releaseToNative("native-view-null:" + source);
            return;
        }

        boolean viewChanged = nativeRecents != nativeView;
        boolean wasVisible = nativeVisible;
        if (viewChanged) {
            // Activity recreation or a MIUI container replacement: never transfer a saved state
            // to another View. Restore the old object before adopting the new authority.
            restoreNativeState();
            hideCustom();
            setShowing(false);
            nativeRecents = nativeView;
            attemptedCurrentVisibilityEpoch = false;
            cancelTakeoverFallback();
        }
        nativeVisible = visible;

        if (!visible) {
            if (taskMutationInFlight && landscape && showing && nativeView == nativeRecents) {
                // removeTask/removeAllVisibleRecentTasks may hide RecentsContainer even though
                // the user only dismissed a card. Keep the custom session and re-conceal the
                // native container instead of exposing or returning to MIUI Recents.
                nativeVisible = true;
                if (!captureAndConcealNative()) {
                    failToNative("retain after task mutation", null);
                }
                Log.d(TAG, "ignored native hide during task mutation: " + source);
                return;
            }
            attemptedCurrentVisibilityEpoch = false;
            cancelTakeoverFallback();
            normalizeLauncherHome("native-hidden:" + source);
            releaseAfterAuthoritativeHide("native-hidden:" + source);
        } else if (landscape && (!wasVisible || viewChanged
                || !attemptedCurrentVisibilityEpoch)) {
            // Conceal native Recents in the same main-thread turn in which MIUI makes it visible.
            // Its geometry and gesture state remain alive, but it never gets a frame on screen.
            if (!beginVisualTakeover("native-visible:" + source)) return;
            // Task loading still waits for MIUI's completion event. The fallback is only for
            // three-button navigation or ROM variants which omit that event.
            scheduleTakeoverFallback("native-visible:" + source);
        } else {
            if (!landscape) releaseToNative("native-visible-portrait:" + source);
        }
    }

    private void attemptTakeover(String source) {
        if (disposed || attemptedCurrentVisibilityEpoch || !landscape
                || !nativeVisible || nativeRecents == null) return;
        attemptedCurrentVisibilityEpoch = true;
        loadTasks(source);
    }

    private void scheduleTakeoverFallback(String source) {
        if (disposed || attemptedCurrentVisibilityEpoch || !landscape
                || !nativeVisible || nativeRecents == null) return;
        cancelTakeoverFallback();
        Runnable fallback = () -> {
            if (takeoverFallback == null || disposed || !landscape
                    || !nativeVisible || nativeRecents == null) return;
            takeoverFallback = null;
            attemptTakeover("settle-fallback:" + source);
        };
        takeoverFallback = fallback;
        mainHandler.postDelayed(fallback, TAKEOVER_FALLBACK_MS);
    }

    private void loadTasks(String source) {
        if (disposed || !landscape || !nativeVisible || nativeRecents == null) return;
        int token = nextGeneration();
        customView.setBusy(true);
        scheduleOperationTimeout(token, "read tasks");
        if (!showing) {
            customView.setAlpha(0f);
            customView.setVisibility(View.GONE);
        }
        Log.d(TAG, "load generation=" + token + " source=" + source);
        WeakReference<LandscapeRecentsController> owner = new WeakReference<>(this);
        RecentTaskGateway taskGateway = gateway;
        Handler callbackHandler = mainHandler;
        activeWork = worker.submit(() -> {
            RecentTaskGateway.Result<List<RecentTaskItem>> result = taskGateway.readTasks();
            callbackHandler.post(() -> {
                LandscapeRecentsController controller = owner.get();
                if (controller == null || !controller.isCurrent(token)) return;
                if (!result.isSuccess()) {
                    controller.failToNative("readTasks", result.error);
                    return;
                }
                controller.renderAfterLayout(token, result.value);
            });
        });
    }

    private void renderAfterLayout(int token, List<RecentTaskItem> tasks) {
        if (!isCurrent(token)) return;
        customView.bind(tasks);
        customView.setBusy(false);
        customView.setVisibility(View.VISIBLE);
        if (!showing) customView.setAlpha(0f);
        customView.bringToFront();
        customView.requestLayout();

        ViewTreeObserver observer = customView.getViewTreeObserver();
        if (!observer.isAlive()) {
            failToNative("custom view has no ViewTreeObserver", null);
            return;
        }
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                ViewTreeObserver current = customView.getViewTreeObserver();
                if (current.isAlive()) current.removeOnPreDrawListener(this);
                if (!isCurrent(token)) return true;
                if (customView.getWidth() <= 0 || customView.getHeight() <= 0) {
                    failToNative("custom view layout is empty", null);
                    return true;
                }
                if (!landscape || !nativeVisible || nativeRecents == null
                        || !nativeRecents.isAttachedToWindow()) {
                    releaseToNative("authority changed before draw");
                    return true;
                }
                if (!hideNativeAfterSnapshot()) {
                    failToNative("native state could not be captured", null);
                    return true;
                }
                cancelOperationTimeout();
                customView.setAlpha(1f);
                setShowing(true);
                return true;
            }
        });
        customView.invalidate();
    }

    private void launch(RecentTaskItem task) {
        if (!canOperate()) return;
        int token = nextGeneration();
        customView.setBusy(true);
        scheduleOperationTimeout(token, "launch task");
        int taskId = task.taskId;
        WeakReference<LandscapeRecentsController> owner = new WeakReference<>(this);
        RecentTaskGateway taskGateway = gateway;
        Handler callbackHandler = mainHandler;
        activeWork = worker.submit(() -> {
            RecentTaskGateway.Result<Void> result = taskGateway.launch(taskId);
            callbackHandler.post(() -> {
                LandscapeRecentsController controller = owner.get();
                if (controller == null || !controller.isCurrent(token)) return;
                if (!result.isSuccess()) {
                    controller.failToNative("launch task " + taskId, result.error);
                    return;
                }
                // The launched task owns the screen now; do not retain a transparent overlay.
                controller.releaseToNative("task-launched");
            });
        });
    }

    private void dismiss(RecentTaskItem task) {
        runMiuiDismissAndReload(task);
    }

    private void clear() {
        runClearAndReload();
    }

    private void runMiuiDismissAndReload(RecentTaskItem task) {
        if (!canOperate()) return;
        int token = nextGeneration();
        taskMutationInFlight = true;
        cancelTaskMutationGuard();
        customView.setBusy(true);
        scheduleOperationTimeout(token, "MIUI dismiss task " + task.taskId);

        // The native EventBus is MAIN-thread state. It owns task data deletion and MIUI's
        // ProcessManager cleanup; do not replace it with a second Binder mutation.
        RecentTaskGateway.Result<Void> mutation = gateway.dismissViaMiui(
                nativeRecents, task.taskId);
        if (!mutation.isSuccess()) {
            keepCustomAfterMutationFailure(token,
                    "MIUI dismiss task " + task.taskId, mutation.error);
            return;
        }

        WeakReference<LandscapeRecentsController> owner = new WeakReference<>(this);
        RecentTaskGateway taskGateway = gateway;
        Handler callbackHandler = mainHandler;
        int taskId = task.taskId;
        activeWork = worker.submit(() -> {
            RecentTaskGateway.Result<List<RecentTaskItem>> confirmed =
                    taskGateway.readTasksUntilRemoved(taskId);
            callbackHandler.post(() -> {
                LandscapeRecentsController controller = owner.get();
                if (controller == null || !controller.isCurrent(token)) return;
                if (!confirmed.isSuccess()) {
                    controller.keepCustomAfterMutationFailure(token,
                            "MIUI dismiss confirmation " + taskId, confirmed.error);
                    return;
                }
                controller.renderAfterLayout(token, confirmed.value);
                controller.scheduleTaskMutationGuardRelease(token);
            });
        });
    }

    private void runClearAndReload() {
        if (!canOperate()) return;
        String operation = "clear visible tasks";
        int token = nextGeneration();
        taskMutationInFlight = true;
        cancelTaskMutationGuard();
        customView.setBusy(true);
        scheduleOperationTimeout(token, operation);
        WeakReference<LandscapeRecentsController> owner = new WeakReference<>(this);
        RecentTaskGateway taskGateway = gateway;
        Handler callbackHandler = mainHandler;
        activeWork = worker.submit(() -> {
            // Exactly one mutating binder method is invoked. There is no event-bus, per-task,
            // native-click, or alternate-method cascade.
            RecentTaskGateway.Result<Void> mutation = taskGateway.clearVisibleTasks();
            if (!mutation.isSuccess()) {
                callbackHandler.post(() -> {
                    LandscapeRecentsController controller = owner.get();
                    if (controller != null && controller.isCurrent(token)) {
                        controller.taskMutationInFlight = false;
                        controller.failToNative(operation, mutation.error);
                    }
                });
                return;
            }
            RecentTaskGateway.Result<List<RecentTaskItem>> refreshed = taskGateway.readTasks();
            callbackHandler.post(() -> {
                LandscapeRecentsController controller = owner.get();
                if (controller == null || !controller.isCurrent(token)) return;
                if (!refreshed.isSuccess()) {
                    controller.taskMutationInFlight = false;
                    controller.failToNative(operation + " refresh", refreshed.error);
                    return;
                }
                controller.renderAfterLayout(token, refreshed.value);
                controller.scheduleTaskMutationGuardRelease(token);
            });
        });
    }

    private boolean canOperate() {
        return !disposed && showing && landscape && nativeVisible;
    }

    private int nextGeneration() {
        cancelOperationTimeout();
        int token = generation.incrementAndGet();
        Future<?> previous = activeWork;
        if (previous != null) previous.cancel(true);
        return token;
    }

    private boolean isCurrent(int token) {
        return !disposed && generation.get() == token;
    }

    private boolean beginVisualTakeover(String source) {
        customView.setBusy(true);
        customView.setAlpha(1f);
        customView.setVisibility(View.VISIBLE);
        customView.bringToFront();
        if (!captureAndConcealNative()) {
            failToNative("early visual takeover:" + source, null);
            return false;
        }
        setShowing(true);
        return true;
    }

    private boolean hideNativeAfterSnapshot() {
        return captureAndConcealNative();
    }

    private boolean captureAndConcealNative() {
        View target = nativeRecents;
        if (target == null) return false;
        try {
            if (nativeState == null) {
                nativeState = new NativeViewState(target, target.getVisibility(), target.getAlpha(),
                        target.getImportantForAccessibility());
            } else if (nativeState.view != target) {
                return false;
            }

            // Keep VISIBLE so MIUI retains its overview state and gesture lifecycle. The custom
            // full-screen View owns hit testing while alpha=0 prevents native cards/background
            // from ever being drawn underneath it.
            nativeMutationDepth++;
            try {
                target.setVisibility(View.VISIBLE);
                target.setAlpha(0f);
                target.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            } finally {
                nativeMutationDepth--;
            }
            if (disposed || !landscape || !nativeVisible || target != nativeRecents) {
                restoreNativeState();
                return false;
            }
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "hide native Recents failed", error);
            restoreNativeState();
            return false;
        }
    }

    private void releaseToNative(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> releaseToNative(reason));
            return;
        }
        generation.incrementAndGet();
        cancelOperationTimeout();
        cancelTakeoverFallback();
        cancelTaskMutationGuard();
        taskMutationInFlight = false;
        Future<?> work = activeWork;
        if (work != null) work.cancel(true);
        activeWork = null;
        restoreNativeState();
        hideCustom();
        setShowing(false);
        Log.d(TAG, "native authority: " + reason);
    }

    /**
     * MIUI has already set the authoritative container to GONE/INVISIBLE.  Undo
     * only our alpha/accessibility changes; restoring the old VISIBLE value here
     * would reopen Recents after MIUI closed it.
     */
    private void releaseAfterAuthoritativeHide(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> releaseAfterAuthoritativeHide(reason));
            return;
        }
        generation.incrementAndGet();
        cancelOperationTimeout();
        cancelTakeoverFallback();
        cancelTaskMutationGuard();
        taskMutationInFlight = false;
        Future<?> work = activeWork;
        if (work != null) work.cancel(true);
        activeWork = null;
        NativeViewState state = nativeState;
        nativeState = null;
        if (state != null) {
            nativeMutationDepth++;
            try {
                restoreAlpha(state);
                restoreAccessibility(state);
            } finally {
                nativeMutationDepth--;
            }
        }
        hideCustom();
        setShowing(false);
        Log.d(TAG, "native hidden authority: " + reason);
    }

    private void restoreNativeState() {
        NativeViewState state = nativeState;
        nativeState = null;
        if (state == null) return;
        nativeMutationDepth++;
        try {
            restoreVisibility(state);
            restoreAlpha(state);
            restoreAccessibility(state);
        } finally {
            nativeMutationDepth--;
        }
    }

    private static void restoreVisibility(NativeViewState state) {
        try { state.view.setVisibility(state.visibility); }
        catch (Throwable error) { Log.e(TAG, "restore native Recents visibility", error); }
    }

    private static void restoreAlpha(NativeViewState state) {
        try { state.view.setAlpha(state.alpha); }
        catch (Throwable error) { Log.e(TAG, "restore native Recents alpha", error); }
    }

    private static void restoreAccessibility(NativeViewState state) {
        try { state.view.setImportantForAccessibility(state.importantForAccessibility); }
        catch (Throwable error) { Log.e(TAG, "restore native Recents accessibility", error); }
    }

    private void hideCustom() {
        customView.setBusy(false);
        customView.setAlpha(0f);
        customView.setVisibility(View.GONE);
    }

    private void failToNative(String operation, Throwable error) {
        if (error == null) {
            Log.w(TAG, operation + "; keeping MIUI Recents");
        } else {
            Log.w(TAG, operation + "; keeping MIUI Recents", error);
        }
        releaseToNative("failure:" + operation);
    }

    private void keepCustomAfterMutationFailure(int token, String operation, Throwable error) {
        if (!isCurrent(token)) return;
        cancelOperationTimeout();
        customView.setBusy(false);
        if (error == null) Log.w(TAG, operation + "; retaining custom Recents");
        else Log.w(TAG, operation + "; retaining custom Recents", error);
        scheduleTaskMutationGuardRelease(token);
    }

    /**
     * A hidden View is not enough: MIUI persists OVERVIEW in LauncherStateManager and would
     * resurrect portrait Recents on the next configuration change.
     */
    private void exitOverviewToHome(String reason) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> exitOverviewToHome(reason));
            return;
        }
        normalizeLauncherHome(reason);
        nativeVisible = false;
        attemptedCurrentVisibilityEpoch = false;
        View target = nativeRecents;
        if (target != null) {
            nativeMutationDepth++;
            try {
                target.setVisibility(View.GONE);
            } catch (Throwable error) {
                Log.w(TAG, "hide native Recents for home", error);
            } finally {
                nativeMutationDepth--;
            }
        }
        releaseAfterAuthoritativeHide("home:" + reason);
    }

    private void normalizeLauncherHome(String reason) {
        Activity activity = activityRef.get();
        RecentTaskGateway.Result<Void> result = gateway.exitOverviewToHome(activity, nativeRecents);
        if (!result.isSuccess()) {
            Log.w(TAG, "failed to commit LauncherState.NORMAL: " + reason, result.error);
        } else {
            Log.d(TAG, "LauncherState.NORMAL: " + reason);
        }
    }

    private void scheduleOperationTimeout(int token, String operation) {
        cancelOperationTimeout();
        Runnable timeout = () -> {
            if (operationTimeout == null || !isCurrent(token)) return;
            operationTimeout = null;
            ExecutorService timedOutWorker = worker;
            timedOutWorker.shutdownNow();
            worker = newWorker();
            activeWork = null;
            taskMutationInFlight = false;
            cancelTaskMutationGuard();
            failToNative(operation + " timed out after " + OPERATION_TIMEOUT_MS + "ms", null);
        };
        operationTimeout = timeout;
        mainHandler.postDelayed(timeout, OPERATION_TIMEOUT_MS);
    }

    private void cancelOperationTimeout() {
        Runnable timeout = operationTimeout;
        operationTimeout = null;
        if (timeout != null) mainHandler.removeCallbacks(timeout);
    }

    private void cancelTakeoverFallback() {
        Runnable fallback = takeoverFallback;
        takeoverFallback = null;
        if (fallback != null) mainHandler.removeCallbacks(fallback);
    }

    private void scheduleTaskMutationGuardRelease(int token) {
        cancelTaskMutationGuard();
        Runnable release = () -> {
            if (taskMutationGuard == null || !isCurrent(token)) return;
            taskMutationGuard = null;
            taskMutationInFlight = false;
        };
        taskMutationGuard = release;
        mainHandler.postDelayed(release, TASK_MUTATION_GUARD_MS);
    }

    private void cancelTaskMutationGuard() {
        Runnable release = taskMutationGuard;
        taskMutationGuard = null;
        if (release != null) mainHandler.removeCallbacks(release);
    }

    private void setShowing(boolean visible) {
        if (showing == visible) return;
        showing = visible;
        if (callback != null) {
            try {
                callback.onCustomRecentsVisibilityChanged(visible);
            } catch (Throwable error) {
                Log.w(TAG, "visibility callback", error);
            }
        }
    }

    private void disposeOnMain() {
        cancelOperationTimeout();
        cancelTakeoverFallback();
        cancelTaskMutationGuard();
        taskMutationInFlight = false;
        restoreNativeState();
        hideCustom();
        setShowing(false);
        ViewGroup parent = parentRef.get();
        if (parent != null && customView.getParent() == parent) parent.removeView(customView);
        nativeRecents = null;
        nativeVisible = false;
        activityRef.clear();
        parentRef.clear();
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else mainHandler.post(action);
    }

    private static ViewGroup.LayoutParams createLayoutParams(ViewGroup parent) {
        if (parent instanceof FrameLayout) {
            return new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static final class NativeViewState {
        final View view;
        final int visibility;
        final float alpha;
        final int importantForAccessibility;

        NativeViewState(View view, int visibility, float alpha,
                        int importantForAccessibility) {
            this.view = view;
            this.visibility = visibility;
            this.alpha = alpha;
            this.importantForAccessibility = importantForAccessibility;
        }
    }
}
