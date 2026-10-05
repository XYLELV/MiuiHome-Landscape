package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.R;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.hoshinoriji.miuihomelandscape.overlay.recents.LandscapeRecentsModel;
import com.hoshinoriji.miuihomelandscape.overlay.recents.LandscapeRecentsView;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.util.List;

public class LandscapeRecentsController {
    private static final String TAG = "[MiuiHomeLandscape/RecentsCtl] ";
    private final WeakReference<Activity> actRef;
    private boolean attached;
    private View exitShield;
    private LandscapeRecentsView view;

    public LandscapeRecentsController(Activity a) {
        this.actRef = new WeakReference<>(a);
    }

    public boolean isShowing() {
        return this.view != null && this.view.getVisibility() == 0;
    }

    public void show(String source) {
        Activity a = this.actRef.get();
        if (a == null) {
            return;
        }
        if (a.getResources().getConfiguration().orientation != 2) {
            m98xfc88d396("portrait-guard:" + source);
            log("show ignored in portrait source=" + source);
            return;
        }
        ensureAttached(a);
        if (this.view == null) {
            return;
        }
        List<LandscapeRecentsModel.TaskRef> tasks = LandscapeRecentsModel.getTasks(a);
        this.view.bindTasks(tasks);
        this.view.animate().cancel();
        this.view.setAlpha(1.0f);
        this.view.setTranslationX(0.0f);
        this.view.setTranslationY(0.0f);
        this.view.setVisibility(0);
        this.view.bringToFront();
        this.view.requestFocus();
        this.view.playEnterAnimation();
        log("show source=" + source + " tasks=" + tasks.size());
    }

    public void m98xfc88d396(String source) {
        if (this.view != null) {
            this.view.animate().cancel();
            this.view.setAlpha(1.0f);
            this.view.setTranslationX(0.0f);
            this.view.setTranslationY(0.0f);
            this.view.setVisibility(8);
        }
        log("hide source=" + source);
    }

