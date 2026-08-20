package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;

import java.util.List;

import de.robv.android.xposed.XposedBridge;

/**
 * Vili 横屏 Dock：9 格，和 Grid 数据/坐标完全独立。
 *
 *   - 容器本身透明，由 overlay 提供圆角悬浮背景
 *   - 每格自适应分宽 (weight=1)，不设硬编码宽度
 *   - 图标大小跟随 MIUI hotseat dimen；比 grid 略小 (× 0.9) 避免压迫
 *   - 空槽完全透明；全局编辑由桌面空白长按进入，应用选择器在编辑工具栏打开
 *   - 实槽：点击=启动，长按=开始拖拽 (ClipData "dock:I")
 *   - 所有槽 (空/实) 都是 drop target，接受 "grid:P:S" 或 "dock:I"
 */
public class LandscapeDockView extends LinearLayout {
    private static final String TAG = "[MiuiHomeLandscape/Dock] ";

    public interface Listener {
        /** 点击实槽启动应用。 */
        void onAppClick(LandscapeItem item);
        /** Edit-mode remove only affects the landscape layout, never the installed app. */
        void onAppRemoveRequest(LandscapeItem item, DockPosition position);
        /** 拖拽落位到 dock。fromDescriptor 形如 "grid:P:S" 或 "dock:I"。 */
        void onDropOnDock(String fromDescriptor, DockPosition to);
    }

    private Listener listener;
    private final FrameLayout[] slots = new FrameLayout[DockPosition.SLOTS];
    private int hoverSlot = -1;
    private List<LandscapeItem> boundItems;
    private AppRenderer boundRenderer;
    private boolean editMode;
    private float iconScale = 0.9f;

