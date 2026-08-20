package com.hoshinoriji.miuihomelandscape.overlay;

import android.graphics.drawable.Drawable;

import com.hoshinoriji.miuihomelandscape.model.ComponentKey;

/** 抽象：根据 ComponentKey 取图标和 label。由 Controller 注入具体实现。 */
public interface AppRenderer {
    Drawable getIcon(ComponentKey key);
    CharSequence getLabel(ComponentKey key);
}
