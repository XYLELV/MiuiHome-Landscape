package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.overlay.LandscapePagedGridView;
import de.robv.android.xposed.XposedBridge;
import java.util.Objects;

public class LandscapeOverlayView extends ViewGroup {
    private static final String TAG_EVT = "[MiuiHomeLandscape/Overlay] ";
    private OnBlankLongPress blankLongPress;
    private final LandscapeDockView dock;
    private final int dockHeightPx;
    private float downX;
    private float downY;
    private final LandscapePagedGridView grid;
    private final int indicatorHeightPx;
    private boolean longFired;
    private final Runnable longPressRunnable;
    private final long longPressTimeout;
    private final PageIndicatorView pageIndicator;
    private final int touchSlop;

    public interface OnBlankLongPress {
        void onBlankLongPress();
    }

    void m79x9701c8e2() {
        if (!this.longFired && this.blankLongPress != null) {
            this.longFired = true;
            log("blank longpress fired → openAppPicker");
            this.blankLongPress.onBlankLongPress();
        }
    }

    public LandscapeOverlayView(Context ctx) {
        super(ctx);
        this.longPressRunnable = new Runnable() {
            @Override
            public final void run() {
                this.f$0.m79x9701c8e2();
            }
        };
        setBackgroundColor(0);
        setClickable(true);
        setFocusable(true);
        setLongClickable(true);
        setClipChildren(true);
        setClipToPadding(true);
        this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        this.longPressTimeout = ViewConfiguration.getLongPressTimeout();
        this.dockHeightPx = MiuiStyleResolver.resolveDimenPx(ctx, 72, "hotseat_height", "hot_seats_height", "hotseats_height");
        this.indicatorHeightPx = dp(ctx, 18);
        this.grid = new LandscapePagedGridView(ctx);
        this.pageIndicator = new PageIndicatorView(ctx);
        this.dock = new LandscapeDockView(ctx);
        LandscapePagedGridView landscapePagedGridView = this.grid;
        final PageIndicatorView pageIndicatorView = this.pageIndicator;
        Objects.requireNonNull(pageIndicatorView);
        landscapePagedGridView.setPageListener(new LandscapePagedGridView.PageListener() {
            @Override
            public final void onPageChanged(int i, int i2) {
                pageIndicatorView.setPageInfo(i, i2);
            }
        });
        addView(this.grid, new ViewGroup.LayoutParams(-1, -1));
        addView(this.pageIndicator, new ViewGroup.LayoutParams(-1, -2));
        addView(this.dock, new ViewGroup.LayoutParams(-1, -2));
        log("ctor: dockH=" + this.dockHeightPx + " cellModel=8x3 slotsPerPage=24");
    }

    public LandscapePagedGridView getGrid() {
        return this.grid;
    }

    public PageIndicatorView getPageIndicator() {
        return this.pageIndicator;
    }

    public LandscapeDockView getDock() {
        return this.dock;
    }

    public void setOnBlankLongPressListener(OnBlankLongPress l) {
        this.blankLongPress = l;
    }

