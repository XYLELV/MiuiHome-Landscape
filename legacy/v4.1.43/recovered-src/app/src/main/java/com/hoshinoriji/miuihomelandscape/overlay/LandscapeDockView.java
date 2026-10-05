package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.DragEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;
import de.robv.android.xposed.XposedBridge;
import java.util.List;

public class LandscapeDockView extends LinearLayout {
    private static final String TAG = "[MiuiHomeLandscape/Dock] ";
    private int hoverSlot;
    private Listener listener;
    private final FrameLayout[] slots;

    public interface Listener {
        void onAppClick(LandscapeItem landscapeItem);

        void onDropOnDock(String str, DockPosition dockPosition);
    }

    public LandscapeDockView(Context ctx) {
        super(ctx);
        this.slots = new FrameLayout[9];
        this.hoverSlot = -1;
        setOrientation(0);
        setGravity(16);
        setClickable(true);
        setLongClickable(false);
        setBackgroundColor(0);
        int padV = dp(ctx, 4);
        int padH = dp(ctx, 12);
        setPadding(padH, padV, padH, padV);
        for (int i = 0; i < 9; i++) {
            View cell = new FrameLayout(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1.0f);
            int margin = dp(ctx, 4);
            lp.leftMargin = margin;
            lp.rightMargin = margin;
            addView(cell, lp);
            this.slots[i] = cell;
        }
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void bind(List<LandscapeItem> dockItems, AppRenderer renderer) {
        View child;
        clearDropHover();
        for (FrameLayout f : this.slots) {
            f.removeAllViews();
            f.setOnClickListener(null);
            f.setOnLongClickListener(null);
            f.setOnDragListener(null);
            f.setClickable(false);
            f.setLongClickable(false);
        }
        LandscapeItem[] byIdx = new LandscapeItem[9];
        if (dockItems != null) {
            for (LandscapeItem it : dockItems) {
                if (it != null && it.dockIndex >= 0 && it.dockIndex < 9) {
                    byIdx[it.dockIndex] = it;
                }
            }
        }
        Context ctx = getContext();
        for (int i = 0; i < 9; i++) {
            final DockPosition pos = new DockPosition(i);
            final LandscapeItem item = byIdx[i];
            FrameLayout holder = this.slots[i];
            if (item == null) {
                child = buildEmptySlot(ctx);
            } else {
                child = buildFilledSlot(ctx, item, renderer);
                holder.setClickable(true);
                holder.setLongClickable(true);
                holder.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public final void onClick(View view) {
                        this.f$0.m78xa5b92a42(item, view);
                    }
                });
                holder.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public final boolean onLongClick(View view) {
                        return LandscapeDockView.lambda$bind$1(pos, view);
                    }
                });
            }
            holder.addView(child, new FrameLayout.LayoutParams(-1, -1));
        }
        log("[bind] slots=9 items=" + (dockItems != null ? dockItems.size() : 0));
    }

    void m78xa5b92a42(LandscapeItem item, View v) {
        if (this.listener != null) {
            this.listener.onAppClick(item);
        }
    }

    static boolean lambda$bind$1(DockPosition pos, View v) {
        LandscapePagedGridView.startCellDrag(v, "dock:" + pos.dockIndex);
        return true;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return handleDropAtX(event.getX(), event);
    }

    public boolean handleDropAtX(float x, DragEvent event) {
        CharSequence desc;
        switch (event.getAction()) {
            case 1:
                return event.getClipDescription() != null && LandscapePagedGridView.DRAG_LABEL.contentEquals(event.getClipDescription().getLabel());
            case 2:
            case 5:
                updateDropHover(positionForX(x).dockIndex);
                return true;
            case GridPosition.ROWS:
                clearDropHover();
                DockPosition pos = positionForX(x);
                ClipData data = event.getClipData();
                if (data != null && data.getItemCount() > 0 && (desc = data.getItemAt(0).getText()) != null && this.listener != null) {
                    log("[drop] x=" + Math.round(x) + " slot=" + pos.dockIndex + " from=" + ((Object) desc));
                    this.listener.onDropOnDock(desc.toString(), pos);
                }
                return true;
            case 4:
                clearDropHover();
                LandscapePagedGridView.finishActiveDragAnimation();
                return true;
            case 6:
                clearDropHover();
                return true;
            default:
                return false;
        }
    }

    private void updateDropHover(int slot) {
        int next = Math.max(0, Math.min(8, slot));
        if (this.hoverSlot == next) {
            return;
        }
        clearDropHover();
        this.hoverSlot = next;
        FrameLayout target = this.slots[next];
        if (target == null) {
            return;
        }
        target.setPivotX(target.getWidth() / 2.0f);
        target.setPivotY(target.getHeight() / 2.0f);
        target.animate().cancel();
        target.animate().scaleX(1.07f).scaleY(1.07f).setDuration(110L).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void clearDropHover() {
        if (this.hoverSlot >= 0 && this.hoverSlot < this.slots.length && this.slots[this.hoverSlot] != null) {
            FrameLayout target = this.slots[this.hoverSlot];
            target.animate().cancel();
            target.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150L).setInterpolator(new OvershootInterpolator(1.1f)).start();
        }
        this.hoverSlot = -1;
    }

    private DockPosition positionForX(float x) {
        int contentLeft = getPaddingLeft();
        int contentRight = Math.max(contentLeft + 1, getWidth() - getPaddingRight());
        int usable = Math.max(1, contentRight - contentLeft);
        int rel = Math.max(0, Math.min(usable - 1, Math.round(x) - contentLeft));
        int slot = (int) ((((long) rel) * 9) / ((long) usable));
        return new DockPosition(Math.max(0, Math.min(8, slot)));
    }

    private static View buildEmptySlot(Context ctx) {
        FrameLayout f = new FrameLayout(ctx);
        f.setClickable(false);
        f.setLongClickable(false);
        return f;
    }

    private static View buildFilledSlot(Context ctx, LandscapeItem it, AppRenderer r) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(1);
        ll.setGravity(17);
        ImageView iv = new ImageView(ctx);
        Drawable d = r.getIcon(it.key);
        if (d != null) {
            iv.setImageDrawable(d);
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int baseSz = MiuiStyleResolver.resolveDimenPx(ctx, 52, "hotseat_icon_size", "hotseats_icon_size", "app_icon_size");
        int iconSz = Math.round(baseSz * 0.9f);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSz, iconSz);
        ivLp.gravity = 17;
        ll.addView(iv, ivLp);
        TextView tv = new TextView(ctx);
        tv.setText("");
        tv.setTextColor(-1);
        tv.setTextSize(2, 10.0f);
        tv.setVisibility(8);
        ll.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        return ll;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
