package com.hoshinoriji.miuihomelandscape.core;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Applies launcher-window-only cutout/system-bar changes and restores exactly. */
public final class WindowLease {
    private boolean held;
    private boolean hidNavigation;
    private boolean navigationWasVisible = true;
    private int systemBarsBehavior;
    private int layoutInDisplayCutoutMode;
    private int statusBarColor;
    private int navigationBarColor;

    public void apply(Activity activity, boolean hideGestureHandle) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (!held) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            layoutInDisplayCutoutMode = attrs.layoutInDisplayCutoutMode;
            statusBarColor = window.getStatusBarColor();
            navigationBarColor = window.getNavigationBarColor();
            if (decor != null) {
                WindowInsets insets = decor.getRootWindowInsets();
                navigationWasVisible = insets == null
                        || insets.isVisible(WindowInsets.Type.navigationBars());
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) {
                    systemBarsBehavior = controller.getSystemBarsBehavior();
                }
            }
            held = true;
        }

        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attrs);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (decor == null) return;
        if (hideGestureHandle) {
            hidNavigation = true;
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else if (hidNavigation) {
            restoreNavigation(decor);
            hidNavigation = false;
        }
    }

    public void restore(Activity activity) {
        if (!held || activity == null) return;
        Window window = activity.getWindow();
        if (window == null) {
            clear();
            return;
        }
        View decor = window.getDecorView();
        if (decor != null) {
            if (hidNavigation) restoreNavigation(decor);
        }
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.layoutInDisplayCutoutMode = layoutInDisplayCutoutMode;
        window.setAttributes(attrs);
        window.setStatusBarColor(statusBarColor);
        window.setNavigationBarColor(navigationBarColor);
        clear();
    }

    private void restoreNavigation(View decor) {
        WindowInsetsController controller = decor.getWindowInsetsController();
        if (controller == null) return;
        if (navigationWasVisible) controller.show(WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(systemBarsBehavior);
    }

    private void clear() {
        held = false;
        hidNavigation = false;
    }
}
