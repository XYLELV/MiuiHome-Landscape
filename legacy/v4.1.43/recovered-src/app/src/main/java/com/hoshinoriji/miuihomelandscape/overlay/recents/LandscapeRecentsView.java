package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.R;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.hoshinoriji.miuihomelandscape.overlay.recents.LandscapeRecentsCardView;
import com.hoshinoriji.miuihomelandscape.overlay.recents.LandscapeRecentsModel;
import de.robv.android.xposed.XposedBridge;
import java.util.ArrayList;
import java.util.List;

public class LandscapeRecentsView extends FrameLayout {
    private static final String TAG = "[MiuiHomeLandscape/RecentsView] ";
    private float bgDownX;
    private float bgDownY;
    private boolean bottomExitArmed;
    private final ImageButton clearAllButton;
    private final ArrayList<LandscapeRecentsModel.TaskRef> currentTasks;
    private final TextView emptyHint;
    private final HorizontalScrollView hsv;
    private Listener listener;
    private final LinearLayout row;

    public interface Listener {
        void onBackgroundTapped();

        void onClearAllRequested();

        void onTaskDismissRequested(LandscapeRecentsModel.TaskRef taskRef, View view);

        void onTaskTap(LandscapeRecentsModel.TaskRef taskRef);
    }

    public LandscapeRecentsView(Context ctx) {
        super(ctx);
        this.currentTasks = new ArrayList<>();
        setBackgroundColor(-15724524);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setFitsSystemWindows(false);
        setElevation(dp(ctx, 4096.0f));
        setTranslationZ(dp(ctx, 4096.0f));
        this.hsv = new HorizontalScrollView(ctx);
        this.hsv.setLayoutDirection(1);
        this.hsv.setHorizontalScrollBarEnabled(false);
        this.hsv.setSmoothScrollingEnabled(false);
        this.hsv.setOverScrollMode(2);
        this.hsv.setClipChildren(false);
        this.hsv.setClipToPadding(false);
        ViewGroup.LayoutParams hsvLp = new FrameLayout.LayoutParams(-1, -1);
        addView(this.hsv, hsvLp);
        this.row = new LinearLayout(ctx);
        this.row.setLayoutDirection(1);
        this.row.setOrientation(0);
        this.row.setGravity(48);
        this.row.setClipChildren(false);
        this.row.setClipToPadding(false);
        this.hsv.addView(this.row, new FrameLayout.LayoutParams(-2, -1));
        this.emptyHint = new TextView(ctx);
        this.emptyHint.setText("无最近任务");
        this.emptyHint.setTextColor(-5592406);
        this.emptyHint.setVisibility(8);
        ViewGroup.LayoutParams hintLp = new FrameLayout.LayoutParams(-2, -2, 17);
        addView(this.emptyHint, hintLp);
        this.clearAllButton = new ImageButton(ctx);
        this.clearAllButton.setImageResource(R.drawable.ic_menu_close_clear_cancel);
        this.clearAllButton.setBackground(circleBg());
        int sz = (int) dp(ctx, 48.0f);
        FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(sz, sz, 81);
        clearLp.bottomMargin = (int) dp(ctx, 24.0f);
        addView(this.clearAllButton, clearLp);
        this.clearAllButton.setVisibility(8);
        this.clearAllButton.setEnabled(false);
        this.clearAllButton.setClickable(false);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == 0) {
            this.bgDownX = ev.getX();
            this.bgDownY = ev.getY();
            this.bottomExitArmed = isBottomExitStart(ev.getX(), ev.getY());
            if (this.bottomExitArmed) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        } else if (action == 2) {
            if (this.bottomExitArmed) {
                if (isBottomExitSwipe(ev.getX(), ev.getY())) {
                    this.bottomExitArmed = false;
                    if (this.listener != null) {
                        this.listener.onBackgroundTapped();
                    }
                    log("bottom exit swipe move dx=" + Math.round(ev.getX() - this.bgDownX) + " dy=" + Math.round(ev.getY() - this.bgDownY));
                }
                return true;
            }
        } else if (action == 1) {
            float dx = Math.abs(ev.getX() - this.bgDownX);
            float dy = Math.abs(ev.getY() - this.bgDownY);
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (this.bottomExitArmed) {
                this.bottomExitArmed = false;
                if (isBottomExitSwipe(ev.getX(), ev.getY())) {
                    if (this.listener != null) {
                        this.listener.onBackgroundTapped();
                    }
                    log("bottom exit swipe up dx=" + Math.round(ev.getX() - this.bgDownX) + " dy=" + Math.round(ev.getY() - this.bgDownY));
                    return true;
                }
            }
            if (dx <= slop && dy <= slop && isBackgroundTap(ev.getX(), ev.getY())) {
                if (this.listener != null) {
                    this.listener.onBackgroundTapped();
                }
                return true;
            }
        } else if (action == 3) {
            this.bottomExitArmed = false;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isBottomExitStart(float x, float y) {
        int h = getHeight();
        if (h <= 0) {
            return false;
        }
        float lane = dp(getContext(), 88.0f);
        return y >= ((float) h) - lane;
    }

    private boolean isBottomExitSwipe(float x, float y) {
        float dx = Math.abs(x - this.bgDownX);
        float dy = y - this.bgDownY;
        float up = -dy;
        float minUp = Math.max(dp(getContext(), 42.0f), ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2.0f);
        return up >= minUp && up >= 0.55f * dx;
    }

    private boolean isBackgroundTap(float x, float y) {
        if (isPointInsideView(this.clearAllButton, x, y)) {
            return false;
        }
        ArrayList<LandscapeRecentsCardView> cards = new ArrayList<>();
        collectCards(this.row, cards);
        for (LandscapeRecentsCardView card : cards) {
            if (isPointInsideView(card, x, y)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPointInsideView(View child, float rootX, float rootY) {
        if (child == null || child.getVisibility() != 0) {
            return false;
        }
        int[] childLoc = new int[2];
        int[] rootLoc = new int[2];
        child.getLocationOnScreen(childLoc);
        getLocationOnScreen(rootLoc);
        float left = childLoc[0] - rootLoc[0];
        float top = childLoc[1] - rootLoc[1];
        if (rootX < left || rootX > child.getWidth() + left || rootY < top || rootY > child.getHeight() + top) {
            return false;
        }
        return true;
    }

    public void bindTasks(List<LandscapeRecentsModel.TaskRef> tasks) {
        this.currentTasks.clear();
        if (tasks != null) {
            this.currentTasks.addAll(tasks);
        }
        rebuildLayout(true);
    }

    public void playEnterAnimation() {
        this.row.animate().cancel();
        this.clearAllButton.animate().cancel();
        this.row.setAlpha(0.0f);
        this.row.setTranslationY(dp(getContext(), 18.0f));
        this.clearAllButton.setAlpha(0.0f);
        this.clearAllButton.setTranslationY(dp(getContext(), 10.0f));
        this.row.animate().alpha(1.0f).translationY(0.0f).setDuration(260L).setInterpolator(new DecelerateInterpolator(1.6f)).withLayer().start();
        this.clearAllButton.animate().alpha(1.0f).translationY(0.0f).setStartDelay(80L).setDuration(220L).setInterpolator(new DecelerateInterpolator(1.5f)).withLayer().start();
    }

    public void rebuildLayout(boolean scrollToRight) {
        int topPadding;
        int bottomReserved;
        int topPadding2;
        this.row.removeAllViews();
        if (this.currentTasks.isEmpty()) {
            this.emptyHint.setVisibility(0);
            return;
        }
        this.emptyHint.setVisibility(8);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenH = Math.min(dm.widthPixels, dm.heightPixels);
        int screenW = Math.max(dm.widthPixels, dm.heightPixels);
        int verticalGap = (int) dp(getContext(), 16.0f);
        int columns = (int) dp(getContext(), 30.0f);
        int bottomReserved2 = (int) dp(getContext(), 118.0f);
        int availableH = Math.max((int) dp(getContext(), 260.0f), ((screenH - columns) - bottomReserved2) - verticalGap);
        int cardH = Math.max((int) dp(getContext(), 110.0f), Math.min(availableH / 2, (int) dp(getContext(), 170.0f)));
        int cardW = Math.max((int) dp(getContext(), 260.0f), Math.min((int) (screenW * 0.28f), (int) dp(getContext(), 430.0f)));
        int sidePadding = Math.max((int) dp(getContext(), 32.0f), (screenW - (cardW * 2)) / 5);
        int columnGap = (int) dp(getContext(), 24.0f);
        this.row.setPadding(sidePadding, columns, sidePadding, bottomReserved2);
        Context ctx = getContext();
        int i = 1;
        int columns2 = (this.currentTasks.size() + 1) / 2;
        boolean firstColumnAdded = false;
        int visualIndex = 0;
        while (visualIndex < columns2) {
            DisplayMetrics dm2 = dm;
            int newestIndex = (this.currentTasks.size() - i) - (visualIndex * 2);
            LinearLayout column = new LinearLayout(ctx);
            int screenH2 = screenH;
            column.setLayoutDirection(1);
            column.setOrientation(1);
            column.setGravity(48);
            column.setClipChildren(false);
            column.setClipToPadding(false);
            int screenW2 = screenW;
            LinearLayout.LayoutParams columnLp = new LinearLayout.LayoutParams(cardW, -2);
            if (firstColumnAdded) {
                columnLp.leftMargin = columnGap;
            }
            this.row.addView(column, columnLp);
            int r = 0;
            while (true) {
                LinearLayout.LayoutParams columnLp2 = columnLp;
                if (r >= 2) {
                    topPadding = columns;
                    bottomReserved = bottomReserved2;
                    topPadding2 = columns2;
                    break;
                }
                int taskIndex = newestIndex - r;
                if (taskIndex < 0) {
                    topPadding = columns;
                    bottomReserved = bottomReserved2;
                    topPadding2 = columns2;
                    break;
                }
                int newestIndex2 = newestIndex;
                if (taskIndex >= this.currentTasks.size()) {
                    topPadding = columns;
                    bottomReserved = bottomReserved2;
                    topPadding2 = columns2;
                    break;
                }
                LandscapeRecentsModel.TaskRef ref = this.currentTasks.get(taskIndex);
                int topPadding3 = columns;
                int topPadding4 = columns2;
                int bottomReserved3 = bottomReserved2;
                LinearLayout column2 = column;
                Context ctx2 = ctx;
                LandscapeRecentsCardView card = new LandscapeRecentsCardView(ctx, ref, cardW, cardH, new LandscapeRecentsCardView.Callback() {
                    @Override
                    public void onCardTapped(LandscapeRecentsCardView c) {
                        if (LandscapeRecentsView.this.listener != null) {
                            LandscapeRecentsView.this.listener.onTaskTap(c.getTaskRef());
                        }
                    }

                    @Override
                    public void onCardDismissed(LandscapeRecentsCardView c) {
                        ViewGroup parent = (ViewGroup) c.getParent();
                        if (parent != null) {
                            parent.removeView(c);
                        }
                        if (LandscapeRecentsView.this.listener != null) {
                            LandscapeRecentsView.this.listener.onTaskDismissRequested(c.getTaskRef(), c);
                        }
                        LandscapeRecentsView.this.currentTasks.remove(c.getTaskRef());
                        LandscapeRecentsView.this.rebuildLayout(false);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
                if (r > 0) {
                    lp.topMargin = verticalGap;
                }
                column2.addView(card, lp);
                r++;
                columns2 = topPadding4;
                column = column2;
                columnLp = columnLp2;
                newestIndex = newestIndex2;
                columns = topPadding3;
                bottomReserved2 = bottomReserved3;
                ctx = ctx2;
            }
            visualIndex++;
            columns2 = topPadding2;
            dm = dm2;
            screenH = screenH2;
            screenW = screenW2;
            firstColumnAdded = true;
            columns = topPadding;
            bottomReserved2 = bottomReserved;
            i = 1;
            ctx = ctx;
        }
        int topPadding5 = columns2;
        if (scrollToRight) {
            post(new Runnable() {
                @Override
                public final void run() {
                    this.f$0.m103xa827331a();
                }
            });
        }
        log("rebuildLayout n=" + this.currentTasks.size() + " rows=2 columns=" + topPadding5 + " cardW=" + cardW + " cardH=" + cardH);
    }

    void m103xa827331a() {
        this.hsv.fullScroll(66);
    }

    public void clearAll() {
        this.currentTasks.clear();
        this.row.removeAllViews();
        this.emptyHint.setVisibility(0);
    }

    private void pruneEmptyColumns() {
        for (int i = this.row.getChildCount() - 1; i >= 0; i--) {
            View v = this.row.getChildAt(i);
            if ((v instanceof ViewGroup) && ((ViewGroup) v).getChildCount() == 0) {
                this.row.removeViewAt(i);
            }
        }
        if (this.row.getChildCount() == 0) {
            this.emptyHint.setVisibility(0);
        }
    }

    private void collectCards(View v, ArrayList<LandscapeRecentsCardView> out) {
        if (v instanceof LandscapeRecentsCardView) {
            out.add((LandscapeRecentsCardView) v);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                collectCards(vg.getChildAt(i), out);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == 0) {
            this.bgDownX = ev.getX();
            this.bgDownY = ev.getY();
            this.bottomExitArmed = isBottomExitStart(ev.getX(), ev.getY());
            return true;
        }
        if (ev.getActionMasked() == 2 && this.bottomExitArmed) {
            if (isBottomExitSwipe(ev.getX(), ev.getY())) {
                this.bottomExitArmed = false;
                if (this.listener != null) {
                    this.listener.onBackgroundTapped();
                }
                log("bottom exit swipe touch dx=" + Math.round(ev.getX() - this.bgDownX) + " dy=" + Math.round(ev.getY() - this.bgDownY));
            }
            return true;
        }
        if (ev.getActionMasked() == 1) {
            float dx = Math.abs(ev.getX() - this.bgDownX);
            float dy = Math.abs(ev.getY() - this.bgDownY);
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (this.bottomExitArmed) {
                this.bottomExitArmed = false;
                if (isBottomExitSwipe(ev.getX(), ev.getY())) {
                    if (this.listener != null) {
                        this.listener.onBackgroundTapped();
                    }
                    log("bottom exit swipe touch-up dx=" + Math.round(ev.getX() - this.bgDownX) + " dy=" + Math.round(ev.getY() - this.bgDownY));
                    return true;
                }
            } else if (dx <= slop && dy <= slop && isBackgroundTap(ev.getX(), ev.getY()) && this.listener != null) {
                this.listener.onBackgroundTapped();
            }
        } else if (ev.getActionMasked() == 3) {
            this.bottomExitArmed = false;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == 4) {
            if (event.getAction() == 1 && this.listener != null) {
                this.listener.onBackgroundTapped();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private Drawable circleBg() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(1);
        g.setColor(-1);
        return g;
    }

    private static float dp(Context ctx, float v) {
        return ctx.getResources().getDisplayMetrics().density * v;
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
