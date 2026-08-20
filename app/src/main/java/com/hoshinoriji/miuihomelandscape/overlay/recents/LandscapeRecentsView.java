package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
            if (!busy && listener != null) listener.onRefresh();
        });
        clearButton.setOnClickListener(v -> {
            if (!busy && listener != null) listener.onClear();
        });
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void bind(List<RecentTaskItem> tasks) {
        taskCount = tasks.size();
        cards.removeAllViews();
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
        for (RecentTaskItem task : tasks) cards.addView(createCard(task), cardParams());
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
            if (!busy && listener != null) listener.onLaunch(task);
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
            if (!busy && listener != null) listener.onDismiss(task);
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
