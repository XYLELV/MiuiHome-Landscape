package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
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

import java.lang.ref.WeakReference;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

/**
 * Landscape grid pager with one authoritative 8x3 input model.
 *
 * Visual layout, click hit-test, long press drag source, and drop target all use
 * the same cell rect calculation. Child views only render icons; this parent
 * consumes touch and decides the target by page/row/col.
 */
public class LandscapePagedGridView extends ViewGroup {

    public interface Listener {
        void onAppClick(LandscapeItem item);
        void onAppLongPress(LandscapeItem item, GridPosition pos, View source);
        void onAppRemoveRequest(LandscapeItem item, GridPosition pos);
        void onEmptySlotLongPress();
        void onEditModeExitRequest();
        void onDragHover(String fromDescriptor, GridPosition over, int screenX, int screenY);
        void onDropOnGrid(String fromDescriptor, GridPosition to);
        void onInsertOnGrid(String fromDescriptor, GridPosition to);
    }

    public interface PageListener {
        void onPageChanged(int currentPage, int pageCount);
    }

    public static final String DRAG_LABEL = "miuihome-landscape-drag";

    private static final String TAG = "[MiuiHomeLandscape/Pager] ";
    private static final int PAGE_PAD_H_DP = 12;
    private static final int PAGE_TOP_INSET_DP = 34;
    private static final int PAGE_BOTTOM_INSET_DP = 8;
    private static final int SNAP_DURATION_MS = 260;
    private static final long BLANK_GLOBAL_EDIT_DELAY_MS =
            ViewConfiguration.getLongPressTimeout();
    // v4.1.16: 边缘翻页太灵敏——拖 apk 到边缘文件夹会被自动翻页吃掉。
    // 把触发区从 140dp 缩到 70dp（避开正常列宽），dwell 从 280ms 拉长到
    // 580ms（短促悬停不再翻页，必须明确停留才换页）。
    private static final int EDGE_SCROLL_ZONE_DP = 70;
    private static final long EDGE_SCROLL_DWELL_MS = 580L;
    private static final long EDGE_SCROLL_REPEAT_MS = 620L;
    private static final long DRAG_ANIM_MS = 140L;
    private static final float DRAG_SOURCE_SCALE = 0.92f;
    private static final float DRAG_SOURCE_ALPHA = 0.38f;
    private static final float DROP_HOVER_SCALE = 1.055f;
    private static WeakReference<View> activeDragSource = new WeakReference<>(null);
    private static String currentDragDescriptor;

    private final Scroller scroller;
    private final int touchSlop;
    private final int minFling;
    private final int maxFling;
    private final Runnable longPressRunnable = this::fireLongPress;
    private final Runnable blankGlobalEditRunnable = this::fireBlankGlobalEdit;

    private Listener listener;
    private PageListener pageListener;
    private AppRenderer renderer;
    private LandscapeItem[] matrix = new LandscapeItem[GridPosition.SLOTS_PER_PAGE];

    private int pageCount = 1;
    private float downX;
    private float downY;
    private float lastX;
    private boolean paging;
    private boolean longPressFired;
    private boolean globalEditMode;
    private boolean labelsEnabled = true;
    private GridPosition focusedEditPos;
    private GridPosition hoverDropPos;
    private View hoverDropView;
    private Hit downHit;
    private String activeDragDescriptor;
    private VelocityTracker velocityTracker;
    private int activeEdgeDirection;
    private boolean edgeScrollScheduled;
    private final Runnable edgeScrollRunnable = new Runnable() {
        @Override public void run() {
            edgeScrollScheduled = false;
            int direction = activeEdgeDirection;
            if (direction == 0) return;

            int width = getWidth();
            if (width <= 0 || pageCount <= 1) {
                cancelEdgeScroll();
                return;
            }

            int current = Math.max(0, Math.min(pageCount - 1,
                    Math.round((float) getScrollX() / width)));
            int target = Math.max(0, Math.min(pageCount - 1, current + direction));
            if (target == current) {
                cancelEdgeScroll();
                return;
            }

            log("[page] edge-auto dir=" + direction
                    + " fromPage=" + current
                    + " toPage=" + target
                    + " pageWidth=" + width);
            smoothToPage(target);
            scheduleEdgeScroll(EDGE_SCROLL_REPEAT_MS);
        }
    };

