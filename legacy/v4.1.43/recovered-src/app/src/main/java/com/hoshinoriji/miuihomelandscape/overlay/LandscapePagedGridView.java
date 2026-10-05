package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.util.List;

public class LandscapePagedGridView extends ViewGroup {
    private static final long BLANK_GLOBAL_EDIT_DELAY_MS = 2000;
    private static final long DRAG_ANIM_MS = 140;
    public static final String DRAG_LABEL = "miuihome-landscape-drag";
    private static final float DRAG_SOURCE_ALPHA = 0.38f;
    private static final float DRAG_SOURCE_SCALE = 0.92f;
    private static final float DROP_HOVER_SCALE = 1.055f;
    private static final long EDGE_SCROLL_DWELL_MS = 580;
    private static final long EDGE_SCROLL_REPEAT_MS = 620;
    private static final int EDGE_SCROLL_ZONE_DP = 70;
    private static final int PAGE_BOTTOM_INSET_DP = 8;
    private static final int PAGE_PAD_H_DP = 12;
    private static final int PAGE_TOP_INSET_DP = 34;
    private static final int SNAP_DURATION_MS = 260;
    private static final String TAG = "[MiuiHomeLandscape/Pager] ";
    private static WeakReference<View> activeDragSource = new WeakReference<>(null);
    private static String currentDragDescriptor;
    private String activeDragDescriptor;
    private int activeEdgeDirection;
    private final Runnable blankGlobalEditRunnable;
    private Hit downHit;
    private float downX;
    private float downY;
    private final Runnable edgeScrollRunnable;
    private boolean edgeScrollScheduled;
    private GridPosition focusedEditPos;
    private boolean globalEditMode;
    private GridPosition hoverDropPos;
    private View hoverDropView;
    private float lastX;
    private Listener listener;
    private boolean longPressFired;
    private final Runnable longPressRunnable;
    private LandscapeItem[] matrix;
    private final int maxFling;
    private final int minFling;
    private int pageCount;
    private PageListener pageListener;
    private boolean paging;
    private AppRenderer renderer;
    private final Scroller scroller;
    private final int touchSlop;
    private VelocityTracker velocityTracker;

    public interface Listener {
        void onAppClick(LandscapeItem landscapeItem);

        void onAppLongPress(LandscapeItem landscapeItem, GridPosition gridPosition, View view);

        void onAppRemoveRequest(LandscapeItem landscapeItem, GridPosition gridPosition);

        void onDragHover(String str, GridPosition gridPosition, int i, int i2);

        void onDropOnGrid(String str, GridPosition gridPosition);

        void onEmptySlotLongPress();

        void onInsertOnGrid(String str, GridPosition gridPosition);
    }

    public interface PageListener {
        void onPageChanged(int i, int i2);
    }

