package com.hoshinoriji.miuihomelandscape.overlay.recents;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Immutable UI projection of an API 33 RecentTaskInfo object. */
final class RecentTaskItem {
    final int taskId;
    final Intent baseIntent;
    final CharSequence title;
    final Drawable icon;

    RecentTaskItem(int taskId, Intent baseIntent, CharSequence title, Drawable icon) {
        this.taskId = taskId;
        this.baseIntent = baseIntent;
        this.title = title;
        this.icon = icon;
    }
}