    public LandscapePagedGridView(Context ctx) {
        super(ctx);
        setClickable(true);
        setFocusable(true);
        setLongClickable(true);
        setClipChildren(true);
        setClipToPadding(true);
        setWillNotDraw(false);

        scroller = new Scroller(ctx);
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPageListener(PageListener listener) {
        this.pageListener = listener;
        notifyPageChanged();
    }

    public void setLabelsEnabled(boolean enabled) {
        if (labelsEnabled == enabled) return;
        labelsEnabled = enabled;
        rebuildPageViews();
        requestLayout();
        invalidate();
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setEditMode(boolean enabled) {
        if (enabled) {
            setGlobalEditMode(true);
        } else {
            clearEditMode();
        }
    }

    public boolean isGlobalEditMode() {
        return globalEditMode;
    }

    private void setSingleEditMode(GridPosition pos) {
        if (pos == null) return;
        if (!globalEditMode && samePosition(focusedEditPos, pos)) {
            return;
        }
        globalEditMode = false;
        focusedEditPos = pos;
        rebuildPageViews();
        requestLayout();
        invalidate();
        log("[edit] mode=single pos=" + pos);
    }

    private void setGlobalEditMode(boolean enabled) {
        if (globalEditMode == enabled && focusedEditPos == null) {
            return;
        }
        globalEditMode = enabled;
        focusedEditPos = null;
        rebuildPageViews();
        requestLayout();
        invalidate();
        log("[edit] mode=" + (globalEditMode ? "global" : "off"));
    }

    private void clearEditMode() {
        if (!globalEditMode && focusedEditPos == null) {
            return;
        }
        globalEditMode = false;
        focusedEditPos = null;
        rebuildPageViews();
        requestLayout();
        invalidate();
        log("[edit] mode=false");
    }

    private boolean isEditing() {
        return globalEditMode || focusedEditPos != null;
    }

    public void bind(List<LandscapeItem> gridItems, AppRenderer renderer) {
        this.renderer = renderer;

        int maxAbs = -1;
        if (gridItems != null) {
            for (LandscapeItem item : gridItems) {
                if (item == null || item.kind != LandscapeItem.Kind.GRID) {
                    continue;
                }
                if (item.pageIndex < 0 || !GridPosition.isValidSlotIndex(item.slotIndex)) {
                    continue;
                }
                int abs = GridPosition.toAbsoluteIndex(item.pageIndex, item.slotIndex);
                if (abs > maxAbs) {
                    maxAbs = abs;
                }
            }
        }

        pageCount = Math.max(1, maxAbs < 0 ? 1
                : (maxAbs / GridPosition.SLOTS_PER_PAGE) + 1);
        matrix = new LandscapeItem[pageCount * GridPosition.SLOTS_PER_PAGE];

        if (gridItems != null) {
            for (LandscapeItem item : gridItems) {
                if (item == null || item.kind != LandscapeItem.Kind.GRID) {
                    continue;
                }
                if (item.pageIndex < 0 || !GridPosition.isValidSlotIndex(item.slotIndex)) {
                    continue;
                }
                int abs = GridPosition.toAbsoluteIndex(item.pageIndex, item.slotIndex);
                if (abs >= 0 && abs < matrix.length) {
                    matrix[abs] = item;
                }
            }
        }
        if (focusedEditPos != null
                && (focusedEditPos.pageIndex >= pageCount
                || itemAt(focusedEditPos.pageIndex, focusedEditPos.slotIndex) == null)) {
            focusedEditPos = null;
        }

        rebuildPageViews();
        requestLayout();
        post(() -> {
            // 不在每次 bind 后做 cell 缩放/淡入动画。
            // onResume → refreshOverlay → bind 会被频繁触发，
            // 该动画会让用户从应用返回桌面时看到"整个桌面缩小+淡入"的位移感。
            // 保留 logModelSnapshot/notifyPageChanged 必要逻辑。
            logModelSnapshot();
            notifyPageChanged();
        });
    }

    private void animateVisibleCellsAfterBind() {
        int page = currentPage();
        if (page < 0 || page >= getChildCount()) return;
        View pageView = getChildAt(page);
        if (!(pageView instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) pageView;
        int count = Math.min(group.getChildCount(), GridPosition.SLOTS_PER_PAGE);
        for (int slot = 0; slot < count; slot++) {
            if (itemAt(page, slot) == null) continue;
            View child = group.getChildAt(slot);
            child.animate().cancel();
            child.setAlpha(0.72f);
            child.setScaleX(0.965f);
            child.setScaleY(0.965f);
            child.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(Math.min(90L, slot * 6L))
                    .setDuration(150L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void rebuildPageViews() {
        clearDropHover();
        removeAllViews();
        Context ctx = getContext();
        for (int page = 0; page < pageCount; page++) {
            PageView pageView = new PageView(ctx);
            pageView.setPageIndex(page);
            for (int slot = 0; slot < GridPosition.SLOTS_PER_PAGE; slot++) {
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
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClickable(false);
        cell.setLongClickable(false);
        cell.setFocusable(false);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        ImageView icon = new ImageView(ctx);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable drawable = renderer == null ? null : renderer.getIcon(item.key);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        }
        int iconSize = MiuiStyleResolver.resolveDimenPx(ctx, 52,
                "app_icon_size", "config_icon_size", "workspace_icon_size");
        cell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        if (labelsEnabled) {
            TextView label = new TextView(ctx);
            CharSequence text = renderer == null ? null : renderer.getLabel(item.key);
            label.setText(text == null ? "" : text);
            label.setTextColor(Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setIncludeFontPadding(false);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    MiuiStyleResolver.resolveTextSizePx(ctx, 12,
                            "workspace_icon_text_size", "icon_text_size"));
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(ctx, 2);
            cell.addView(label, labelLp);
        }
        return new IconCellView(ctx, cell, isRemoveBadgeVisible(page, slot));
    }

    private View buildFolderCell(Context ctx, LandscapeItem item) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClickable(false);
        cell.setLongClickable(false);
        cell.setFocusable(false);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        int iconSize = MiuiStyleResolver.resolveDimenPx(ctx, 52,
                "app_icon_size", "config_icon_size", "workspace_icon_size");
        LinearLayout miniGrid = new LinearLayout(ctx);
        miniGrid.setOrientation(LinearLayout.VERTICAL);
        miniGrid.setGravity(Gravity.CENTER);
        int pad = Math.max(2, iconSize / 10);
        miniGrid.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x55FFFFFF);
        bg.setCornerRadius(iconSize * 0.22f);
        miniGrid.setBackground(bg);

        int miniSize = Math.max(1, (iconSize - pad * 3) / 2);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col;
                ImageView iv = new ImageView(ctx);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (idx < item.folderChildren.size() && renderer != null) {
                    Drawable d = renderer.getIcon(item.folderChildren.get(idx));
                    if (d != null) iv.setImageDrawable(d);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(miniSize, miniSize);
                if (col > 0) lp.leftMargin = pad;
                line.addView(iv, lp);
            }
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (row > 0) lineLp.topMargin = pad;
            miniGrid.addView(line, lineLp);
        }
        cell.addView(miniGrid, new LinearLayout.LayoutParams(iconSize, iconSize));

        if (labelsEnabled) {
            TextView label = new TextView(ctx);
            label.setText(item.folderTitle == null ? "文件夹" : item.folderTitle);
            label.setTextColor(Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setIncludeFontPadding(false);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    MiuiStyleResolver.resolveTextSizePx(ctx, 12,
                            "workspace_icon_text_size", "icon_text_size"));
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(ctx, 2);
            cell.addView(label, labelLp);
        }
        return cell;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.max(0, MeasureSpec.getSize(widthSpec));
        int height = Math.max(0, MeasureSpec.getSize(heightSpec));
        setMeasuredDimension(width, height);

        int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        if (changed && width > 0 && height > 0) {
            int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
        }
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * width, 0, (i + 1) * width, height);
        }
        clampScrollToPageBounds();
        notifyPageChanged();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            notifyPageChanged();
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return getVisibility() == VISIBLE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(event);
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(event);
            case MotionEvent.ACTION_UP:
                performClick();
                return handleTouchUp(event);
            case MotionEvent.ACTION_CANCEL:
                cleanupTouchState();
                snapToNearestPage();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private boolean handleTouchDown(MotionEvent event) {
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
        downX = event.getX();
        downY = event.getY();
        lastX = downX;
        paging = false;
        longPressFired = false;
        downHit = hitTest(downX, downY, true);
        logHit("down", downHit, downX, downY);
        if (downHit != null && downHit.item != null) {
            postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
        } else if (downHit != null) {
            postDelayed(blankGlobalEditRunnable, BLANK_GLOBAL_EDIT_DELAY_MS);
        }
        return true;
    }

    private boolean handleTouchMove(MotionEvent event) {
        float x = event.getX();
        float dxFromDown = x - downX;
        float dyFromDown = event.getY() - downY;

        if (Math.abs(dxFromDown) > touchSlop || Math.abs(dyFromDown) > touchSlop) {
            removeCallbacks(longPressRunnable);
            removeCallbacks(blankGlobalEditRunnable);
        }

        if (!longPressFired && Math.abs(dxFromDown) > touchSlop
                && Math.abs(dxFromDown) > Math.abs(dyFromDown)) {
            paging = true;
        }

        if (paging) {
            int delta = Math.round(lastX - x);
            lastX = x;
            int maxScroll = Math.max(0, (pageCount - 1) * getWidth());
            int next = Math.max(0, Math.min(maxScroll, getScrollX() + delta));
            scrollTo(next, 0);
        }
        return true;
    }

    private boolean handleTouchUp(MotionEvent event) {
        removeCallbacks(longPressRunnable);
        removeCallbacks(blankGlobalEditRunnable);

        if (longPressFired) {
            cleanupTouchState();
            return true;
        }

        if (paging) {
            settleAfterSwipe();
            cleanupTouchState();
            return true;
        }

        Hit upHit = hitTest(event.getX(), event.getY(), true);
        logHit("up", upHit, event.getX(), event.getY());
        if (sameCell(downHit, upHit)) {
            if (upHit != null && upHit.item != null && upHit.removeBadge) {
                log("[remove] request source=overlay p=" + upHit.pageIndex
                        + " s=" + upHit.slotIndex
                        + " app=" + appName(upHit.item));
                if (listener != null) {
                    listener.onAppRemoveRequest(upHit.item,
                            new GridPosition(upHit.pageIndex, upHit.slotIndex));
                }
            } else if (globalEditMode) {
                if (upHit == null || upHit.item == null) {
                    if (listener != null) listener.onEditModeExitRequest();
                } else if (upHit.item.isFolder()) {
                    log("[touch] global-edit folder-open p=" + upHit.pageIndex
                            + " s=" + upHit.slotIndex
                            + " app=" + appName(upHit.item));
                    if (listener != null) {
                        listener.onAppClick(upHit.item);
                    }
                } else {
                    log("[touch] global-edit tap ignored p=" + upHit.pageIndex
                            + " s=" + upHit.slotIndex
                            + " app=" + appName(upHit.item));
                }
            } else if (focusedEditPos != null) {
                if (upHit == null || upHit.item == null) {
                    clearEditMode();
                } else if (samePosition(focusedEditPos,
                        new GridPosition(upHit.pageIndex, upHit.slotIndex))) {
                    log("[touch] single-edit tap ignored p=" + upHit.pageIndex
                            + " s=" + upHit.slotIndex
                            + " app=" + appName(upHit.item));
                } else {
                    clearEditMode();
                    log("[touch] click source=overlay target=grid p=" + upHit.pageIndex
                            + " s=" + upHit.slotIndex
                            + " row=" + upHit.row
                            + " col=" + upHit.col
                            + " app=" + appName(upHit.item));
                    if (listener != null) {
                        listener.onAppClick(upHit.item);
                    }
                }
            } else if (upHit != null && upHit.item != null) {
                log("[touch] click source=overlay target=grid p=" + upHit.pageIndex
                        + " s=" + upHit.slotIndex
                        + " row=" + upHit.row
                        + " col=" + upHit.col
                        + " app=" + appName(upHit.item));
                if (listener != null) {
                    listener.onAppClick(upHit.item);
                }
            } else if (upHit != null) {
                log("[touch] click-empty source=overlay p=" + upHit.pageIndex
                        + " s=" + upHit.slotIndex
                        + " row=" + upHit.row
                        + " col=" + upHit.col);
            }
        }
        cleanupTouchState();
        return true;
    }

    private void fireLongPress() {
        if (longPressFired || paging) {
            return;
        }
        Hit hit = downHit;
        if (hit == null) {
            return;
        }
        longPressFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        log("[longClick] source=overlay p=" + hit.pageIndex
                + " s=" + hit.slotIndex
                + " row=" + hit.row
                + " col=" + hit.col
                + " app=" + appName(hit.item));

        if (hit.item == null) {
            log("[longClick] blank ignored source=overlay p=" + hit.pageIndex
                    + " s=" + hit.slotIndex
                    + " row=" + hit.row
                    + " col=" + hit.col);
            return;
        }

        GridPosition pos = new GridPosition(hit.pageIndex, hit.slotIndex);
        if (!globalEditMode) {
            setSingleEditMode(pos);
        }
        View source = findCellView(hit.pageIndex, hit.slotIndex);
        if (listener != null) {
            listener.onAppLongPress(hit.item, pos, source == null ? this : source);
        }
    }

    private void fireBlankGlobalEdit() {
        if (longPressFired || paging) {
            return;
        }
        Hit hit = downHit;
        if (hit == null || hit.item != null) {
            return;
        }
        longPressFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        log("[longClick] blank-global source=overlay delayMs=" + BLANK_GLOBAL_EDIT_DELAY_MS
                + " p=" + hit.pageIndex
                + " s=" + hit.slotIndex
                + " row=" + hit.row
                + " col=" + hit.col);
        setGlobalEditMode(true);
        if (listener != null) {
            listener.onEmptySlotLongPress();
        }
    }

    private void settleAfterSwipe() {
        int width = getWidth();
        if (width <= 0) {
            return;
        }
        float velocityX = 0f;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000, maxFling);
            velocityX = velocityTracker.getXVelocity();
        }

        int current = Math.round((float) getScrollX() / width);
        int target = current;
        if (velocityX > minFling) {
            target = current - 1;
        } else if (velocityX < -minFling) {
            target = current + 1;
        }
        smoothToPage(target);
    }

    private void snapToNearestPage() {
        int width = getWidth();
        if (width > 0) {
            smoothToPage(Math.round((float) getScrollX() / width));
        }
    }

    private void smoothToPage(int page) {
        int width = getWidth();
        if (width <= 0) {
            return;
        }
        int targetPage = Math.max(0, Math.min(pageCount - 1, page));
        int targetX = targetPage * width;
        log("[page] snap fromX=" + getScrollX()
                + " toPage=" + targetPage
                + " pageWidth=" + width
                + " pageCount=" + pageCount);
        scroller.startScroll(getScrollX(), 0, targetX - getScrollX(), 0, SNAP_DURATION_MS);
        notifyPageChanged(targetPage);
        postInvalidateOnAnimation();
    }

    private void cleanupTouchState() {
        removeCallbacks(longPressRunnable);
        removeCallbacks(blankGlobalEditRunnable);
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        downHit = null;
        paging = false;
        longPressFired = false;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                boolean ours = event.getClipDescription() != null
                        && DRAG_LABEL.contentEquals(event.getClipDescription().getLabel());
                activeDragDescriptor = ours ? dragDescriptor(event) : null;
                log("[drag] source=" + (ours ? "overlay" : "native/other")
                        + " desc=" + activeDragDescriptor
                        + " action=STARTED viewport=" + getWidth() + "x" + getHeight()
                        + " pageWidth=" + getWidth()
                        + " pageCount=" + pageCount
                        + " cellModel=" + GridPosition.COLS + "x" + GridPosition.ROWS);
                return ours;
            }
            case DragEvent.ACTION_DRAG_LOCATION:
                Hit hover = hitTest(event.getX(), event.getY(), false);
                updateDropHover(hover);
                notifyDragHover(event, preciseFolderHover(event, hover));
                edgeScrollIfNeeded(event.getX());
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                // Do not cancel edge paging here: Android often reports EXITED
                // exactly when the drag shadow crosses the edge band. Keeping
                // the armed direction preserves MIUI-like page turning.
                clearDropHover();
                return true;
            case DragEvent.ACTION_DROP: {
                cancelEdgeScroll();
                Hit hit = hitTest(event.getX(), event.getY(), false);
                clearDropHover();
                logHit("drop", hit, event.getX(), event.getY());
                if (hit != null) {
                    String desc = dragDescriptor(event);
                    if (desc != null && listener != null) {
                        GridPosition insertTarget = insertionTarget(hit, event.getX());
                        if (insertTarget != null) {
                            log("[drop] mode=insert from=" + desc
                                    + " to=" + insertTarget
                                    + " hitSlot=" + hit.slotIndex
                                    + " app=" + appName(hit.item));
                            listener.onInsertOnGrid(desc, insertTarget);
                        } else {
                            listener.onDropOnGrid(desc,
                                    new GridPosition(hit.pageIndex, hit.slotIndex));
                        }
                    }
                }
                // A global edit session remains active across multiple moves. A one-icon
                // long-press session still ends after its drop.
                if (!globalEditMode) clearEditMode();
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                cancelEdgeScroll();
                notifyDragHover(null, null);
                clearDropHover();
                finishActiveDragAnimation();
                if (!globalEditMode) clearEditMode();
                snapToNearestPage();
                activeDragDescriptor = null;
                return true;
            default:
                return true;
        }
    }