    public LandscapePagedGridView(Context ctx) {
        super(ctx);
        this.longPressRunnable = new Runnable() {
            @Override
            public final void run() {
                this.f$0.fireLongPress();
            }
        };
        this.blankGlobalEditRunnable = new Runnable() {
            @Override
            public final void run() {
                this.f$0.fireBlankGlobalEdit();
            }
        };
        this.matrix = new LandscapeItem[24];
        this.pageCount = 1;
        this.edgeScrollRunnable = new Runnable() {
            @Override
            public void run() {
                LandscapePagedGridView.this.edgeScrollScheduled = false;
                int direction = LandscapePagedGridView.this.activeEdgeDirection;
                if (direction == 0) {
                    return;
                }
                int width = LandscapePagedGridView.this.getWidth();
                if (width <= 0 || LandscapePagedGridView.this.pageCount <= 1) {
                    LandscapePagedGridView.this.cancelEdgeScroll();
                    return;
                }
                int current = Math.max(0, Math.min(LandscapePagedGridView.this.pageCount - 1, Math.round(LandscapePagedGridView.this.getScrollX() / width)));
                int target = Math.max(0, Math.min(LandscapePagedGridView.this.pageCount - 1, current + direction));
                if (target == current) {
                    LandscapePagedGridView.this.cancelEdgeScroll();
                    return;
                }
                LandscapePagedGridView.log("[page] edge-auto dir=" + direction + " fromPage=" + current + " toPage=" + target + " pageWidth=" + width);
                LandscapePagedGridView.this.smoothToPage(target);
                LandscapePagedGridView.this.scheduleEdgeScroll(LandscapePagedGridView.EDGE_SCROLL_REPEAT_MS);
            }
        };
        setClickable(true);
        setFocusable(true);
        setLongClickable(true);
        setClipChildren(true);
        setClipToPadding(true);
        setWillNotDraw(false);
        this.scroller = new Scroller(ctx);
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        this.touchSlop = vc.getScaledTouchSlop();
        this.minFling = vc.getScaledMinimumFlingVelocity();
        this.maxFling = vc.getScaledMaximumFlingVelocity();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPageListener(PageListener listener) {
        this.pageListener = listener;
        notifyPageChanged();
    }

    public int getPageCount() {
        return this.pageCount;
    }

    public void setEditMode(boolean enabled) {
        if (enabled) {
            setGlobalEditMode(true);
        } else {
            clearEditMode();
        }
    }

    public boolean isGlobalEditMode() {
        return this.globalEditMode;
    }

    private void setSingleEditMode(GridPosition pos) {
        if (pos == null) {
            return;
        }
        if (!this.globalEditMode && samePosition(this.focusedEditPos, pos)) {
            return;
        }
        this.globalEditMode = false;
        this.focusedEditPos = pos;
        rebuildPageViews();
        requestLayout();
        invalidate();
        log("[edit] mode=single pos=" + pos);
    }

    private void setGlobalEditMode(boolean enabled) {
        if (this.globalEditMode == enabled && this.focusedEditPos == null) {
            return;
        }
        this.globalEditMode = enabled;
        this.focusedEditPos = null;
        rebuildPageViews();
        requestLayout();
        invalidate();
        log("[edit] mode=" + (this.globalEditMode ? "global" : "off"));
    }

    private void clearEditMode() {
        if (!this.globalEditMode && this.focusedEditPos == null) {
            return;
        }
        this.globalEditMode = false;
        this.focusedEditPos = null;
        rebuildPageViews();
        requestLayout();
        invalidate();
        log("[edit] mode=false");
    }

    private boolean isEditing() {
        return this.globalEditMode || this.focusedEditPos != null;
    }

    public void bind(List<LandscapeItem> gridItems, AppRenderer renderer) {
        int abs;
        int abs2;
        this.renderer = renderer;
        int maxAbs = -1;
        if (gridItems != null) {
            for (LandscapeItem item : gridItems) {
                if (item != null && item.kind == LandscapeItem.Kind.GRID && item.pageIndex >= 0 && GridPosition.isValidSlotIndex(item.slotIndex) && (abs2 = GridPosition.toAbsoluteIndex(item.pageIndex, item.slotIndex)) > maxAbs) {
                    maxAbs = abs2;
                }
            }
        }
        this.pageCount = Math.max(1, maxAbs < 0 ? 1 : (maxAbs / 24) + 1);
        this.matrix = new LandscapeItem[this.pageCount * 24];
        if (gridItems != null) {
            for (LandscapeItem item2 : gridItems) {
                if (item2 != null && item2.kind == LandscapeItem.Kind.GRID && item2.pageIndex >= 0 && GridPosition.isValidSlotIndex(item2.slotIndex) && (abs = GridPosition.toAbsoluteIndex(item2.pageIndex, item2.slotIndex)) >= 0 && abs < this.matrix.length) {
                    this.matrix[abs] = item2;
                }
            }
        }
        if (this.focusedEditPos != null && (this.focusedEditPos.pageIndex >= this.pageCount || itemAt(this.focusedEditPos.pageIndex, this.focusedEditPos.slotIndex) == null)) {
            this.focusedEditPos = null;
        }
        rebuildPageViews();
        requestLayout();
        post(new Runnable() {
            @Override
            public final void run() {
                this.f$0.m91x6ba57956();
            }
        });
    }

    void m91x6ba57956() {
        logModelSnapshot();
        notifyPageChanged();
    }

    private void animateVisibleCellsAfterBind() {
        int page = currentPage();
        if (page < 0 || page >= getChildCount()) {
            return;
        }
        View pageView = getChildAt(page);
        if (pageView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) pageView;
            int count = Math.min(group.getChildCount(), 24);
            for (int slot = 0; slot < count; slot++) {
                if (itemAt(page, slot) != null) {
                    View child = group.getChildAt(slot);
                    child.animate().cancel();
                    child.setAlpha(0.72f);
                    child.setScaleX(0.965f);
                    child.setScaleY(0.965f);
                    child.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setStartDelay(Math.min(90L, ((long) slot) * 6)).setDuration(150L).setInterpolator(new DecelerateInterpolator()).start();
                }
            }
        }
    }

    private void rebuildPageViews() {
        clearDropHover();
        removeAllViews();
        Context ctx = getContext();
        for (int page = 0; page < this.pageCount; page++) {
            PageView pageView = new PageView(ctx);
            pageView.setPageIndex(page);
            for (int slot = 0; slot < 24; slot++) {
                pageView.addView(buildVisualCell(ctx, itemAt(page, slot), page, slot));
            }
            addView(pageView);
        }
    }

