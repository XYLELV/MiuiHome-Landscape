package com.hoshinoriji.miuihomelandscape.overlay;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

/**
 * 按名字读 com.miui.home 的 dimen 资源——让 MIUI 控制图标/文字尺寸。
 * 所有方法 null-safe + 带默认 fallback，绝不抛异常影响 overlay 启动。
 */
public final class MiuiStyleResolver {

    public static final String MIUI_HOME_PKG = "com.miui.home";

    private MiuiStyleResolver() {}

    /** 顺序尝试 dimenNames，取第一个 > 0 的；都没有则返回 fallbackDp 转 px。 */
    public static int resolveDimenPx(Context ctx, int fallbackDp, String... dimenNames) {
        Resources res = ctx.getResources();
        for (String name : dimenNames) {
            int id = res.getIdentifier(name, "dimen", MIUI_HOME_PKG);
            if (id != 0) {
                try {
                    int v = res.getDimensionPixelSize(id);
                    if (v > 0) return v;
                } catch (Throwable ignored) {}
            }
        }
        return dp(ctx, fallbackDp);
    }

    /** 取字号，返回 px。fallbackSp 是 backup。 */
    public static float resolveTextSizePx(Context ctx, int fallbackSp, String... dimenNames) {
        Resources res = ctx.getResources();
        for (String name : dimenNames) {
            int id = res.getIdentifier(name, "dimen", MIUI_HOME_PKG);
            if (id != 0) {
                try {
                    float v = res.getDimension(id);
                    if (v > 0f) return v;
                } catch (Throwable ignored) {}
            }
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fallbackSp,
                ctx.getResources().getDisplayMetrics());
    }

    public static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    public static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
