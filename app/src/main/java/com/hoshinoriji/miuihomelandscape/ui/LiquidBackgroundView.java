package com.hoshinoriji.miuihomelandscape.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Animated blue-white backdrop used only while the settings Activity is visible. */
public final class LiquidBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private final Matrix blobMatrix1 = new Matrix();
    private final Matrix blobMatrix2 = new Matrix();
    private final Matrix blobMatrix3 = new Matrix();
    private Shader baseShader;
    private RadialGradient blobShader1;
    private RadialGradient blobShader2;
    private RadialGradient blobShader3;
    private ValueAnimator animator;
    private float phase;

    public LiquidBackgroundView(Context context) {
        super(context);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(1.2f));
        line.setColor(0x55FFFFFF);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(14000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float w = Math.max(1f, width);
        float h = Math.max(1f, height);
        float max = Math.max(w, h);
        baseShader = new LinearGradient(0f, 0f, w, h,
                new int[]{0xFFF9FCFF, 0xFFE2F3FF, 0xFFBBDFFF, 0xFFF4FBFF},
                new float[]{0f, 0.42f, 0.73f, 1f}, Shader.TileMode.CLAMP);
        blobShader1 = blobShader(max * 0.48f, 0xA87BC8FF);
        blobShader2 = blobShader(max * 0.42f, 0x8E4D8FFF);
        blobShader3 = blobShader(max * 0.40f, 0x76FFFFFF);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());
        paint.setShader(baseShader);
        canvas.drawRect(0f, 0f, width, height, paint);

        drawBlob(canvas, blobShader1, blobMatrix1,
                width * (0.15f + phase * 0.12f), height * 0.18f);
        drawBlob(canvas, blobShader2, blobMatrix2,
                width * (0.88f - phase * 0.14f), height * 0.54f);
        drawBlob(canvas, blobShader3, blobMatrix3,
                width * 0.44f, height * (0.92f - phase * 0.10f));

        wave.reset();
        wave.moveTo(-width * 0.05f, height * (0.70f - phase * 0.03f));
        wave.cubicTo(width * 0.23f, height * 0.58f,
                width * 0.63f, height * 0.84f,
                width * 1.06f, height * 0.62f);
        canvas.drawPath(wave, line);
    }

    private void drawBlob(Canvas canvas, RadialGradient shader, Matrix matrix, float x, float y) {
        if (shader == null) return;
        matrix.reset();
        matrix.setTranslate(x, y);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
    }

    private static RadialGradient blobShader(float radius, int color) {
        return new RadialGradient(0f, 0f, Math.max(1f, radius),
                new int[]{color, Color.TRANSPARENT}, new float[]{0f, 1f},
                Shader.TileMode.CLAMP);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
