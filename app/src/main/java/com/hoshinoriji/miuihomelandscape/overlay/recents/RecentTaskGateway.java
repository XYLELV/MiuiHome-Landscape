package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The single binder gateway used by the custom recents UI.
 *
 * <p>No hidden Android class appears in a method signature or field. If MIUI changes the
 * API 33 binder surface, callers receive a failure and leave native Recents in charge.</p>
 */
final class RecentTaskGateway {
    static final class Result<T> {
        final T value;
        final Throwable error;

        private Result(T value, Throwable error) {
            this.value = value;
            this.error = error;
        }

        static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> failure(Throwable error) {
            return new Result<>(null, error);
        }

        boolean isSuccess() {
            return error == null;
        }
    }

    private static final int MAX_TASKS = 32;
    private final Context appContext;
    private final PackageManager packageManager;

    RecentTaskGateway(Context context) {
        appContext = context.getApplicationContext();
        packageManager = context.getPackageManager();
    }

    Result<List<RecentTaskItem>> readTasks() {
        try {
            Object service = getActivityTaskManagerService();
            Method method = findMethod(service.getClass(), "getRecentTasks",
                    int.class, int.class, int.class);
            Object slice = method.invoke(service, MAX_TASKS, 0, currentUserId());
            List<?> rawTasks = unwrapList(slice);
            if (rawTasks == null) {
                throw new IllegalStateException("getRecentTasks returned no list");
            }

            ArrayList<RecentTaskItem> result = new ArrayList<>(rawTasks.size());
            for (Object taskInfo : rawTasks) {
                RecentTaskItem task = projectTask(taskInfo);
                if (task != null) result.add(task);
            }
            if (!rawTasks.isEmpty() && result.isEmpty()) {
                throw new IllegalStateException(
                        "recent task list was non-empty but no task could be projected");
            }
            return Result.success(Collections.unmodifiableList(result));
        } catch (Throwable error) {
            return Result.failure(unwrapReflectionError(error));
        }
    }

    /** One launch request, through IActivityTaskManager.startActivityFromRecents only. */
    Result<Void> launch(int taskId) {
        try {
            Object service = getActivityTaskManagerService();
            Method method = findMethod(service.getClass(), "startActivityFromRecents",
                    int.class, Bundle.class);
            Object value = method.invoke(service, taskId, null);
            // ActivityManager START_SUCCESS..START_TASK_TO_FRONT occupy 0..99.
            // 100+ are abort/cancel/conflict results and must not dismiss our UI as success.
            if (value instanceof Integer) {
                int result = (Integer) value;
                if (result < 0 || result > 99) {
                    throw new IllegalStateException("startActivityFromRecents=" + result);
                }
            }
            return Result.success(null);
        } catch (Throwable error) {
            return Result.failure(unwrapReflectionError(error));
        }
    }

    /**
     * Uses the target MIUI Home task-dismiss event, which removes the task data and invokes
     * MIUI's own process-clean path. This must be called on the Launcher main thread.
     */
    Result<Void> dismissViaMiui(View nativeRecents, int taskId) {
        try {
            if (nativeRecents == null) throw new IllegalArgumentException("nativeRecents == null");
            Object recentsView = invokeNoArgs(nativeRecents, "getRecentsView");
            Object taskViewsValue = invokeNoArgs(recentsView, "getTaskViews");
            if (!(taskViewsValue instanceof Iterable)) {
                throw new IllegalStateException("RecentsView#getTaskViews is not iterable");
            }

            Object matchingView = null;
            Object matchingTask = null;
            for (Object taskView : (Iterable<?>) taskViewsValue) {
                if (taskView == null) continue;
                Object task = null;
                try {
                    task = invokeNoArgs(taskView, "getTask");
                } catch (Throwable ignored) {
                    task = readOptionalField(taskView, "mTask");
                }
                if (task != null && taskContainsId(task, taskId)) {
                    matchingView = taskView;
                    matchingTask = task;
                    break;
                }
            }
            if (matchingTask == null || matchingView == null) {
                throw new IllegalStateException("MIUI TaskView not found for task " + taskId);
            }

            ClassLoader loader = nativeRecents.getClass().getClassLoader();
            Object animation = immediateAnimationProps(loader);
            Class<?> eventClass = Class.forName(
                    "com.miui.home.recents.messages.TaskViewDismissedEvent", false, loader);
            Object event = newCompatibleEvent(eventClass, matchingTask, matchingView, animation);

            Class<?> helper = Class.forName(
                    "com.miui.home.library.utils.AsyncTaskExecutorHelper", false, loader);
            Method getEventBus = helper.getDeclaredMethod("getEventBus");
            if (!getEventBus.isAccessible()) getEventBus.setAccessible(true);
            Object eventBus = getEventBus.invoke(null);
            if (eventBus == null) throw new IllegalStateException("MIUI EventBus is null");
            Method post = findCompatibleOneArgMethod(eventBus.getClass(), "post", eventClass);
            post.invoke(eventBus, event);
            return Result.success(null);
        } catch (Throwable error) {
            return Result.failure(unwrapReflectionError(error));
        }
    }

