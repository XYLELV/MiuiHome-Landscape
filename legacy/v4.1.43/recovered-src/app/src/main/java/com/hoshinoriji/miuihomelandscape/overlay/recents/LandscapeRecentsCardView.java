package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.overlay.recents.LandscapeRecentsModel;

public class LandscapeRecentsCardView extends FrameLayout {
    private final Callback callback;
    private boolean cancelTap;
    private float downRawX;
    private float downRawY;
    private boolean dragging;
    private final ImageView iconView;
    private final LandscapeRecentsModel.TaskRef ref;
    private final ImageView thumbView;
    private final TextView titleView;
    private final int touchSlop;

    public interface Callback {
        void onCardDismissed(LandscapeRecentsCardView landscapeRecentsCardView);

        void onCardTapped(LandscapeRecentsCardView landscapeRecentsCardView);
    }

    public LandscapeRecentsCardView(Context ctx, LandscapeRecentsModel.TaskRef ref, int width, int height, Callback callback) {
        super(ctx);
        this.ref = ref;
        this.callback = callback;
        this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        setLayoutParams(new LinearLayout.LayoutParams(width, height));
        setBackground(new ColorDrawable(-14540254));
        final float corner = dp(ctx, 24.0f);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
            }
        });
        setClipToOutline(true);
        this.thumbView = new ImageView(ctx);
        this.thumbView.setBackgroundColor(-15658735);
        this.thumbView.setAdjustViewBounds(false);
        this.thumbView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        this.thumbView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        if (ref.thumbnail != null && !ref.thumbnail.isRecycled()) {
            this.thumbView.setImageBitmap(ref.thumbnail);
        }
        addView(this.thumbView);
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(0);
        header.setGravity(16);
        header.setPadding((int) dp(ctx, 12.0f), (int) dp(ctx, 8.0f), (int) dp(ctx, 12.0f), (int) dp(ctx, 8.0f));
        header.setBackgroundColor(1711276032);
        ViewGroup.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2, 48);
        addView(header, headerLp);
        this.iconView = new ImageView(ctx);
        int iconSize = (int) dp(ctx, 24.0f);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.rightMargin = (int) dp(ctx, 8.0f);
        this.iconView.setLayoutParams(iconLp);
        if (ref.icon != null) {
            this.iconView.setImageDrawable(ref.icon);
        }
        header.addView(this.iconView);
        this.titleView = new TextView(ctx);
        this.titleView.setTextSize(2, 13.0f);
        this.titleView.setTextColor(-1);
        this.titleView.setSingleLine(true);
        this.titleView.setEllipsize(TextUtils.TruncateAt.END);
        CharSequence label = ref.title;
        if (label == null || label.length() == 0) {
            label = ref.component != null ? ref.component.getPackageName() : "";
        }
        this.titleView.setText(label);
        header.addView(this.titleView, new LinearLayout.LayoutParams(0, -2, 1.0f));
    }

    public LandscapeRecentsModel.TaskRef getTaskRef() {
        return this.ref;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case 0:
                this.downRawX = ev.getRawX();
                this.downRawY = ev.getRawY();
                this.dragging = false;
                this.cancelTap = false;
                return true;
            case 1:
            case GridPosition.ROWS:
                if (this.dragging) {
                    float dy = ev.getRawY() - this.downRawY;
                    float dx = ev.getRawX() - this.downRawX;
                    int h = getHeight();
                    boolean dismiss = ev.getActionMasked() == 1 && dy < ((float) (-h)) * 0.3f && Math.abs(dy) > Math.abs(dx) * 1.25f;
                    if (dismiss) {
                        animateDismiss();
                    } else {
                        animateRestore();
                    }
                    this.dragging = false;
                    return true;
                }
                if (!this.cancelTap && ev.getActionMasked() == 1 && this.callback != null) {
                    this.callback.onCardTapped(this);
                }
                return true;
            case 2:
                float dx2 = ev.getRawX() - this.downRawX;
                float dy2 = ev.getRawY() - this.downRawY;
                if (!this.dragging) {
                    if (Math.abs(dy2) > this.touchSlop && Math.abs(dy2) > Math.abs(dx2)) {
                        this.dragging = true;
                        this.cancelTap = true;
                        ViewGroup p = (ViewGroup) getParent();
                        if (p != null) {
                            p.requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (Math.abs(dx2) > this.touchSlop) {
                        this.cancelTap = true;
                        return false;
                    }
                }
                if (this.dragging) {
                    float ty = Math.min(0.0f, dy2);
                    setTranslationY(ty);
                    float h2 = getHeight() == 0 ? 1.0f : getHeight();
                    float a = 1.0f - Math.min(1.0f, Math.abs(ty) / h2);
                    setAlpha(Math.max(0.2f, a));
                }
                return true;
            default:
                return super.onTouchEvent(ev);
        }
    }

    private void animateRestore() {
        animate().translationY(0.0f).alpha(1.0f).setDuration(100L).start();
    }

    private void animateDismiss() {
        final int h = getHeight();
        ValueAnimator anim = ValueAnimator.ofFloat(getTranslationY(), (-h) - 50.0f);
        anim.setDuration(100L);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public final void onAnimationUpdate(ValueAnimator valueAnimator) {
                this.f$0.m93x98cb0eac(h, valueAnimator);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (LandscapeRecentsCardView.this.callback != null) {
                    LandscapeRecentsCardView.this.callback.onCardDismissed(LandscapeRecentsCardView.this);
                }
            }
        });
        anim.start();
    }

    void m93x98cb0eac(int h, ValueAnimator a) {
        float v = ((Float) a.getAnimatedValue()).floatValue();
        setTranslationY(v);
        setAlpha(Math.max(0.0f, 1.0f - (Math.abs(v) / Math.max(1, h))));
    }

    private static float dp(Context ctx, float v) {
        return ctx.getResources().getDisplayMetrics().density * v;
    }
}
