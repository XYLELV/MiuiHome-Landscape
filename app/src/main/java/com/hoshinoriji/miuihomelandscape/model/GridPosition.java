package com.hoshinoriji.miuihomelandscape.model;

/**
 * 横屏主区坐标 (pageIndex, slotIndex)。
 *
 * V5：8 列 × 3 行，每页 24 格；页数按实际使用动态增长。
 * 不设置 slot 0 为 "+添加应用" 入口——入口彻底隐藏，由 overlay 空白区长按触发。
 */
public final class GridPosition {
    public static final int COLS = 8;
    public static final int ROWS = 3;
    public static final int SLOTS_PER_PAGE = COLS * ROWS; // 24

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
        return slotIndex >= 0 && slotIndex < SLOTS_PER_PAGE;
    }

    public static int pageForAbsoluteIndex(int absoluteIndex) {
        if (absoluteIndex < 0) throw new IllegalArgumentException("absoluteIndex " + absoluteIndex);
        return absoluteIndex / SLOTS_PER_PAGE;
    }

    public static int slotForAbsoluteIndex(int absoluteIndex) {
        if (absoluteIndex < 0) throw new IllegalArgumentException("absoluteIndex " + absoluteIndex);
        return absoluteIndex % SLOTS_PER_PAGE;
    }

    public static int toAbsoluteIndex(int pageIndex, int slotIndex) {
        return pageIndex * SLOTS_PER_PAGE + slotIndex;
    }

    public int absoluteIndex() {
        return toAbsoluteIndex(pageIndex, slotIndex);
    }

    /** 网格内 (row, col) 坐标，row∈[0,ROWS), col∈[0,COLS) */
    public int row() { return slotIndex / COLS; }
    public int col() { return slotIndex % COLS; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof GridPosition)) return false;
        GridPosition g = (GridPosition) o;
        return g.pageIndex == pageIndex && g.slotIndex == slotIndex;
    }
    @Override public int hashCode() { return pageIndex * 31 + slotIndex; }
    @Override public String toString() { return "G(p=" + pageIndex + ",s=" + slotIndex + ")"; }
}