    public LandscapeDockView(Context ctx) {
        super(ctx);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setLongClickable(false);
        // 透明：不再画一条黑条
        setBackgroundColor(Color.TRANSPARENT);
        int padV = dp(ctx, 4);
        int padH = dp(ctx, 12);
        setPadding(padH, padV, padH, padV);

        for (int i = 0; i < DockPosition.SLOTS; i++) {
            FrameLayout cell = new FrameLayout(ctx);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LayoutParams.MATCH_PARENT, 1f);
            int margin = dp(ctx, 4);
            lp.leftMargin = margin;
            lp.rightMargin = margin;
            addView(cell, lp);
            slots[i] = cell;
        }
    }

    public void setListener(Listener l) { this.listener = l; }

    public void setEditMode(boolean enabled) {
        if (editMode == enabled) return;
        editMode = enabled;
        bind(boundItems, boundRenderer);
    }

    public boolean isEditMode() { return editMode; }

    /** Applies one of the three curated Dock sizes and immediately rebinds visible icons. */
    public void setIconScale(float scale) {
        float sanitized = Math.max(0.72f, Math.min(1.08f, scale));
        if (Math.abs(iconScale - sanitized) < 0.001f) return;
        iconScale = sanitized;
        bind(boundItems, boundRenderer);
        requestLayout();
    }

    public void bind(List<LandscapeItem> dockItems, AppRenderer renderer) {
        boundItems = dockItems;
        boundRenderer = renderer;
        clearDropHover();
        for (FrameLayout f : slots) {
            f.removeAllViews();
            f.setOnClickListener(null);
            f.setOnLongClickListener(null);
            f.setOnDragListener(null);
            f.setClickable(false);
            f.setLongClickable(false);
        }

        LandscapeItem[] byIdx = new LandscapeItem[DockPosition.SLOTS];
        if (dockItems != null) {
            for (LandscapeItem it : dockItems) {
                if (it == null) continue;
                if (it.dockIndex >= 0 && it.dockIndex < DockPosition.SLOTS) {
                    byIdx[it.dockIndex] = it;
                }
            }
        }

        Context ctx = getContext();
        for (int i = 0; i < DockPosition.SLOTS; i++) {
            final DockPosition pos = new DockPosition(i);
            final LandscapeItem item = byIdx[i];
            FrameLayout holder = slots[i];

            View child;
            if (item == null) {
                child = buildEmptySlot(ctx);
                // 空槽不拦截事件；holder 也保持不可点击
            } else {
                child = buildFilledSlot(ctx, item, renderer, iconScale);
                holder.setClickable(true);
                holder.setLongClickable(true);
                holder.setOnClickListener(v -> {
                    if (!editMode && listener != null) listener.onAppClick(item);
                });
                holder.setOnLongClickListener(v -> {
                    LandscapePagedGridView.startCellDrag(
                            v, "dock:" + pos.dockIndex);
                    return true;
                });
            }

            // 所有槽位都是 drop target
            holder.addView(child, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            if (item != null && editMode) {
                TextView remove = buildRemoveBadge(ctx);
                remove.setContentDescription("从 Dock 移除");
                remove.setOnClickListener(v -> {
                    if (listener != null) listener.onAppRemoveRequest(item, pos);
                });
                FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                        dp(ctx, 26), dp(ctx, 26), Gravity.TOP | Gravity.START);
                badgeLp.leftMargin = dp(ctx, 1);
                badgeLp.topMargin = dp(ctx, 1);
                holder.addView(remove, badgeLp);
            }
        }
        log("[bind] slots=" + DockPosition.SLOTS
                + " items=" + (dockItems == null ? 0 : dockItems.size()));
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return handleDropAtX(event.getX(), event);
    }

    public boolean handleDropAtX(float x, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getClipDescription() != null
                        && LandscapePagedGridView.DRAG_LABEL.contentEquals(
                                event.getClipDescription().getLabel());
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
                updateDropHover(positionForX(x).dockIndex);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                clearDropHover();
                return true;
            case DragEvent.ACTION_DROP: {
                clearDropHover();
                DockPosition pos = positionForX(x);
                ClipData data = event.getClipData();
                if (data != null && data.getItemCount() > 0) {
                    CharSequence desc = data.getItemAt(0).getText();
                    if (desc != null && listener != null) {
                        log("[drop] x=" + Math.round(x)
                                + " slot=" + pos.dockIndex
                                + " from=" + desc);
                        listener.onDropOnDock(desc.toString(), pos);
                    }
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                clearDropHover();
                LandscapePagedGridView.finishActiveDragAnimation();
                return true;
        }
        return false;
    }

    private void updateDropHover(int slot) {
        int next = Math.max(0, Math.min(DockPosition.SLOTS - 1, slot));
        if (hoverSlot == next) return;
        clearDropHover();
        hoverSlot = next;
        FrameLayout target = slots[next];
        if (target == null) return;
        target.setPivotX(target.getWidth() / 2f);
        target.setPivotY(target.getHeight() / 2f);
        target.animate().cancel();
        target.animate()
                .scaleX(1.07f)
                .scaleY(1.07f)
                .setDuration(110L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void clearDropHover() {
        if (hoverSlot >= 0 && hoverSlot < slots.length && slots[hoverSlot] != null) {
            FrameLayout target = slots[hoverSlot];
            target.animate().cancel();
            target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150L)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
        }
        hoverSlot = -1;
    }

    private DockPosition positionForX(float x) {
        int contentLeft = getPaddingLeft();
        int contentRight = Math.max(contentLeft + 1, getWidth() - getPaddingRight());
        int usable = Math.max(1, contentRight - contentLeft);
        int rel = Math.max(0, Math.min(usable - 1, Math.round(x) - contentLeft));
        int slot = (int) ((long) rel * DockPosition.SLOTS / usable);
        slot = Math.max(0, Math.min(DockPosition.SLOTS - 1, slot));
        return new DockPosition(slot);
    }

    private static View buildEmptySlot(Context ctx) {
        // 完全透明，无 "+"，无背景
        FrameLayout f = new FrameLayout(ctx);
        f.setClickable(false);
        f.setLongClickable(false);
        return f;
    }

    private static View buildFilledSlot(
            Context ctx, LandscapeItem it, AppRenderer r, float iconScale) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);

        ImageView iv = new ImageView(ctx);
        Drawable d = r == null ? null : r.getIcon(it.key);
        if (d != null) iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // MIUI hotseat 原生图标大小；× 0.9 避免占满行高
        int baseSz = MiuiStyleResolver.resolveDimenPx(ctx, 52,
                "hotseat_icon_size",
                "hotseats_icon_size",
                "app_icon_size");
        int iconSz = Math.round(baseSz * iconScale);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSz, iconSz);
        ivLp.gravity = Gravity.CENTER;
        ll.addView(iv, ivLp);

        // Dock 默认不显示文本 (跟随 MIUI 风格)；留空 TextView 占位避免抖动
        TextView tv = new TextView(ctx);
        tv.setText("");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setVisibility(View.GONE);
        ll.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return ll;
    }

    private static TextView buildRemoveBadge(Context ctx) {
        TextView badge = new TextView(ctx);
        badge.setText("×");
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        badge.setGravity(Gravity.CENTER);
        badge.setClickable(true);
        badge.setFocusable(true);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xEE202124);
        background.setStroke(dp(ctx, 1), 0xAAFFFFFF);
        badge.setBackground(background);
        return badge;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
