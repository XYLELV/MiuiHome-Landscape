package com.hoshinoriji.miuihomelandscape.model;

public final class DockPosition {
    public static final int SLOTS = 9;
    public final int dockIndex;

    public DockPosition(int dockIndex) {
        if (dockIndex < 0 || dockIndex >= 9) {
            throw new IllegalArgumentException("dockIndex " + dockIndex);
        }
        this.dockIndex = dockIndex;
    }

    public boolean equals(Object o) {
        return (o instanceof DockPosition) && ((DockPosition) o).dockIndex == this.dockIndex;
    }

    public int hashCode() {
        return this.dockIndex;
    }

    public String toString() {
        return "D(" + this.dockIndex + ")";
    }
}
