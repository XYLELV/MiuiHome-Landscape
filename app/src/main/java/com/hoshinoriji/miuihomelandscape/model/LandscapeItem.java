package com.hoshinoriji.miuihomelandscape.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Single landscape overlay item, either an app shortcut or a landscape folder. */
public final class LandscapeItem {
    public enum Kind { GRID, DOCK }

    public final Kind kind;
    public final ComponentKey key;
    public final long folderId;
    public final String folderTitle;
    public final List<ComponentKey> folderChildren;
    public final int pageIndex;
    public final int slotIndex;
    public final int dockIndex;

    private LandscapeItem(Kind k, ComponentKey c, long fid, String title,
            List<ComponentKey> children, int p, int s, int d) {
        kind = k;
        key = c;
        folderId = fid;
        folderTitle = title;
        folderChildren = children == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(children));
        pageIndex = p;
        slotIndex = s;
        dockIndex = d;
    }

    public static LandscapeItem grid(GridPosition g, ComponentKey k) {
        return new LandscapeItem(Kind.GRID, k, -1L, null, null,
                g.pageIndex, g.slotIndex, -1);
    }

    public static LandscapeItem gridFolder(GridPosition g, long folderId,
            String title, List<ComponentKey> children) {
        return new LandscapeItem(Kind.GRID, null, folderId, title, children,
                g.pageIndex, g.slotIndex, -1);
    }

    public static LandscapeItem dock(DockPosition d, ComponentKey k) {
        return new LandscapeItem(Kind.DOCK, k, -1L, null, null,
                -1, -1, d.dockIndex);
    }

    public boolean isFolder() { return folderId >= 0; }
    public GridPosition asGridPosition() { return new GridPosition(pageIndex, slotIndex); }
    public DockPosition asDockPosition() { return new DockPosition(dockIndex); }
}
