package com.hoshinoriji.miuihomelandscape;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import com.hoshinoriji.miuihomelandscape.core.DeviceProfile;
import com.hoshinoriji.miuihomelandscape.core.ModuleSettings;
import com.hoshinoriji.miuihomelandscape.overlay.LandscapeController;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Narrow Xposed entry point for the validated Xiaomi 11T Pro / MIUI Home build. */
public final class MiuiHomeLandscapeModule implements IXposedHookLoadPackage {
    public static final String VERSION = "5.3.1-vili";

    private static final String TAG = "[MiuiHomeLandscape/V5] ";
    private static final String LAUNCHER_CLASS = "com.miui.home.launcher.Launcher";
    private static final String RECENTS_CLASS = "com.miui.home.recents.views.RecentsContainer";
    private static final String RECENTS_GESTURE_COMPLETE_EVENT =
            "com.miui.home.recents.messages.FsGestureEnterRecentsCompleteEvent";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean PROFILE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SETTINGS_RECEIVER_INSTALLED = new AtomicBoolean(false);
    private static final AtomicInteger SETTINGS_GENERATION = new AtomicInteger();
    private static final ThreadLocal<Boolean> INTERNAL_ORIENTATION_CHANGE =
            new ThreadLocal<>();
    private static final Map<Activity, Integer> ORIGINAL_ORIENTATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile WeakReference<Activity> currentLauncher = new WeakReference<>(null);