    private void notifyDragHover(DragEvent event, Hit hit) {
        if (listener == null) return;
        String desc = event == null ? null : dragDescriptor(event);
        GridPosition pos = hit == null ? null : new GridPosition(hit.pageIndex, hit.slotIndex);
        int screenX = -1;
        int screenY = -1;
        if (event != null) {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            screenX = loc[0] + Math.round(event.getX());
            screenY = loc[1] + Math.round(event.getY());
        }
        listener.onDragHover(desc, pos, screenX, screenY);
    }

    private Hit preciseFolderHover(DragEvent event, Hit broadHit) {
        if (event == null || broadHit == null || broadHit.item == null
                || !broadHit.item.isFolder()) {
            return broadHit;
        }
        Hit precise = hitTest(event.getX(), event.getY(), true);
        if (precise == null || precise.item == null || !precise.item.isFolder()) {
            return null;
        }
        return broadHit;
    }

    private String dragDescriptor(DragEvent event) {
        if (event != null && event.getClipData() != null && event.getClipData().getItemCount() > 0) {
            CharSequence text = event.getClipData().getItemAt(0).getText();
            if (text != null) {
                activeDragDescriptor = text.toString();
                currentDragDescriptor = activeDragDescriptor;
            }
        }
        if (activeDragDescriptor == null) {
            activeDragDescriptor = currentDragDescriptor;
        }
        return activeDragDescriptor;
    }