    /** Waits until Android's recent-task list confirms that the selected task is gone. */
    Result<List<RecentTaskItem>> readTasksUntilRemoved(int taskId) {
        long[] delays = new long[]{0L, 70L, 120L, 180L, 260L};
        Result<List<RecentTaskItem>> last = null;
        for (long delay : delays) {
            if (delay > 0L) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return Result.failure(interrupted);
                }
            }
            last = readTasks();
            if (!last.isSuccess()) return last;
            boolean found = false;
            for (RecentTaskItem item : last.value) {
                if (item.taskId == taskId) {
                    found = true;
                    break;
                }
            }
            if (!found) return last;
        }
        return Result.failure(new IllegalStateException(
                "task " + taskId + " still present after MIUI dismiss"));
    }

    /** Ends MIUI overview and synchronously commits LauncherState.NORMAL. */
    @SuppressLint({"PrivateApi", "BlockedPrivateApi"})
    Result<Void> exitOverviewToHome(Activity launcher, View nativeRecents) {
        try {
            if (launcher == null) throw new IllegalArgumentException("launcher == null");
            ClassLoader loader = launcher.getClassLoader();
            Object stateManager = invokeNoArgs(launcher, "getStateManager");
            Class<?> stateClass = Class.forName(
                    "com.miui.home.launcher.LauncherState", false, loader);
            Field normalField = stateClass.getDeclaredField("NORMAL");
            if (!normalField.isAccessible()) normalField.setAccessible(true);
            Object normal = normalField.get(null);
            Object current = invokeNoArgs(stateManager, "getState");
            if (current == normal) return Result.success(null);

            Throwable nativeExitError = null;
            if (nativeRecents != null) {
                try {
                    invokeNoArgs(nativeRecents, "dismissRecentsToHome");
                } catch (Throwable error) {
                    nativeExitError = unwrapReflectionError(error);
                }
            }

            current = invokeNoArgs(stateManager, "getState");
            if (current != normal) {
                Method goToState = findMethod(
                        stateManager.getClass(), "goToState", stateClass, boolean.class);
                goToState.invoke(stateManager, normal, false);
            }
            Object committed = invokeNoArgs(stateManager, "getState");
            if (committed != normal && nativeExitError != null) throw nativeExitError;
            if (committed != normal) {
                throw new IllegalStateException("LauncherState did not commit NORMAL");
            }
            return Result.success(null);
        } catch (Throwable error) {
            return Result.failure(unwrapReflectionError(error));
        }
    }

    /** One clear request. There is deliberately no per-task removal fallback cascade. */
    Result<Void> clearVisibleTasks() {
        try {
            Object service = getActivityTaskManagerService();
            Method method = findMethod(service.getClass(), "removeAllVisibleRecentTasks");
            method.invoke(service);
            return Result.success(null);
        } catch (Throwable error) {
            return Result.failure(unwrapReflectionError(error));
        }
    }

    private RecentTaskItem projectTask(Object taskInfo) throws Exception {
        int taskId = readInt(taskInfo, -1, "taskId", "persistentId", "id");
        if (taskId < 0) return null;

        Intent rawIntent = (Intent) readField(taskInfo, "baseIntent");
        Intent baseIntent = rawIntent == null ? null : new Intent(rawIntent);
        ComponentName component = firstComponent(taskInfo, baseIntent);
        if (component == null) return null;

        // The launcher itself is not an app-switcher card.
        if (appContext.getPackageName().equals(component.getPackageName())) return null;

        CharSequence title = component.getPackageName();
        Drawable icon = null;
        try {
            ActivityInfo activityInfo = packageManager.getActivityInfo(component, 0);
            CharSequence loaded = activityInfo.loadLabel(packageManager);
            if (loaded != null && loaded.length() > 0) title = loaded;
            icon = activityInfo.loadIcon(packageManager);
        } catch (Throwable ignored) {
            try {
                CharSequence loaded = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(component.getPackageName(), 0));
                if (loaded != null && loaded.length() > 0) title = loaded;
                icon = packageManager.getApplicationIcon(component.getPackageName());
            } catch (Throwable ignoredAgain) {
                // Text-only cards remain usable; task launch is by id, not this Intent.
            }
        }
        return new RecentTaskItem(taskId, baseIntent, title, icon);
    }

    private static ComponentName firstComponent(Object taskInfo, Intent baseIntent) {
        if (baseIntent != null && baseIntent.getComponent() != null) {
            return baseIntent.getComponent();
        }
        for (String name : new String[]{"realActivity", "origActivity", "baseActivity", "topActivity"}) {
            try {
                Object value = readField(taskInfo, name);
                if (value instanceof ComponentName) return (ComponentName) value;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @SuppressLint({"PrivateApi", "BlockedPrivateApi"})
    private static Object getActivityTaskManagerService() throws Exception {
        Class<?> activityTaskManager = Class.forName("android.app.ActivityTaskManager");
        Method getService = activityTaskManager.getDeclaredMethod("getService");
        if (!getService.isAccessible()) getService.setAccessible(true);
        Object service = getService.invoke(null);
        if (service == null) throw new IllegalStateException("ActivityTaskManager service is null");
        return service;
    }

    private static int currentUserId() {
        try {
            Object handle = Process.myUserHandle();
            Method identifier = handle.getClass().getDeclaredMethod("getIdentifier");
            if (!identifier.isAccessible()) identifier.setAccessible(true);
            Object value = identifier.invoke(handle);
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {
            // Android allocates a block of 100000 UIDs per user. This stable platform encoding
            // keeps the native fallback reachable even when hidden-API reflection is restricted.
        }
        return Process.myUid() / 100000;
    }

    private static List<?> unwrapList(Object slice) throws Exception {
        if (slice instanceof List) return (List<?>) slice;
        if (slice == null) return null;
        Method getList = findMethod(slice.getClass(), "getList");
        Object value = getList.invoke(slice);
        return value instanceof List ? (List<?>) value : null;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = owner;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                if (!method.isAccessible()) method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        // Binder proxy methods are commonly exposed through an implemented interface.
        Method method = owner.getMethod(name, parameterTypes);
        if (!method.isAccessible()) method.setAccessible(true);
        return method;
    }

    private static Object invokeNoArgs(Object owner, String name) throws Exception {
        if (owner == null) throw new NullPointerException(name + " owner == null");
        return findMethod(owner.getClass(), name).invoke(owner);
    }

    private static Object readOptionalField(Object owner, String name) {
        try {
            return readField(owner, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean taskContainsId(Object task, int taskId) {
        for (String fieldName : new String[]{"key", "cti1Key", "cti2Key"}) {
            Object key = readOptionalField(task, fieldName);
            if (key != null && readInt(key, -1, "id", "taskId") == taskId) return true;
        }
        return readInt(task, -1, "id", "taskId") == taskId;
    }

    @SuppressLint({"PrivateApi", "BlockedPrivateApi"})
    private static Object immediateAnimationProps(ClassLoader loader) {
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.shared.recents.utilities.AnimationProps", false, loader);
            Field field = type.getDeclaredField("IMMEDIATE");
            if (!field.isAccessible()) field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object newCompatibleEvent(Class<?> eventClass, Object task, Object taskView,
                                             Object animation) throws Exception {
        for (Constructor<?> constructor : eventClass.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 3
                    || !parameters[0].isInstance(task)
                    || !parameters[1].isInstance(taskView)
                    || (animation != null && !parameters[2].isInstance(animation))) {
                continue;
            }
            if (!constructor.isAccessible()) constructor.setAccessible(true);
            return constructor.newInstance(task, taskView, animation);
        }
        throw new NoSuchMethodException(eventClass.getName() + " compatible constructor");
    }

    private static Method findCompatibleOneArgMethod(Class<?> owner, String name,
                                                     Class<?> argumentType)
            throws NoSuchMethodException {
        Class<?> current = owner;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals(name) && parameters.length == 1
                        && parameters[0].isAssignableFrom(argumentType)) {
                    if (!method.isAccessible()) method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + "(" + argumentType + ")");
    }

    private static Object readField(Object owner, String name) throws Exception {
        Class<?> current = owner.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                if (!field.isAccessible()) field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static int readInt(Object owner, int fallback, String... names) {
        for (String name : names) {
            try {
                Object value = readField(owner, name);
                if (value instanceof Integer && ((Integer) value) >= 0) return (Integer) value;
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private static Throwable unwrapReflectionError(Throwable error) {
        Throwable cause = error.getCause();
        return cause == null ? error : cause;
    }
}
