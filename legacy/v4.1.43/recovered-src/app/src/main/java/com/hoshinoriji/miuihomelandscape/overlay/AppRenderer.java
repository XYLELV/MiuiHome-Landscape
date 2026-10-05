package com.hoshinoriji.miuihomelandscape.overlay;

import android.graphics.drawable.Drawable;
import com.hoshinoriji.miuihomelandscape.model.ComponentKey;

public interface AppRenderer {
    Drawable getIcon(ComponentKey componentKey);

    CharSequence getLabel(ComponentKey componentKey);
}
