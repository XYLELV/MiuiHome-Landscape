package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

/** A phone-landscape task switcher which never reaches into MIUI private Views. */
final class LandscapeRecentsView extends FrameLayout {
    interface Listener {
        void onLaunch(RecentTaskItem task);
        void onDismiss(RecentTaskItem task);
        void onClear();
        void onRefresh();
        /** Tap outside every card and button. */
        void onBlankTap();
    }

    private static final int COLOR_SURFACE = Color.rgb(20, 22, 28);
    private static final int COLOR_CARD = Color.rgb(43, 46, 56);
    private static final int COLOR_BUTTON = Color.rgb(62, 66, 78);

    private final LinearLayout cards;
    private final TextView clearButton;
    private final TextView refreshButton;
    private final ProgressBar progress;
    private Listener listener;
    private boolean busy;
    private int taskCount;
    /** Next bind is the first of a new Recents session and should animate the cards in. */
    private boolean entrancePending;
    private View pendingDismissCard;
    /** Exit animation running: the session is ending, so cards and buttons ignore taps. */
    private boolean exiting;

    LandscapeRecentsView(Context context) {
        super(context);
        setBackgroundColor(COLOR_SURFACE);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("横屏最近任务");

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        addView(body, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(28), dp(10), dp(28), dp(8));
        body.addView(toolbar, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(64)));

        TextView title = text("最近任务", 20, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f));

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progress.setVisibility(GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        progressLp.setMarginEnd(dp(10));
        toolbar.addView(progress, progressLp);

        refreshButton = toolbarButton("刷新");
        clearButton = toolbarButton("全部清除");
        toolbar.addView(refreshButton, buttonParams());
        toolbar.addView(clearButton, buttonParams());

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        scroller.setPadding(dp(18), dp(4), dp(18), dp(24));
        body.addView(scroller, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(cards, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        refreshButton.setOnClickListener(v -> {
            if (!busy && !exiting && listener != null) listener.onRefresh();
        });
        // Cards consume their own taps; the remaining card strip, toolbar and margins go home.
        View.OnClickListener blank = v -> {
            if (!exiting && listener != null) listener.onBlankTap();
        };
        setOnClickListener(blank);
        cards.setClickable(true);
        cards.setOnClickListener(blank);
        clearButton.setOnClickListener(v -> {
            if (!busy && !exiting && listener != null) listener.onClear();
        });
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void bind(List<RecentTaskItem> tasks) {
        taskCount = tasks.size();
        pendingDismissCard = null;
        cards.removeAllViews();
        boolean animateEntrance = entrancePending;
        entrancePending = false;
        if (tasks.isEmpty()) {
            TextView empty = text("没有最近任务", 16, Color.LTGRAY);
            empty.setGravity(Gravity.CENTER);
            cards.addView(empty, new LinearLayout.LayoutParams(
                    Math.max(dp(300), getResources().getDisplayMetrics().widthPixels - dp(36)),
                    LayoutParams.MATCH_PARENT));
            clearButton.setEnabled(false);
            clearButton.setAlpha(0.4f);
            return;
        }

        clearButton.setEnabled(!busy);
        clearButton.setAlpha(busy ? 0.4f : 1f);
        int index = 0;
        for (RecentTaskItem task : tasks) {
            View card = createCard(task);
            cards.addView(card, cardParams());
            if (animateEntrance) animateCardIn(card, index++);
        }
    }

    void prepareEntrance() {
        entrancePending = true;
    }

    private void animateCardIn(View card, int index) {
        card.setAlpha(0f);
        card.setTranslationY(dp(36));
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(Math.min(6, index) * 32L)
                .setDuration(240L)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /** Lifts the dismissed card away immediately; the list is rebuilt once MIUI confirms. */
    private void animateCardDismiss(View card) {
        pendingDismissCard = card;
        card.animate().cancel();
        card.animate()
                .alpha(0f)
                .translationY(-card.getHeight() * 0.35f)
                .setStartDelay(0L)
                .setDuration(180L)
                .setInterpolator(new AccelerateInterpolator())
                .start();
    }

    /** MIUI did not remove the task: put its card back. */
    void restorePendingDismiss() {
        View card = pendingDismissCard;
        pendingDismissCard = null;
        if (card == null) return;
        card.animate().cancel();
        card.animate().alpha(1f).translationY(0f).setDuration(160L)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    void animateExit(Runnable endAction) {
        exiting = true;
        animate().cancel();
        animate()
                .alpha(0f)
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(160L)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(endAction)
                .start();
    }

    /** Called whenever the view is hidden or retaken so no half-finished transition survives. */
    void cancelTransitions() {
        exiting = false;
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        pendingDismissCard = null;
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        progress.setVisibility(busy ? VISIBLE : GONE);
        refreshButton.setEnabled(!busy);
        clearButton.setEnabled(!busy && taskCount > 0);
        refreshButton.setAlpha(busy ? 0.4f : 1f);
        clearButton.setAlpha(busy ? 0.4f : 1f);
    }

    private View createCard(RecentTaskItem task) {
        FrameLayout card = new FrameLayout(getContext());
        card.setBackground(rounded(COLOR_CARD, 22));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("打开 " + task.title);
        card.setOnClickListener(v -> {
            if (!busy && !exiting && listener != null) listener.onLaunch(task);
        });

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(28), dp(24), dp(22));
        card.addView(content, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        ImageView icon = new ImageView(getContext());
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (task.icon != null) icon.setImageDrawable(task.icon);
        content.addView(icon, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView label = text(task.title, 15, Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(18);
        content.addView(label, labelLp);

        TextView close = text("×", 24, Color.WHITE);
        close.setGravity(Gravity.CENTER);
        close.setBackground(rounded(Color.argb(190, 72, 76, 88), 18));
        close.setContentDescription("关闭 " + task.title);
        close.setOnClickListener(v -> {
            if (busy || exiting || listener == null) return;
            animateCardDismiss(card);
            listener.onDismiss(task);
        });
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dp(38), dp(38),
                Gravity.TOP | Gravity.END);
        closeLp.setMargins(0, dp(12), dp(12), 0);
        card.addView(close, closeLp);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        int width = Math.min(dp(228),
                Math.max(dp(180), getResources().getDisplayMetrics().widthPixels / 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width,
                LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(10), dp(8), dp(10), dp(8));
        return lp;
    }

    private TextView toolbarButton(String value) {
        TextView button = text(value, 14, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(COLOR_BUTTON, 18));
        button.setMinWidth(dp(72));
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(38));
        lp.setMarginStart(dp(10));
        return lp;
    }

    private TextView text(CharSequence value, float sp, int color) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sp);
        return text;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
