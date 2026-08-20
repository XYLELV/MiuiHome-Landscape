package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;

import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.core.ModuleSettings;

import de.robv.android.xposed.XposedBridge;

/**
 * Vili 专用横屏 overlay 根容器。视觉、点击、长按、拖拽和命中测试
 * 统一使用 8×3 输入模型；可见时由本层消费触摸，避免事件穿透到已隐藏的
 * MIUI Workspace。布局分为上方分页 Grid、页码和下方悬浮 Dock：
 *   ┌─────────────────────────────────┐
 *   │ GRID ZONE (0,0,W,H-dockH)       │
 *   ├─────────────────────────────────┤
 *   │ DOCK ZONE (0,H-dockH,W,H)       │
 *   └─────────────────────────────────┘
 *
 * 事件策略：
 *   - dispatchTouchEvent: 打日志 (TAG="[MiuiHomeLandscape/Overlay]")；可见即优先分发给我们的 children
 *   - onInterceptTouchEvent: **默认 false** —— 让 grid/dock 的 cell 自己处理点击/长按
 *   - onTouchEvent: 只要到达这里 (说明子没消费)，**一律 return true**
 *     → 事件不会再穿透回底层 MIUI Workspace
 *   - setClickable(true) + setFocusable(true) + setLongClickable(true)
 *
 * 不变量：
 *   1. grid rect = (0, 0, W, H - dockH)    clipChildren=true
 *   2. dock rect = (0, H - dockH, W, H)    clipChildren=true
 *   3. overlay.visible == View.VISIBLE 时，没有任何 touch 能穿透到 sibling
 */
public class LandscapeOverlayView extends ViewGroup {

    private static final String TAG_EVT = "[MiuiHomeLandscape/Overlay] ";

    public interface OnBlankLongPress { void onBlankLongPress(); }

    public interface EditListener {
        void onDone();
    }

    private final LandscapePagedGridView grid;
    private final PageIndicatorView pageIndicator;
    private final LandscapeDockView dock;
    private OnBlankLongPress blankLongPress;
    private EditListener editListener;
    private boolean editMode;
    private boolean dockEnabled = true;
    private int dockAppearance = ModuleSettings.DOCK_APPEARANCE_AUTO;
    private int dockSize = ModuleSettings.DOCK_SIZE_STANDARD;
    private boolean brightWallpaper = true;

    private final int dockHeightPx;
    private final int indicatorHeightPx;
    private int safeLeft;
    private int safeTop;
    private int safeRight;
    private int safeBottom;

    // 空白长按检测
    private float downX, downY;
    private boolean longFired;
    private final int touchSlop;
    private final long longPressTimeout;
    private final Runnable longPressRunnable = () -> {
        if (!longFired && blankLongPress != null) {
            longFired = true;
            log("blank longpress fired -> enterGlobalEdit");
            blankLongPress.onBlankLongPress();
        }
    };

