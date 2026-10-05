package com.hoshinoriji.miuihomelandscape.model;

public final class GridPosition {
    public static final int COLS = 8;
    public static final int ROWS = 3;
    public static final int SLOTS_PER_PAGE = 24;
    public final int pageIndex;
    public final int slotIndex;

    public GridPosition(int pageIndex, int slotIndex) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex " + pageIndex);
        }
        if (!isValidSlotIndex(slotIndex)) {
            throw new IllegalArgumentException("slotIndex " + slotIndex);
        }
        this.pageIndex = pageIndex;
        this.slotIndex = slotIndex;
    }

    public static boolean isValidSlotIndex(int slotIndex) {
        return slotIndex >= 0 && slotIndex < 24;
    }

    public static int pageForAbsoluteIndex(int absoluteIndex) {
        if (absoluteIndex < 0) {
            throw new IllegalArgumentException("absoluteIndex " + absoluteIndex);
        }
        return absoluteIndex / 24;
    }

    public static int slotForAbsoluteIndex(int absoluteIndex) {
        if (absoluteIndex < 0) {
            throw new IllegalArgumentException("absoluteIndex " + absoluteIndex);
        }
        return absoluteIndex % 24;
    }

    public static int toAbsoluteIndex(int pageIndex, int slotIndex) {
        return (pageIndex * 24) + slotIndex;
    }

    public int absoluteIndex() {
        return toAbsoluteIndex(this.pageIndex, this.slotIndex);
    }

    public int row() {
        return this.slotIndex / 8;
    }

    public int col() {
        return this.slotIndex % 8;
    }

    public boolean equals(Object o) {
        if (!(o instanceof GridPosition)) {
            return false;
        }
        GridPosition g = (GridPosition) o;
        return g.pageIndex == this.pageIndex && g.slotIndex == this.slotIndex;
    }

    public int hashCode() {
        return (this.pageIndex * 31) + this.slotIndex;
    }

    public String toString() {
        return "G(p=" + this.pageIndex + ",s=" + this.slotIndex + ")";
    }
}
