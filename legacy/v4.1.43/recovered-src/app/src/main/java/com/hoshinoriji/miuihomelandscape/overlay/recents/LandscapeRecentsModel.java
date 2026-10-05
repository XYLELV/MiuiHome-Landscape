package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LandscapeRecentsModel {
    private static final String TAG = "[MiuiHomeLandscape/Recents] ";

    private LandscapeRecentsModel() {
    }

    public static final class TaskRef {
        public final ComponentName component;
        public final Drawable icon;
        public final Object task;
        public final int taskId;
        public final Bitmap thumbnail;
        public final CharSequence title;

        TaskRef(Object task, int taskId, CharSequence title, Drawable icon, Bitmap thumbnail, ComponentName component) {
            this.task = task;
            this.taskId = taskId;
            this.title = title;
            this.icon = icon;
            this.thumbnail = thumbnail;
            this.component = component;
        }
    }

    public static List<TaskRef> getTasks(Context launcherCtx) {
        TaskRef ref;
        if (launcherCtx == null) {
            return Collections.emptyList();
        }
        ClassLoader cl = launcherCtx.getClassLoader();
        try {
            Class<?> modelCls = XposedHelpers.findClassIfExists("com.miui.home.recents.RecentsModel", cl);
            if (modelCls == null) {
                log("RecentsModel class not found");
                return Collections.emptyList();
            }
            Object model = null;
            try {
                model = XposedHelpers.callStaticMethod(modelCls, "getInstance", new Object[]{launcherCtx});
            } catch (Throwable th) {
            }
            if (model == null) {
                try {
                    model = XposedHelpers.callStaticMethod(modelCls, "getInstance", new Object[0]);
                } catch (Throwable th2) {
                }
            }
            if (model == null) {
                log("RecentsModel.getInstance returned null");
                return Collections.emptyList();
            }
            Object stack = null;
            String[] stackMethods = {"getRecentsTaskStack", "getTaskStack", "getRecentsTaskLoadPlan"};
            for (String m : stackMethods) {
                try {
                    stack = XposedHelpers.callMethod(model, m, new Object[0]);
                } catch (Throwable th3) {
                }
                if (stack != null) {
                    break;
                }
            }
            if (stack == null) {
                log("could not resolve TaskStack");
                return Collections.emptyList();
            }
            if (stack.getClass().getSimpleName().contains("LoadPlan")) {
                try {
                    stack = XposedHelpers.callMethod(stack, "getTaskStack", new Object[0]);
                } catch (Throwable th4) {
                }
            }
            if (stack == null) {
                return Collections.emptyList();
            }
            Object listObj = null;
            String[] listMethods = {"getStackTasks", "getTasks"};
            for (String m2 : listMethods) {
                try {
                    listObj = XposedHelpers.callMethod(stack, m2, new Object[0]);
                } catch (Throwable th5) {
                }
                if (listObj instanceof List) {
                    break;
                }
            }
            if (!(listObj instanceof List)) {
                log("could not resolve task list");
                return Collections.emptyList();
            }
            List<?> raw = (List) listObj;
            ArrayList<TaskRef> out = new ArrayList<>(raw.size());
            for (Object t : raw) {
                if (t != null && (ref = toTaskRef(t)) != null) {
                    out.add(ref);
                }
            }
            return out;
        } catch (Throwable t2) {
            log("getTasks failed: " + t2);
            return Collections.emptyList();
        }
    }

    private static TaskRef toTaskRef(Object task) {
        Bitmap thumb;
        Object base;
        int taskId = -1;
        try {
            Object key = XposedHelpers.getObjectField(task, "key");
            if (key != null) {
                taskId = XposedHelpers.getIntField(key, "id");
            }
        } catch (Throwable th) {
        }
        if (taskId < 0) {
            try {
                taskId = XposedHelpers.getIntField(task, "taskId");
            } catch (Throwable th2) {
            }
        }
        CharSequence title = null;
        try {
            Object t = XposedHelpers.getObjectField(task, "title");
            if (t instanceof CharSequence) {
                title = (CharSequence) t;
            }
        } catch (Throwable th3) {
        }
        Drawable icon = null;
        try {
            Object i = XposedHelpers.getObjectField(task, "icon");
            if (i instanceof Drawable) {
                icon = (Drawable) i;
            }
        } catch (Throwable th4) {
        }
        Drawable icon2 = icon;
        Bitmap thumb2 = null;
        try {
            Object th5 = XposedHelpers.getObjectField(task, "thumbnail");
            if (th5 instanceof Bitmap) {
                thumb2 = (Bitmap) th5;
            }
        } catch (Throwable th6) {
        }
        if (thumb2 == null) {
            try {
                Object thumbData = XposedHelpers.callMethod(task, "getThumbnail", new Object[0]);
                if (thumbData instanceof Bitmap) {
                    thumb2 = (Bitmap) thumbData;
                } else if (thumbData != null) {
                    try {
                        Object inner = XposedHelpers.getObjectField(thumbData, "thumbnail");
                        if (inner instanceof Bitmap) {
                            thumb2 = (Bitmap) inner;
                        }
                    } catch (Throwable th7) {
                    }
                }
                thumb = thumb2;
            } catch (Throwable th8) {
                thumb = thumb2;
            }
        } else {
            thumb = thumb2;
        }
        ComponentName cn = null;
        try {
            Object key2 = XposedHelpers.getObjectField(task, "key");
            if (key2 != null && (base = XposedHelpers.getObjectField(key2, "baseIntent")) != null) {
                Object c = XposedHelpers.callMethod(base, "getComponent", new Object[0]);
                if (c instanceof ComponentName) {
                    cn = (ComponentName) c;
                }
            }
        } catch (Throwable th9) {
        }
        try {
            return new TaskRef(task, taskId, title == null ? "" : title, icon2, thumb, cn);
        } catch (Throwable t2) {
            log("toTaskRef failed: " + t2);
            return null;
        }
    }

    public static boolean dismissTask(Context launcherCtx, TaskRef ref, View taskViewStub) {
        Object bus;
        if (ref == null || launcherCtx == null) {
            return false;
        }
        ClassLoader cl = launcherCtx.getClassLoader();
        boolean any = false;
        Object animation = makeAnimationProps(cl);
        Object event = makeTaskDismissedEvent(cl, ref.task, taskViewStub, animation);
        if (event != null) {
            try {
                Class<?> helper = XposedHelpers.findClassIfExists("com.miui.home.library.utils.AsyncTaskExecutorHelper", cl);
                if (helper != null && (bus = XposedHelpers.callStaticMethod(helper, "getEventBus", new Object[0])) != null) {
                    XposedHelpers.callMethod(bus, "post", new Object[]{event});
                    log("dismiss via EventBus task=" + ref.taskId);
                    any = true;
                }
            } catch (Throwable t) {
                log("EventBus dismiss failed: " + t);
            }
        }
        try {
            ActivityManager am = (ActivityManager) launcherCtx.getSystemService("activity");
            if (am != null && ref.taskId >= 0) {
                XposedHelpers.callMethod(am, "removeTask", new Object[]{Integer.valueOf(ref.taskId)});
                log("dismiss via ActivityManager.removeTask task=" + ref.taskId);
                return true;
            }
            return any;
        } catch (Throwable t2) {
            log("ActivityManager.removeTask failed: " + t2);
            return any;
        }
    }

    public static void clearAllTasks(Context launcherCtx) {
        View v;
        if (launcherCtx == null) {
            return;
        }
        ClassLoader cl = launcherCtx.getClassLoader();
        int i = 0;
        if (launcherCtx instanceof Activity) {
            Activity a = (Activity) launcherCtx;
            String[] candidateIds = {"clear_animation_view", "button_clear_all", "clear_all", "clear_all_button", "btn_clear_all", "recents_clear_all"};
            for (String name : candidateIds) {
                try {
                    int id = a.getResources().getIdentifier(name, "id", "com.miui.home");
                    if (id != 0 && (v = findInDecor(a.getWindow().getDecorView(), id)) != null && v.isShown()) {
                        boolean clicked = v.performClick();
                        log("clearAll via native id=" + name + " clicked=" + clicked);
                        if (clicked) {
                            return;
                        }
                    }
                } catch (Throwable t) {
                    log("clearAll native click " + name + " failed: " + t);
                }
            }
            if (clickNativeClearCandidate(a)) {
                return;
            }
        }
        try {
            Class<?> modelCls = XposedHelpers.findClassIfExists("com.miui.home.recents.RecentsModel", cl);
            Object model = null;
            if (modelCls != null) {
                try {
                    model = XposedHelpers.callStaticMethod(modelCls, "getInstance", new Object[]{launcherCtx});
                } catch (Throwable th) {
                }
                if (model == null) {
                    try {
                        model = XposedHelpers.callStaticMethod(modelCls, "getInstance", new Object[0]);
                    } catch (Throwable th2) {
                    }
                }
            }
            if (model != null) {
                String[] methods = {"removeAllTasks", "clearAllTasks", "clearAll", "removeAllVisibleTasks", "removeAllRecentTasks", "removeAllTask", "clearAllTask"};
                for (String mname : methods) {
                    try {
                        try {
                            XposedHelpers.callMethod(model, mname, new Object[0]);
                            log("clearAll via RecentsModel#" + mname);
                        } catch (Throwable th3) {
                        }
                    } catch (Throwable th4) {
                    }
                }
            }
        } catch (Throwable t2) {
            log("clearAll RecentsModel path failed: " + t2);
        }
        try {
            Class<?> helper = XposedHelpers.findClassIfExists("com.miui.home.library.utils.AsyncTaskExecutorHelper", cl);
            Object bus = helper == null ? null : XposedHelpers.callStaticMethod(helper, "getEventBus", new Object[0]);
            if (bus != null) {
                String[] eventNames = {"com.miui.home.recents.messages.ClearAllTasksEvent", "com.miui.home.recents.messages.AllTaskDismissedEvent", "com.miui.home.recents.messages.RemoveAllTaskEvent", "com.miui.home.recents.messages.RecentsClearAllEvent"};
                int length = eventNames.length;
                int i2 = 0;
                while (i2 < length) {
                    String name2 = eventNames[i2];
                    Class<?> evt = XposedHelpers.findClassIfExists(name2, cl);
                    if (evt != null) {
                        try {
                            Object event = XposedHelpers.newInstance(evt, new Object[i]);
                            XposedHelpers.callMethod(bus, "post", new Object[]{event});
                            log("clearAll via EventBus " + name2);
                            break;
                        } catch (Throwable t3) {
                            log("clearAll EventBus " + name2 + " ctor failed: " + t3);
                            i2++;
                            i = 0;
                        }
                    }
                    i2++;
                    i = 0;
                }
            }
        } catch (Throwable t4) {
            log("clearAll EventBus path failed: " + t4);
        }
        List<TaskRef> tasks = getTasks(launcherCtx);
        try {
            ActivityManager am = (ActivityManager) launcherCtx.getSystemService("activity");
            if (am != null) {
                int ok = 0;
                for (TaskRef ref : tasks) {
                    if (ref != null && ref.taskId >= 0) {
                        try {
                            XposedHelpers.callMethod(am, "removeTask", new Object[]{Integer.valueOf(ref.taskId)});
                            ok++;
                        } catch (Throwable t5) {
                            log("clearAll removeTask " + ref.taskId + " failed: " + t5);
                        }
                    }
                }
                log("clearAll removeTask iterated total=" + tasks.size() + " ok=" + ok);
            }
        } catch (Throwable t6) {
            log("clearAll AM iterate failed: " + t6);
        }
        try {
            Class<?> atmCls = Class.forName("android.app.ActivityTaskManager");
            Object service = XposedHelpers.callStaticMethod(atmCls, "getService", new Object[0]);
            if (service != null) {
                XposedHelpers.callMethod(service, "removeAllVisibleRecentTasks", new Object[0]);
                log("clearAll via IActivityTaskManager.removeAllVisibleRecentTasks");
            }
        } catch (Throwable t7) {
            log("clearAll ATM fallback failed: " + t7);
        }
        try {
            Class<?> modelCls2 = XposedHelpers.findClassIfExists("com.miui.home.recents.RecentsModel", cl);
            Object model2 = null;
            if (modelCls2 != null) {
                try {
                    model2 = XposedHelpers.callStaticMethod(modelCls2, "getInstance", new Object[]{launcherCtx});
                } catch (Throwable th5) {
                }
                if (model2 == null) {
                    try {
                        model2 = XposedHelpers.callStaticMethod(modelCls2, "getInstance", new Object[0]);
                    } catch (Throwable th6) {
                    }
                }
            }
            if (model2 != null) {
                String[] strArr = {"invalidateRecentTasks", "invalidateLoadPlan", "preloadRecentsTasks", "reload", "refresh"};
                for (int i3 = 0; i3 < 5; i3++) {
                    String mname2 = strArr[i3];
                    try {
                        XposedHelpers.callMethod(model2, mname2, new Object[0]);
                        log("invalidate via " + mname2);
                    } catch (Throwable th7) {
                    }
                }
            }
        } catch (Throwable th8) {
        }
    }

    private static View findInDecor(View root, int id) {
        if (root == null || id == 0) {
            return null;
        }
        View v = root.findViewById(id);
        if (v != null) {
            return v;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = findInDecor(vg.getChildAt(i), id);
                if (child != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private static boolean clickNativeClearCandidate(Activity a) {
        View decor;
        boolean clicked;
        try {
            decor = a.getWindow() == null ? null : a.getWindow().getDecorView();
        } catch (Throwable t) {
            log("clearAll native candidate scan failed: " + t);
        }
        if (decor == null) {
            return false;
        }
        ArrayList<View> out = new ArrayList<>();
        collectClearCandidates(decor, out, decor.getWidth(), decor.getHeight());
        for (View v : out) {
            try {
                clicked = v.performClick();
                log("clearAll via native candidate " + v.getClass().getName() + " clicked=" + clicked + " desc=" + ((Object) v.getContentDescription()));
            } catch (Throwable t2) {
                log("clearAll native candidate failed: " + t2);
            }
            if (clicked) {
                return true;
            }
        }
        return false;
    }

    private static void collectClearCandidates(View v, ArrayList<View> out, int screenW, int screenH) {
        CharSequence cs;
        if (v == null || !v.isShown()) {
            return;
        }
        if (v.isClickable()) {
            Rect r = new Rect();
            boolean hasRect = v.getGlobalVisibleRect(r);
            String text = "";
            if ((v instanceof TextView) && (cs = ((TextView) v).getText()) != null) {
                text = cs.toString();
            }
            CharSequence descCs = v.getContentDescription();
            String desc = descCs == null ? "" : descCs.toString();
            String all = (text + " " + desc + " " + v.getClass().getName()).toLowerCase();
            boolean bottomCenter = false;
            boolean clearText = all.contains("clear") || all.contains("clean") || all.contains("remove all") || all.contains("清") || all.contains("一键");
            if (hasRect && screenW > 0 && screenH > 0 && r.centerX() > screenW * 0.35f && r.centerX() < screenW * 0.65f && r.centerY() > screenH * 0.68f && r.width() < screenW * 0.25f && r.height() < screenH * 0.25f) {
                bottomCenter = true;
            }
            if (clearText || bottomCenter) {
                out.add(v);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                collectClearCandidates(vg.getChildAt(i), out, screenW, screenH);
            }
        }
    }

    public static boolean launchTask(Context launcherCtx, TaskRef ref) {
        if (ref == null || launcherCtx == null) {
            return false;
        }
        try {
            ActivityManager am = (ActivityManager) launcherCtx.getSystemService("activity");
            if (am != null && ref.taskId >= 0) {
                am.moveTaskToFront(ref.taskId, 0);
                return true;
            }
        } catch (Throwable t) {
            log("launchTask failed: " + t);
        }
        return false;
    }

    private static Object makeTaskDismissedEvent(ClassLoader cl, Object task, View taskView, Object animation) {
        try {
            Class<?> eventCls = XposedHelpers.findClassIfExists("com.miui.home.recents.messages.TaskViewDismissedEvent", cl);
            if (eventCls == null) {
                return null;
            }
            return XposedHelpers.newInstance(eventCls, new Object[]{task, taskView, animation});
        } catch (Throwable t) {
            log("makeTaskDismissedEvent failed: " + t);
            return null;
        }
    }

    private static Object makeAnimationProps(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists("com.android.systemui.shared.recents.utilities.AnimationProps", cl);
            if (cls == null) {
                return null;
            }
            try {
                Object immediate = XposedHelpers.getStaticObjectField(cls, "IMMEDIATE");
                if (immediate != null) {
                    return immediate;
                }
            } catch (Throwable th) {
            }
            return XposedHelpers.newInstance(cls, new Object[0]);
        } catch (Throwable th2) {
            return null;
        }
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
