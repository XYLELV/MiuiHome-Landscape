package com.hoshinoriji.miuihomelandscape;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.overlay.LandscapeController;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Locale;

public class MiuiHomeLandscapeModule implements IXposedHookLoadPackage {
    private static final String ANDROID_PKG = "android";
    public static final String AUTHOR = "Chatgpt 5.4thinking ＆星野莉二";
    public static final String DISCLAIMER = "里面代码全部由 ChatGPT 编写，不能确保没有 bug";
    private static final String[] LAUNCHER_CLASSES = {"com.miui.home.launcher.Launcher", "com.miui.home.launcher.LauncherActivity"};
    private static final String[] MINUS_HOST_CLASSES = {"com.miui.home.launcher.DeviceConfig", "com.miui.home.launcher.common.Utilities", "com.miui.home.launcher.Utilities", "com.miui.home.settings.background.assistant.AssistantSettingsHelper", "com.miui.home.launcher.assistant.util.MinusScreenUtils"};
    private static final String[] MINUS_METHOD_NAMES = {"isSupportMinusScreen", "isSupportAssistant", "isSupportGoogleSearch", "isSupportHomeFeed", "isMinusScreenEnable", "hasMinusScreen", "isAssistantOpen"};
    private static final String TAG = "[MiuiHomeLandscape] ";
    private static final String TARGET_PKG = "com.miui.home";
    public static final String VERSION = "4.1.43";
    private static volatile boolean sGlobalCutoutHooked;
    private static int sGlobalCutoutLogCount;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (ANDROID_PKG.equals(lpparam.packageName)) {
            hookGlobalCutoutPolicy(lpparam.classLoader);
            return;
        }
        if ("com.miui.home".equals(lpparam.packageName)) {
            log(">> v4.1.43 loaded | overlay mode | proc=" + lpparam.processName);
            hookSetRequestedOrientation();
            hookActivityLifecycleFallback();
            hookLauncherLifecycle(lpparam.classLoader);
            hookLandscapeMinusScreenGuards(lpparam.classLoader);
            hookRecentsVisibilitySignal(lpparam.classLoader);
            hookGenericNativeRecentsVisibilitySignal();
            hookMainActivity(lpparam.classLoader);
            log(">> hook setup done");
        }
    }

    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:12:0x0036 -> B:21:0x004c). Please report as a decompilation issue!!! */
    private void hookGlobalCutoutPolicy(ClassLoader cl) {
        if (sGlobalCutoutHooked) {
            return;
        }
        sGlobalCutoutHooked = true;
        log("[global-cutout] setup in android process");
        try {
            Class<?> wms = XposedHelpers.findClassIfExists("com.android.server.wm.WindowManagerService", cl);
            if (wms != null) {
                XposedBridge.hookAllMethods(wms, "addWindow", new XC_MethodHook() {
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        MiuiHomeLandscapeModule.forceCutoutOnArgs(param.args, "WMS#addWindow");
                    }
                });
                XposedBridge.hookAllMethods(wms, "relayoutWindow", new XC_MethodHook() {
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        MiuiHomeLandscapeModule.forceCutoutOnArgs(param.args, "WMS#relayoutWindow");
                    }
                });
                log("[global-cutout] hooked WindowManagerService add/relayout");
            } else {
                log("[global-cutout] WindowManagerService not found");
            }
        } catch (Throwable t) {
            log("[global-cutout] WMS hook failed: " + t);
        }
        try {
            Class<?> ws = XposedHelpers.findClassIfExists("com.android.server.wm.WindowState", cl);
            if (ws != null) {
                XposedBridge.hookAllConstructors(ws, new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        try {
                            MiuiHomeLandscapeModule.forceCutoutMode(XposedHelpers.getObjectField(param.thisObject, "mAttrs"), "WindowState#ctor");
                        } catch (Throwable th) {
                        }
                    }
                });
                XposedBridge.hookAllMethods(ws, "getAttrs", new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        MiuiHomeLandscapeModule.forceCutoutMode(param.getResult(), "WindowState#getAttrs");
                    }
                });
                log("[global-cutout] hooked WindowState attrs");
                return;
            }
            log("[global-cutout] WindowState not found");
        } catch (Throwable t2) {
            log("[global-cutout] WindowState hook failed: " + t2);
        }
    }

    public static void forceCutoutOnArgs(Object[] args, String source) {
        if (args == null) {
            return;
        }
        for (Object arg : args) {
            forceCutoutMode(arg, source);
        }
    }

    public static void forceCutoutMode(Object obj, String source) {
        if (obj instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) obj;
            int type = lp.type;
            boolean appWindow = type >= 1 && type <= 99;
            if (appWindow && lp.layoutInDisplayCutoutMode != 1) {
                lp.layoutInDisplayCutoutMode = 1;
                if (sGlobalCutoutLogCount < 20) {
                    sGlobalCutoutLogCount++;
                    log("[global-cutout] force shortEdges source=" + source + " type=" + type + " title=" + ((Object) lp.getTitle()));
                }
            }
        }
    }

    private void hookSetRequestedOrientation() {
        XposedBridge.hookAllMethods(Activity.class, "setRequestedOrientation", new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                if (MiuiHomeLandscapeModule.isLauncherActivity(a) && param.args.length > 0 && (param.args[0] instanceof Integer) && MiuiHomeLandscapeModule.isPortraitLock(((Integer) param.args[0]).intValue())) {
                    param.args[0] = 13;
                }
            }
        });
        log("[OK] setRequestedOrientation");
    }

    class AnonymousClass6 extends XC_MethodHook {
        AnonymousClass6() {
        }

        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
            final Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
            if (MiuiHomeLandscapeModule.isLauncherActivity(a)) {
                try {
                    a.getWindow().getDecorView().post(new Runnable() {
                        @Override
                        public final void run() {
                            LandscapeController.forActivity(a).onLauncherCreate();
                        }
                    });
                } catch (Throwable t) {
                    MiuiHomeLandscapeModule.log("[Activity#onCreate] " + t);
                }
            }
        }
    }

    private void hookActivityLifecycleFallback() {
        try {
            XposedBridge.hookAllMethods(Activity.class, "onCreate", new AnonymousClass6());
            log("[OK] Activity#onCreate fallback");
        } catch (Throwable t) {
            log("[FAIL] Activity#onCreate fallback: " + t);
        }
        try {
            XposedBridge.hookAllMethods(Activity.class, "onConfigurationChanged", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    Configuration cfg;
                    Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                    if (MiuiHomeLandscapeModule.isLauncherActivity(a)) {
                        try {
                            if (param.args.length > 0 && (param.args[0] instanceof Configuration)) {
                                cfg = (Configuration) param.args[0];
                            } else {
                                cfg = a.getResources().getConfiguration();
                            }
                            LandscapeController.forActivity(a).onConfigurationChanged(cfg);
                        } catch (Throwable t2) {
                            MiuiHomeLandscapeModule.log("[Activity#onCfgChanged] " + t2);
                        }
                    }
                }
            });
            log("[OK] Activity#onConfigurationChanged fallback");
        } catch (Throwable t2) {
            log("[FAIL] Activity#onConfigurationChanged fallback: " + t2);
        }
        try {
            XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                    if (MiuiHomeLandscapeModule.isLauncherActivity(a)) {
                        try {
                            LandscapeController.forActivity(a).onResume();
                        } catch (Throwable t3) {
                            MiuiHomeLandscapeModule.log("[Activity#onResume] " + t3);
                        }
                    }
                }
            });
            log("[OK] Activity#onResume fallback");
        } catch (Throwable t3) {
            log("[FAIL] Activity#onResume fallback: " + t3);
        }
        try {
            XposedBridge.hookAllMethods(Activity.class, "onWindowFocusChanged", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                    if (MiuiHomeLandscapeModule.isLauncherActivity(a)) {
                        try {
                            boolean z = false;
                            if (param.args.length > 0 && (param.args[0] instanceof Boolean) && ((Boolean) param.args[0]).booleanValue()) {
                                z = true;
                            }
                            boolean hasFocus = z;
                            LandscapeController.forActivity(a).onWindowFocusChanged(hasFocus);
                        } catch (Throwable t4) {
                            MiuiHomeLandscapeModule.log("[Activity#onWindowFocusChanged] " + t4);
                        }
                    }
                }
            });
            log("[OK] Activity#onWindowFocusChanged fallback");
        } catch (Throwable t4) {
            log("[FAIL] Activity#onWindowFocusChanged fallback: " + t4);
        }
        try {
            XposedBridge.hookAllMethods(Activity.class, "onDestroy", new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                    if (MiuiHomeLandscapeModule.isLauncherActivity(a)) {
                        LandscapeController.dispose(a);
                    }
                }
            });
            log("[OK] Activity#onDestroy fallback");
        } catch (Throwable t5) {
            log("[FAIL] Activity#onDestroy fallback: " + t5);
        }
    }

    private void hookLauncherLifecycle(ClassLoader cl) {
        for (String cn : LAUNCHER_CLASSES) {
            Class<?> cls = XposedHelpers.findClassIfExists(cn, cl);
            if (cls != null) {
                try {
                    XposedBridge.hookAllMethods(cls, "onCreate", new AnonymousClass11());
                    log("[OK] " + cn + "#onCreate");
                } catch (Throwable t) {
                    log("[FAIL] " + cn + "#onCreate: " + t);
                }
                try {
                    XposedBridge.hookAllMethods(cls, "onConfigurationChanged", new XC_MethodHook() {
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Configuration cfg;
                            Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                            if (a == null) {
                                return;
                            }
                            try {
                                if (param.args.length > 0 && (param.args[0] instanceof Configuration)) {
                                    cfg = (Configuration) param.args[0];
                                } else {
                                    cfg = a.getResources().getConfiguration();
                                }
                                LandscapeController.forActivity(a).onConfigurationChanged(cfg);
                            } catch (Throwable t2) {
                                MiuiHomeLandscapeModule.log("[onCfgChanged] " + t2);
                            }
                        }
                    });
                    log("[OK] " + cn + "#onConfigurationChanged");
                } catch (Throwable t2) {
                    log("[FAIL] " + cn + "#onConfigurationChanged: " + t2);
                }
                try {
                    XposedBridge.hookAllMethods(cls, "onResume", new XC_MethodHook() {
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                            if (a == null) {
                                return;
                            }
                            try {
                                LandscapeController.forActivity(a).onResume();
                            } catch (Throwable t3) {
                                MiuiHomeLandscapeModule.log("[onResume] " + t3);
                            }
                        }
                    });
                    log("[OK] " + cn + "#onResume");
                } catch (Throwable t3) {
                    log("[FAIL] " + cn + "#onResume: " + t3);
                }
                try {
                    XposedBridge.hookAllMethods(cls, "onWindowFocusChanged", new XC_MethodHook() {
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                            if (a == null) {
                                return;
                            }
                            try {
                                boolean z = false;
                                if (param.args.length > 0 && (param.args[0] instanceof Boolean) && ((Boolean) param.args[0]).booleanValue()) {
                                    z = true;
                                }
                                boolean hasFocus = z;
                                LandscapeController.forActivity(a).onWindowFocusChanged(hasFocus);
                            } catch (Throwable t4) {
                                MiuiHomeLandscapeModule.log("[onWindowFocusChanged] " + t4);
                            }
                        }
                    });
                    log("[OK] " + cn + "#onWindowFocusChanged");
                } catch (Throwable t4) {
                    log("[FAIL] " + cn + "#onWindowFocusChanged: " + t4);
                }
                try {
                    XposedBridge.hookAllMethods(cls, "onDestroy", new XC_MethodHook() {
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
                            if (a != null) {
                                LandscapeController.dispose(a);
                            }
                        }
                    });
                } catch (Throwable th) {
                }
            }
        }
    }

    class AnonymousClass11 extends XC_MethodHook {
        AnonymousClass11() {
        }

        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
            final Activity a = MiuiHomeLandscapeModule.toActivity(param.thisObject);
            if (a == null) {
                return;
            }
            try {
                a.getWindow().getDecorView().post(new Runnable() {
                    @Override
                    public final void run() {
                        LandscapeController.forActivity(a).onLauncherCreate();
                    }
                });
            } catch (Throwable t) {
                MiuiHomeLandscapeModule.log("[onCreate] " + t);
            }
        }
    }

    private void hookLandscapeMinusScreenGuards(ClassLoader cl) {
        for (String hostName : MINUS_HOST_CLASSES) {
            Class<?> host = XposedHelpers.findClassIfExists(hostName, cl);
            if (host == null) {
                log("[minus-guard] host not found: " + hostName);
            } else {
                for (String method : MINUS_METHOD_NAMES) {
                    try {
                        int before = countCallbacks();
                        try {
                            XposedBridge.hookAllMethods(host, method, new XC_MethodHook() {
                                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                                    if (MiuiHomeLandscapeModule.isReturningBoolean(param.getResult()) && MiuiHomeLandscapeModule.isCurrentlyLandscape()) {
                                        param.setResult(false);
                                    }
                                }
                            });
                            int after = countCallbacks();
                            if (after > before) {
                                log("[OK] minus-guard " + hostName + "#" + method);
                            } else {
                                log("[skip] minus-guard no method " + hostName + "#" + method);
                            }
                        } catch (Throwable th) {
                            t = th;
                            log("[FAIL] minus-guard " + hostName + "#" + method + ": " + t);
                        }
                    } catch (Throwable th2) {
                        t = th2;
                    }
                }
            }
        }
    }

    private void hookRecentsVisibilitySignal(ClassLoader cl) {
        String[] candidates = {"com.miui.home.recents.views.RecentsContainer", "com.miui.home.recents.RecentsContainer"};
        for (final String cn : candidates) {
            Class<?> cls = XposedHelpers.findClassIfExists(cn, cl);
            if (cls == null) {
                log("[recents] class not found: " + cn);
            } else {
                try {
                    XposedBridge.hookAllMethods(cls, "setVisibility", new XC_MethodHook() {
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (param.args.length != 0) {
                                if (!(param.args[0] instanceof Integer)) {
                                    return;
                                }
                                boolean visible = ((Integer) param.args[0]).intValue() == 0;
                                if (!visible || !MiuiHomeLandscapeModule.isCurrentlyLandscape() || !(param.thisObject instanceof View)) {
                                    return;
                                }
                                View v = (View) param.thisObject;
                                v.setAlpha(0.0f);
                                v.setImportantForAccessibility(4);
                                MiuiHomeLandscapeModule.log("[recents] native pre-muted before visible: " + cn);
                            }
                        }

                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (param.args.length != 0) {
                                if (!(param.args[0] instanceof Integer)) {
                                    return;
                                }
                                boolean visible = ((Integer) param.args[0]).intValue() == 0;
                                if (visible && MiuiHomeLandscapeModule.isCurrentlyLandscape() && (param.thisObject instanceof View)) {
                                    View v = (View) param.thisObject;
                                    v.setAlpha(0.0f);
                                    v.setImportantForAccessibility(4);
                                }
                                LandscapeController.onRecentsVisibilityChanged(visible, cn + "#setVisibility");
                            }
                        }
                    });
                    log("[OK] " + cn + "#setVisibility signal");
                } catch (Throwable t) {
                    log("[FAIL] " + cn + "#setVisibility signal: " + t);
                }
            }
        }
    }

    private void hookGenericNativeRecentsVisibilitySignal() {
        try {
            XposedBridge.hookAllMethods(View.class, "setVisibility", new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    MiuiHomeLandscapeModule.handleGenericNativeRecentsVisibility(param, true);
                }

                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                    MiuiHomeLandscapeModule.handleGenericNativeRecentsVisibility(param, false);
                }
            });
            log("[OK] generic View#setVisibility native-recents guard");
        } catch (Throwable t) {
            log("[FAIL] generic View#setVisibility native-recents guard: " + t);
        }
    }

    public static void handleGenericNativeRecentsVisibility(XC_MethodHook.MethodHookParam param, boolean before) {
        if (param == null || param.args == null || param.args.length == 0 || !(param.args[0] instanceof Integer) || !(param.thisObject instanceof View) || !isCurrentlyLandscape()) {
            return;
        }
        View v = (View) param.thisObject;
        if (looksLikeNativeRecentsContainer(v)) {
            boolean visible = ((Integer) param.args[0]).intValue() == 0;
            if (visible) {
                muteNativeRecentsTree(v);
                if (!before) {
                    LandscapeController.onRecentsVisibilityChanged(true, "generic:" + v.getClass().getName());
                    return;
                }
                return;
            }
            if (!before) {
                LandscapeController.onRecentsVisibilityChanged(false, "generic:" + v.getClass().getName());
            }
        }
    }

    private static boolean looksLikeNativeRecentsView(View v) {
        String cls;
        String name;
        if (v == null || (cls = v.getClass().getName()) == null) {
            return false;
        }
        String lower = cls.toLowerCase(Locale.US);
        if (lower.startsWith(LandscapeBridge.MODULE_PKG)) {
            return false;
        }
        if (lower.contains("recents") || lower.contains("taskstack") || lower.contains("task_stack") || lower.contains("taskview") || lower.contains("task_view") || lower.contains("overview")) {
            return true;
        }
        try {
            int id = v.getId();
            if (id != -1 && (name = v.getResources().getResourceEntryName(id)) != null) {
                String n = name.toLowerCase(Locale.US);
                if (!n.contains("recents") && !n.contains("task_stack") && !n.contains("task_view")) {
                    if (!n.contains("overview")) {
                        return false;
                    }
                }
                return true;
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private static boolean looksLikeNativeRecentsContainer(View v) {
        String name;
        if (v == null) {
            return false;
        }
        String lower = v.getClass().getName().toLowerCase(Locale.US);
        if (lower.contains("recentscontainer") || lower.contains("recents_container") || lower.contains("overviewpanel")) {
            return true;
        }
        try {
            int id = v.getId();
            if (id != -1 && (name = v.getResources().getResourceEntryName(id)) != null) {
                String n = name.toLowerCase(Locale.US);
                if (!n.contains("recents_container")) {
                    if (!n.contains("overview_container")) {
                        return false;
                    }
                }
                return true;
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private static void muteNativeRecentsTree(View v) {
        if (v == null) {
            return;
        }
        try {
            v.setAlpha(0.0f);
            v.setImportantForAccessibility(4);
        } catch (Throwable th) {
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int count = Math.min(g.getChildCount(), 80);
            for (int i = 0; i < count; i++) {
                muteNativeRecentsTree(g.getChildAt(i));
            }
        }
    }

    private static int countCallbacks() {
        return 0;
    }

    public static boolean isReturningBoolean(Object r) {
        return r instanceof Boolean;
    }

    public static boolean isCurrentlyLandscape() {
        Object at;
        try {
            Class<?> atCls = XposedHelpers.findClassIfExists("android.app.ActivityThread", MiuiHomeLandscapeModule.class.getClassLoader());
            if (atCls == null || (at = XposedHelpers.callStaticMethod(atCls, "currentActivityThread", new Object[0])) == null) {
                return false;
            }
            Object appObj = XposedHelpers.callMethod(at, "getApplication", new Object[0]);
            if (!(appObj instanceof Context)) {
                return false;
            }
            Context app = (Context) appObj;
            Configuration cfg = app.getResources().getConfiguration();
            return cfg.orientation == 2;
        } catch (Throwable th) {
            return false;
        }
    }

    private void hookMainActivity(ClassLoader cl) {
        try {
            Class<?> k = XposedHelpers.findClassIfExists("com.hoshinoriji.miuihomelandscape.MainActivity", cl);
            if (k != null) {
                XposedBridge.hookAllMethods(k, "isModuleActive", XC_MethodReplacement.returnConstant(true));
                log("[OK] MainActivity#isModuleActive");
            }
        } catch (Throwable t) {
            log("[FAIL] MainActivity#isModuleActive: " + t);
        }
    }

    public static boolean isPortraitLock(int o) {
        switch (o) {
            case 1:
            case 5:
            case 7:
            case DockPosition.SLOTS:
            case 12:
            case 14:
                return true;
            default:
                return false;
        }
    }

    public static Activity toActivity(Object obj) {
        if (obj instanceof Activity) {
            return (Activity) obj;
        }
        return null;
    }

    public static boolean isLauncherActivity(Activity a) {
        if (a == null || !"com.miui.home".equals(a.getPackageName())) {
            return false;
        }
        String cn = a.getClass().getName();
        for (String c : LAUNCHER_CLASSES) {
            if (c.equals(cn)) {
                return true;
            }
        }
        return cn.contains("Launcher");
    }

    public static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
