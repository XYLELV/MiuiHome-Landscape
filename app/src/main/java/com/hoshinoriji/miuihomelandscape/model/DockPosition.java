package com.hoshinoriji.miuihomelandscape.model;

/** Dock 坐标。dockIndex=0..8，和 GridPosition 是两套独立类型。 */
public final class DockPosition {
    public static final int SLOTS = 9;
    public final int dockIndex;
    public DockPosition(int dockIndex) {
        if (dockIndex < 0 || dockIndex >= SLOTS)
            throw new IllegalArgumentException("dockIndex " + dockIndex);
        this.dockIndex = dockIndex;
    }
    @Override public boolean equals(Object o) {
        return (o instanceof DockPosition) && ((DockPosition) o).dockIndex == dockIndex;
    }
    @Override public int hashCode() { return dockIndex; }
    @Override public String toString() { return "D(" + dockIndex + ")"; }
}