    private View buildVisualCell(Context ctx, LandscapeItem item, int page, int slot) {
        if (item == null) {
            View empty = new View(ctx);
            empty.setClickable(false);
            empty.setLongClickable(false);
            empty.setFocusable(false);
            return empty;
        }
        if (item.isFolder()) {
            return new IconCellView(ctx, buildFolderCell(ctx, item), false);
        }
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(1);
        cell.setGravity(17);
        cell.setClickable(false);
        cell.setLongClickable(false);
        cell.setFocusable(false);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable drawable = this.renderer == null ? null : this.renderer.getIcon(item.key);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        }
        int iconSize = MiuiStyleResolver.resolveDimenPx(ctx, 52, "app_icon_size", "config_icon_size", "workspace_icon_size");
        cell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView label = new TextView(ctx);
        CharSequence text = this.renderer != null ? this.renderer.getLabel(item.key) : null;
        label.setText(text == null ? "" : text);
        label.setTextColor(-1);
        label.setGravity(17);
        label.setMaxLines(1);
        label.setIncludeFontPadding(false);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(0, MiuiStyleResolver.resolveTextSizePx(ctx, PAGE_PAD_H_DP, "workspace_icon_text_size", "icon_text_size"));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.topMargin = dp(ctx, 2);
        cell.addView(label, labelLp);
        return new IconCellView(ctx, cell, isRemoveBadgeVisible(page, slot));
    }

    private View buildFolderCell(Context ctx, LandscapeItem item) {
        Drawable d;
        LinearLayout linearLayout = new LinearLayout(ctx);
        linearLayout.setOrientation(1);
        int i = 17;
        linearLayout.setGravity(17);
        int i2 = 0;
        linearLayout.setClickable(false);
        linearLayout.setLongClickable(false);
        linearLayout.setFocusable(false);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        int iconSize = MiuiStyleResolver.resolveDimenPx(ctx, 52, "app_icon_size", "config_icon_size", "workspace_icon_size");
        LinearLayout linearLayout2 = new LinearLayout(ctx);
        linearLayout2.setOrientation(1);
        linearLayout2.setGravity(17);
        int i3 = 2;
        int pad = Math.max(2, iconSize / 10);
        linearLayout2.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(1442840575);
        bg.setCornerRadius(iconSize * 0.22f);
        linearLayout2.setBackground(bg);
        int miniSize = Math.max(1, (iconSize - (pad * 3)) / 2);
        int row = 0;
        while (row < i3) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(i2);
            line.setGravity(i);
            int col = 0;
            while (col < i3) {
                int idx = (row * 2) + col;
                ImageView iv = new ImageView(ctx);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (idx < item.folderChildren.size() && this.renderer != null && (d = this.renderer.getIcon(item.folderChildren.get(idx))) != null) {
                    iv.setImageDrawable(d);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(miniSize, miniSize);
                if (col > 0) {
                    lp.leftMargin = pad;
                }
                line.addView(iv, lp);
                col++;
                i3 = 2;
            }
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-2, -2);
            if (row > 0) {
                lineLp.topMargin = pad;
            }
            linearLayout2.addView(line, lineLp);
            row++;
            i = 17;
            i2 = 0;
            i3 = 2;
        }
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView label = new TextView(ctx);
        label.setText(item.folderTitle == null ? "文件夹" : item.folderTitle);
        label.setTextColor(-1);
        label.setGravity(17);
        label.setMaxLines(1);
        label.setIncludeFontPadding(false);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(0, MiuiStyleResolver.resolveTextSizePx(ctx, PAGE_PAD_H_DP, "workspace_icon_text_size", "icon_text_size"));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.topMargin = dp(ctx, 2);
        linearLayout.addView(label, labelLp);
        return linearLayout;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.max(0, View.MeasureSpec.getSize(widthSpec));
        int height = Math.max(0, View.MeasureSpec.getSize(heightSpec));
        setMeasuredDimension(width, height);
        int childWidthSpec = View.MeasureSpec.makeMeasureSpec(width, 1073741824);
        int childHeightSpec = View.MeasureSpec.makeMeasureSpec(height, 1073741824);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        if (changed && width > 0 && height > 0) {
            int childWidthSpec = View.MeasureSpec.makeMeasureSpec(width, 1073741824);
            int childHeightSpec = View.MeasureSpec.makeMeasureSpec(height, 1073741824);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
        }
        for (int i2 = 0; i2 < getChildCount(); i2++) {
            getChildAt(i2).layout(i2 * width, 0, (i2 + 1) * width, height);
        }
        clampScrollToPageBounds();
        notifyPageChanged();
    }

    @Override
    public void computeScroll() {
        if (this.scroller.computeScrollOffset()) {
            scrollTo(this.scroller.getCurrX(), 0);
            notifyPageChanged();
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return getVisibility() == 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.velocityTracker == null) {
            this.velocityTracker = VelocityTracker.obtain();
        }
        this.velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case GridPosition.ROWS:
                cleanupTouchState();
                snapToNearestPage();
                break;
        }
        return true;
    }

    private boolean handleTouchDown(MotionEvent event) {
        if (!this.scroller.isFinished()) {
            this.scroller.abortAnimation();
        }
        this.downX = event.getX();
        this.downY = event.getY();
        this.lastX = this.downX;
        this.paging = false;
        this.longPressFired = false;
        this.downHit = hitTest(this.downX, this.downY, true);
        logHit("down", this.downHit, this.downX, this.downY);
        if (this.downHit != null && this.downHit.item != null) {
            postDelayed(this.longPressRunnable, ViewConfiguration.getLongPressTimeout());
        } else if (this.downHit != null) {
            postDelayed(this.blankGlobalEditRunnable, BLANK_GLOBAL_EDIT_DELAY_MS);
        }
        return true;
    }

    private boolean handleTouchMove(MotionEvent event) {
        float x = event.getX();
        float dxFromDown = x - this.downX;
        float dyFromDown = event.getY() - this.downY;
        if (Math.abs(dxFromDown) > this.touchSlop || Math.abs(dyFromDown) > this.touchSlop) {
            removeCallbacks(this.longPressRunnable);
            removeCallbacks(this.blankGlobalEditRunnable);
        }
        if (!this.longPressFired && Math.abs(dxFromDown) > this.touchSlop && Math.abs(dxFromDown) > Math.abs(dyFromDown)) {
            this.paging = true;
        }
        if (this.paging) {
            int delta = Math.round(this.lastX - x);
            this.lastX = x;
            int maxScroll = Math.max(0, (this.pageCount - 1) * getWidth());
            int next = Math.max(0, Math.min(maxScroll, getScrollX() + delta));
            scrollTo(next, 0);
        }
        return true;
    }

    private boolean handleTouchUp(MotionEvent event) {
        removeCallbacks(this.longPressRunnable);
        removeCallbacks(this.blankGlobalEditRunnable);
        if (this.longPressFired) {
            cleanupTouchState();
            return true;
        }
        if (this.paging) {
            settleAfterSwipe();
            cleanupTouchState();
            return true;
        }
        Hit upHit = hitTest(event.getX(), event.getY(), true);
        logHit("up", upHit, event.getX(), event.getY());
        if (sameCell(this.downHit, upHit)) {
            if (upHit != null && upHit.item != null && upHit.removeBadge) {
                log("[remove] request source=overlay p=" + upHit.pageIndex + " s=" + upHit.slotIndex + " app=" + appName(upHit.item));
                if (this.listener != null) {
                    this.listener.onAppRemoveRequest(upHit.item, new GridPosition(upHit.pageIndex, upHit.slotIndex));
                }
            } else if (this.globalEditMode) {
                if (upHit == null || upHit.item == null) {
                    clearEditMode();
                } else if (upHit.item.isFolder()) {
                    log("[touch] global-edit folder-open p=" + upHit.pageIndex + " s=" + upHit.slotIndex + " app=" + appName(upHit.item));
                    if (this.listener != null) {
                        this.listener.onAppClick(upHit.item);
                    }
                } else {
                    log("[touch] global-edit tap ignored p=" + upHit.pageIndex + " s=" + upHit.slotIndex + " app=" + appName(upHit.item));
                }
            } else if (this.focusedEditPos != null) {
                if (upHit == null || upHit.item == null) {
                    clearEditMode();
                } else if (samePosition(this.focusedEditPos, new GridPosition(upHit.pageIndex, upHit.slotIndex))) {
                    log("[touch] single-edit tap ignored p=" + upHit.pageIndex + " s=" + upHit.slotIndex + " app=" + appName(upHit.item));
                } else {
                    clearEditMode();
                    log("[touch] click source=overlay target=grid p=" + upHit.pageIndex + " s=" + upHit.slotIndex + " row=" + upHit.row + " col=" + upHit.col + " app=" + appName(upHit.item));
                    if (this.listener != null) {
                        this.listener.onAppClick(upHit.item);
                    }
                }
            } else if (upHit != null && upHit.item != null) {
                log("[touch] click source=overlay target=grid p=" + upHit.pageIndex + " s=" + upHit.slotIndex + " row=" + upHit.row + " col=" + upHit.col + " app=" + appName(upHit.item));
                if (this.listener != null) {
                    this.listener.onAppClick(upHit.item);
                }
            } else if (upHit != null) {
                log("[touch] click-empty source=overlay p=" + upHit.pageIndex + " s=" + upHit.slotIndex + " row=" + upHit.row + " col=" + upHit.col);
            }
        }
        cleanupTouchState();
        return true;
    }

    public void fireLongPress() {
        Hit hit;
        if (this.longPressFired || this.paging || (hit = this.downHit) == null) {
            return;
        }
        this.longPressFired = true;
        performHapticFeedback(0);
        log("[longClick] source=overlay p=" + hit.pageIndex + " s=" + hit.slotIndex + " row=" + hit.row + " col=" + hit.col + " app=" + appName(hit.item));
        if (hit.item == null) {
            log("[longClick] blank ignored source=overlay p=" + hit.pageIndex + " s=" + hit.slotIndex + " row=" + hit.row + " col=" + hit.col);
            return;
        }
        GridPosition pos = new GridPosition(hit.pageIndex, hit.slotIndex);
        if (!this.globalEditMode) {
            setSingleEditMode(pos);
        }
        View source = findCellView(hit.pageIndex, hit.slotIndex);
        if (this.listener != null) {
            this.listener.onAppLongPress(hit.item, pos, source == null ? this : source);
        }
    }

    public void fireBlankGlobalEdit() {
        Hit hit;
        if (this.longPressFired || this.paging || (hit = this.downHit) == null || hit.item != null) {
            return;
        }
        this.longPressFired = true;
        performHapticFeedback(0);
        log("[longClick] blank-global source=overlay delayMs=2000 p=" + hit.pageIndex + " s=" + hit.slotIndex + " row=" + hit.row + " col=" + hit.col);
        setGlobalEditMode(true);
        if (this.listener != null) {
            this.listener.onEmptySlotLongPress();
        }
    }

    private void settleAfterSwipe() {
        int width = getWidth();
        if (width <= 0) {
            return;
        }
        float velocityX = 0.0f;
        if (this.velocityTracker != null) {
            this.velocityTracker.computeCurrentVelocity(1000, this.maxFling);
            velocityX = this.velocityTracker.getXVelocity();
        }
        int current = Math.round(getScrollX() / width);
        int target = current;
        if (velocityX > this.minFling) {
            target = current - 1;
        } else if (velocityX < (-this.minFling)) {
            target = current + 1;
        }
        smoothToPage(target);
    }

    private void snapToNearestPage() {
        int width = getWidth();
        if (width > 0) {
            smoothToPage(Math.round(getScrollX() / width));
        }
    }

    public void smoothToPage(int page) {
        int width = getWidth();
        if (width <= 0) {
            return;
        }
        int targetPage = Math.max(0, Math.min(this.pageCount - 1, page));
        int targetX = targetPage * width;
        log("[page] snap fromX=" + getScrollX() + " toPage=" + targetPage + " pageWidth=" + width + " pageCount=" + this.pageCount);
        this.scroller.startScroll(getScrollX(), 0, targetX - getScrollX(), 0, SNAP_DURATION_MS);
        notifyPageChanged(targetPage);
        postInvalidateOnAnimation();
    }

    private void cleanupTouchState() {
        removeCallbacks(this.longPressRunnable);
        removeCallbacks(this.blankGlobalEditRunnable);
        if (this.velocityTracker != null) {
            this.velocityTracker.recycle();
            this.velocityTracker = null;
        }
        this.downHit = null;
        this.paging = false;
        this.longPressFired = false;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        String desc;
        boolean z = false;
        switch (event.getAction()) {
            case 1:
                if (event.getClipDescription() != null && DRAG_LABEL.contentEquals(event.getClipDescription().getLabel())) {
                    z = true;
                }
                boolean ours = z;
                this.activeDragDescriptor = ours ? dragDescriptor(event) : null;
                log("[drag] source=" + (ours ? "overlay" : "native/other") + " desc=" + this.activeDragDescriptor + " action=STARTED viewport=" + getWidth() + "x" + getHeight() + " pageWidth=" + getWidth() + " pageCount=" + this.pageCount + " cellModel=8x3");
                break;
            case 2:
                Hit hover = hitTest(event.getX(), event.getY(), false);
                updateDropHover(hover);
                notifyDragHover(event, preciseFolderHover(event, hover));
                edgeScrollIfNeeded(event.getX());
                break;
            case GridPosition.ROWS:
                cancelEdgeScroll();
                Hit hit = hitTest(event.getX(), event.getY(), false);
                clearDropHover();
                logHit("drop", hit, event.getX(), event.getY());
                if (hit != null && (desc = dragDescriptor(event)) != null && this.listener != null) {
                    GridPosition insertTarget = insertionTarget(hit, event.getX());
                    if (insertTarget != null) {
                        log("[drop] mode=insert from=" + desc + " to=" + insertTarget + " hitSlot=" + hit.slotIndex + " app=" + appName(hit.item));
                        this.listener.onInsertOnGrid(desc, insertTarget);
                    } else {
                        this.listener.onDropOnGrid(desc, new GridPosition(hit.pageIndex, hit.slotIndex));
                    }
                }
                clearEditMode();
                break;
            case 4:
                cancelEdgeScroll();
                notifyDragHover(null, null);
                clearDropHover();
                finishActiveDragAnimation();
                clearEditMode();
                snapToNearestPage();
                this.activeDragDescriptor = null;
                break;
            case 6:
                clearDropHover();
                break;
        }
        return true;
    }

    private void notifyDragHover(DragEvent event, Hit hit) {
        if (this.listener == null) {
            return;
        }
        String desc = event == null ? null : dragDescriptor(event);
        GridPosition pos = hit != null ? new GridPosition(hit.pageIndex, hit.slotIndex) : null;
        int screenX = -1;
        int screenY = -1;
        if (event != null) {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            screenX = loc[0] + Math.round(event.getX());
            screenY = loc[1] + Math.round(event.getY());
        }
        this.listener.onDragHover(desc, pos, screenX, screenY);
    }

    private Hit preciseFolderHover(DragEvent event, Hit broadHit) {
        if (event == null || broadHit == null || broadHit.item == null || !broadHit.item.isFolder()) {
            return broadHit;
        }
        Hit precise = hitTest(event.getX(), event.getY(), true);
        if (precise == null || precise.item == null || !precise.item.isFolder()) {
            return null;
        }
        return broadHit;
    }

    private String dragDescriptor(DragEvent event) {
        CharSequence text;
        if (event != null && event.getClipData() != null && event.getClipData().getItemCount() > 0 && (text = event.getClipData().getItemAt(0).getText()) != null) {
            this.activeDragDescriptor = text.toString();
            currentDragDescriptor = this.activeDragDescriptor;
        }
        if (this.activeDragDescriptor == null) {
            this.activeDragDescriptor = currentDragDescriptor;
        }
        return this.activeDragDescriptor;
    }

    public static void startCellDrag(View source, String descriptor) {
        startCellDrag(source, descriptor, false);
    }

    public static String currentDragDescriptor() {
        return currentDragDescriptor;
    }

    public static void startCellDrag(final View source, final String descriptor, boolean deferred) {
        if (source == null) {
            return;
        }
        if (source.getWidth() <= 0 || source.getHeight() <= 0) {
            if (!deferred) {
                source.post(new Runnable() {
                    @Override
                    public final void run() {
                        LandscapePagedGridView.startCellDrag(source, descriptor, true);
                    }
                });
                return;
            } else {
                log("[drag] skip start: source has no size descriptor=" + descriptor);
                return;
            }
        }
        currentDragDescriptor = descriptor;
        ClipData clip = ClipData.newPlainText(DRAG_LABEL, descriptor);
        View.DragShadowBuilder shadow = new LiftDragShadowBuilder(source);
        try {
            boolean started = source.startDragAndDrop(clip, shadow, null, 0);
            if (started) {
                animateDragSourceStart(source);
            }
        } catch (Throwable t) {
            finishActiveDragAnimation();
            log("[drag] start failed descriptor=" + descriptor + " err=" + t);
        }
    }

    public static void finishActiveDragAnimation() {
        View source = activeDragSource.get();
        activeDragSource.clear();
        currentDragDescriptor = null;
        if (source == null) {
            return;
        }
        source.animate().cancel();
        source.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(170L).setInterpolator(new OvershootInterpolator(1.15f)).start();
    }

    private static void animateDragSourceStart(View source) {
        activeDragSource = new WeakReference<>(source);
        source.setPivotX(source.getWidth() / 2.0f);
        source.setPivotY(source.getHeight() / 2.0f);
        source.animate().cancel();
        source.animate().scaleX(DRAG_SOURCE_SCALE).scaleY(DRAG_SOURCE_SCALE).alpha(DRAG_SOURCE_ALPHA).setDuration(DRAG_ANIM_MS).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void edgeScrollIfNeeded(float x) {
        int width = getWidth();
        if (width <= 0 || this.pageCount <= 1) {
            cancelEdgeScroll();
            return;
        }
        int edge = dp(getContext(), EDGE_SCROLL_ZONE_DP);
        int delta = 0;
        if (x < edge) {
            delta = -1;
        } else if (x > width - edge) {
            delta = 1;
        }
        int current = Math.max(0, Math.min(this.pageCount - 1, Math.round(getScrollX() / width)));
        if ((delta < 0 && current <= 0) || (delta > 0 && current >= this.pageCount - 1)) {
            delta = 0;
        }
        if (delta == 0) {
            cancelEdgeScroll();
            return;
        }
        if (this.activeEdgeDirection != delta) {
            cancelEdgeScroll();
            this.activeEdgeDirection = delta;
            scheduleEdgeScroll(EDGE_SCROLL_DWELL_MS);
            log("[page] edge-armed dir=" + delta + " x=" + Math.round(x) + " edge=" + edge + " currentPage=" + current);
            return;
        }
        if (!this.edgeScrollScheduled) {
            scheduleEdgeScroll(EDGE_SCROLL_DWELL_MS);
        }
    }

    public void scheduleEdgeScroll(long delayMs) {
        if (this.edgeScrollScheduled || this.activeEdgeDirection == 0) {
            return;
        }
        this.edgeScrollScheduled = true;
        postDelayed(this.edgeScrollRunnable, delayMs);
    }

    public void cancelEdgeScroll() {
        if (this.edgeScrollScheduled) {
            removeCallbacks(this.edgeScrollRunnable);
        }
        this.edgeScrollScheduled = false;
        this.activeEdgeDirection = 0;
    }

    private void updateDropHover(Hit hit) {
        GridPosition next = hit == null ? null : new GridPosition(hit.pageIndex, hit.slotIndex);
        if (samePosition(this.hoverDropPos, next)) {
            return;
        }
        clearDropHover();
        this.hoverDropPos = next;
        if (next == null) {
            return;
        }
        this.hoverDropView = findCellView(next.pageIndex, next.slotIndex);
        if (this.hoverDropView == null) {
            return;
        }
        this.hoverDropView.setPivotX(this.hoverDropView.getWidth() / 2.0f);
        this.hoverDropView.setPivotY(this.hoverDropView.getHeight() / 2.0f);
        this.hoverDropView.animate().cancel();
        this.hoverDropView.animate().scaleX(DROP_HOVER_SCALE).scaleY(DROP_HOVER_SCALE).setDuration(110L).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void clearDropHover() {
        if (this.hoverDropView != null) {
            this.hoverDropView.animate().cancel();
            this.hoverDropView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150L).setInterpolator(new OvershootInterpolator(1.1f)).start();
        }
        this.hoverDropView = null;
        this.hoverDropPos = null;
    }

    private Hit hitTest(float x, float y, boolean requireAppTouchTarget) {
        LandscapeItem item;
        boolean removeBadge;
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || this.pageCount <= 0) {
            return null;
        }
        float contentX = x + getScrollX();
        int page = (int) Math.floor(contentX / width);
        if (page < 0 || page >= this.pageCount) {
            return null;
        }
        int localX = Math.round(contentX - (page * width));
        int localY = Math.round(y);
        Rect gridRect = contentRect(width, height);
        if (!gridRect.contains(localX, localY)) {
            return null;
        }
        int relX = localX - gridRect.left;
        int relY = localY - gridRect.top;
        int col = (int) ((((long) relX) * 8) / ((long) Math.max(1, gridRect.width())));
        int row = (int) ((((long) relY) * 3) / ((long) Math.max(1, gridRect.height())));
        int col2 = Math.max(0, Math.min(7, col));
        int row2 = Math.max(0, Math.min(2, row));
        int slot = (row2 * 8) + col2;
        Rect cell = cellRect(width, height, slot);
        LandscapeItem item2 = itemAt(page, slot);
        if (item2 == null) {
            item = item2;
            removeBadge = false;
        } else {
            boolean removeBadge2 = isRemoveBadgeVisible(page, slot) && removeBadgeRect(cell).contains(localX, localY);
            if (requireAppTouchTarget && !removeBadge2 && !appTouchRect(cell).contains(localX, localY)) {
                item = null;
                removeBadge = removeBadge2;
            } else {
                item = item2;
                removeBadge = removeBadge2;
            }
        }
        return new Hit(page, slot, row2, col2, item, cell, removeBadge);
    }

    private GridPosition insertionTarget(Hit hit, float x) {
        int width;
        if (hit == null || hit.item == null || (width = getWidth()) <= 0) {
            return null;
        }
        int localX = Math.round((getScrollX() + x) - (hit.pageIndex * width));
        int edgeBand = Math.max(1, hit.rect.width() / 4);
        if (localX <= hit.rect.left + edgeBand) {
            return new GridPosition(hit.pageIndex, hit.slotIndex);
        }
        if (localX < hit.rect.right - edgeBand) {
            return null;
        }
        int nextSlot = hit.slotIndex + 1;
        int nextPage = hit.pageIndex;
        if (nextSlot >= 24) {
            nextSlot = 0;
            nextPage++;
        }
        return new GridPosition(nextPage, nextSlot);
    }

    private LandscapeItem itemAt(int page, int slot) {
        int abs = GridPosition.toAbsoluteIndex(page, slot);
        if (abs < 0 || abs >= this.matrix.length) {
            return null;
        }
        return this.matrix[abs];
    }

    private boolean isRemoveBadgeVisible(int page, int slot) {
        return this.globalEditMode || (this.focusedEditPos != null && this.focusedEditPos.pageIndex == page && this.focusedEditPos.slotIndex == slot);
    }

    private View findCellView(int page, int slot) {
        if (page < 0 || page >= getChildCount()) {
            return null;
        }
        View pageView = getChildAt(page);
        if (!(pageView instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) pageView;
        if (slot < 0 || slot >= group.getChildCount()) {
            return null;
        }
        return group.getChildAt(slot);
    }

    private void clampScrollToPageBounds() {
        int width = getWidth();
        if (width > 0) {
            int maxScroll = Math.max(0, (this.pageCount - 1) * width);
            if (getScrollX() > maxScroll) {
                scrollTo(maxScroll, 0);
            }
            notifyPageChanged();
            return;
        }
        scrollTo(0, 0);
        notifyPageChanged(0);
    }

    private int currentPage() {
        int width = getWidth();
        if (width <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(this.pageCount - 1, Math.round(getScrollX() / width)));
    }

    private void notifyPageChanged() {
        notifyPageChanged(currentPage());
    }

    private void notifyPageChanged(int currentPage) {
        if (this.pageListener != null) {
            this.pageListener.onPageChanged(Math.max(0, Math.min(this.pageCount - 1, currentPage)), this.pageCount);
        }
    }

    private void logModelSnapshot() {
        int width = getWidth();
        int height = getHeight();
        clampScrollToPageBounds();
        log("bind viewport=" + width + "x" + height + " pageWidth=" + width + " pageHeight=" + height + " pages=" + this.pageCount + " cellModel=8x3 slotsPerPage=24");
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int slot = 0; slot < 24; slot++) {
            Rect rect = cellRect(width, height, slot);
            log("  page0 slot=" + slot + " row=" + (slot / 8) + " col=" + (slot % 8) + " rect=(" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom + ") app=" + appName(itemAt(0, slot)));
        }
    }

    private void logHit(String type, Hit hit, float x, float y) {
        if (hit == null) {
            log("[hit] " + type + " x=" + Math.round(x) + " y=" + Math.round(y) + " scrollX=" + getScrollX() + " pageWidth=" + getWidth() + " pageCount=" + this.pageCount + " result=outside");
        } else {
            log("[hit] " + type + " x=" + Math.round(x) + " y=" + Math.round(y) + " scrollX=" + getScrollX() + " pageWidth=" + getWidth() + " page=" + hit.pageIndex + "/" + this.pageCount + " row=" + hit.row + " col=" + hit.col + " slot=" + hit.slotIndex + " remove=" + hit.removeBadge + " rect=(" + hit.rect.left + "," + hit.rect.top + "," + hit.rect.right + "," + hit.rect.bottom + ") app=" + appName(hit.item));
        }
    }

    private static boolean sameCell(Hit a, Hit b) {
        return a != null && b != null && a.pageIndex == b.pageIndex && a.slotIndex == b.slotIndex;
    }

    private static boolean samePosition(GridPosition a, GridPosition b) {
        return a != null && b != null && a.pageIndex == b.pageIndex && a.slotIndex == b.slotIndex;
    }

    private static Rect contentRect(int width, int height) {
        int padH = dpStatic(width, PAGE_PAD_H_DP);
        int top = Math.max(dpStatic(height, PAGE_TOP_INSET_DP), Math.round(height * 0.07f));
        int bottom = Math.max(dpStatic(height, 8), Math.round(height * 0.02f));
        return new Rect(padH, top, Math.max(padH, width - padH), Math.max(top, height - bottom));
    }

    public static Rect cellRect(int width, int height, int slot) {
        Rect content = contentRect(width, height);
        int row = slot / 8;
        int col = slot % 8;
        int left = content.left + Math.round((content.width() * col) / 8.0f);
        int right = content.left + Math.round((content.width() * (col + 1)) / 8.0f);
        int top = content.top + Math.round((content.height() * row) / 3.0f);
        int bottom = content.top + Math.round((content.height() * (row + 1)) / 3.0f);
        return new Rect(left, top, right, bottom);
    }

    private static Rect appTouchRect(Rect cell) {
        int targetW = Math.round(cell.width() * 0.62f);
        int targetH = Math.round(cell.height() * 0.78f);
        int targetW2 = Math.max(1, Math.min(targetW, cell.width()));
        int targetH2 = Math.max(1, Math.min(targetH, cell.height()));
        int cx = cell.centerX();
        int cy = cell.centerY();
        return new Rect(cx - (targetW2 / 2), cy - (targetH2 / 2), ((targetW2 + 1) / 2) + cx, ((targetH2 + 1) / 2) + cy);
    }

    public static Rect removeBadgeRect(Rect cell) {
        Rect target = appTouchRect(cell);
        int size = Math.max(24, Math.round(Math.min(cell.width(), cell.height()) * 0.18f));
        int left = target.left - (size / 3);
        int top = target.top - (size / 3);
        return new Rect(left, top, left + size, top + size);
    }

    public static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static int dpStatic(int pixelsForDensity, int value) {
        return Math.max(0, Math.round(value * Math.max(1.0f, pixelsForDensity / 720.0f)));
    }

    private static String appName(LandscapeItem item) {
        if (item == null) {
            return "empty";
        }
        if (item.isFolder()) {
            return "folder#" + item.folderId + "(" + item.folderChildren.size() + ")";
        }
        if (item.key == null) {
            return "empty";
        }
        return item.key.packageName + "/" + item.key.className;
    }

    public static void log(String msg) {
        String line = TAG + msg;
        XposedBridge.log(line);
        try {
            Log.i("MiuiHomeLandscape", line);
        } catch (Throwable th) {
        }
    }

    private static final class Hit {
        final int col;
        final LandscapeItem item;
        final int pageIndex;
        final Rect rect;
        final boolean removeBadge;
        final int row;
        final int slotIndex;

        Hit(int pageIndex, int slotIndex, int row, int col, LandscapeItem item, Rect rect, boolean removeBadge) {
            this.pageIndex = pageIndex;
            this.slotIndex = slotIndex;
            this.row = row;
            this.col = col;
            this.item = item;
            this.rect = rect;
            this.removeBadge = removeBadge;
        }
    }

    public static class PageView extends ViewGroup {
        private int pageIndex;

        public PageView(Context ctx) {
            super(ctx);
            setClipChildren(true);
            setClipToPadding(true);
            setClickable(false);
            setLongClickable(false);
        }

        void setPageIndex(int pageIndex) {
            this.pageIndex = pageIndex;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = View.MeasureSpec.getSize(widthSpec);
            int height = View.MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int count = Math.min(getChildCount(), 24);
            for (int slot = 0; slot < count; slot++) {
                Rect rect = LandscapePagedGridView.cellRect(width, height, slot);
                getChildAt(slot).measure(View.MeasureSpec.makeMeasureSpec(rect.width(), 1073741824), View.MeasureSpec.makeMeasureSpec(rect.height(), 1073741824));
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            int count = Math.min(getChildCount(), 24);
            if (changed) {
                for (int slot = 0; slot < count; slot++) {
                    Rect rect = LandscapePagedGridView.cellRect(width, height, slot);
                    getChildAt(slot).measure(View.MeasureSpec.makeMeasureSpec(rect.width(), 1073741824), View.MeasureSpec.makeMeasureSpec(rect.height(), 1073741824));
                }
            }
            for (int slot2 = 0; slot2 < count; slot2++) {
                Rect rect2 = LandscapePagedGridView.cellRect(width, height, slot2);
                getChildAt(slot2).layout(rect2.left, rect2.top, rect2.right, rect2.bottom);
            }
        }

        @Override
        public String toString() {
            return "PageView{" + this.pageIndex + "}";
        }
    }

    private static class LiftDragShadowBuilder extends View.DragShadowBuilder {
        private static final float SHADOW_SCALE = 1.08f;
        private final Bitmap bitmap;
        private final Paint paint;
        private final int shadowH;
        private final int shadowW;

        LiftDragShadowBuilder(View source) {
            Bitmap captured;
            super(source);
            this.paint = new Paint(3);
            int w = Math.max(1, source.getWidth());
            int h = Math.max(1, source.getHeight());
            try {
                captured = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(captured);
                source.draw(canvas);
            } catch (Throwable th) {
                captured = null;
            }
            this.bitmap = captured;
            this.shadowW = Math.max(1, Math.round(w * SHADOW_SCALE));
            this.shadowH = Math.max(1, Math.round(h * SHADOW_SCALE));
            this.paint.setAlpha(235);
        }

        @Override
        public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
            outShadowSize.set(this.shadowW, this.shadowH);
            outShadowTouchPoint.set(this.shadowW / 2, this.shadowH / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            if (this.bitmap == null || this.bitmap.isRecycled()) {
                super.onDrawShadow(canvas);
            } else {
                Rect dst = new Rect(0, 0, this.shadowW, this.shadowH);
                canvas.drawBitmap(this.bitmap, (Rect) null, dst, this.paint);
            }
        }
    }

    private static class IconCellView extends ViewGroup {
        private final View badge;
        private final View content;

        IconCellView(Context ctx, View content, boolean editMode) {
            super(ctx);
            this.content = content;
            setClipChildren(false);
            setClipToPadding(false);
            setClickable(false);
            setLongClickable(false);
            addView(content);
            this.badge = new RemoveBadgeView(ctx);
            this.badge.setVisibility(editMode ? 0 : 8);
            addView(this.badge);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = View.MeasureSpec.getSize(widthSpec);
            int height = View.MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            this.content.measure(View.MeasureSpec.makeMeasureSpec(width, 1073741824), View.MeasureSpec.makeMeasureSpec(height, 1073741824));
            Rect badgeRect = LandscapePagedGridView.removeBadgeRect(new Rect(0, 0, width, height));
            this.badge.measure(View.MeasureSpec.makeMeasureSpec(badgeRect.width(), 1073741824), View.MeasureSpec.makeMeasureSpec(badgeRect.height(), 1073741824));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            this.content.layout(0, 0, width, height);
            Rect badgeRect = LandscapePagedGridView.removeBadgeRect(new Rect(0, 0, width, height));
            this.badge.layout(badgeRect.left, badgeRect.top, badgeRect.right, badgeRect.bottom);
        }
    }

    private static class RemoveBadgeView extends View {
        private final Paint border;
        private final Paint cross;
        private final Paint fill;

        RemoveBadgeView(Context ctx) {
            super(ctx);
            this.fill = new Paint(1);
            this.border = new Paint(1);
            this.cross = new Paint(1);
            this.fill.setStyle(Paint.Style.FILL);
            this.fill.setColor(-301989888);
            this.border.setStyle(Paint.Style.STROKE);
            this.border.setStrokeWidth(Math.max(1.0f, LandscapePagedGridView.dp(ctx, 1)));
            this.border.setColor(-1);
            this.cross.setStyle(Paint.Style.STROKE);
            this.cross.setStrokeCap(Paint.Cap.ROUND);
            this.cross.setStrokeWidth(Math.max(2.0f, LandscapePagedGridView.dp(ctx, 2)));
            this.cross.setColor(-1);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2.0f;
            float cy = getHeight() / 2.0f;
            float borderHalf = this.border.getStrokeWidth() / 2.0f;
            float radius = Math.max(0.0f, (Math.min(getWidth(), getHeight()) / 2.0f) - borderHalf);
            canvas.drawCircle(cx, cy, radius, this.fill);
            canvas.drawCircle(cx, cy, radius, this.border);
            float arm = radius * 0.42f;
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, this.cross);
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, this.cross);
        }
    }
}
