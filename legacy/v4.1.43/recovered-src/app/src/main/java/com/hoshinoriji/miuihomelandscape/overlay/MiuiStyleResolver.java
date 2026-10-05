package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

public final class MiuiStyleResolver {
    public static final String MIUI_HOME_PKG = "com.miui.home";

    private MiuiStyleResolver() {
    }

    public static int resolveDimenPx(Context ctx, int fallbackDp, String... dimenNames) {
        Resources res = ctx.getResources();
        for (String name : dimenNames) {
            int id = res.getIdentifier(name, "dimen", "com.miui.home");
            if (id != 0) {
                try {
                    int v = res.getDimensionPixelSize(id);
                    if (v > 0) {
                        return v;
                    }
                } catch (Throwable th) {
                }
            }
        }
        return dp(ctx, fallbackDp);
    }

    public static float resolveTextSizePx(Context ctx, int fallbackSp, String... dimenNames) {
        Resources res = ctx.getResources();
        for (String name : dimenNames) {
            int id = res.getIdentifier(name, "dimen", "com.miui.home");
            if (id != 0) {
                try {
                    float v = res.getDimension(id);
                    if (v > 0.0f) {
                        return v;
                    }
                } catch (Throwable th) {
                }
            }
        }
        return TypedValue.applyDimension(2, fallbackSp, ctx.getResources().getDisplayMetrics());
    }

    public static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    public static int dp(Context ctx, float v) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * v);
    }
}
