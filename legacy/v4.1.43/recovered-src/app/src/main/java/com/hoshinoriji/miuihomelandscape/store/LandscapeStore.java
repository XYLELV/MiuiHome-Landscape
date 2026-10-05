package com.hoshinoriji.miuihomelandscape.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LandscapeStore {
    private static final String K_DOCK = "dock_items";
    private static final String K_FOLDERS = "folders";
    private static final String K_GRID = "grid_items";
    private static final String K_GRID_FOLDERS = "grid_folders";
    private static final String K_NEXT_FOLDER_ID = "next_folder_id";
    private static final String PREF_NAME = "miui_home_landscape_overlay_v4_store";
    private static volatile LandscapeStore sInstance;
    private final SharedPreferences prefs;

    public static LandscapeStore get(Context ctx) {
        if (sInstance == null) {
            synchronized (LandscapeStore.class) {
                if (sInstance == null) {
                    sInstance = new LandscapeStore(ctx.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private LandscapeStore(Context ctx) {
        Context app = ctx.getApplicationContext();
        Context de = app.createDeviceProtectedStorageContext();
        this.prefs = (de != null ? de : app).getSharedPreferences(PREF_NAME, 0);
    }

    public synchronized void resetDatabase() {
        this.prefs.edit().clear().commit();
    }

    public synchronized List<LandscapeItem> listGrid() {
        List<LandscapeItem> out;
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        Set<Integer> all = new HashSet<>();
        all.addAll(grid.keySet());
        all.addAll(gridFolders.keySet());
        List<Integer> keys = new ArrayList<>(all);
        Collections.sort(keys);
        out = new ArrayList<>();
        for (Integer abs : keys) {
            int slot = GridPosition.slotForAbsoluteIndex(abs.intValue());
            if (GridPosition.isValidSlotIndex(slot)) {
                GridPosition pos = new GridPosition(GridPosition.pageForAbsoluteIndex(abs.intValue()), slot);
                Long folderId = gridFolders.get(abs);
                if (folderId != null) {
                    FolderRecord folder = folders.get(folderId);
                    if (folder != null) {
                        out.add(LandscapeItem.gridFolder(pos, folderId.longValue(), folder.title, folder.children));
                    }
                } else {
                    ComponentKey key = grid.get(abs);
                    if (key != null) {
                        out.add(LandscapeItem.grid(pos, key));
                    }
                }
            }
        }
        return out;
    }

    public synchronized List<GridPosition> appendToGrid(List<ComponentKey> keys) {
        List<GridPosition> written = new ArrayList<>();
        if (keys != null && !keys.isEmpty()) {
            Map<Integer, ComponentKey> grid = readGridMap();
            Map<Integer, Long> gridFolders = readGridFolderMap();
            int cursor = 0;
            for (ComponentKey key : keys) {
                if (key != null) {
                    while (true) {
                        if (!grid.containsKey(Integer.valueOf(cursor)) && !gridFolders.containsKey(Integer.valueOf(cursor))) {
                            break;
                        }
                        cursor++;
                    }
                    grid.put(Integer.valueOf(cursor), key);
                    written.add(new GridPosition(GridPosition.pageForAbsoluteIndex(cursor), GridPosition.slotForAbsoluteIndex(cursor)));
                    cursor++;
                }
            }
            writeGridMap(grid);
            return written;
        }
        return written;
    }

    public synchronized List<GridPosition> appendUniqueToGrid(List<ComponentKey> keys) {
        List<GridPosition> written = new ArrayList<>();
        if (keys != null && !keys.isEmpty()) {
            Map<Integer, ComponentKey> grid = readGridMap();
            Map<Integer, Long> gridFolders = readGridFolderMap();
            Map<Integer, ComponentKey> dock = readDockMap();
            Map<Long, FolderRecord> folders = readFolderMap();
            Set<ComponentKey> existing = new HashSet<>();
            existing.addAll(grid.values());
            existing.addAll(dock.values());
            for (FolderRecord folder : folders.values()) {
                existing.addAll(folder.children);
            }
            int cursor = 0;
            for (ComponentKey key : keys) {
                if (key != null && !existing.contains(key)) {
                    while (true) {
                        if (!grid.containsKey(Integer.valueOf(cursor)) && !gridFolders.containsKey(Integer.valueOf(cursor))) {
                            break;
                        }
                        cursor++;
                    }
                    grid.put(Integer.valueOf(cursor), key);
                    existing.add(key);
                    written.add(new GridPosition(GridPosition.pageForAbsoluteIndex(cursor), GridPosition.slotForAbsoluteIndex(cursor)));
                    cursor++;
                }
            }
            writeGridMap(grid);
            return written;
        }
        return written;
    }

    public synchronized void removeGrid(GridPosition g) {
        if (g == null) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        int abs = g.absoluteIndex();
        Long folderId = gridFolders.remove(Integer.valueOf(abs));
        if (folderId != null) {
            folders.remove(folderId);
        }
        grid.remove(Integer.valueOf(abs));
        writeGridBundle(grid, gridFolders, folders);
    }

    public synchronized void moveOrSwapGrid(GridPosition from, GridPosition to) {
        if (from != null && to != null) {
            if (!from.equals(to)) {
                Map<Integer, ComponentKey> grid = readGridMap();
                Map<Integer, Long> gridFolders = readGridFolderMap();
                Map<Long, FolderRecord> folders = readFolderMap();
                int fromAbs = from.absoluteIndex();
                int toAbs = to.absoluteIndex();
                ComponentKey srcApp = grid.get(Integer.valueOf(fromAbs));
                Long srcFolder = gridFolders.get(Integer.valueOf(fromAbs));
                if (srcApp == null && srcFolder == null) {
                    return;
                }
                ComponentKey dstApp = grid.get(Integer.valueOf(toAbs));
                Long dstFolder = gridFolders.get(Integer.valueOf(toAbs));
                grid.remove(Integer.valueOf(fromAbs));
                grid.remove(Integer.valueOf(toAbs));
                gridFolders.remove(Integer.valueOf(fromAbs));
                gridFolders.remove(Integer.valueOf(toAbs));
                if (srcApp != null) {
                    grid.put(Integer.valueOf(toAbs), srcApp);
                } else {
                    gridFolders.put(Integer.valueOf(toAbs), srcFolder);
                }
                if (dstApp != null) {
                    grid.put(Integer.valueOf(fromAbs), dstApp);
                } else if (dstFolder != null) {
                    gridFolders.put(Integer.valueOf(fromAbs), dstFolder);
                }
                writeGridBundle(grid, gridFolders, folders);
            }
        }
    }

    public synchronized void insertGrid(GridPosition from, GridPosition to) {
        if (from != null && to != null) {
            if (!from.equals(to)) {
                Map<Integer, ComponentKey> grid = readGridMap();
                Map<Integer, Long> gridFolders = readGridFolderMap();
                Map<Long, FolderRecord> folders = readFolderMap();
                int fromAbs = from.absoluteIndex();
                int toAbs = to.absoluteIndex();
                GridEntry src = readGridEntry(grid, gridFolders, fromAbs);
                if (src == null) {
                    return;
                }
                removeGridEntry(grid, gridFolders, fromAbs);
                if (fromAbs < toAbs) {
                    for (int i = fromAbs + 1; i < toAbs; i++) {
                        moveGridEntry(grid, gridFolders, i, i - 1);
                    }
                    int i2 = toAbs - 1;
                    putGridEntry(grid, gridFolders, i2, src);
                } else {
                    for (int i3 = fromAbs - 1; i3 >= toAbs; i3--) {
                        moveGridEntry(grid, gridFolders, i3, i3 + 1);
                    }
                    putGridEntry(grid, gridFolders, toAbs, src);
                }
                writeGridBundle(grid, gridFolders, folders);
            }
        }
    }

    public synchronized void insertDockToGrid(DockPosition from, GridPosition to) {
        if (from == null || to == null) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, ComponentKey> dock = readDockMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        ComponentKey src = dock.get(Integer.valueOf(from.dockIndex));
        if (src == null) {
            return;
        }
        int toAbs = to.absoluteIndex();
        int max = Math.max(toAbs, maxGridAbsoluteIndex(grid, gridFolders));
        for (int i = max; i >= toAbs; i--) {
            moveGridEntry(grid, gridFolders, i, i + 1);
        }
        int i2 = from.dockIndex;
        dock.remove(Integer.valueOf(i2));
        putGridEntry(grid, gridFolders, toAbs, GridEntry.app(src));
        writeAll(grid, dock, gridFolders, folders);
    }

    public synchronized LandscapeItem getGridItem(GridPosition pos) {
        LandscapeItem landscapeItemGridFolder = null;
        if (pos == null) {
            return null;
        }
        int abs = pos.absoluteIndex();
        Map<Integer, ComponentKey> grid = readGridMap();
        ComponentKey app = grid.get(Integer.valueOf(abs));
        if (app != null) {
            return LandscapeItem.grid(pos, app);
        }
        Long folderId = readGridFolderMap().get(Integer.valueOf(abs));
        if (folderId == null) {
            return null;
        }
        FolderRecord folder = readFolderMap().get(folderId);
        if (folder != null) {
            landscapeItemGridFolder = LandscapeItem.gridFolder(pos, folderId.longValue(), folder.title, folder.children);
        }
        return landscapeItemGridFolder;
    }

    public synchronized void createFolderFromGrid(GridPosition from, GridPosition to) {
        if (from != null && to != null) {
            if (!from.equals(to)) {
                Map<Integer, ComponentKey> grid = readGridMap();
                Map<Integer, Long> gridFolders = readGridFolderMap();
                Map<Long, FolderRecord> folders = readFolderMap();
                int fromAbs = from.absoluteIndex();
                int toAbs = to.absoluteIndex();
                ComponentKey src = grid.get(Integer.valueOf(fromAbs));
                ComponentKey dst = grid.get(Integer.valueOf(toAbs));
                if (src != null && dst != null && !gridFolders.containsKey(Integer.valueOf(toAbs))) {
                    long id = nextFolderId();
                    folders.put(Long.valueOf(id), new FolderRecord(defaultFolderTitle(), listOf(dst, src)));
                    grid.remove(Integer.valueOf(fromAbs));
                    grid.remove(Integer.valueOf(toAbs));
                    gridFolders.put(Integer.valueOf(toAbs), Long.valueOf(id));
                    writeGridBundle(grid, gridFolders, folders);
                }
            }
        }
    }

    public synchronized void createFolderFromDock(DockPosition from, GridPosition to) {
        if (from == null || to == null) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, ComponentKey> dock = readDockMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        int toAbs = to.absoluteIndex();
        ComponentKey src = dock.get(Integer.valueOf(from.dockIndex));
        ComponentKey dst = grid.get(Integer.valueOf(toAbs));
        if (src != null && dst != null && !gridFolders.containsKey(Integer.valueOf(toAbs))) {
            long id = nextFolderId();
            folders.put(Long.valueOf(id), new FolderRecord(defaultFolderTitle(), listOf(dst, src)));
            dock.remove(Integer.valueOf(from.dockIndex));
            grid.remove(Integer.valueOf(toAbs));
            gridFolders.put(Integer.valueOf(toAbs), Long.valueOf(id));
            writeAll(grid, dock, gridFolders, folders);
        }
    }

    public synchronized void addGridToFolder(GridPosition from, long folderId) {
        if (from == null || folderId < 0) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        int fromAbs = from.absoluteIndex();
        ComponentKey src = grid.get(Integer.valueOf(fromAbs));
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (src != null && folder != null && !folder.children.contains(src)) {
            grid.remove(Integer.valueOf(fromAbs));
            folder.children.add(src);
            folders.put(Long.valueOf(folderId), folder);
            writeGridBundle(grid, gridFolders, folders);
        }
    }

    public synchronized void insertGridToFolder(GridPosition from, long folderId, int insertIndex) {
        if (from == null || folderId < 0) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        int fromAbs = from.absoluteIndex();
        ComponentKey src = grid.get(Integer.valueOf(fromAbs));
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (src != null && folder != null && !folder.children.contains(src)) {
            grid.remove(Integer.valueOf(fromAbs));
            int target = Math.max(0, Math.min(folder.children.size(), insertIndex));
            folder.children.add(target, src);
            folders.put(Long.valueOf(folderId), folder);
            writeGridBundle(grid, gridFolders, folders);
        }
    }

    public synchronized void addDockToFolder(DockPosition from, long folderId) {
        if (from == null || folderId < 0) {
            return;
        }
        Map<Integer, ComponentKey> dock = readDockMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        ComponentKey src = dock.get(Integer.valueOf(from.dockIndex));
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (src != null && folder != null && !folder.children.contains(src)) {
            dock.remove(Integer.valueOf(from.dockIndex));
            folder.children.add(src);
            folders.put(Long.valueOf(folderId), folder);
            writeDockAndFolders(dock, folders);
        }
    }

    public synchronized void insertDockToFolder(DockPosition from, long folderId, int insertIndex) {
        if (from == null || folderId < 0) {
            return;
        }
        Map<Integer, ComponentKey> dock = readDockMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        ComponentKey src = dock.get(Integer.valueOf(from.dockIndex));
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (src != null && folder != null && !folder.children.contains(src)) {
            dock.remove(Integer.valueOf(from.dockIndex));
            int target = Math.max(0, Math.min(folder.children.size(), insertIndex));
            folder.children.add(target, src);
            folders.put(Long.valueOf(folderId), folder);
            writeDockAndFolders(dock, folders);
        }
    }

    public synchronized void moveFolderChild(long folderId, int fromIndex, int toIndex) {
        if (folderId < 0 || fromIndex == toIndex) {
            return;
        }
        Map<Long, FolderRecord> folders = readFolderMap();
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (folder != null && folder.children != null && !folder.children.isEmpty()) {
            int size = folder.children.size();
            if (fromIndex >= 0 && fromIndex < size) {
                int target = Math.max(0, Math.min(size - 1, toIndex));
                ComponentKey moved = folder.children.remove(fromIndex);
                folder.children.add(target, moved);
                folders.put(Long.valueOf(folderId), folder);
                writeFoldersOnly(folders);
            }
        }
    }

    public synchronized void swapFolderChild(long folderId, int fromIndex, int toIndex) {
        if (folderId < 0 || fromIndex == toIndex) {
            return;
        }
        Map<Long, FolderRecord> folders = readFolderMap();
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (folder != null && folder.children != null && !folder.children.isEmpty()) {
            int size = folder.children.size();
            if (fromIndex >= 0 && fromIndex < size && toIndex >= 0 && toIndex < size) {
                Collections.swap(folder.children, fromIndex, toIndex);
                folders.put(Long.valueOf(folderId), folder);
                writeFoldersOnly(folders);
            }
        }
    }

    public synchronized void renameFolder(long folderId, String title) {
        if (folderId < 0) {
            return;
        }
        Map<Long, FolderRecord> folders = readFolderMap();
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (folder == null) {
            return;
        }
        String nextTitle = title == null ? "" : title.trim();
        folders.put(Long.valueOf(folderId), new FolderRecord(nextTitle, folder.children));
        writeFoldersOnly(folders);
    }

    public synchronized void removeFolderChildToGrid(long folderId, int childIndex) {
        if (folderId < 0) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (folder != null && childIndex >= 0 && childIndex < folder.children.size()) {
            Integer folderAbs = null;
            Iterator<Map.Entry<Integer, Long>> it = gridFolders.entrySet().iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Map.Entry<Integer, Long> entry = it.next();
                if (entry != null && entry.getValue() != null && entry.getValue().longValue() == folderId) {
                    folderAbs = entry.getKey();
                    break;
                }
            }
            if (folderAbs == null) {
                return;
            }
            ComponentKey child = folder.children.remove(childIndex);
            if (child == null) {
                return;
            }
            if (folder.children.size() <= 1) {
                gridFolders.remove(folderAbs);
                folders.remove(Long.valueOf(folderId));
                if (folder.children.size() == 1) {
                    grid.put(folderAbs, folder.children.get(0));
                    int cursor = 0;
                    while (true) {
                        if (!grid.containsKey(Integer.valueOf(cursor)) && !gridFolders.containsKey(Integer.valueOf(cursor))) {
                            break;
                        }
                        cursor++;
                    }
                    grid.put(Integer.valueOf(cursor), child);
                } else {
                    grid.put(folderAbs, child);
                }
            } else {
                folders.put(Long.valueOf(folderId), folder);
                int cursor2 = 0;
                while (true) {
                    if (!grid.containsKey(Integer.valueOf(cursor2)) && !gridFolders.containsKey(Integer.valueOf(cursor2))) {
                        break;
                    }
                    cursor2++;
                }
                grid.put(Integer.valueOf(cursor2), child);
            }
            writeGridBundle(grid, gridFolders, folders);
        }
    }

    public synchronized void removeFolderChildToGrid(long folderId, int childIndex, GridPosition to, boolean insert) {
        if (folderId < 0 || to == null) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        Map<Long, FolderRecord> folders = readFolderMap();
        FolderRecord folder = folders.get(Long.valueOf(folderId));
        if (folder != null && childIndex >= 0 && childIndex < folder.children.size()) {
            Integer folderAbs = null;
            Iterator<Map.Entry<Integer, Long>> it = gridFolders.entrySet().iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Map.Entry<Integer, Long> entry = it.next();
                if (entry != null && entry.getValue() != null && entry.getValue().longValue() == folderId) {
                    folderAbs = entry.getKey();
                    break;
                }
            }
            if (folderAbs == null) {
                return;
            }
            ComponentKey child = folder.children.remove(childIndex);
            if (child == null) {
                return;
            }
            if (folder.children.size() <= 1) {
                gridFolders.remove(folderAbs);
                folders.remove(Long.valueOf(folderId));
                if (folder.children.size() == 1) {
                    grid.put(folderAbs, folder.children.get(0));
                }
            } else {
                folders.put(Long.valueOf(folderId), folder);
            }
            int toAbs = Math.max(0, to.absoluteIndex());
            if (grid.containsKey(Integer.valueOf(toAbs)) || gridFolders.containsKey(Integer.valueOf(toAbs))) {
                int cursor = toAbs;
                while (true) {
                    if (!grid.containsKey(Integer.valueOf(cursor)) && !gridFolders.containsKey(Integer.valueOf(cursor))) {
                        break;
                    }
                    cursor++;
                }
                toAbs = cursor;
            }
            grid.put(Integer.valueOf(toAbs), child);
            writeGridBundle(grid, gridFolders, folders);
        }
    }

    public synchronized void moveGridToDock(GridPosition from, DockPosition to) {
        if (from == null || to == null) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        if (readGridFolderMap().containsKey(Integer.valueOf(from.absoluteIndex()))) {
            return;
        }
        Map<Integer, ComponentKey> dock = readDockMap();
        int fromAbs = from.absoluteIndex();
        ComponentKey src = grid.get(Integer.valueOf(fromAbs));
        if (src == null) {
            return;
        }
        ComponentKey dst = dock.get(Integer.valueOf(to.dockIndex));
        grid.remove(Integer.valueOf(fromAbs));
        dock.remove(Integer.valueOf(to.dockIndex));
        dock.put(Integer.valueOf(to.dockIndex), src);
        if (dst != null) {
            grid.put(Integer.valueOf(fromAbs), dst);
        }
        writeBoth(grid, dock);
    }

    public synchronized void moveDockToGrid(DockPosition from, GridPosition to) {
        if (from == null || to == null) {
            return;
        }
        Map<Integer, ComponentKey> grid = readGridMap();
        Map<Integer, Long> gridFolders = readGridFolderMap();
        if (gridFolders.containsKey(Integer.valueOf(to.absoluteIndex()))) {
            return;
        }
        Map<Integer, ComponentKey> dock = readDockMap();
        ComponentKey src = dock.get(Integer.valueOf(from.dockIndex));
        if (src == null) {
            return;
        }
        int toAbs = to.absoluteIndex();
        ComponentKey dst = grid.get(Integer.valueOf(toAbs));
        dock.remove(Integer.valueOf(from.dockIndex));
        grid.remove(Integer.valueOf(toAbs));
        grid.put(Integer.valueOf(toAbs), src);
        if (dst != null) {
            dock.put(Integer.valueOf(from.dockIndex), dst);
        }
        writeBoth(grid, dock);
    }

    public synchronized void moveOrSwapDock(DockPosition from, DockPosition to) {
        if (from != null && to != null) {
            if (!from.equals(to)) {
                Map<Integer, ComponentKey> dock = readDockMap();
                ComponentKey src = dock.get(Integer.valueOf(from.dockIndex));
                if (src == null) {
                    return;
                }
                ComponentKey dst = dock.get(Integer.valueOf(to.dockIndex));
                dock.remove(Integer.valueOf(from.dockIndex));
                dock.remove(Integer.valueOf(to.dockIndex));
                dock.put(Integer.valueOf(to.dockIndex), src);
                if (dst != null) {
                    dock.put(Integer.valueOf(from.dockIndex), dst);
                }
                writeDockMap(dock);
            }
        }
    }

    public synchronized int maxAbsoluteIndexPlusOne() {
        int max;
        max = -1;
        for (Integer abs : readGridMap().keySet()) {
            if (abs != null && abs.intValue() > max) {
                max = abs.intValue();
            }
        }
        for (Integer abs2 : readGridFolderMap().keySet()) {
            if (abs2 != null && abs2.intValue() > max) {
                max = abs2.intValue();
            }
        }
        return max + 1;
    }

    public synchronized List<LandscapeItem> listDock() {
        List<LandscapeItem> out;
        Map<Integer, ComponentKey> dock = readDockMap();
        List<Integer> keys = new ArrayList<>(dock.keySet());
        Collections.sort(keys);
        out = new ArrayList<>();
        for (Integer index : keys) {
            if (index != null && index.intValue() >= 0 && index.intValue() < 9) {
                out.add(LandscapeItem.dock(new DockPosition(index.intValue()), dock.get(index)));
            }
        }
        return out;
    }

    public synchronized void upsertDock(DockPosition d, ComponentKey k) {
        if (d == null || k == null) {
            return;
        }
        Map<Integer, ComponentKey> dock = readDockMap();
        dock.put(Integer.valueOf(d.dockIndex), k);
        writeDockMap(dock);
    }

    public synchronized void removeDock(DockPosition d) {
        if (d == null) {
            return;
        }
        Map<Integer, ComponentKey> dock = readDockMap();
        dock.remove(Integer.valueOf(d.dockIndex));
        writeDockMap(dock);
    }

    private Map<Integer, ComponentKey> readGridMap() {
        return parseMap(this.prefs.getString(K_GRID, ""));
    }

    private Map<Integer, ComponentKey> readDockMap() {
        return parseMap(this.prefs.getString(K_DOCK, ""));
    }

    private Map<Integer, Long> readGridFolderMap() {
        Map<Integer, Long> out = new HashMap<>();
        String raw = this.prefs.getString(K_GRID_FOLDERS, "");
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    try {
                        out.put(Integer.valueOf(Integer.parseInt(parts[0])), Long.valueOf(Long.parseLong(parts[1])));
                    } catch (Throwable th) {
                    }
                }
            }
        }
        return out;
    }

    private Map<Long, FolderRecord> readFolderMap() {
        Map<Long, FolderRecord> out = new HashMap<>();
        String raw = this.prefs.getString(K_FOLDERS, "");
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    try {
                        long id = Long.parseLong(parts[0]);
                        String title = decode(parts[1]);
                        List<ComponentKey> children = parseComponentList(parts[2]);
                        out.put(Long.valueOf(id), new FolderRecord(title, children));
                    } catch (Throwable th) {
                    }
                }
            }
        }
        return out;
    }

    private void writeGridMap(Map<Integer, ComponentKey> grid) {
        this.prefs.edit().putString(K_GRID, serializeMap(grid)).commit();
    }

    private void writeDockMap(Map<Integer, ComponentKey> dock) {
        this.prefs.edit().putString(K_DOCK, serializeMap(dock)).commit();
    }

    private void writeBoth(Map<Integer, ComponentKey> grid, Map<Integer, ComponentKey> dock) {
        this.prefs.edit().putString(K_GRID, serializeMap(grid)).putString(K_DOCK, serializeMap(dock)).commit();
    }

    private void writeGridBundle(Map<Integer, ComponentKey> grid, Map<Integer, Long> gridFolders, Map<Long, FolderRecord> folders) {
        this.prefs.edit().putString(K_GRID, serializeMap(grid)).putString(K_GRID_FOLDERS, serializeGridFolders(gridFolders)).putString(K_FOLDERS, serializeFolders(folders)).commit();
    }

    private void writeDockAndFolders(Map<Integer, ComponentKey> dock, Map<Long, FolderRecord> folders) {
        this.prefs.edit().putString(K_DOCK, serializeMap(dock)).putString(K_FOLDERS, serializeFolders(folders)).commit();
    }

    private void writeFoldersOnly(Map<Long, FolderRecord> folders) {
        this.prefs.edit().putString(K_FOLDERS, serializeFolders(folders)).commit();
    }

    private void writeAll(Map<Integer, ComponentKey> grid, Map<Integer, ComponentKey> dock, Map<Integer, Long> gridFolders, Map<Long, FolderRecord> folders) {
        this.prefs.edit().putString(K_GRID, serializeMap(grid)).putString(K_DOCK, serializeMap(dock)).putString(K_GRID_FOLDERS, serializeGridFolders(gridFolders)).putString(K_FOLDERS, serializeFolders(folders)).commit();
    }

    private long nextFolderId() {
        long id = this.prefs.getLong(K_NEXT_FOLDER_ID, 1L);
        this.prefs.edit().putLong(K_NEXT_FOLDER_ID, 1 + id).commit();
        return id;
    }

    private static Map<Integer, ComponentKey> parseMap(String raw) {
        Map<Integer, ComponentKey> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                String[] parts = line.split("\t");
                if (parts.length == 4) {
                    try {
                        int index = Integer.parseInt(parts[0]);
                        String pkg = decode(parts[1]);
                        String cls = decode(parts[2]);
                        long serial = Long.parseLong(parts[3]);
                        if (pkg != null && cls != null) {
                            out.put(Integer.valueOf(index), new ComponentKey(pkg, cls, serial));
                        }
                    } catch (Throwable th) {
                    }
                }
            }
        }
        return out;
    }

    private static String serializeMap(Map<Integer, ComponentKey> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        List<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (Integer index : keys) {
            ComponentKey key = map.get(index);
            if (index != null && key != null) {
                sb.append(index).append('\t').append(encode(key.packageName)).append('\t').append(encode(key.className)).append('\t').append(key.userSerial).append('\n');
            }
        }
        return sb.toString();
    }

    private static String serializeGridFolders(Map<Integer, Long> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        List<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (Integer index : keys) {
            Long folderId = map.get(index);
            if (index != null && folderId != null) {
                sb.append(index).append('\t').append(folderId).append('\n');
            }
        }
        return sb.toString();
    }

    private static String serializeFolders(Map<Long, FolderRecord> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        List<Long> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (Long id : keys) {
            FolderRecord folder = map.get(id);
            if (id != null && folder != null) {
                sb.append(id).append('\t').append(encode(folder.title)).append('\t').append(serializeComponentList(folder.children)).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<ComponentKey> parseComponentList(String raw) {
        List<ComponentKey> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String[] entries = raw.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    String pkg = decode(parts[0]);
                    String cls = decode(parts[1]);
                    long serial = Long.parseLong(parts[2]);
                    out.add(new ComponentKey(pkg, cls, serial));
                } catch (Throwable th) {
                }
            }
        }
        return out;
    }

    private static String serializeComponentList(List<ComponentKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ComponentKey key : keys) {
            if (key != null) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(encode(key.packageName)).append(':').append(encode(key.className)).append(':').append(key.userSerial);
            }
        }
        return sb.toString();
    }

    private static List<ComponentKey> listOf(ComponentKey first, ComponentKey second) {
        List<ComponentKey> out = new ArrayList<>();
        if (first != null) {
            out.add(first);
        }
        if (second != null && !second.equals(first)) {
            out.add(second);
        }
        return out;
    }

    public static String defaultFolderTitle() {
        return "文件夹";
    }

    private static GridEntry readGridEntry(Map<Integer, ComponentKey> grid, Map<Integer, Long> gridFolders, int abs) {
        ComponentKey app = grid.get(Integer.valueOf(abs));
        if (app != null) {
            return GridEntry.app(app);
        }
        Long folderId = gridFolders.get(Integer.valueOf(abs));
        if (folderId == null) {
            return null;
        }
        return GridEntry.folder(folderId);
    }

    private static void removeGridEntry(Map<Integer, ComponentKey> grid, Map<Integer, Long> gridFolders, int abs) {
        grid.remove(Integer.valueOf(abs));
        gridFolders.remove(Integer.valueOf(abs));
    }

    private static void putGridEntry(Map<Integer, ComponentKey> grid, Map<Integer, Long> gridFolders, int abs, GridEntry entry) {
        removeGridEntry(grid, gridFolders, abs);
        if (entry == null) {
            return;
        }
        if (entry.app == null) {
            if (entry.folderId != null) {
                gridFolders.put(Integer.valueOf(abs), entry.folderId);
                return;
            }
            return;
        }
        grid.put(Integer.valueOf(abs), entry.app);
    }

    private static void moveGridEntry(Map<Integer, ComponentKey> grid, Map<Integer, Long> gridFolders, int fromAbs, int toAbs) {
        GridEntry entry = readGridEntry(grid, gridFolders, fromAbs);
        removeGridEntry(grid, gridFolders, fromAbs);
        putGridEntry(grid, gridFolders, toAbs, entry);
    }

    private static int maxGridAbsoluteIndex(Map<Integer, ComponentKey> grid, Map<Integer, Long> gridFolders) {
        int max = -1;
        for (Integer abs : grid.keySet()) {
            if (abs != null && abs.intValue() > max) {
                max = abs.intValue();
            }
        }
        for (Integer abs2 : gridFolders.keySet()) {
            if (abs2 != null && abs2.intValue() > max) {
                max = abs2.intValue();
            }
        }
        return max;
    }

    private static String encode(String value) {
        if (value == null) {
            value = "";
        }
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), 2);
    }

    private static String decode(String value) {
        byte[] bytes = Base64.decode(value, 2);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class GridEntry {
        final ComponentKey app;
        final Long folderId;

        private GridEntry(ComponentKey app, Long folderId) {
            this.app = app;
            this.folderId = folderId;
        }

        static GridEntry app(ComponentKey key) {
            return new GridEntry(key, null);
        }

        static GridEntry folder(Long folderId) {
            return new GridEntry(null, folderId);
        }
    }

    private static final class FolderRecord {
        final List<ComponentKey> children;
        final String title;

        FolderRecord(String title, List<ComponentKey> children) {
            this.title = (title == null || title.isEmpty()) ? LandscapeStore.defaultFolderTitle() : title;
            this.children = children == null ? new ArrayList() : new ArrayList(children);
        }
    }
}