    public void hideDelayed(final String source, long delayMs) {
        if (this.view == null) {
            log("hideDelayed ignored source=" + source);
            return;
        }
        long safeDelay = Math.max(620L, delayMs);
        this.view.animate().cancel();
        this.view.setAlpha(1.0f);
        this.view.setTranslationX(0.0f);
        this.view.setTranslationY(0.0f);
        this.view.bringToFront();
        this.view.postDelayed(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m98xfc88d396(source);
            }
        }, safeDelay);
        log("hideDelayed source=" + source + " delayMs=" + delayMs + " safeDelay=" + safeDelay);
    }

    public void showExitShield(String source) {
        Activity act = this.actRef.get();
        if (act != null) {
            showExitShield(act, source);
        }
    }

    public void dispose() {
        removeExitShield();
        if (this.view != null && (this.view.getParent() instanceof ViewGroup)) {
            ((ViewGroup) this.view.getParent()).removeView(this.view);
        }
        this.view = null;
        this.attached = false;
    }

    private void ensureAttached(Activity a) {
        ViewGroup parent;
        if (!this.attached || this.view == null) {
            View decor = a.getWindow() == null ? null : a.getWindow().getDecorView();
            if (decor instanceof ViewGroup) {
                parent = (ViewGroup) decor;
            } else {
                parent = (ViewGroup) a.findViewById(R.id.content);
            }
            if (parent == null) {
                log("no decor/content parent");
                return;
            }
            this.view = new LandscapeRecentsView(a);
            this.view.setVisibility(8);
            this.view.setListener(new AnonymousClass1());
            parent.addView(this.view, new FrameLayout.LayoutParams(-1, -1));
            this.attached = true;
            log("attached parent=" + parent.getClass().getName());
        }
    }

    class AnonymousClass1 implements LandscapeRecentsView.Listener {
        AnonymousClass1() {
        }

        @Override
        public void onTaskTap(LandscapeRecentsModel.TaskRef ref) {
            Activity act = (Activity) LandscapeRecentsController.this.actRef.get();
            if (act != null && LandscapeRecentsModel.launchTask(act, ref)) {
                LandscapeRecentsController.this.m98xfc88d396("task-tap");
            }
        }

        @Override
        public void onTaskDismissRequested(LandscapeRecentsModel.TaskRef ref, View card) {
            Activity act = (Activity) LandscapeRecentsController.this.actRef.get();
            if (act == null) {
                return;
            }
            LandscapeRecentsModel.dismissTask(act, ref, card);
        }

        @Override
        public void onClearAllRequested() {
            Activity act = (Activity) LandscapeRecentsController.this.actRef.get();
            if (act != null) {
                LandscapeRecentsModel.clearAllTasks(act);
            }
            if (LandscapeRecentsController.this.view != null) {
                LandscapeRecentsController.this.view.postDelayed(new Runnable() {
                    @Override
                    public final void run() {
                        this.f$0.m99x8b83b728();
                    }
                }, 120L);
            } else {
                LandscapeRecentsController.this.exitRecentsToHome("clear-all");
            }
        }

        void m99x8b83b728() {
            if (LandscapeRecentsController.this.view != null) {
                LandscapeRecentsController.this.view.clearAll();
            }
            LandscapeRecentsController.this.exitRecentsToHome("clear-all");
        }

        @Override
        public void onBackgroundTapped() {
            LandscapeRecentsController.this.exitRecentsToHome("bg-tap");
        }
    }

    public void exitRecentsToHome(final String source) {
        Activity act = this.actRef.get();
        if (act == null) {
            return;
        }
        try {
            Intent home = new Intent("android.intent.action.MAIN");
            home.addCategory("android.intent.category.HOME");
            home.setFlags(270532608);
            act.startActivity(home);
            if (this.view != null) {
                this.view.postDelayed(new Runnable() {
                    @Override
                    public final void run() {
                        this.f$0.m97x65116da1(source);
                    }
                }, 620L);
            } else {
                m98xfc88d396("exit:" + source);
            }
            log("exit-to-home via HOME intent source=" + source);
        } catch (Throwable t) {
            log("exit-to-home failed: " + t);
            try {
                act.onBackPressed();
            } catch (Throwable th) {
            }
        }
    }

    void m97x65116da1(String source) {
        m98xfc88d396("exit-delayed:" + source);
    }

    private void showExitShield(Activity a, String source) {
        View decor = a.getWindow() == null ? null : a.getWindow().getDecorView();
        if (decor instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) decor;
            removeExitShield();
            int shieldW = estimateLeftUnsafeWidth(a, parent);
            this.exitShield = new View(a);
            this.exitShield.setBackgroundColor(-15724524);
            this.exitShield.setClickable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(shieldW, -1);
            lp.leftMargin = 0;
            parent.addView(this.exitShield, lp);
            this.exitShield.bringToFront();
            this.exitShield.postDelayed(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.removeExitShield();
                }
            }, 1400L);
            log("exitShield show source=" + source + " w=" + shieldW + " parentW=" + parent.getWidth());
        }
    }

    private int estimateLeftUnsafeWidth(Activity a, ViewGroup decor) {
        int fallback = (int) dp(a, 96.0f);
        int width = fallback;
        try {
            View content = a.findViewById(R.id.content);
            if (content != null) {
                int[] decorLoc = new int[2];
                int[] contentLoc = new int[2];
                decor.getLocationOnScreen(decorLoc);
                content.getLocationOnScreen(contentLoc);
                width = Math.max(width, (contentLoc[0] - decorLoc[0]) + ((int) dp(a, 12.0f)));
            }
        } catch (Throwable th) {
        }
        int max = decor.getWidth() > 0 ? decor.getWidth() / 4 : (int) dp(a, 260.0f);
        return Math.max((int) dp(a, 48.0f), Math.min(width, max));
    }

    public void removeExitShield() {
        if (this.exitShield != null && (this.exitShield.getParent() instanceof ViewGroup)) {
            ((ViewGroup) this.exitShield.getParent()).removeView(this.exitShield);
        }
        this.exitShield = null;
    }

    private static float dp(Activity a, float v) {
        return a.getResources().getDisplayMetrics().density * v;
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
