package com.hoshinoriji.miuihomelandscape.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LandscapeItem {
    public final int dockIndex;
    public final List<ComponentKey> folderChildren;
    public final long folderId;
    public final String folderTitle;
    public final ComponentKey key;
    public final Kind kind;
    public final int pageIndex;
    public final int slotIndex;

    public enum Kind {
        GRID,
        DOCK
    }

    private LandscapeItem(Kind k, ComponentKey c, long fid, String title, List<ComponentKey> children, int p, int s, int d) {
        List<ComponentKey> listUnmodifiableList;
        this.kind = k;
        this.key = c;
        this.folderId = fid;
        this.folderTitle = title;
        if (children == null) {
            listUnmodifiableList = Collections.emptyList();
        } else {
            listUnmodifiableList = Collections.unmodifiableList(new ArrayList(children));
        }
        this.folderChildren = listUnmodifiableList;
        this.pageIndex = p;
        this.slotIndex = s;
        this.dockIndex = d;
    }

    public static LandscapeItem grid(GridPosition g, ComponentKey k) {
        return new LandscapeItem(Kind.GRID, k, -1L, null, null, g.pageIndex, g.slotIndex, -1);
    }

    public static LandscapeItem gridFolder(GridPosition g, long folderId, String title, List<ComponentKey> children) {
        return new LandscapeItem(Kind.GRID, null, folderId, title, children, g.pageIndex, g.slotIndex, -1);
    }

    public static LandscapeItem dock(DockPosition d, ComponentKey k) {
        return new LandscapeItem(Kind.DOCK, k, -1L, null, null, -1, -1, d.dockIndex);
    }

    public boolean isFolder() {
        return this.folderId >= 0;
    }

    public GridPosition asGridPosition() {
        return new GridPosition(this.pageIndex, this.slotIndex);
    }

    public DockPosition asDockPosition() {
        return new DockPosition(this.dockIndex);
    }
}