    public static void startCellDrag(View source, String descriptor) {
        startCellDrag(source, descriptor, false);
    }

    public static String currentDragDescriptor() {
        return currentDragDescriptor;
    }

    private static void startCellDrag(View source, String descriptor, boolean deferred) {
        if (source == null) {
            return;
        }
        if (source.getWidth() <= 0 || source.getHeight() <= 0) {
            if (!deferred) {
                source.post(() -> startCellDrag(source, descriptor, true));
            } else {
                log("[drag] skip start: source has no size descriptor=" + descriptor);
            }
            return;
        }
        currentDragDescriptor = descriptor;
        ClipData clip = ClipData.newPlainText(DRAG_LABEL, descriptor);
        View.DragShadowBuilder shadow = new LiftDragShadowBuilder(source);
        boolean started;
        try {
            started = source.startDragAndDrop(clip, shadow, null, 0);
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
        if (source == null) return;
        source.animate().cancel();
        source.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(170L)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();
    }

    private static void animateDragSourceStart(View source) {
        activeDragSource = new WeakReference<>(source);
        source.setPivotX(source.getWidth() / 2f);
        source.setPivotY(source.getHeight() / 2f);
        source.animate().cancel();
        source.animate()
                .scaleX(DRAG_SOURCE_SCALE)
                .scaleY(DRAG_SOURCE_SCALE)
                .alpha(DRAG_SOURCE_ALPHA)
                .setDuration(DRAG_ANIM_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void edgeScrollIfNeeded(float x) {
        int width = getWidth();
        if (width <= 0 || pageCount <= 1) {
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

        int current = Math.max(0, Math.min(pageCount - 1,
                Math.round((float) getScrollX() / width)));
        if ((delta < 0 && current <= 0) || (delta > 0 && current >= pageCount - 1)) {
            delta = 0;
        }
        if (delta == 0) {
            cancelEdgeScroll();
            return;
        }

        if (activeEdgeDirection != delta) {
            cancelEdgeScroll();
            activeEdgeDirection = delta;
            scheduleEdgeScroll(EDGE_SCROLL_DWELL_MS);
            log("[page] edge-armed dir=" + delta
                    + " x=" + Math.round(x)
                    + " edge=" + edge
                    + " currentPage=" + current);
        } else if (!edgeScrollScheduled) {
            scheduleEdgeScroll(EDGE_SCROLL_DWELL_MS);
        }
    }

    private void scheduleEdgeScroll(long delayMs) {
        if (edgeScrollScheduled || activeEdgeDirection == 0) return;
        edgeScrollScheduled = true;
        postDelayed(edgeScrollRunnable, delayMs);
    }

    private void cancelEdgeScroll() {
        if (edgeScrollScheduled) {
            removeCallbacks(edgeScrollRunnable);
        }
        edgeScrollScheduled = false;
        activeEdgeDirection = 0;
    }

    private void updateDropHover(Hit hit) {
        GridPosition next = hit == null ? null
                : new GridPosition(hit.pageIndex, hit.slotIndex);
        if (samePosition(hoverDropPos, next)) {
            return;
        }
        clearDropHover();
        hoverDropPos = next;
        if (next == null) {
            return;
        }
        hoverDropView = findCellView(next.pageIndex, next.slotIndex);
        if (hoverDropView == null) {
            return;
        }
        hoverDropView.setPivotX(hoverDropView.getWidth() / 2f);
        hoverDropView.setPivotY(hoverDropView.getHeight() / 2f);
        hoverDropView.animate().cancel();
        hoverDropView.animate()
                .scaleX(DROP_HOVER_SCALE)
                .scaleY(DROP_HOVER_SCALE)
                .setDuration(110L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void clearDropHover() {
        if (hoverDropView != null) {
            hoverDropView.animate().cancel();
            hoverDropView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150L)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
        }
        hoverDropView = null;
        hoverDropPos = null;
    }

    private Hit hitTest(float x, float y, boolean requireAppTouchTarget) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || pageCount <= 0) {
            return null;
        }

        float contentX = x + getScrollX();
        int page = (int) Math.floor(contentX / width);
        if (page < 0 || page >= pageCount) {
            return null;
        }

        int localX = Math.round(contentX - page * width);
        int localY = Math.round(y);
        Rect gridRect = contentRect(width, height);
        if (!gridRect.contains(localX, localY)) {
            return null;
        }

        int relX = localX - gridRect.left;
        int relY = localY - gridRect.top;
        int col = (int) ((long) relX * GridPosition.COLS / Math.max(1, gridRect.width()));
        int row = (int) ((long) relY * GridPosition.ROWS / Math.max(1, gridRect.height()));
        col = Math.max(0, Math.min(GridPosition.COLS - 1, col));
        row = Math.max(0, Math.min(GridPosition.ROWS - 1, row));
        int slot = row * GridPosition.COLS + col;
        Rect cell = cellRect(width, height, slot);
        LandscapeItem item = itemAt(page, slot);
        boolean removeBadge = false;
        if (item != null) {
            removeBadge = isRemoveBadgeVisible(page, slot)
                    && removeBadgeRect(cell).contains(localX, localY);
            if (requireAppTouchTarget && !removeBadge
                    && !appTouchRect(cell).contains(localX, localY)) {
                item = null;
            }
        }
        return new Hit(page, slot, row, col, item, cell, removeBadge);
    }

    private GridPosition insertionTarget(Hit hit, float x) {
        if (hit == null || hit.item == null) {
            return null;
        }
        int width = getWidth();
        if (width <= 0) {
            return null;
        }
        int localX = Math.round(x + getScrollX() - hit.pageIndex * width);
        int edgeBand = Math.max(1, hit.rect.width() / 4);
        if (localX <= hit.rect.left + edgeBand) {
            return new GridPosition(hit.pageIndex, hit.slotIndex);
        }
        if (localX >= hit.rect.right - edgeBand) {
            int nextSlot = hit.slotIndex + 1;
            int nextPage = hit.pageIndex;
            if (nextSlot >= GridPosition.SLOTS_PER_PAGE) {
                nextSlot = 0;
                nextPage++;
            }
            return new GridPosition(nextPage, nextSlot);
        }
        return null;
    }

    private LandscapeItem itemAt(int page, int slot) {
        int abs = GridPosition.toAbsoluteIndex(page, slot);
        return abs >= 0 && abs < matrix.length ? matrix[abs] : null;
    }

    private boolean isRemoveBadgeVisible(int page, int slot) {
        return globalEditMode
                || (focusedEditPos != null
                && focusedEditPos.pageIndex == page
                && focusedEditPos.slotIndex == slot);
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
        return slot >= 0 && slot < group.getChildCount() ? group.getChildAt(slot) : null;
    }

    private void clampScrollToPageBounds() {
        int width = getWidth();
        if (width <= 0) {
            scrollTo(0, 0);
            notifyPageChanged(0);
            return;
        }
        int maxScroll = Math.max(0, (pageCount - 1) * width);
        if (getScrollX() > maxScroll) {
            scrollTo(maxScroll, 0);
        }
        notifyPageChanged();
    }

    private int currentPage() {
        int width = getWidth();
        if (width <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(pageCount - 1,
                Math.round((float) getScrollX() / width)));
    }

    private void notifyPageChanged() {
        notifyPageChanged(currentPage());
    }

    private void notifyPageChanged(int currentPage) {
        if (pageListener != null) {
            pageListener.onPageChanged(
                    Math.max(0, Math.min(pageCount - 1, currentPage)),
                    pageCount);
        }
    }

    private void logModelSnapshot() {
        int width = getWidth();
        int height = getHeight();
        clampScrollToPageBounds();
        log("bind viewport=" + width + "x" + height
                + " pageWidth=" + width
                + " pageHeight=" + height
                + " pages=" + pageCount
                + " cellModel=" + GridPosition.COLS + "x" + GridPosition.ROWS
                + " slotsPerPage=" + GridPosition.SLOTS_PER_PAGE);
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int slot = 0; slot < GridPosition.SLOTS_PER_PAGE; slot++) {
            Rect rect = cellRect(width, height, slot);
            log("  page0 slot=" + slot
                    + " row=" + (slot / GridPosition.COLS)
                    + " col=" + (slot % GridPosition.COLS)
                    + " rect=(" + rect.left + "," + rect.top
                    + "," + rect.right + "," + rect.bottom + ")"
                    + " app=" + appName(itemAt(0, slot)));
        }
    }

    private void logHit(String type, Hit hit, float x, float y) {
        if (hit == null) {
            log("[hit] " + type
                    + " x=" + Math.round(x)
                    + " y=" + Math.round(y)
                    + " scrollX=" + getScrollX()
                    + " pageWidth=" + getWidth()
                    + " pageCount=" + pageCount
                    + " result=outside");
            return;
        }
        log("[hit] " + type
                + " x=" + Math.round(x)
                + " y=" + Math.round(y)
                + " scrollX=" + getScrollX()
                + " pageWidth=" + getWidth()
                + " page=" + hit.pageIndex + "/" + pageCount
                + " row=" + hit.row
                + " col=" + hit.col
                + " slot=" + hit.slotIndex
                + " remove=" + hit.removeBadge
                + " rect=(" + hit.rect.left + "," + hit.rect.top
                + "," + hit.rect.right + "," + hit.rect.bottom + ")"
                + " app=" + appName(hit.item));
    }

    private static boolean sameCell(Hit a, Hit b) {
        return a != null && b != null
                && a.pageIndex == b.pageIndex
                && a.slotIndex == b.slotIndex;
    }

    private static boolean samePosition(GridPosition a, GridPosition b) {
        return a != null && b != null
                && a.pageIndex == b.pageIndex
                && a.slotIndex == b.slotIndex;
    }

    private static Rect contentRect(int width, int height) {
        int padH = dpStatic(width, PAGE_PAD_H_DP);
        int top = Math.max(dpStatic(height, PAGE_TOP_INSET_DP),
                Math.round(height * 0.07f));
        int bottom = Math.max(dpStatic(height, PAGE_BOTTOM_INSET_DP),
                Math.round(height * 0.02f));
        return new Rect(padH, top, Math.max(padH, width - padH),
                Math.max(top, height - bottom));
    }

    private static Rect cellRect(int width, int height, int slot) {
        Rect content = contentRect(width, height);
        int row = slot / GridPosition.COLS;
        int col = slot % GridPosition.COLS;
        int left = content.left + Math.round((float) content.width() * col / GridPosition.COLS);
        int right = content.left + Math.round((float) content.width() * (col + 1) / GridPosition.COLS);
        int top = content.top + Math.round((float) content.height() * row / GridPosition.ROWS);
        int bottom = content.top + Math.round((float) content.height() * (row + 1) / GridPosition.ROWS);
        return new Rect(left, top, right, bottom);
    }

    private static Rect appTouchRect(Rect cell) {
        int targetW = Math.round(cell.width() * 0.62f);
        int targetH = Math.round(cell.height() * 0.78f);
        targetW = Math.max(1, Math.min(targetW, cell.width()));
        targetH = Math.max(1, Math.min(targetH, cell.height()));
        int cx = cell.centerX();
        int cy = cell.centerY();
        return new Rect(cx - targetW / 2, cy - targetH / 2,
                cx + (targetW + 1) / 2, cy + (targetH + 1) / 2);
    }

    private static Rect removeBadgeRect(Rect cell) {
        Rect target = appTouchRect(cell);
        int size = Math.max(24, Math.round(Math.min(cell.width(), cell.height()) * 0.18f));
        int left = target.left - size / 3;
        int top = target.top - size / 3;
        return new Rect(left, top, left + size, top + size);
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static int dpStatic(int pixelsForDensity, int value) {
        // The grid runs only on device pixels here. Use a conservative 3x-ish
        // fallback when static helpers do not have a Context.
        return Math.max(0, Math.round(value * Math.max(1f, pixelsForDensity / 720f)));
    }

    private static String appName(LandscapeItem item) {
        if (item == null) {
            return "empty";
        }
        if (item.isFolder()) return "folder#" + item.folderId
                + "(" + item.folderChildren.size() + ")";
        if (item.key == null) return "empty";
        return item.key.packageName + "/" + item.key.className;
    }

    private static void log(String msg) {
        String line = TAG + msg;
        XposedBridge.log(line);
        try { android.util.Log.i("MiuiHomeLandscape", line); } catch (Throwable ignored) {}
    }

    private static final class Hit {
        final int pageIndex;
        final int slotIndex;
        final int row;
        final int col;
        final LandscapeItem item;
        final Rect rect;
        final boolean removeBadge;

        Hit(int pageIndex, int slotIndex, int row, int col,
                LandscapeItem item, Rect rect, boolean removeBadge) {
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
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            int count = Math.min(getChildCount(), GridPosition.SLOTS_PER_PAGE);
            for (int slot = 0; slot < count; slot++) {
                Rect rect = cellRect(width, height, slot);
                getChildAt(slot).measure(
                        MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            int count = Math.min(getChildCount(), GridPosition.SLOTS_PER_PAGE);
            if (changed) {
                for (int slot = 0; slot < count; slot++) {
                    Rect rect = cellRect(width, height, slot);
                    getChildAt(slot).measure(
                            MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(rect.height(), MeasureSpec.EXACTLY));
                }
            }
            for (int slot = 0; slot < count; slot++) {
                Rect rect = cellRect(width, height, slot);
                getChildAt(slot).layout(rect.left, rect.top, rect.right, rect.bottom);
            }
        }

        @Override
        public String toString() {
            return "PageView{" + pageIndex + "}";
        }
    }

    private static class LiftDragShadowBuilder extends View.DragShadowBuilder {
        private static final float SHADOW_SCALE = 1.08f;
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final int shadowW;
        private final int shadowH;

        LiftDragShadowBuilder(View source) {
            super(source);
            int w = Math.max(1, source.getWidth());
            int h = Math.max(1, source.getHeight());
            Bitmap captured;
            try {
                captured = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(captured);
                source.draw(canvas);
            } catch (Throwable t) {
                captured = null;
            }
            bitmap = captured;
            shadowW = Math.max(1, Math.round(w * SHADOW_SCALE));
            shadowH = Math.max(1, Math.round(h * SHADOW_SCALE));
            paint.setAlpha(235);
        }

        @Override
        public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
            outShadowSize.set(shadowW, shadowH);
            outShadowTouchPoint.set(shadowW / 2, shadowH / 2);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) {
                super.onDrawShadow(canvas);
                return;
            }
            Rect dst = new Rect(0, 0, shadowW, shadowH);
            canvas.drawBitmap(bitmap, null, dst, paint);
        }
    }

    private static class IconCellView extends ViewGroup {
        private final View content;
        private final View badge;
        private final Rect bounds = new Rect();

        IconCellView(Context ctx, View content, boolean editMode) {
            super(ctx);
            this.content = content;
            setClipChildren(false);
            setClipToPadding(false);
            setClickable(false);
            setLongClickable(false);
            addView(content);

            badge = new RemoveBadgeView(ctx);
            badge.setVisibility(editMode ? VISIBLE : GONE);
            addView(badge);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            content.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            bounds.set(0, 0, width, height);
            Rect badgeRect = removeBadgeRect(bounds);
            badge.measure(
                    MeasureSpec.makeMeasureSpec(badgeRect.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(badgeRect.height(), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            content.layout(0, 0, width, height);
            bounds.set(0, 0, width, height);
            Rect badgeRect = removeBadgeRect(bounds);
            badge.layout(badgeRect.left, badgeRect.top, badgeRect.right, badgeRect.bottom);
        }
    }

    private static class RemoveBadgeView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);

        RemoveBadgeView(Context ctx) {
            super(ctx);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xEE000000);

            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, dp(ctx, 1)));
            border.setColor(0xFFFFFFFF);

            cross.setStyle(Paint.Style.STROKE);
            cross.setStrokeCap(Paint.Cap.ROUND);
            cross.setStrokeWidth(Math.max(2f, dp(ctx, 2)));
            cross.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float borderHalf = border.getStrokeWidth() / 2f;
            float radius = Math.max(0f, Math.min(getWidth(), getHeight()) / 2f - borderHalf);
            canvas.drawCircle(cx, cy, radius, fill);
            canvas.drawCircle(cx, cy, radius, border);

            float arm = radius * 0.42f;
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, cross);
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, cross);
        }
    }
}
