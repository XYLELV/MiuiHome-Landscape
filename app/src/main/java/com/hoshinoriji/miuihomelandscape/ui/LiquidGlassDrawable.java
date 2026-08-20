package com.hoshinoriji.miuihomelandscape.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Lightweight blue-white glass surface with rim light and refractive color bloom. */
public final class LiquidGlassDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloom = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private final float radius;
    private int drawableAlpha = 255;

    public LiquidGlassDrawable(float radiusPx) {
        radius = radiusPx;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, radiusPx / 16f));
        innerRim.setStyle(Paint.Style.STROKE);
        innerRim.setStrokeWidth(Math.max(1f, radiusPx / 28f));
    }

    @Override protected void onBoundsChange(Rect bounds) {
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        float width = Math.max(1f, rect.width());
        float height = Math.max(1f, rect.height());
        fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0xE8FFFFFF, 0xBDE8F6FF, 0xA8CBE9FF, 0xDDFBFDFF},
                new float[]{0f, 0.38f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        bloom.setShader(new RadialGradient(rect.left + width * 0.16f,
                rect.top + height * 0.06f, Math.max(width, height) * 0.8f,
                new int[]{0x8FFFFFFF, 0x24388FFF, Color.TRANSPARENT},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0xF8FFFFFF, 0x7278C8FF, 0xEFFFFFFF}, null,
                Shader.TileMode.CLAMP));
        innerRim.setColor(0x58FFFFFF);
    }

    @Override public void draw(Canvas canvas) {
        int checkpoint = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        fill.setAlpha(drawableAlpha);
        bloom.setAlpha(drawableAlpha);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, bloom);
        canvas.restoreToCount(checkpoint);

        rim.setAlpha(drawableAlpha);
        innerRim.setAlpha(Math.round(drawableAlpha * 0.56f));
        RectF outer = new RectF(rect);
        outer.inset(rim.getStrokeWidth() / 2f, rim.getStrokeWidth() / 2f);
        canvas.drawRoundRect(outer, radius, radius, rim);
        RectF inner = new RectF(rect);
        inner.inset(Math.max(2f, radius / 9f), Math.max(2f, radius / 9f));
        canvas.drawRoundRect(inner, Math.max(1f, radius - radius / 9f),
                Math.max(1f, radius - radius / 9f), innerRim);
    }

    @Override public void setAlpha(int alpha) {
        drawableAlpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        bloom.setColorFilter(colorFilter);
        rim.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