    public static ViewGroup.LayoutParams matchParentLp() {
        return new ViewGroup.LayoutParams(-1, -1);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int wantW = View.MeasureSpec.getSize(widthSpec);
        int wantH = View.MeasureSpec.getSize(heightSpec);
        int orient = getResources().getConfiguration().orientation;
        if (orient == 2) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int bigger = Math.max(dm.widthPixels, dm.heightPixels);
            int smaller = Math.min(dm.widthPixels, dm.heightPixels);
            if (wantW < bigger * 0.98f) {
                wantW = bigger;
            }
            if (wantH < smaller * 0.9f) {
                wantH = smaller;
            }
        }
        int finalW = Math.max(0, wantW);
        int finalH = Math.max(0, wantH);
        setMeasuredDimension(finalW, finalH);
        int dH = Math.min(this.dockHeightPx, finalH);
        int iH = Math.min(this.indicatorHeightPx, Math.max(0, finalH - dH));
        int gH = Math.max(0, (finalH - dH) - iH);
        int wSpec = View.MeasureSpec.makeMeasureSpec(finalW, 1073741824);
        int gSpec = View.MeasureSpec.makeMeasureSpec(gH, 1073741824);
        int iSpec = View.MeasureSpec.makeMeasureSpec(iH, 1073741824);
        int dSpec = View.MeasureSpec.makeMeasureSpec(dH, 1073741824);
        this.grid.measure(wSpec, gSpec);
        this.pageIndicator.measure(wSpec, iSpec);
        this.dock.measure(wSpec, dSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int W = r - l;
        int H = b - t;
        int dH = Math.min(this.dockHeightPx, H);
        int iH = Math.min(this.indicatorHeightPx, Math.max(0, H - dH));
        int gH = Math.max(0, (H - dH) - iH);
        if (changed && W > 0 && H > 0) {
            int wSpec = View.MeasureSpec.makeMeasureSpec(W, 1073741824);
            int gSpec = View.MeasureSpec.makeMeasureSpec(gH, 1073741824);
            int iSpec = View.MeasureSpec.makeMeasureSpec(iH, 1073741824);
            int dSpec = View.MeasureSpec.makeMeasureSpec(dH, 1073741824);
            this.grid.measure(wSpec, gSpec);
            this.pageIndicator.measure(wSpec, iSpec);
            this.dock.measure(wSpec, dSpec);
            log("layout changed overlay=" + W + "x" + H + " gridRect=(0,0," + W + "," + gH + ") indicatorRect=(0," + gH + "," + W + "," + (gH + iH) + ") dockRect=(0," + (gH + iH) + "," + W + "," + H + ")");
        }
        this.grid.layout(0, 0, W, gH);
        this.pageIndicator.layout(0, gH, W, gH + iH);
        this.dock.layout(0, gH + iH, W, H);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int act = ev.getActionMasked();
        if (act == 0 || act == 1 || act == 3) {
            log("[touch] dispatch act=" + actName(act) + " x=" + ((int) ev.getX()) + " y=" + ((int) ev.getY()) + " visible=" + (getVisibility() == 0));
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case 0:
                this.downX = ev.getX();
                this.downY = ev.getY();
                this.longFired = false;
                postDelayed(this.longPressRunnable, this.longPressTimeout);
                break;
            case 1:
            case GridPosition.ROWS:
                removeCallbacks(this.longPressRunnable);
                break;
            case 2:
                if (Math.abs(ev.getX() - this.downX) > this.touchSlop || Math.abs(ev.getY() - this.downY) > this.touchSlop) {
                    removeCallbacks(this.longPressRunnable);
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        if (event.getClipDescription() == null || !LandscapePagedGridView.DRAG_LABEL.contentEquals(event.getClipDescription().getLabel())) {
            return false;
        }
        switch (event.getAction()) {
            case GridPosition.ROWS:
                if (event.getY() >= this.grid.getBottom()) {
                    ClipData data = event.getClipData();
                    CharSequence desc = (data == null || data.getItemCount() <= 0) ? null : data.getItemAt(0).getText();
                    log("[drag] dock-zone drop y=" + Math.round(event.getY()) + " gridBottom=" + this.grid.getBottom() + " from=" + ((Object) desc));
                    break;
                }
                break;
            case 4:
                LandscapePagedGridView.finishActiveDragAnimation();
                break;
        }
        return true;
    }

    private static String actName(int a) {
        switch (a) {
            case 0:
                return "DOWN";
            case 1:
                return "UP";
            case 2:
                return "MOVE";
            case GridPosition.ROWS:
                return "CANCEL";
            default:
                return "act#" + a;
        }
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    public static class PageIndicatorView extends ViewGroup {
        private final Paint activePaint;
        private int currentPage;
        private final Paint inactivePaint;
        private int pageCount;

        public PageIndicatorView(Context ctx) {
            super(ctx);
            this.activePaint = new Paint(1);
            this.inactivePaint = new Paint(1);
            this.pageCount = 1;
            setWillNotDraw(false);
            setClickable(false);
            setBackgroundColor(0);
            this.activePaint.setColor(-1);
            this.inactivePaint.setColor(1728053247);
        }

        public void setPageInfo(int currentPage, int pageCount) {
            int count = Math.max(1, pageCount);
            int current = Math.max(0, Math.min(count - 1, currentPage));
            if (this.currentPage == current && this.pageCount == count) {
                return;
            }
            this.currentPage = current;
            this.pageCount = count;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthSpec), View.MeasureSpec.getSize(heightSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (this.pageCount <= 1 || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            float radius = Math.max(3.0f, getHeight() * 0.16f);
            float gap = 3.2f * radius;
            float totalW = ((this.pageCount - 1) * gap) + (radius * 2.0f);
            float startX = ((getWidth() - totalW) / 2.0f) + radius;
            float cy = getHeight() / 2.0f;
            int i = 0;
            while (i < this.pageCount) {
                canvas.drawCircle((i * gap) + startX, cy, radius, i == this.currentPage ? this.activePaint : this.inactivePaint);
                i++;
            }
        }
    }

    private static void log(String m) {
        XposedBridge.log(TAG_EVT + m);
    }
}
