package com.hoshinoriji.miuihomelandscape.core;

import android.view.View;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reversible ownership of the small set of native Launcher views hidden by the
 * landscape surface.  We intentionally do not replace MIUI listeners: the
 * overlay is the top-most touch target, so listener clobbering is unnecessary.
 */
public final class NativeViewLease {
    private final Map<View, State> states = new IdentityHashMap<>();

    public void hide(View... views) {
        if (views == null) return;
        for (View view : views) {
            if (view == null) continue;
            if (!states.containsKey(view)) states.put(view, State.capture(view));
            view.setVisibility(View.GONE);
            view.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    public void restoreAll() {
        for (Map.Entry<View, State> entry : states.entrySet()) {
            View view = entry.getKey();
            State state = entry.getValue();
            if (view == null || state == null) continue;
            view.setVisibility(state.visibility);
            view.setImportantForAccessibility(state.importantForAccessibility);
        }
        states.clear();
    }

    public boolean isHeld() {
        return !states.isEmpty();
    }

    private static final class State {
        final int visibility;
        final int importantForAccessibility;

        State(int visibility, int importantForAccessibility) {
            this.visibility = visibility;
            this.importantForAccessibility = importantForAccessibility;
        }

        static State capture(View view) {
            return new State(view.getVisibility(), view.getImportantForAccessibility());
        }
    }
}
