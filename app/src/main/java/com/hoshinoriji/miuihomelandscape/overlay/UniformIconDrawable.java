package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * Gives legacy, circular and adaptive application icons one consistent edge-to-edge MIUI mask.
 * There is deliberately no synthetic light tile or border: those produced a visible white rim.
 */
public final class UniformIconDrawable extends Drawable {
    private static final float CORNER_RATIO = 0.225f;
    private static final float LEGACY_OVERSCAN_RATIO = 0.10f;

    private final Drawable icon;
    private final Path clipPath = new Path();
    private final RectF tile = new RectF();
    private int alpha = 255;

    public static Drawable wrap(Context context, Drawable drawable) {
        if (drawable == null || drawable instanceof UniformIconDrawable) return drawable;
        return new UniformIconDrawable(context, drawable.mutate());
    }

    private UniformIconDrawable(Context context, Drawable drawable) {
        icon = drawable;
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        tile.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        float radius = Math.min(tile.width(), tile.height()) * CORNER_RATIO;

        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(tile, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        // Adaptive icons already provide a full-bleed background. Legacy circular bitmaps need
        // a small overscan so their transparent outside pixels cannot reveal a pale halo.
        int overscan = icon instanceof AdaptiveIconDrawable ? 0
                : Math.round(Math.min(tile.width(), tile.height()) * LEGACY_OVERSCAN_RATIO);
        Rect oldBounds = new Rect(icon.getBounds());
        icon.setBounds(bounds.left - overscan, bounds.top - overscan,
                bounds.right + overscan, bounds.bottom + overscan);
        icon.setAlpha(alpha);
        icon.draw(canvas);
        icon.setBounds(oldBounds);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int value) {
        alpha = Math.max(0, Math.min(255, value));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        icon.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override public int getIntrinsicWidth() {
        return icon.getIntrinsicWidth();
    }

    @Override public int getIntrinsicHeight() {
        return icon.getIntrinsicHeight();
    }
}