    public LandscapeOverlayView(Context ctx) {
        super(ctx);

        // MIUI keeps the wallpaper below android.R.id.content.  A very light
        // scrim makes labels readable without replacing the user's wallpaper.
        setBackgroundColor(0x16000000);
        setClickable(true);
        setFocusable(true);
        setLongClickable(true);
        setClipChildren(true);
        setClipToPadding(true);

        this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        this.longPressTimeout = ViewConfiguration.getLongPressTimeout();

        this.dockHeightPx = MiuiStyleResolver.resolveDimenPx(ctx, 86,
                "hotseat_height",
                "hot_seats_height",
                "hotseats_height");
        this.indicatorHeightPx = dp(ctx, 18);
        grid = new LandscapePagedGridView(ctx);
        pageIndicator = new PageIndicatorView(ctx);
        dock = new LandscapeDockView(ctx);
        applyDockStyle(true, ModuleSettings.DOCK_APPEARANCE_AUTO,
                ModuleSettings.DOCK_SIZE_STANDARD, true);
        grid.setPageListener(pageIndicator::setPageInfo);

        setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safe = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            if (safeLeft != safe.left || safeTop != safe.top
                    || safeRight != safe.right || safeBottom != safe.bottom) {
                safeLeft = safe.left;
                safeTop = safe.top;
                safeRight = safe.right;
                safeBottom = safe.bottom;
                requestLayout();
            }
            return insets;
        });

        addView(grid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(pageIndicator, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(dock, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        log("ctor: dockH=" + dockHeightPx
                + " cellModel=" + GridPosition.COLS + "x" + GridPosition.ROWS
                + " slotsPerPage=" + GridPosition.SLOTS_PER_PAGE);
    }

    public LandscapePagedGridView getGrid() { return grid; }
    public PageIndicatorView getPageIndicator() { return pageIndicator; }
    public LandscapeDockView getDock() { return dock; }

    /** Keeps the user's wallpaper visible while optionally adding label contrast. */
    public void setDimWallpaper(boolean enabled) {
        setBackgroundColor(enabled ? 0x16000000 : Color.TRANSPARENT);
    }

    public void setOnBlankLongPressListener(OnBlankLongPress l) { this.blankLongPress = l; }

    public void setEditListener(EditListener listener) { this.editListener = listener; }

    public void setEditMode(boolean enabled) {
        if (editMode == enabled) return;
        editMode = enabled;
        grid.setEditMode(enabled);
        dock.setEditMode(enabled);
        log("edit mode=" + enabled);
    }

    public boolean isEditMode() { return editMode; }

    public void applyDockStyle(
            boolean enabled, int appearance, int size, boolean wallpaperIsBright) {
        int safeAppearance = sanitizeAppearance(appearance);
        int safeSize = sanitizeSize(size);
        boolean layoutChanged = dockEnabled != enabled || dockSize != safeSize;
        if (!layoutChanged && dockAppearance == safeAppearance
                && brightWallpaper == wallpaperIsBright
                && dock.getBackground() != null) return;
        dockEnabled = enabled;
        dockAppearance = safeAppearance;
        dockSize = safeSize;
        brightWallpaper = wallpaperIsBright;
        dock.setVisibility(enabled ? VISIBLE : GONE);

        int resolvedAppearance = safeAppearance == ModuleSettings.DOCK_APPEARANCE_AUTO
                ? (wallpaperIsBright
                        ? ModuleSettings.DOCK_APPEARANCE_REGULAR
                        : ModuleSettings.DOCK_APPEARANCE_CLEAR)
                : safeAppearance;
        boolean clear = resolvedAppearance == ModuleSettings.DOCK_APPEARANCE_CLEAR;
        // Colorless glass: opacity may adapt, but the Dock must never tint the wallpaper.
        int centerAlpha = clear ? 18 : 54;
        int edgeAlpha = clear ? 38 : 82;
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(edgeAlpha, 250, 253, 255),
                        Color.argb(centerAlpha, 255, 255, 255),
                        Color.argb(edgeAlpha, 250, 253, 255)
                });
        background.setCornerRadius(dp(getContext(), safeSize == ModuleSettings.DOCK_SIZE_LARGE
                ? 34 : safeSize == ModuleSettings.DOCK_SIZE_COMPACT ? 24 : 30));
        background.setStroke(dp(getContext(), 1), clear ? 0x70FFFFFF : 0x9AFFFFFF);
        dock.setBackground(background);
        dock.setIconScale(safeSize == ModuleSettings.DOCK_SIZE_COMPACT
                ? 0.78f : safeSize == ModuleSettings.DOCK_SIZE_LARGE ? 1.02f : 0.9f);
        if (layoutChanged) requestLayout();
        dock.invalidate();
        log("dock style enabled=" + enabled + " appearance=" + safeAppearance
                + " resolved=" + resolvedAppearance + " size=" + safeSize
                + " brightWallpaper=" + wallpaperIsBright);
    }

    public static ViewGroup.LayoutParams matchParentLp() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ─── measure / layout: 硬分区 ────────────────────────────────

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int finalW = Math.max(0, MeasureSpec.getSize(widthSpec));
        int finalH = Math.max(0, MeasureSpec.getSize(heightSpec));
        setMeasuredDimension(finalW, finalH);

        int availableW = Math.max(0, finalW - safeLeft - safeRight);
        int availableH = Math.max(0, finalH - safeTop - safeBottom);
        int dH = dockEnabled ? Math.min(resolvedDockHeight(), availableH) : 0;
        int iH = Math.min(indicatorHeightPx, Math.max(0, availableH - dH));
        int gH = Math.max(0, availableH - dH - iH);
        int dockW = Math.max(0, Math.min(availableW - dp(getContext(), 24),
                dp(getContext(), 9 * resolvedDockPitchDp() + 48)));

        int wSpec = MeasureSpec.makeMeasureSpec(availableW, MeasureSpec.EXACTLY);
        int dockWSpec = MeasureSpec.makeMeasureSpec(dockW, MeasureSpec.EXACTLY);
        int gSpec = MeasureSpec.makeMeasureSpec(gH,     MeasureSpec.EXACTLY);
        int iSpec = MeasureSpec.makeMeasureSpec(iH,     MeasureSpec.EXACTLY);
        int dSpec = MeasureSpec.makeMeasureSpec(dH,     MeasureSpec.EXACTLY);

        grid.measure(wSpec, gSpec);
        pageIndicator.measure(wSpec, iSpec);
        dock.measure(dockWSpec, dSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int W = r - l;
        int H = b - t;
        int contentLeft = safeLeft;
        int contentTop = safeTop;
        int availableW = Math.max(0, W - safeLeft - safeRight);
        int availableH = Math.max(0, H - safeTop - safeBottom);
        int dH = dockEnabled ? Math.min(resolvedDockHeight(), availableH) : 0;
        int iH = Math.min(indicatorHeightPx, Math.max(0, availableH - dH));
        int gH = Math.max(0, availableH - dH - iH);
        int dockW = Math.max(0, Math.min(availableW - dp(getContext(), 24),
                dp(getContext(), 9 * resolvedDockPitchDp() + 48)));
        int dockLeft = contentLeft + Math.max(0, (availableW - dockW) / 2);

        if (changed && W > 0 && H > 0) {
            int wSpec = MeasureSpec.makeMeasureSpec(availableW, MeasureSpec.EXACTLY);
            int dockWSpec = MeasureSpec.makeMeasureSpec(dockW, MeasureSpec.EXACTLY);
            int gSpec = MeasureSpec.makeMeasureSpec(gH, MeasureSpec.EXACTLY);
            int iSpec = MeasureSpec.makeMeasureSpec(iH, MeasureSpec.EXACTLY);
            int dSpec = MeasureSpec.makeMeasureSpec(dH, MeasureSpec.EXACTLY);
            grid.measure(wSpec, gSpec);
            pageIndicator.measure(wSpec, iSpec);
            dock.measure(dockWSpec, dSpec);
            log("layout changed overlay=" + W + "x" + H
                    + " safe=" + safeLeft + "," + safeTop + "," + safeRight + "," + safeBottom
                    + " grid=" + availableW + "x" + gH
                    + " dock=" + dockW + "x" + dH);
        }

        grid.layout(contentLeft, contentTop, contentLeft + availableW, contentTop + gH);
        pageIndicator.layout(contentLeft, contentTop + gH,
                contentLeft + availableW, contentTop + gH + iH);
        dock.layout(dockLeft, contentTop + gH + iH,
                dockLeft + dockW, contentTop + gH + iH + dH);
    }

    // ─── events: 彻底不让事件穿透到底层 MIUI ────────────────────

    /**
     * 进入这一层的每一个 root-level MotionEvent 都打印；方便真机 logcat
     * 直接看到 "是我们在处理，不是 MIUI"。只打 DOWN/UP/CANCEL，避免 MOVE 洪水。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int act = ev.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN
                || act == MotionEvent.ACTION_UP
                || act == MotionEvent.ACTION_CANCEL) {
            log("[touch] dispatch act=" + actName(act)
                    + " x=" + (int) ev.getX() + " y=" + (int) ev.getY()
                    + " visible=" + (getVisibility() == VISIBLE));
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 默认不拦截：让 grid/dock 的实槽自己处理 click/longClick/drag-start。 */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    /**
     * 到这里 = 子 view 没消费的事件 (空白处) —— 我们自己起长按计时，
     * 并且**一律返回 true**，不再放事件回去给底层 MIUI Workspace。
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                longFired = false;
                postDelayed(longPressRunnable, longPressTimeout);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > touchSlop
                        || Math.abs(ev.getY() - downY) > touchSlop) {
                    removeCallbacks(longPressRunnable);
                }
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                removeCallbacks(longPressRunnable);
                if (editMode && !longFired && editListener != null) {
                    editListener.onDone();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                return true;
        }
        // 其他类型的事件 (POINTER_DOWN etc.) 也吞掉，别再漏给 MIUI
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        if (event.getClipDescription() == null
                || !LandscapePagedGridView.DRAG_LABEL.contentEquals(
                        event.getClipDescription().getLabel())) {
            return false;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DRAG_EXITED:
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                LandscapePagedGridView.finishActiveDragAnimation();
                return true;
            case DragEvent.ACTION_DROP:
                if (event.getY() < dock.getTop() || event.getY() > dock.getBottom()) {
                    return true;
                }
                ClipData data = event.getClipData();
                CharSequence desc = data != null && data.getItemCount() > 0
                        ? data.getItemAt(0).getText() : null;
                log("[drag] dock-zone drop y=" + Math.round(event.getY())
                        + " gridBottom=" + grid.getBottom()
                        + " from=" + desc);
                return dock.handleDropAtX(event.getX() - dock.getLeft(), event);
            default:
                return true;
        }
    }

    private static String actName(int a) {
        switch (a) {
            case MotionEvent.ACTION_DOWN:   return "DOWN";
            case MotionEvent.ACTION_MOVE:   return "MOVE";
            case MotionEvent.ACTION_UP:     return "UP";
            case MotionEvent.ACTION_CANCEL: return "CANCEL";
            default: return "act#" + a;
        }
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private int resolvedDockHeight() {
        float scale = dockSize == ModuleSettings.DOCK_SIZE_COMPACT
                ? 0.82f : dockSize == ModuleSettings.DOCK_SIZE_LARGE ? 1.16f : 1f;
        return Math.max(dp(getContext(), 60), Math.round(dockHeightPx * scale));
    }

    private int resolvedDockPitchDp() {
        return dockSize == ModuleSettings.DOCK_SIZE_COMPACT
                ? 62 : dockSize == ModuleSettings.DOCK_SIZE_LARGE ? 80 : 72;
    }

    private static int sanitizeAppearance(int value) {
        return value == ModuleSettings.DOCK_APPEARANCE_REGULAR
                || value == ModuleSettings.DOCK_APPEARANCE_CLEAR
                ? value : ModuleSettings.DOCK_APPEARANCE_AUTO;
    }

    private static int sanitizeSize(int value) {
        return value == ModuleSettings.DOCK_SIZE_COMPACT
                || value == ModuleSettings.DOCK_SIZE_LARGE
                ? value : ModuleSettings.DOCK_SIZE_STANDARD;
    }

    public static class PageIndicatorView extends ViewGroup {
        private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int currentPage;
        private int pageCount = 1;

        public PageIndicatorView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            setClickable(false);
            setBackgroundColor(Color.TRANSPARENT);
            activePaint.setColor(0xFFFFFFFF);
            inactivePaint.setColor(0x66FFFFFF);
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
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                    MeasureSpec.getSize(heightSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            // Drawing-only view.
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (pageCount <= 1 || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            float radius = Math.max(3f, getHeight() * 0.16f);
            float gap = radius * 3.2f;
            float totalW = (pageCount - 1) * gap + radius * 2f;
            float startX = (getWidth() - totalW) / 2f + radius;
            float cy = getHeight() / 2f;
            for (int i = 0; i < pageCount; i++) {
                canvas.drawCircle(startX + i * gap, cy, radius,
                        i == currentPage ? activePaint : inactivePaint);
            }
        }
    }

    private static void log(String m) { XposedBridge.log(TAG_EVT + m); }
}
