package com.hoshinoriji.miuihomelandscape.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transactional storage for the independent landscape layout.
 *
 * <p>The complete layout is one versioned JSON snapshot. Every successful mutation writes the
 * previous valid snapshot to a backup key, the new snapshot, and the new revision in one
 * {@link SharedPreferences.Editor#commit()} transaction. A malformed or unreadable snapshot is
 * never interpreted as an empty layout and is never automatically cleared.</p>
 */
public final class LandscapeStore {

    private static final String TAG = "MIHL.Store";
    private static final String PREF_NAME = "miui_home_landscape_overlay_v4_store";

    private static final String K_SNAPSHOT = "layout_snapshot_v5";
    private static final String K_BACKUP = "layout_snapshot_v5_backup";
    private static final String K_REVISION = "layout_revision_v5";

    // Read-only migration support for layouts written by the v4 multi-key store.
    private static final String LEGACY_GRID = "grid_items";
    private static final String LEGACY_DOCK = "dock_items";
    private static final String LEGACY_GRID_FOLDERS = "grid_folders";
    private static final String LEGACY_FOLDERS = "folders";
    private static final String LEGACY_NEXT_FOLDER_ID = "next_folder_id";

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_GRID_PAGES = 256;
    private static final int MAX_GRID_ABSOLUTE_INDEX =
            MAX_GRID_PAGES * GridPosition.SLOTS_PER_PAGE - 1;
    private static final int MAX_FOLDERS = 512;
    private static final int MAX_FOLDER_CHILDREN = 256;
    private static final int MAX_COMPONENT_TEXT_LENGTH = 1024;
    private static final int MAX_FOLDER_TITLE_LENGTH = 80;

    private static volatile LandscapeStore sInstance;

    private final SharedPreferences prefs;
    private boolean writeBlocked;

    public static LandscapeStore get(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        LandscapeStore instance = sInstance;
        if (instance == null) {
            synchronized (LandscapeStore.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new LandscapeStore(context.getApplicationContext());
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    private LandscapeStore(Context context) {
        Context app = context == null ? null : context.getApplicationContext();
        if (app == null) app = context;
        Context deviceProtected = app.createDeviceProtectedStorageContext();
        if (deviceProtected != null) app = deviceProtected;
        prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Kept only so an older controller still links. Automatic database reset is deliberately
     * disabled: recovery code must never erase a user's layout because an unrelated I/O failed.
     */
    @Deprecated
    public synchronized void resetDatabase() {
        Log.w(TAG, "resetDatabase() ignored; call resetLayout() only after explicit user consent");
    }

    /** Explicit destructive reset intended to be called only from a confirmed user action. */
    public synchronized boolean resetLayout() {
        LoadedSnapshot loaded = loadSnapshot();
        long snapshotRevision = loaded.readable && loaded.snapshot != null
                ? loaded.snapshot.revision : 0L;
        long previousRevision = Math.max(snapshotRevision, safeStoredRevision());
        if (previousRevision == Long.MAX_VALUE) return false;

        LayoutSnapshot empty = LayoutSnapshot.empty(previousRevision + 1L);
        try {
            PreferenceState before = capturePreferenceState();
            String nextJson = serializeSnapshot(empty);
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(K_SNAPSHOT, nextJson)
                    .putLong(K_REVISION, empty.revision);
            // Preserve the last known-good state. If storage is corrupt, leave its backup intact.
            if (loaded.readable) {
                String previousJson = loaded.rawJson != null
                        ? loaded.rawJson : serializeSnapshot(loaded.snapshot);
                editor.putString(K_BACKUP, previousJson);
            }
            boolean committed = commitWithRollback(editor, before, "explicit reset");
            if (committed) writeBlocked = false;
            return committed;
        } catch (RuntimeException | JSONException e) {
            Log.e(TAG, "Explicit layout reset failed", e);
            return false;
        }
    }

    public synchronized long getRevision() {
        LoadedSnapshot loaded = loadSnapshot();
        return loaded.readable ? loaded.snapshot.revision : -1L;
    }

    /** Reads grid, dock, revision and health from one validated snapshot. */
    public synchronized LayoutRead readLayout() {
        LoadedSnapshot loaded = loadSnapshot();
        if (!loaded.readable) return LayoutRead.unreadable();
        return LayoutRead.readable(
                loaded.snapshot.revision,
                loaded.snapshot.initialized,
                buildGridItems(loaded.snapshot),
                buildDockItems(loaded.snapshot));
    }

    public synchronized boolean isReadable() {
        return loadSnapshot().readable;
    }

    public synchronized List<LandscapeItem> listGrid() {
        LoadedSnapshot loaded = loadSnapshot();
        if (!loaded.readable) return Collections.emptyList();
        return buildGridItems(loaded.snapshot);
    }

    private static List<LandscapeItem> buildGridItems(LayoutSnapshot state) {
        Set<Integer> occupied = new HashSet<>();
        occupied.addAll(state.grid.keySet());
        occupied.addAll(state.gridFolders.keySet());
        List<Integer> indexes = new ArrayList<>(occupied);
        Collections.sort(indexes);

        List<LandscapeItem> result = new ArrayList<>(indexes.size());
        for (Integer absolute : indexes) {
            GridPosition position = positionForAbsolute(absolute);
            if (position == null) continue;
            ComponentKey app = state.grid.get(absolute);
            if (app != null) {
                result.add(LandscapeItem.grid(position, app));
                continue;
            }
            Long folderId = state.gridFolders.get(absolute);
            FolderRecord folder = folderId == null ? null : state.folders.get(folderId);
            if (folder != null) {
                result.add(LandscapeItem.gridFolder(
                        position, folderId, folder.title, folder.children));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized List<LandscapeItem> listDock() {
        LoadedSnapshot loaded = loadSnapshot();
        if (!loaded.readable) return Collections.emptyList();
        return buildDockItems(loaded.snapshot);
    }

    private static List<LandscapeItem> buildDockItems(LayoutSnapshot state) {
        List<Integer> indexes = new ArrayList<>(state.dock.keySet());
        Collections.sort(indexes);
        List<LandscapeItem> result = new ArrayList<>(indexes.size());
        for (Integer index : indexes) {
            if (!isValidDockIndex(index)) continue;
            result.add(LandscapeItem.dock(
                    new DockPosition(index), state.dock.get(index)));
        }
        return Collections.unmodifiableList(result);
    }

    /** Compatibility alias. Duplicates are rejected just like appendUniqueToGrid. */
    public synchronized List<GridPosition> appendToGrid(List<ComponentKey> keys) {
        return appendUniqueToGrid(keys);
    }

    public synchronized List<GridPosition> appendUniqueToGrid(List<ComponentKey> keys) {
        if (keys == null) return Collections.emptyList();
        Mutation mutation = beginMutation();
        if (mutation == null) return Collections.emptyList();

        Set<ComponentKey> existing = allComponents(mutation.next);
        List<GridPosition> written = new ArrayList<>();
        int cursor = 0;
        for (ComponentKey key : keys) {
            if (!isValidComponent(key) || existing.contains(key)) continue;
            int free = findFreeGridSlot(mutation.next, cursor);
            if (free < 0) break;
            mutation.next.grid.put(free, key);
            existing.add(key);
            written.add(positionForAbsolute(free));
            cursor = free + 1;
        }
        boolean initializationChanged = !mutation.next.initialized;
        mutation.next.initialized = true;
        if (written.isEmpty() && !initializationChanged) return Collections.emptyList();
        return persist(mutation)
                ? Collections.unmodifiableList(written) : Collections.emptyList();
    }

    /** Returns null when both persisted snapshots are unreadable. */
    public synchronized Set<ComponentKey> listComponentKeys() {
        LoadedSnapshot loaded = loadSnapshot();
        if (loaded.snapshot == null) return null;
        return Collections.unmodifiableSet(new HashSet<>(allComponents(loaded.snapshot)));
    }

    /** Atomically adds an app to the first free Grid slot or removes it from any container. */
    public synchronized boolean setComponentEnabled(ComponentKey key, boolean enabled) {
        if (!isValidComponent(key)) return false;
        Mutation mutation = beginMutation();
        if (mutation == null) return false;
        boolean currentlyEnabled = allComponents(mutation.next).contains(key);
        if (currentlyEnabled == enabled) return true;
        if (enabled) {
            int free = findFreeGridSlot(mutation.next, 0);
            if (free < 0) return false;
            mutation.next.grid.put(free, key);
            mutation.next.initialized = true;
        } else {
            removeComponentFromOtherContainer(mutation.next, -1L, key);
        }
        return persist(mutation);
    }

    public synchronized void removeGrid(GridPosition position) {
        if (!isValidGridPosition(position)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        int absolute = position.absoluteIndex();
        ComponentKey app = mutation.next.grid.remove(absolute);
        Long folderId = mutation.next.gridFolders.remove(absolute);
        if (folderId != null) mutation.next.folders.remove(folderId);
        if (app != null || folderId != null) persist(mutation);
    }

    public synchronized void moveOrSwapGrid(GridPosition from, GridPosition to) {
        if (!isValidGridPosition(from) || !isValidGridPosition(to) || from.equals(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        int fromAbsolute = from.absoluteIndex();
        int toAbsolute = to.absoluteIndex();
        GridEntry source = getGridEntry(mutation.next, fromAbsolute);
        if (source == null) return;
        GridEntry destination = getGridEntry(mutation.next, toAbsolute);
        removeGridEntry(mutation.next, fromAbsolute);
        removeGridEntry(mutation.next, toAbsolute);
        putGridEntry(mutation.next, toAbsolute, source);
        putGridEntry(mutation.next, fromAbsolute, destination);
        persist(mutation);
    }

    /** Moves a grid item to the requested insertion boundary. */
    public synchronized void insertGrid(GridPosition from, GridPosition to) {
        if (!isValidGridPosition(from) || !isValidGridPosition(to) || from.equals(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        int fromAbsolute = from.absoluteIndex();
        int toAbsolute = to.absoluteIndex();
        GridEntry source = getGridEntry(mutation.next, fromAbsolute);
        if (source == null) return;

        removeGridEntry(mutation.next, fromAbsolute);
        if (fromAbsolute < toAbsolute) {
            int target = toAbsolute - 1;
            for (int index = fromAbsolute + 1; index <= target; index++) {
                moveGridEntry(mutation.next, index, index - 1);
            }
            putGridEntry(mutation.next, target, source);
        } else {
            for (int index = fromAbsolute - 1; index >= toAbsolute; index--) {
                moveGridEntry(mutation.next, index, index + 1);
            }
            putGridEntry(mutation.next, toAbsolute, source);
        }
        persist(mutation);
    }

    public synchronized void insertDockToGrid(DockPosition from, GridPosition to) {
        if (!isValidDockPosition(from) || !isValidGridPosition(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        ComponentKey source = mutation.next.dock.get(from.dockIndex);
        if (source == null || !makeGridInsertionRoom(mutation.next, to.absoluteIndex())) return;
        mutation.next.dock.remove(from.dockIndex);
        putGridEntry(mutation.next, to.absoluteIndex(), GridEntry.app(source));
        persist(mutation);
    }

    public synchronized LandscapeItem getGridItem(GridPosition position) {
        if (!isValidGridPosition(position)) return null;
        LoadedSnapshot loaded = loadSnapshot();
        if (!loaded.readable) return null;
        int absolute = position.absoluteIndex();
        ComponentKey app = loaded.snapshot.grid.get(absolute);
        if (app != null) return LandscapeItem.grid(position, app);
        Long folderId = loaded.snapshot.gridFolders.get(absolute);
        FolderRecord folder = folderId == null ? null : loaded.snapshot.folders.get(folderId);
        return folder == null ? null : LandscapeItem.gridFolder(
                position, folderId, folder.title, folder.children);
    }

    /** Returns the folder item, including an immutable copy of its children, or null. */
    public synchronized LandscapeItem getFolder(long folderId) {
        if (folderId <= 0L) return null;
        LoadedSnapshot loaded = loadSnapshot();
        if (!loaded.readable) return null;
        Integer absolute = findFolderAbsolute(loaded.snapshot, folderId);
        FolderRecord folder = loaded.snapshot.folders.get(folderId);
        GridPosition position = positionForAbsolute(absolute);
        return folder == null || position == null ? null : LandscapeItem.gridFolder(
                position, folderId, folder.title, folder.children);
    }

    public synchronized void createFolderFromGrid(GridPosition from, GridPosition to) {
        if (!isValidGridPosition(from) || !isValidGridPosition(to) || from.equals(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        int fromAbsolute = from.absoluteIndex();
        int toAbsolute = to.absoluteIndex();
        ComponentKey source = mutation.next.grid.get(fromAbsolute);
        ComponentKey destination = mutation.next.grid.get(toAbsolute);
        if (source == null || destination == null || source.equals(destination)) return;
        long folderId = allocateFolderId(mutation.next);
        if (folderId < 0L) return;
        mutation.next.grid.remove(fromAbsolute);
        mutation.next.grid.remove(toAbsolute);
        mutation.next.gridFolders.put(toAbsolute, folderId);
        mutation.next.folders.put(folderId,
                new FolderRecord(defaultFolderTitle(), pair(destination, source)));
        persist(mutation);
    }

    public synchronized void createFolderFromDock(DockPosition from, GridPosition to) {
        if (!isValidDockPosition(from) || !isValidGridPosition(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        ComponentKey source = mutation.next.dock.get(from.dockIndex);
        ComponentKey destination = mutation.next.grid.get(to.absoluteIndex());
        if (source == null || destination == null || source.equals(destination)) return;
        long folderId = allocateFolderId(mutation.next);
        if (folderId < 0L) return;
        mutation.next.dock.remove(from.dockIndex);
        mutation.next.grid.remove(to.absoluteIndex());
        mutation.next.gridFolders.put(to.absoluteIndex(), folderId);
        mutation.next.folders.put(folderId,
                new FolderRecord(defaultFolderTitle(), pair(destination, source)));
        persist(mutation);
    }

    public synchronized void addGridToFolder(GridPosition from, long folderId) {
        insertGridToFolder(from, folderId, Integer.MAX_VALUE);
    }

    public synchronized void insertGridToFolder(
            GridPosition from, long folderId, int insertIndex) {
        if (!isValidGridPosition(from) || folderId <= 0L) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        ComponentKey source = mutation.next.grid.get(from.absoluteIndex());
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (source == null || folder == null || folder.children.contains(source)
                || folder.children.size() >= MAX_FOLDER_CHILDREN) return;
        int target = clampInsertionIndex(insertIndex, folder.children.size());
        mutation.next.grid.remove(from.absoluteIndex());
        folder.children.add(target, source);
        persist(mutation);
    }

    public synchronized void addDockToFolder(DockPosition from, long folderId) {
        insertDockToFolder(from, folderId, Integer.MAX_VALUE);
    }

    public synchronized void insertDockToFolder(
            DockPosition from, long folderId, int insertIndex) {
        if (!isValidDockPosition(from) || folderId <= 0L) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        ComponentKey source = mutation.next.dock.get(from.dockIndex);
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (source == null || folder == null || folder.children.contains(source)
                || folder.children.size() >= MAX_FOLDER_CHILDREN) return;
        int target = clampInsertionIndex(insertIndex, folder.children.size());
        mutation.next.dock.remove(from.dockIndex);
        folder.children.add(target, source);
        persist(mutation);
    }

    /**
     * Atomically moves valid applications selected by the folder picker into the target folder.
     * Components already in that folder and duplicates in the input are filtered out. A selected
     * component in Grid, Dock, or another folder is removed from that source first; source folders
     * are collapsed using the same invariant as drag-out operations.
     *
     * @return an immutable list containing only the components that were committed
     */
    public synchronized List<ComponentKey> addComponentsToFolder(
            long folderId, List<ComponentKey> keys) {
        if (folderId <= 0L || keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        Mutation mutation = beginMutation();
        if (mutation == null) return Collections.emptyList();
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (folder == null) return Collections.emptyList();

        Set<ComponentKey> selected = new HashSet<>();
        List<ComponentKey> added = new ArrayList<>();
        for (ComponentKey key : keys) {
            if (folder.children.size() >= MAX_FOLDER_CHILDREN) break;
            if (!isValidComponent(key) || !selected.add(key) || folder.children.contains(key)) {
                continue;
            }
            removeComponentFromOtherContainer(mutation.next, folderId, key);
            folder = mutation.next.folders.get(folderId);
            if (folder == null) return Collections.emptyList();
            folder.children.add(key);
            added.add(key);
        }
        if (added.isEmpty()) return Collections.emptyList();
        return persist(mutation)
                ? Collections.unmodifiableList(added) : Collections.emptyList();
    }

    public synchronized void moveFolderChild(long folderId, int fromIndex, int toIndex) {
        if (folderId <= 0L || fromIndex == toIndex) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (folder == null || !isValidListIndex(fromIndex, folder.children.size())) return;
        int target = Math.max(0, Math.min(folder.children.size() - 1, toIndex));
        if (fromIndex == target) return;
        ComponentKey child = folder.children.remove(fromIndex);
        folder.children.add(target, child);
        persist(mutation);
    }

    /** Atomically moves one child between two existing folders. */
    public synchronized void moveFolderChildToFolder(
            long sourceFolderId, int childIndex, long targetFolderId) {
        if (sourceFolderId <= 0L || targetFolderId <= 0L
                || sourceFolderId == targetFolderId) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord source = mutation.next.folders.get(sourceFolderId);
        FolderRecord target = mutation.next.folders.get(targetFolderId);
        if (source == null || target == null
                || !isValidListIndex(childIndex, source.children.size())
                || target.children.size() >= MAX_FOLDER_CHILDREN) return;
        ComponentKey child = source.children.get(childIndex);
        if (target.children.contains(child)) return;
        child = removeFolderChildAndCollapse(mutation.next, sourceFolderId, childIndex);
        if (child == null) return;
        target = mutation.next.folders.get(targetFolderId);
        if (target == null) return;
        target.children.add(child);
        persist(mutation);
    }

    /** Atomically combines a folder child with a grid app into a new folder. */
    public synchronized void createFolderFromFolderChild(
            long sourceFolderId, int childIndex, GridPosition targetPosition) {
        if (sourceFolderId <= 0L || !isValidGridPosition(targetPosition)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord source = mutation.next.folders.get(sourceFolderId);
        ComponentKey destination = mutation.next.grid.get(targetPosition.absoluteIndex());
        if (source == null || destination == null
                || !isValidListIndex(childIndex, source.children.size())) return;
        ComponentKey child = source.children.get(childIndex);
        if (child.equals(destination)) return;
        long newFolderId = allocateFolderId(mutation.next);
        if (newFolderId < 0L) return;
        child = removeFolderChildAndCollapse(mutation.next, sourceFolderId, childIndex);
        if (child == null) return;
        mutation.next.grid.remove(targetPosition.absoluteIndex());
        mutation.next.gridFolders.put(targetPosition.absoluteIndex(), newFolderId);
        mutation.next.folders.put(newFolderId,
                new FolderRecord(defaultFolderTitle(), pair(destination, child)));
        persist(mutation);
    }

    public synchronized void swapFolderChild(long folderId, int fromIndex, int toIndex) {
        if (folderId <= 0L || fromIndex == toIndex) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (folder == null || !isValidListIndex(fromIndex, folder.children.size())
                || !isValidListIndex(toIndex, folder.children.size())) return;
        Collections.swap(folder.children, fromIndex, toIndex);
        persist(mutation);
    }

    public synchronized void renameFolder(long folderId, String title) {
        if (folderId <= 0L) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (folder == null) return;
        String normalized = normalizeFolderTitle(title);
        if (normalized.equals(folder.title)) return;
        folder.title = normalized;
        persist(mutation);
    }

    public synchronized void removeFolderChildToGrid(long folderId, int childIndex) {
        if (folderId <= 0L) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (folder == null || !isValidListIndex(childIndex, folder.children.size())) return;
        ComponentKey child = removeFolderChildAndCollapse(mutation.next, folderId, childIndex);
        if (child == null) return;
        int target = findFreeGridSlot(mutation.next, 0);
        if (target < 0) return;
        mutation.next.grid.put(target, child);
        persist(mutation);
    }

    /**
     * Removes a child to the grid. With insert=true occupied items are shifted right up to the
     * first free slot. With insert=false existing positions remain fixed and the first free slot
     * at or after the requested target is used.
     */
    public synchronized void removeFolderChildToGrid(
            long folderId, int childIndex, GridPosition to, boolean insert) {
        if (folderId <= 0L || !isValidGridPosition(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (folder == null || !isValidListIndex(childIndex, folder.children.size())) return;

        ComponentKey child = removeFolderChildAndCollapse(mutation.next, folderId, childIndex);
        if (child == null) return;
        int target = to.absoluteIndex();
        if (insert) {
            if (!makeGridInsertionRoom(mutation.next, target)) return;
        } else if (isGridOccupied(mutation.next, target)) {
            target = findFreeGridSlot(mutation.next, target);
            if (target < 0) return;
        }
        mutation.next.grid.put(target, child);
        persist(mutation);
    }

    /** Atomically drags a folder child to Dock, swapping with the existing Dock app if needed. */
    public synchronized void moveFolderChildToDock(
            long folderId, int childIndex, DockPosition to) {
        if (folderId <= 0L || !isValidDockPosition(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        FolderRecord folder = mutation.next.folders.get(folderId);
        if (folder == null || !isValidListIndex(childIndex, folder.children.size())) return;
        ComponentKey child = folder.children.get(childIndex);
        ComponentKey dockApp = mutation.next.dock.get(to.dockIndex);
        if (dockApp != null) {
            // A true swap retains the source folder and all applications.
            folder.children.set(childIndex, dockApp);
        } else {
            child = removeFolderChildAndCollapse(mutation.next, folderId, childIndex);
            if (child == null) return;
        }
        mutation.next.dock.put(to.dockIndex, child);
        persist(mutation);
    }

    public synchronized void moveGridToDock(GridPosition from, DockPosition to) {
        if (!isValidGridPosition(from) || !isValidDockPosition(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        int fromAbsolute = from.absoluteIndex();
        ComponentKey source = mutation.next.grid.get(fromAbsolute);
        if (source == null || mutation.next.gridFolders.containsKey(fromAbsolute)) return;
        ComponentKey destination = mutation.next.dock.get(to.dockIndex);
        mutation.next.grid.remove(fromAbsolute);
        mutation.next.dock.put(to.dockIndex, source);
        if (destination != null) mutation.next.grid.put(fromAbsolute, destination);
        persist(mutation);
    }

    public synchronized void moveDockToGrid(DockPosition from, GridPosition to) {
        if (!isValidDockPosition(from) || !isValidGridPosition(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null || mutation.next.gridFolders.containsKey(to.absoluteIndex())) return;
        ComponentKey source = mutation.next.dock.get(from.dockIndex);
        if (source == null) return;
        ComponentKey destination = mutation.next.grid.get(to.absoluteIndex());
        mutation.next.dock.remove(from.dockIndex);
        mutation.next.grid.put(to.absoluteIndex(), source);
        if (destination != null) mutation.next.dock.put(from.dockIndex, destination);
        persist(mutation);
    }

    public synchronized void moveOrSwapDock(DockPosition from, DockPosition to) {
        if (!isValidDockPosition(from) || !isValidDockPosition(to) || from.equals(to)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        ComponentKey source = mutation.next.dock.get(from.dockIndex);
        if (source == null) return;
        ComponentKey destination = mutation.next.dock.get(to.dockIndex);
        mutation.next.dock.remove(from.dockIndex);
        mutation.next.dock.put(to.dockIndex, source);
        if (destination != null) mutation.next.dock.put(from.dockIndex, destination);
        persist(mutation);
    }

    public synchronized int maxAbsoluteIndexPlusOne() {
        LoadedSnapshot loaded = loadSnapshot();
        if (!loaded.readable) return 0;
        int maximum = maxGridAbsoluteIndex(loaded.snapshot);
        return maximum < 0 ? 0 : maximum + 1;
    }

    public synchronized void upsertDock(DockPosition position, ComponentKey key) {
        if (!isValidDockPosition(position) || !isValidComponent(key)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        ComponentKey current = mutation.next.dock.get(position.dockIndex);
        if (key.equals(current)) return;
        if (allComponents(mutation.next).contains(key)) return;
        mutation.next.dock.put(position.dockIndex, key);
        persist(mutation);
    }

    public synchronized void removeDock(DockPosition position) {
        if (!isValidDockPosition(position)) return;
        Mutation mutation = beginMutation();
        if (mutation == null) return;
        if (mutation.next.dock.remove(position.dockIndex) != null) persist(mutation);
    }

    private Mutation beginMutation() {
        if (writeBlocked) {
            Log.e(TAG, "Layout writes are blocked after an unrecoverable persistence failure");
            return null;
        }
        LoadedSnapshot loaded = loadSnapshot();
        if (!loaded.readable || !loaded.writable) return null;
        return new Mutation(loaded, loaded.snapshot.copy());
    }

    private boolean persist(Mutation mutation) {
        if (mutation == null || mutation.loaded.snapshot.revision == Long.MAX_VALUE) return false;
        mutation.next.revision = mutation.loaded.snapshot.revision + 1L;
        try {
            validateSnapshot(mutation.next);
            String previousJson = mutation.loaded.rawJson != null
                    ? mutation.loaded.rawJson : serializeSnapshot(mutation.loaded.snapshot);
            String nextJson = serializeSnapshot(mutation.next);
            PreferenceState before = capturePreferenceState();
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(K_BACKUP, previousJson)
                    .putString(K_SNAPSHOT, nextJson)
                    .putLong(K_REVISION, mutation.next.revision);
            return commitWithRollback(editor, before,
                    "layout revision " + mutation.next.revision);
        } catch (RuntimeException | JSONException | StoreFormatException e) {
            Log.e(TAG, "Layout transaction rejected; previous snapshot preserved", e);
            return false;
        }
    }

    private PreferenceState capturePreferenceState() {
        return new PreferenceState(
                prefs.contains(K_SNAPSHOT), prefs.getString(K_SNAPSHOT, null),
                prefs.contains(K_BACKUP), prefs.getString(K_BACKUP, null),
                prefs.contains(K_REVISION), prefs.getLong(K_REVISION, 0L));
    }

    private boolean commitWithRollback(SharedPreferences.Editor editor,
            PreferenceState before, String operation) {
        boolean committed = false;
        try {
            committed = editor.commit();
        } catch (RuntimeException error) {
            Log.e(TAG, "SharedPreferences threw while committing " + operation, error);
        }
        if (committed) return true;

        Log.e(TAG, "SharedPreferences rejected " + operation
                + "; restoring previous in-memory snapshot");
        SharedPreferences.Editor rollback = prefs.edit();
        if (before.hadPrimary) rollback.putString(K_SNAPSHOT, before.primary);
        else rollback.remove(K_SNAPSHOT);
        if (before.hadBackup) rollback.putString(K_BACKUP, before.backup);
        else rollback.remove(K_BACKUP);
        if (before.hadRevision) rollback.putLong(K_REVISION, before.revision);
        else rollback.remove(K_REVISION);
        try {
            if (rollback.commit()) return false;
        } catch (RuntimeException error) {
            Log.e(TAG, "SharedPreferences rollback threw", error);
        }
        writeBlocked = true;
        Log.e(TAG, "SharedPreferences rollback failed; further mutations are blocked");
        return false;
    }

    private LoadedSnapshot loadSnapshot() {
        boolean primaryPresent = false;
        boolean backupPresent = false;
        try {
            primaryPresent = prefs.contains(K_SNAPSHOT);
            if (primaryPresent) {
                String raw = prefs.getString(K_SNAPSHOT, null);
                LayoutSnapshot primary = parseSnapshot(raw);
                return LoadedSnapshot.readable(primary, raw);
            }
        } catch (RuntimeException | JSONException | StoreFormatException e) {
            Log.e(TAG, "Primary layout snapshot is unreadable; trying backup", e);
        }

        try {
            backupPresent = prefs.contains(K_BACKUP);
            if (backupPresent) {
                String raw = prefs.getString(K_BACKUP, null);
                LayoutSnapshot backup = parseSnapshot(raw);
                return LoadedSnapshot.readable(backup, raw);
            }
        } catch (RuntimeException | JSONException | StoreFormatException e) {
            Log.e(TAG, "Layout backup is unreadable", e);
        }

        if (primaryPresent || backupPresent) {
            // Refuse normal writes. Only resetLayout(), after confirmation, may replace this data.
            return LoadedSnapshot.corrupt();
        }

        try {
            LegacyResult legacy = readLegacySnapshot();
            if (legacy.present) {
                validateSnapshot(legacy.snapshot);
                return LoadedSnapshot.readable(
                        legacy.snapshot, serializeSnapshot(legacy.snapshot));
            }
        } catch (RuntimeException | JSONException | StoreFormatException e) {
            Log.e(TAG, "Legacy layout is unreadable; preserving it without mutation", e);
            return LoadedSnapshot.corrupt();
        }
        return LoadedSnapshot.readable(LayoutSnapshot.empty(0L), null);
    }

    private LegacyResult readLegacySnapshot() throws StoreFormatException {
        boolean present = prefs.contains(LEGACY_GRID)
                || prefs.contains(LEGACY_DOCK)
                || prefs.contains(LEGACY_GRID_FOLDERS)
                || prefs.contains(LEGACY_FOLDERS)
                || prefs.contains(LEGACY_NEXT_FOLDER_ID);
        if (!present) return new LegacyResult(false, LayoutSnapshot.empty(0L));

        LayoutSnapshot state = new LayoutSnapshot();
        state.revision = Math.max(0L, safeStoredRevision());
        state.initialized = true;
        state.grid.putAll(parseLegacyComponentMap(prefs.getString(LEGACY_GRID, "")));
        state.dock.putAll(parseLegacyComponentMap(prefs.getString(LEGACY_DOCK, "")));
        state.gridFolders.putAll(parseLegacyGridFolders(
                prefs.getString(LEGACY_GRID_FOLDERS, "")));
        state.folders.putAll(parseLegacyFolders(prefs.getString(LEGACY_FOLDERS, "")));
        state.nextFolderId = prefs.getLong(LEGACY_NEXT_FOLDER_ID, 1L);
        if (state.nextFolderId < 1L) throw new StoreFormatException("legacy nextFolderId");
        return new LegacyResult(true, state);
    }

    private long safeStoredRevision() {
        try {
            return Math.max(0L, prefs.getLong(K_REVISION, 0L));
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static LayoutSnapshot parseSnapshot(String raw)
            throws JSONException, StoreFormatException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new StoreFormatException("empty snapshot");
        }
        JSONObject root = new JSONObject(raw);
        if (root.getInt("schema") != SCHEMA_VERSION) {
            throw new StoreFormatException("unsupported schema");
        }
        LayoutSnapshot state = new LayoutSnapshot();
        state.revision = root.getLong("revision");
        state.nextFolderId = root.getLong("nextFolderId");
        state.initialized = root.optBoolean("initialized", true);

        JSONArray folders = root.getJSONArray("folders");
        if (folders.length() > MAX_FOLDERS) throw new StoreFormatException("too many folders");
        for (int index = 0; index < folders.length(); index++) {
            JSONObject json = folders.getJSONObject(index);
            long id = json.getLong("id");
            JSONArray childArray = json.getJSONArray("children");
            if (childArray.length() > MAX_FOLDER_CHILDREN) {
                throw new StoreFormatException("too many folder children");
            }
            List<ComponentKey> children = new ArrayList<>(childArray.length());
            for (int child = 0; child < childArray.length(); child++) {
                children.add(parseComponent(childArray.getJSONObject(child)));
            }
            if (state.folders.put(id,
                    new FolderRecord(json.getString("title"), children)) != null) {
                throw new StoreFormatException("duplicate folder id");
            }
        }

        JSONArray grid = root.getJSONArray("grid");
        if (grid.length() > MAX_GRID_ABSOLUTE_INDEX + 1) {
            throw new StoreFormatException("too many grid entries");
        }
        for (int index = 0; index < grid.length(); index++) {
            JSONObject json = grid.getJSONObject(index);
            int absolute = checkedAbsolute(json.getInt("page"), json.getInt("slot"));
            boolean hasComponent = json.has("component");
            boolean hasFolder = json.has("folderId");
            if (hasComponent == hasFolder || isGridOccupied(state, absolute)) {
                throw new StoreFormatException("invalid or duplicate grid slot");
            }
            if (hasComponent) state.grid.put(absolute,
                    parseComponent(json.getJSONObject("component")));
            else state.gridFolders.put(absolute, json.getLong("folderId"));
        }

        JSONArray dock = root.getJSONArray("dock");
        if (dock.length() > DockPosition.SLOTS) throw new StoreFormatException("too many dock items");
        for (int index = 0; index < dock.length(); index++) {
            JSONObject json = dock.getJSONObject(index);
            int slot = json.getInt("slot");
            if (!isValidDockIndex(slot)
                    || state.dock.put(slot, parseComponent(json.getJSONObject("component"))) != null) {
                throw new StoreFormatException("invalid or duplicate dock slot");
            }
        }
        validateSnapshot(state);
        return state;
    }

    private static String serializeSnapshot(LayoutSnapshot state) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", SCHEMA_VERSION);
        root.put("revision", state.revision);
        root.put("nextFolderId", state.nextFolderId);
        root.put("initialized", state.initialized);

        JSONArray grid = new JSONArray();
        Set<Integer> occupied = new HashSet<>();
        occupied.addAll(state.grid.keySet());
        occupied.addAll(state.gridFolders.keySet());
        List<Integer> gridIndexes = new ArrayList<>(occupied);
        Collections.sort(gridIndexes);
        for (Integer absolute : gridIndexes) {
            JSONObject item = new JSONObject();
            item.put("page", GridPosition.pageForAbsoluteIndex(absolute));
            item.put("slot", GridPosition.slotForAbsoluteIndex(absolute));
            ComponentKey component = state.grid.get(absolute);
            if (component != null) item.put("component", serializeComponent(component));
            else item.put("folderId", state.gridFolders.get(absolute));
            grid.put(item);
        }
        root.put("grid", grid);

        JSONArray dock = new JSONArray();
        List<Integer> dockIndexes = new ArrayList<>(state.dock.keySet());
        Collections.sort(dockIndexes);
        for (Integer slot : dockIndexes) {
            JSONObject item = new JSONObject();
            item.put("slot", slot);
            item.put("component", serializeComponent(state.dock.get(slot)));
            dock.put(item);
        }
        root.put("dock", dock);

        JSONArray folders = new JSONArray();
        List<Long> folderIds = new ArrayList<>(state.folders.keySet());
        Collections.sort(folderIds);
        for (Long id : folderIds) {
            FolderRecord folder = state.folders.get(id);
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("title", folder.title);
            JSONArray children = new JSONArray();
            for (ComponentKey child : folder.children) children.put(serializeComponent(child));
            item.put("children", children);
            folders.put(item);
        }
        root.put("folders", folders);
        return root.toString();
    }

    private static ComponentKey parseComponent(JSONObject json)
            throws JSONException, StoreFormatException {
        ComponentKey key = new ComponentKey(
                json.getString("package"), json.getString("class"), json.getLong("userSerial"));
        if (!isValidComponent(key)) throw new StoreFormatException("invalid component");
        return key;
    }

    private static JSONObject serializeComponent(ComponentKey key) throws JSONException {
        return new JSONObject()
                .put("package", key.packageName)
                .put("class", key.className)
                .put("userSerial", key.userSerial);
    }

    private static void validateSnapshot(LayoutSnapshot state) throws StoreFormatException {
        if (state == null || state.revision < 0L || state.nextFolderId < 1L) {
            throw new StoreFormatException("invalid snapshot header");
        }
        if (state.grid.size() + state.gridFolders.size() > MAX_GRID_ABSOLUTE_INDEX + 1
                || state.dock.size() > DockPosition.SLOTS
                || state.folders.size() > MAX_FOLDERS) {
            throw new StoreFormatException("snapshot limits exceeded");
        }

        Set<ComponentKey> components = new HashSet<>();
        for (Map.Entry<Integer, ComponentKey> entry : state.grid.entrySet()) {
            if (!isValidAbsoluteIndex(entry.getKey()) || !isValidComponent(entry.getValue())
                    || state.gridFolders.containsKey(entry.getKey())
                    || !components.add(entry.getValue())) {
                throw new StoreFormatException("invalid or duplicate grid component");
            }
        }
        for (Map.Entry<Integer, ComponentKey> entry : state.dock.entrySet()) {
            if (!isValidDockIndex(entry.getKey()) || !isValidComponent(entry.getValue())
                    || !components.add(entry.getValue())) {
                throw new StoreFormatException("invalid or duplicate dock component");
            }
        }

        Set<Long> referencedFolders = new HashSet<>();
        for (Map.Entry<Integer, Long> entry : state.gridFolders.entrySet()) {
            if (!isValidAbsoluteIndex(entry.getKey()) || entry.getValue() == null
                    || entry.getValue() <= 0L || !state.folders.containsKey(entry.getValue())
                    || !referencedFolders.add(entry.getValue())) {
                throw new StoreFormatException("invalid folder placement");
            }
        }
        if (referencedFolders.size() != state.folders.size()) {
            throw new StoreFormatException("orphan folder");
        }

        long maximumFolderId = 0L;
        for (Map.Entry<Long, FolderRecord> entry : state.folders.entrySet()) {
            long id = entry.getKey() == null ? -1L : entry.getKey();
            FolderRecord folder = entry.getValue();
            if (id <= 0L || folder == null || folder.children.size() < 2
                    || folder.children.size() > MAX_FOLDER_CHILDREN
                    || !isValidFolderTitle(folder.title)) {
                throw new StoreFormatException("invalid folder");
            }
            maximumFolderId = Math.max(maximumFolderId, id);
            Set<ComponentKey> localChildren = new HashSet<>();
            for (ComponentKey child : folder.children) {
                if (!isValidComponent(child) || !localChildren.add(child)
                        || !components.add(child)) {
                    throw new StoreFormatException("invalid or duplicate folder child");
                }
            }
        }
        if (state.nextFolderId <= maximumFolderId) {
            throw new StoreFormatException("next folder id is stale");
        }
    }

    private static Map<Integer, ComponentKey> parseLegacyComponentMap(String raw)
            throws StoreFormatException {
        Map<Integer, ComponentKey> result = new HashMap<>();
        for (String line : legacyLines(raw)) {
            String[] parts = line.split("\\t", -1);
            if (parts.length != 4) throw new StoreFormatException("legacy component row");
            try {
                int index = Integer.parseInt(parts[0]);
                ComponentKey key = new ComponentKey(
                        decodeLegacy(parts[1]), decodeLegacy(parts[2]), Long.parseLong(parts[3]));
                if (result.put(index, key) != null) {
                    throw new StoreFormatException("duplicate legacy component slot");
                }
            } catch (IllegalArgumentException e) {
                throw new StoreFormatException("invalid legacy component", e);
            }
        }
        return result;
    }

    private static Map<Integer, Long> parseLegacyGridFolders(String raw)
            throws StoreFormatException {
        Map<Integer, Long> result = new HashMap<>();
        for (String line : legacyLines(raw)) {
            String[] parts = line.split("\\t", -1);
            if (parts.length != 2) throw new StoreFormatException("legacy folder placement row");
            try {
                int absolute = Integer.parseInt(parts[0]);
                long folderId = Long.parseLong(parts[1]);
                if (result.put(absolute, folderId) != null) {
                    throw new StoreFormatException("duplicate legacy folder placement");
                }
            } catch (NumberFormatException e) {
                throw new StoreFormatException("invalid legacy folder placement", e);
            }
        }
        return result;
    }

    private static Map<Long, FolderRecord> parseLegacyFolders(String raw)
            throws StoreFormatException {
        Map<Long, FolderRecord> result = new HashMap<>();
        for (String line : legacyLines(raw)) {
            String[] parts = line.split("\\t", -1);
            if (parts.length != 3) throw new StoreFormatException("legacy folder row");
            try {
                long id = Long.parseLong(parts[0]);
                FolderRecord folder = new FolderRecord(
                        decodeLegacy(parts[1]), parseLegacyComponentList(parts[2]));
                if (result.put(id, folder) != null) {
                    throw new StoreFormatException("duplicate legacy folder id");
                }
            } catch (IllegalArgumentException e) {
                throw new StoreFormatException("invalid legacy folder", e);
            }
        }
        return result;
    }

    private static List<ComponentKey> parseLegacyComponentList(String raw)
            throws StoreFormatException {
        List<ComponentKey> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        String[] entries = raw.split(",", -1);
        for (String entry : entries) {
            String[] parts = entry.split(":", -1);
            if (parts.length != 3) throw new StoreFormatException("legacy folder child row");
            try {
                result.add(new ComponentKey(
                        decodeLegacy(parts[0]), decodeLegacy(parts[1]), Long.parseLong(parts[2])));
            } catch (IllegalArgumentException e) {
                throw new StoreFormatException("invalid legacy folder child", e);
            }
        }
        return result;
    }

    private static List<String> legacyLines(String raw) throws StoreFormatException {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        String[] lines = raw.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) {
                if (index == lines.length - 1) continue;
                throw new StoreFormatException("empty legacy row");
            }
            result.add(line);
        }
        return result;
    }

    private static String decodeLegacy(String value) {
        byte[] decoded = Base64.decode(value, Base64.NO_WRAP);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static ComponentKey removeFolderChildAndCollapse(
            LayoutSnapshot state, long folderId, int childIndex) {
        FolderRecord folder = state.folders.get(folderId);
        Integer folderAbsolute = findFolderAbsolute(state, folderId);
        if (folder == null || folderAbsolute == null
                || !isValidListIndex(childIndex, folder.children.size())) return null;
        ComponentKey child = folder.children.remove(childIndex);
        if (folder.children.size() >= 2) return child;

        state.gridFolders.remove(folderAbsolute);
        state.folders.remove(folderId);
        if (folder.children.size() == 1) {
            state.grid.put(folderAbsolute, folder.children.get(0));
        }
        return child;
    }

    private static void removeComponentFromOtherContainer(
            LayoutSnapshot state, long targetFolderId, ComponentKey key) {
        Integer gridAbsolute = null;
        for (Map.Entry<Integer, ComponentKey> entry : state.grid.entrySet()) {
            if (key.equals(entry.getValue())) {
                gridAbsolute = entry.getKey();
                break;
            }
        }
        if (gridAbsolute != null) {
            state.grid.remove(gridAbsolute);
            return;
        }

        Integer dockIndex = null;
        for (Map.Entry<Integer, ComponentKey> entry : state.dock.entrySet()) {
            if (key.equals(entry.getValue())) {
                dockIndex = entry.getKey();
                break;
            }
        }
        if (dockIndex != null) {
            state.dock.remove(dockIndex);
            return;
        }

        long sourceFolderId = -1L;
        int childIndex = -1;
        for (Map.Entry<Long, FolderRecord> entry : state.folders.entrySet()) {
            if (entry.getKey() == null || entry.getKey() == targetFolderId) continue;
            int index = entry.getValue().children.indexOf(key);
            if (index >= 0) {
                sourceFolderId = entry.getKey();
                childIndex = index;
                break;
            }
        }
        if (sourceFolderId > 0L) {
            removeFolderChildAndCollapse(state, sourceFolderId, childIndex);
        }
    }

    private static long allocateFolderId(LayoutSnapshot state) {
        if (state.folders.size() >= MAX_FOLDERS || state.nextFolderId <= 0L
                || state.nextFolderId == Long.MAX_VALUE) return -1L;
        long candidate = state.nextFolderId;
        while (state.folders.containsKey(candidate)) {
            if (candidate == Long.MAX_VALUE) return -1L;
            candidate++;
        }
        state.nextFolderId = candidate + 1L;
        return candidate;
    }

    private static int clampInsertionIndex(int requested, int size) {
        if (requested <= 0) return 0;
        return Math.min(requested, size);
    }

    private static boolean makeGridInsertionRoom(LayoutSnapshot state, int target) {
        if (!isValidAbsoluteIndex(target)) return false;
        if (!isGridOccupied(state, target)) return true;
        int free = findFreeGridSlot(state, target);
        if (free < 0) return false;
        for (int index = free; index > target; index--) {
            moveGridEntry(state, index - 1, index);
        }
        return true;
    }

    private static int findFreeGridSlot(LayoutSnapshot state, int start) {
        if (state == null) return -1;
        int cursor = Math.max(0, start);
        while (cursor <= MAX_GRID_ABSOLUTE_INDEX) {
            if (!isGridOccupied(state, cursor)) return cursor;
            cursor++;
        }
        return -1;
    }

    private static int maxGridAbsoluteIndex(LayoutSnapshot state) {
        int maximum = -1;
        for (Integer absolute : state.grid.keySet()) {
            if (absolute != null) maximum = Math.max(maximum, absolute);
        }
        for (Integer absolute : state.gridFolders.keySet()) {
            if (absolute != null) maximum = Math.max(maximum, absolute);
        }
        return maximum;
    }

    private static boolean isGridOccupied(LayoutSnapshot state, int absolute) {
        return state.grid.containsKey(absolute) || state.gridFolders.containsKey(absolute);
    }

    private static GridEntry getGridEntry(LayoutSnapshot state, int absolute) {
        ComponentKey app = state.grid.get(absolute);
        if (app != null) return GridEntry.app(app);
        Long folderId = state.gridFolders.get(absolute);
        return folderId == null ? null : GridEntry.folder(folderId);
    }

    private static void removeGridEntry(LayoutSnapshot state, int absolute) {
        state.grid.remove(absolute);
        state.gridFolders.remove(absolute);
    }

    private static void putGridEntry(LayoutSnapshot state, int absolute, GridEntry entry) {
        removeGridEntry(state, absolute);
        if (entry == null) return;
        if (entry.app != null) state.grid.put(absolute, entry.app);
        else if (entry.folderId != null) state.gridFolders.put(absolute, entry.folderId);
    }

    private static void moveGridEntry(LayoutSnapshot state, int from, int to) {
        GridEntry entry = getGridEntry(state, from);
        removeGridEntry(state, from);
        putGridEntry(state, to, entry);
    }

    private static Integer findFolderAbsolute(LayoutSnapshot state, long folderId) {
        for (Map.Entry<Integer, Long> entry : state.gridFolders.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == folderId) return entry.getKey();
        }
        return null;
    }

    private static Set<ComponentKey> allComponents(LayoutSnapshot state) {
        Set<ComponentKey> result = new HashSet<>();
        result.addAll(state.grid.values());
        result.addAll(state.dock.values());
        for (FolderRecord folder : state.folders.values()) result.addAll(folder.children);
        return result;
    }

    private static boolean isValidGridPosition(GridPosition position) {
        if (position == null || position.pageIndex < 0 || position.pageIndex >= MAX_GRID_PAGES
                || !GridPosition.isValidSlotIndex(position.slotIndex)) return false;
        try {
            return isValidAbsoluteIndex(position.absoluteIndex());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static GridPosition positionForAbsolute(Integer absolute) {
        if (!isValidAbsoluteIndex(absolute)) return null;
        return new GridPosition(
                GridPosition.pageForAbsoluteIndex(absolute),
                GridPosition.slotForAbsoluteIndex(absolute));
    }

    private static int checkedAbsolute(int page, int slot) throws StoreFormatException {
        if (page < 0 || page >= MAX_GRID_PAGES || !GridPosition.isValidSlotIndex(slot)) {
            throw new StoreFormatException("invalid grid coordinate");
        }
        int absolute = GridPosition.toAbsoluteIndex(page, slot);
        if (!isValidAbsoluteIndex(absolute)) throw new StoreFormatException("invalid grid index");
        return absolute;
    }

    private static boolean isValidAbsoluteIndex(Integer absolute) {
        return absolute != null && absolute >= 0 && absolute <= MAX_GRID_ABSOLUTE_INDEX;
    }

    private static boolean isValidDockPosition(DockPosition position) {
        return position != null && isValidDockIndex(position.dockIndex);
    }

    private static boolean isValidDockIndex(Integer index) {
        return index != null && index >= 0 && index < DockPosition.SLOTS;
    }

    private static boolean isValidComponent(ComponentKey key) {
        return key != null
                && isValidComponentText(key.packageName)
                && isValidComponentText(key.className);
    }

    private static boolean isValidComponentText(String value) {
        return value != null && !value.trim().isEmpty()
                && value.length() <= MAX_COMPONENT_TEXT_LENGTH
                && value.indexOf('\u0000') < 0;
    }

    private static boolean isValidFolderTitle(String title) {
        return title != null && !title.trim().isEmpty()
                && title.length() <= MAX_FOLDER_TITLE_LENGTH
                && title.indexOf('\u0000') < 0;
    }

    private static String normalizeFolderTitle(String title) {
        String result = title == null ? "" : title.trim();
        if (result.isEmpty()) return defaultFolderTitle();
        result = result.replace('\u0000', ' ');
        return result.length() <= MAX_FOLDER_TITLE_LENGTH
                ? result : result.substring(0, MAX_FOLDER_TITLE_LENGTH);
    }

    private static boolean isValidListIndex(int index, int size) {
        return index >= 0 && index < size;
    }

    private static List<ComponentKey> pair(ComponentKey first, ComponentKey second) {
        List<ComponentKey> result = new ArrayList<>(2);
        result.add(first);
        result.add(second);
        return result;
    }

    public static String defaultFolderTitle() {
        return "文件夹";
    }

    private static final class PreferenceState {
        final boolean hadPrimary;
        final String primary;
        final boolean hadBackup;
        final String backup;
        final boolean hadRevision;
        final long revision;

        PreferenceState(boolean hadPrimary, String primary,
                boolean hadBackup, String backup,
                boolean hadRevision, long revision) {
            this.hadPrimary = hadPrimary;
            this.primary = primary;
            this.hadBackup = hadBackup;
            this.backup = backup;
            this.hadRevision = hadRevision;
            this.revision = revision;
        }
    }

    private static final class Mutation {
        final LoadedSnapshot loaded;
        final LayoutSnapshot next;

        Mutation(LoadedSnapshot loaded, LayoutSnapshot next) {
            this.loaded = loaded;
            this.next = next;
        }
    }

    private static final class LoadedSnapshot {
        final LayoutSnapshot snapshot;
        final String rawJson;
        final boolean readable;
        final boolean writable;

        private LoadedSnapshot(
                LayoutSnapshot snapshot, String rawJson, boolean readable, boolean writable) {
            this.snapshot = snapshot;
            this.rawJson = rawJson;
            this.readable = readable;
            this.writable = writable;
        }

        static LoadedSnapshot readable(LayoutSnapshot snapshot, String rawJson) {
            return new LoadedSnapshot(snapshot, rawJson, true, true);
        }

        static LoadedSnapshot corrupt() {
            return new LoadedSnapshot(LayoutSnapshot.empty(0L), null, false, false);
        }
    }

    public static final class LayoutRead {
        private final boolean readable;
        private final long revision;
        private final boolean initialized;
        private final List<LandscapeItem> grid;
        private final List<LandscapeItem> dock;

        private LayoutRead(boolean readable, long revision, boolean initialized,
                List<LandscapeItem> grid, List<LandscapeItem> dock) {
            this.readable = readable;
            this.revision = revision;
            this.initialized = initialized;
            this.grid = grid;
            this.dock = dock;
        }

        static LayoutRead readable(long revision, boolean initialized, List<LandscapeItem> grid,
                List<LandscapeItem> dock) {
            return new LayoutRead(true, revision, initialized, grid, dock);
        }

        static LayoutRead unreadable() {
            return new LayoutRead(false, -1L, false,
                    Collections.emptyList(), Collections.emptyList());
        }

        public boolean isReadable() { return readable; }
        public long revision() { return revision; }
        public boolean isInitialized() { return initialized; }
        public List<LandscapeItem> grid() { return grid; }
        public List<LandscapeItem> dock() { return dock; }
    }

    private static final class LegacyResult {
        final boolean present;
        final LayoutSnapshot snapshot;

        LegacyResult(boolean present, LayoutSnapshot snapshot) {
            this.present = present;
            this.snapshot = snapshot;
        }
    }

    private static final class LayoutSnapshot {
        long revision;
        long nextFolderId = 1L;
        boolean initialized;
        final Map<Integer, ComponentKey> grid = new HashMap<>();
        final Map<Integer, ComponentKey> dock = new HashMap<>();
        final Map<Integer, Long> gridFolders = new HashMap<>();
        final Map<Long, FolderRecord> folders = new HashMap<>();

        static LayoutSnapshot empty(long revision) {
            LayoutSnapshot state = new LayoutSnapshot();
            state.revision = Math.max(0L, revision);
            return state;
        }

        LayoutSnapshot copy() {
            LayoutSnapshot copy = new LayoutSnapshot();
            copy.revision = revision;
            copy.nextFolderId = nextFolderId;
            copy.initialized = initialized;
            copy.grid.putAll(grid);
            copy.dock.putAll(dock);
            copy.gridFolders.putAll(gridFolders);
            for (Map.Entry<Long, FolderRecord> entry : folders.entrySet()) {
                copy.folders.put(entry.getKey(), entry.getValue().copy());
            }
            return copy;
        }
    }

    private static final class FolderRecord {
        String title;
        final List<ComponentKey> children;

        FolderRecord(String title, List<ComponentKey> children) {
            this.title = normalizeFolderTitle(title);
            this.children = children == null ? new ArrayList<>() : new ArrayList<>(children);
        }

        FolderRecord copy() {
            return new FolderRecord(title, children);
        }
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

    private static final class StoreFormatException extends Exception {
        private static final long serialVersionUID = 1L;

        StoreFormatException(String message) {
            super(message);
        }

        StoreFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