    private volatile Class<?> launcherClass;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!LandscapeBridge.HOME_PKG.equals(lpparam.packageName)
                || !LandscapeBridge.HOME_PKG.equals(lpparam.processName)) {
            return;
        }
        if (INSTALLED.get()) {
            log("hooks already installed; ignoring duplicate load callback");
            return;
        }

        Class<?> resolvedLauncher = XposedHelpers.findClassIfExists(
                LAUNCHER_CLASS, lpparam.classLoader);
        if (resolvedLauncher == null) {
            log("unsupported MIUI Home: missing " + LAUNCHER_CLASS);
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            log("hooks already installed; ignoring duplicate load callback");
            return;
        }
        launcherClass = resolvedLauncher;

        hookLauncherRequestedOrientation();
        hookLauncherLifecycle();
        hookRecentsVisibility(lpparam.classLoader);
        log("hook installation complete, version=" + VERSION);
    }

    private void hookLauncherRequestedOrientation() {
        try {
            Method method = Activity.class.getDeclaredMethod("setRequestedOrientation", int.class);
            XposedBridge.hookMethod(method,
                    new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Activity activity = exactLauncherActivity(param.thisObject);
                    if (activity == null || Boolean.TRUE.equals(INTERNAL_ORIENTATION_CHANGE.get())
                            || param.args.length != 1
                            || !(param.args[0] instanceof Integer)
                            || !moduleEnabled(activity, "setRequestedOrientation")) {
                        return;
                    }

                    ModuleSettings settings = ModuleSettings.load(activity);
                    int requested = (Integer) param.args[0];
                    rememberOriginalOrientation(activity, requested);
                    int replacement;
                    if (settings.rotationMode() == ModuleSettings.ROTATION_FORCE_LANDSCAPE) {
                        replacement = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                    } else if (settings.rotationMode() == ModuleSettings.ROTATION_FOLLOW_SYSTEM) {
                        replacement = ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
                    } else {
                        replacement = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
                    }
                    if (replacement != requested) {
                        param.args[0] = replacement;
                        log("Launcher#setRequestedOrientation " + requested + " -> " + replacement);
                    }
                }
            });
            log("hooked Launcher-filtered Activity#setRequestedOrientation");
        } catch (Throwable t) {
            log("failed to hook setRequestedOrientation: " + t);
        }
    }

    private void hookLauncherLifecycle() {
        hookAfter("onCreate", new Class<?>[]{Bundle.class}, param -> {
            Activity activity = exactLauncherActivity(param.thisObject);
            if (activity == null) return;
            currentLauncher = new WeakReference<>(activity);
            rememberOriginalOrientation(activity, activity.getRequestedOrientation());
            ensureSettingsReceiver(activity.getApplicationContext());
            if (!moduleEnabled(activity, "onCreate")) return;
            View decor = activity.getWindow().getDecorView();
            decor.post(() -> {
                if (isUsableLauncher(activity) && currentLauncher.get() == activity
                        && moduleEnabled(activity, "onCreate/post")) {
                    LandscapeController.forActivity(activity).onLauncherCreate();
                }
            });
        });

        hookAfter("onConfigurationChanged", new Class<?>[]{Configuration.class}, param -> {
            Activity activity = exactLauncherActivity(param.thisObject);
            if (activity == null || !moduleEnabled(activity, "onConfigurationChanged")) return;
            Configuration configuration = param.args.length > 0
                    && param.args[0] instanceof Configuration
                    ? (Configuration) param.args[0]
                    : activity.getResources().getConfiguration();
            LandscapeController.forActivity(activity).onConfigurationChanged(configuration);
        });

        hookAfter("onResume", new Class<?>[0], param -> {
            Activity activity = exactLauncherActivity(param.thisObject);
            if (activity == null || !moduleEnabled(activity, "onResume")) return;
            LandscapeController.forActivity(activity).onResume();
        });

        hookAfter("onPause", new Class<?>[0], param -> {
            Activity activity = exactLauncherActivity(param.thisObject);
            if (activity == null) return;
            if (moduleEnabled(activity, "onPause")) {
                LandscapeController.forActivity(activity).onPause();
            }
        });

        hookAfter("onWindowFocusChanged", new Class<?>[]{boolean.class}, param -> {
            Activity activity = exactLauncherActivity(param.thisObject);
            if (activity == null || !moduleEnabled(activity, "onWindowFocusChanged")) return;
            boolean hasFocus = param.args.length > 0
                    && param.args[0] instanceof Boolean
                    && (Boolean) param.args[0];
            LandscapeController.forActivity(activity).onWindowFocusChanged(hasFocus);
        });

        hookAfter("onDestroy", new Class<?>[0], param -> {
            Activity activity = exactLauncherActivity(param.thisObject);
            if (activity != null) {
                LandscapeController.disposeForActivityDestroy(activity);
                ORIGINAL_ORIENTATIONS.remove(activity);
                Activity current = currentLauncher.get();
                if (current == activity) currentLauncher = new WeakReference<>(null);
            }
        });
    }

    private void ensureSettingsReceiver(Context appContext) {
        if (!SETTINGS_RECEIVER_INSTALLED.compareAndSet(false, true)) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                boolean settingsUpdate = LandscapeBridge.ACTION_SETTINGS_UPDATE.equals(action);
                boolean ping = LandscapeBridge.ACTION_PING.equals(action);
                if (!settingsUpdate && !ping) return;

                ModuleSettings settings = settingsUpdate
                        ? ModuleSettings.updateFromIntent(context, intent)
                        : ModuleSettings.load(context);
                DeviceProfile profile = DeviceProfile.inspect(context);
                boolean enabled = profile.isEnabled(settings);
                log((settingsUpdate ? "settings updated" : "ping") + "; enabled=" + enabled
                        + " allowUnsupported=" + settings.allowUnsupported()
                        + " dockEnabled=" + settings.dockEnabled()
                        + " dockAppearance=" + settings.dockAppearance()
                        + " dockSize=" + settings.dockSize());

                Intent pong = new Intent(LandscapeBridge.ACTION_PONG)
                        .setPackage(LandscapeBridge.MODULE_PKG)
                        .putExtra(LandscapeBridge.EXTRA_NONCE,
                                intent.getStringExtra(LandscapeBridge.EXTRA_NONCE))
                        .putExtra(LandscapeBridge.EXTRA_VERSION, VERSION)
                        .putExtra(LandscapeBridge.EXTRA_PROFILE_ENABLED, enabled)
                        .putExtra(LandscapeBridge.EXTRA_PROFILE_SUMMARY, profile.summary());
                settings.putInto(pong);
                context.sendBroadcast(pong);

                if (!settingsUpdate) return;
                int settingsToken = SETTINGS_GENERATION.incrementAndGet();
                Activity activity = currentLauncher.get();
                if (activity == null) return;
                if (!enabled) {
                    LandscapeController.dispose(activity);
                    return;
                }
                activity.getWindow().getDecorView().post(() -> {
                    try {
                        if (SETTINGS_GENERATION.get() != settingsToken
                                || !isUsableLauncher(activity)
                                || currentLauncher.get() != activity
                                || !moduleEnabled(activity, "settings/post")) {
                            return;
                        }
                        LandscapeController controller = LandscapeController.forActivity(activity);
                        controller.onLauncherCreate();
                        controller.onConfigurationChanged(
                                activity.getResources().getConfiguration());
                    } catch (Throwable error) {
                        log("settings apply callback failed: " + error);
                    }
                });
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(LandscapeBridge.ACTION_SETTINGS_UPDATE);
        filter.addAction(LandscapeBridge.ACTION_PING);
        try {
            appContext.registerReceiver(
                    receiver,
                    filter,
                    LandscapeBridge.PERMISSION_CONTROL,
                    null,
                    Context.RECEIVER_EXPORTED);
            log("protected settings receiver registered");
        } catch (Throwable t) {
            SETTINGS_RECEIVER_INSTALLED.set(false);
            log("protected settings receiver registration failed: " + t);
        }
    }

    private void hookAfter(String methodName, Class<?>[] parameterTypes, HookAction action) {
        try {
            Method method = findDeclaredMethod(launcherClass, methodName, parameterTypes);
            if (method == null) {
                log("target method not declared; not widening hook: "
                        + LAUNCHER_CLASS + "#" + methodName);
                return;
            }
            XposedBridge.hookMethod(method,
                    new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        action.run(param);
                    } catch (Throwable t) {
                        log(LAUNCHER_CLASS + "#" + methodName + " callback failed: " + t);
                    }
                        }
                    });
            log("hooked exact " + method);
        } catch (Throwable t) {
            log("failed to hook " + LAUNCHER_CLASS + "#" + methodName + ": " + t);
        }
    }

    private void hookRecentsVisibility(ClassLoader classLoader) {
        Class<?> recentsClass = XposedHelpers.findClassIfExists(RECENTS_CLASS, classLoader);
        if (recentsClass == null) {
            log("Recents hook disabled: missing " + RECENTS_CLASS);
            return;
        }

        Method setVisibility = findDeclaredMethod(recentsClass, "setVisibility", int.class);
        if (setVisibility != null) {
            hookExactRecentsMethod(setVisibility, false);
        } else {
            Method onVisibilityChanged = findDeclaredMethod(
                    recentsClass, "onVisibilityChanged", View.class, int.class);
            if (onVisibilityChanged != null) {
                hookExactRecentsMethod(onVisibilityChanged, true);
            } else {
                log("Recents visibility hook skipped: target declares neither setVisibility nor "
                        + "onVisibilityChanged; refusing a global View hook");
            }
        }
        hookRecentsGestureComplete(recentsClass, classLoader);
    }

    /** Waits for MIUI's own full-screen-gesture completion before custom Recents takeover. */
    private void hookRecentsGestureComplete(Class<?> recentsClass, ClassLoader classLoader) {
        Class<?> eventClass = XposedHelpers.findClassIfExists(
                RECENTS_GESTURE_COMPLETE_EVENT, classLoader);
        if (eventClass == null) {
            log("Recents gesture-complete hook unavailable: missing event class");
            return;
        }
        Method method = findDeclaredMethod(recentsClass, "onMessageEvent", eventClass);
        if (method == null) {
            log("Recents gesture-complete hook unavailable: missing exact subscriber");
            return;
        }
        try {
            XposedBridge.hookMethod(method,
                    new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) return;
                    View view = (View) param.thisObject;
                    Activity activity = findActivity(view.getContext());
                    if (activity == null || !isUsableLauncher(activity)
                            || !moduleEnabled(activity, "recentsGestureComplete")) return;
                    ModuleSettings settings = ModuleSettings.load(activity);
                    if (!settings.recentsEnabled()) return;
                    LandscapeController.onNativeRecentsGestureSettled(
                            activity, view, RECENTS_GESTURE_COMPLETE_EVENT);
                }
            });
            log("hooked exact Recents gesture completion " + method);
        } catch (Throwable error) {
            log("failed Recents gesture-complete hook: " + error);
        }
    }

    private void hookExactRecentsMethod(Method method, boolean visibilityCallback) {
        try {
            XposedBridge.hookMethod(method,
                    new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) return;
                    View view = (View) param.thisObject;
                    if (visibilityCallback
                            && (param.args.length < 1 || param.args[0] != view)) {
                        return;
                    }
                    Activity activity = findActivity(view.getContext());
                    if (activity == null || !isUsableLauncher(activity)) return;
                    if (LandscapeController.isInternalNativeRecentsMutation(activity, view)) return;
                    if (!moduleEnabled(activity, "recentsVisibility")) return;
                    ModuleSettings settings = ModuleSettings.load(activity);
                    if (!settings.recentsEnabled()) return;
                    LandscapeController.onNativeRecentsVisibility(
                            activity,
                            view,
                            view.getVisibility() == View.VISIBLE,
                            RECENTS_CLASS + "#" + method.getName());
                }
            });
            log("hooked exact Recents visibility method " + method);
        } catch (Throwable t) {
            log("failed exact Recents visibility hook: " + t);
        }
    }

    private boolean moduleEnabled(Activity activity, String source) {
        ModuleSettings settings = ModuleSettings.load(activity);
        DeviceProfile profile = DeviceProfile.inspect(activity);
        if (PROFILE_LOGGED.compareAndSet(false, true)) {
            log("compatibility profile: " + profile.summary()
                    + " allowUnsupported=" + settings.allowUnsupported());
        }
        if (profile.isEnabled(settings)) return true;
        log("disabled by compatibility gate at " + source);
        LandscapeController.dispose(activity);
        return false;
    }

    private Activity exactLauncherActivity(Object object) {
        if (!(object instanceof Activity)
                || launcherClass == null
                || object.getClass() != launcherClass) {
            return null;
        }
        return (Activity) object;
    }

    private boolean isUsableLauncher(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && exactLauncherActivity(activity) == activity;
    }

    public static int originalRequestedOrientation(Activity activity) {
        if (activity == null) return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        Integer remembered = ORIGINAL_ORIENTATIONS.get(activity);
        if (remembered != null) return remembered;
        int fallback = activity.getRequestedOrientation();
        rememberOriginalOrientation(activity, fallback);
        remembered = ORIGINAL_ORIENTATIONS.get(activity);
        return remembered == null ? fallback : remembered;
    }

    public static void setRequestedOrientationInternal(Activity activity, int orientation) {
        if (activity == null) return;
        INTERNAL_ORIENTATION_CHANGE.set(Boolean.TRUE);
        try {
            activity.setRequestedOrientation(orientation);
        } finally {
            INTERNAL_ORIENTATION_CHANGE.remove();
        }
    }

    private static void rememberOriginalOrientation(Activity activity, int fallback) {
        if (activity == null || ORIGINAL_ORIENTATIONS.containsKey(activity)) return;
        int original = fallback;
        try {
            ActivityInfo info = activity.getPackageManager().getActivityInfo(
                    activity.getComponentName(), PackageManager.ComponentInfoFlags.of(0L));
            if (info != null) original = info.screenOrientation;
        } catch (Throwable ignored) {
        }
        ORIGINAL_ORIENTATIONS.put(activity, original);
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        for (int depth = 0; depth < 8 && current != null; depth++) {
            if (current instanceof Activity) return (Activity) current;
            if (!(current instanceof ContextWrapper)) return null;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) return null;
            current = next;
        }
        return null;
    }

    private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getDeclaredMethod(name, parameters);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + message);
    }

    private interface HookAction {
        void run(XC_MethodHook.MethodHookParam param) throws Throwable;
    }
}
