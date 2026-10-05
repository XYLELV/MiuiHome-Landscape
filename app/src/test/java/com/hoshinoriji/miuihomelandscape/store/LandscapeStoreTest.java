package com.hoshinoriji.miuihomelandscape.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.hoshinoriji.miuihomelandscape.model.ComponentKey;
import com.hoshinoriji.miuihomelandscape.model.DockPosition;
import com.hoshinoriji.miuihomelandscape.model.GridPosition;
import com.hoshinoriji.miuihomelandscape.model.LandscapeItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class LandscapeStoreTest {
    // Mirrors the private storage keys in LandscapeStore; changing them is a data migration.
    private static final String PREF_NAME = "miui_home_landscape_overlay_v4_store";
    private static final String K_SNAPSHOT = "layout_snapshot_v5";
    private static final String K_BACKUP = "layout_snapshot_v5_backup";

    private static final ComponentKey A = app("a");
    private static final ComponentKey B = app("b");
    private static final ComponentKey C = app("c");
    private static final ComponentKey D = app("d");

    private Context context;
    private LandscapeStore store;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs().edit().clear().commit();
        store = LandscapeStore.newInstanceForTests(context);
    }

    @Test
    public void emptyStoreIsReadableAndUninitialized() {
        LandscapeStore.LayoutRead layout = store.readLayout();
        assertTrue(layout.isReadable());
        assertFalse(layout.isInitialized());
        assertEquals(0L, layout.revision());
        assertTrue(layout.grid().isEmpty());
        assertTrue(layout.dock().isEmpty());
    }

    @Test
    public void appendUniqueToGridSkipsDuplicatesAndInitializes() {
        List<GridPosition> written = store.appendUniqueToGrid(Arrays.asList(A, B, A, C));
        assertEquals(Arrays.asList(grid(0), grid(1), grid(2)), written);

        assertTrue(store.appendUniqueToGrid(Arrays.asList(A, B)).isEmpty());
        LandscapeStore.LayoutRead layout = store.readLayout();
        assertTrue(layout.isInitialized());
        assertEquals(3, layout.grid().size());
        assertEquals(A, store.getGridItem(grid(0)).key);
        assertEquals(C, store.getGridItem(grid(2)).key);
    }

    @Test
    public void mutationsPersistAcrossInstancesAndBumpRevision() {
        store.appendUniqueToGrid(Arrays.asList(A, B));
        long before = store.getRevision();
        store.moveGridToDock(grid(1), new DockPosition(4));
        assertEquals(before + 1L, store.getRevision());

        LandscapeStore reopened = LandscapeStore.newInstanceForTests(context);
        LandscapeStore.LayoutRead layout = reopened.readLayout();
        assertEquals(before + 1L, layout.revision());
        assertEquals(1, layout.grid().size());
        assertEquals(1, layout.dock().size());
        assertEquals(B, layout.dock().get(0).key);
        assertEquals(4, layout.dock().get(0).dockIndex);
    }

    @Test
    public void moveOrSwapGridSwapsAndMovesToEmptySlots() {
        store.appendUniqueToGrid(Arrays.asList(A, B));
        store.moveOrSwapGrid(grid(0), grid(1));
        assertEquals(B, store.getGridItem(grid(0)).key);
        assertEquals(A, store.getGridItem(grid(1)).key);

        store.moveOrSwapGrid(grid(1), new GridPosition(1, 5));
        assertNull(store.getGridItem(grid(1)));
        assertEquals(A, store.getGridItem(new GridPosition(1, 5)).key);
    }

    @Test
    public void insertGridShiftsTheItemsInBetween() {
        store.appendUniqueToGrid(Arrays.asList(A, B, C, D));
        // Drop A on the left edge of D: B and C slide left, A lands just before D.
        store.insertGrid(grid(0), grid(3));
        assertEquals(B, store.getGridItem(grid(0)).key);
        assertEquals(C, store.getGridItem(grid(1)).key);
        assertEquals(A, store.getGridItem(grid(2)).key);
        assertEquals(D, store.getGridItem(grid(3)).key);

        // Drop D in front of B: everything from B onward slides right.
        store.insertGrid(grid(3), grid(0));
        assertEquals(D, store.getGridItem(grid(0)).key);
        assertEquals(B, store.getGridItem(grid(1)).key);
        assertEquals(C, store.getGridItem(grid(2)).key);
        assertEquals(A, store.getGridItem(grid(3)).key);
    }

    @Test
    public void folderLifecycleCreatesAddsReordersAndCollapses() {
        store.appendUniqueToGrid(Arrays.asList(A, B, C));
        store.createFolderFromGrid(grid(0), grid(1));

        LandscapeItem folder = store.getGridItem(grid(1));
        assertNotNull(folder);
        assertTrue(folder.isFolder());
        assertEquals(Arrays.asList(B, A), folder.folderChildren);
        assertNull(store.getGridItem(grid(0)));

        store.addGridToFolder(grid(2), folder.folderId);
        store.moveFolderChild(folder.folderId, 2, 0);
        assertEquals(Arrays.asList(C, B, A), store.getFolder(folder.folderId).folderChildren);

        store.renameFolder(folder.folderId, "  工具  ");
        assertEquals("工具", store.getFolder(folder.folderId).folderTitle);

        // Two removals leave one child, which replaces the folder on its grid slot.
        store.removeFolderChildToGrid(folder.folderId, 0);
        store.removeFolderChildToGrid(folder.folderId, 0);
        assertNull(store.getFolder(folder.folderId));
        assertEquals(A, store.getGridItem(grid(1)).key);
        assertEquals(3, store.listComponentKeys().size());
    }

    @Test
    public void gridAndDockSwapKeepEveryComponentUnique() {
        store.appendUniqueToGrid(Arrays.asList(A, B));
        store.moveGridToDock(grid(0), new DockPosition(0));
        store.moveGridToDock(grid(1), new DockPosition(0));
        // B replaced A in the Dock and A went back to B's old grid slot.
        assertEquals(B, store.readLayout().dock().get(0).key);
        assertEquals(A, store.getGridItem(grid(1)).key);

        long revision = store.getRevision();
        store.upsertDock(new DockPosition(3), A);
        assertEquals("a component already placed elsewhere is rejected",
                revision, store.getRevision());
    }

    @Test
    public void disablingAFolderChildCollapsesTheFolder() {
        store.appendUniqueToGrid(Arrays.asList(A, B));
        store.createFolderFromGrid(grid(0), grid(1));

        assertTrue(store.setComponentEnabled(A, false));
        LandscapeItem item = store.getGridItem(grid(1));
        assertFalse(item.isFolder());
        assertEquals(B, item.key);
        assertEquals(Collections.singleton(B), store.listComponentKeys());

        assertTrue(store.setComponentEnabled(A, true));
        assertTrue(store.listComponentKeys().contains(A));
    }

    @Test
    public void removeComponentsPrunesGridDockAndFoldersAtomically() {
        store.appendUniqueToGrid(Arrays.asList(A, B, C, D));
        store.createFolderFromGrid(grid(0), grid(1)); // folder [B, A] on slot 1
        store.moveGridToDock(grid(3), new DockPosition(2)); // D in Dock
        long revision = store.getRevision();

        int removed = store.removeComponents(Arrays.asList(A, D, app("not-in-layout")));
        assertEquals(2, removed);
        assertEquals(revision + 1L, store.getRevision());
        assertEquals(B, store.getGridItem(grid(1)).key);
        assertTrue(store.readLayout().dock().isEmpty());
        Set<ComponentKey> left = store.listComponentKeys();
        assertEquals(2, left.size());
        assertTrue(left.contains(B));
        assertTrue(left.contains(C));

        assertEquals(0, store.removeComponents(Collections.singletonList(A)));
        assertEquals("a no-op prune does not write", revision + 1L, store.getRevision());
    }

    @Test
    public void corruptPrimaryFallsBackToBackup() {
        store.appendUniqueToGrid(Arrays.asList(A, B));
        store.removeGrid(grid(1)); // backup now holds [A, B], primary holds [A]
        prefs().edit().putString(K_SNAPSHOT, "{broken").commit();

        LandscapeStore.LayoutRead layout = store.readLayout();
        assertTrue(layout.isReadable());
        assertEquals(2, layout.grid().size());

        // The next write replaces the corrupt primary with a valid snapshot.
        store.removeGrid(grid(0));
        assertEquals(Collections.singleton(B), store.listComponentKeys());
    }

    @Test
    public void unreadableStorageRejectsWritesUntilExplicitReset() {
        store.appendUniqueToGrid(Arrays.asList(A, B));
        prefs().edit()
                .putString(K_SNAPSHOT, "not json")
                .putString(K_BACKUP, "{\"schema\":99}")
                .commit();

        assertFalse(store.readLayout().isReadable());
        assertNull(store.listComponentKeys());
        assertTrue(store.appendUniqueToGrid(Collections.singletonList(C)).isEmpty());
        assertFalse(store.setComponentEnabled(C, true));
        assertEquals("never silently replaced", "not json",
                prefs().getString(K_SNAPSHOT, null));

        assertTrue(store.resetLayout());
        LandscapeStore.LayoutRead layout = store.readLayout();
        assertTrue(layout.isReadable());
        assertFalse(layout.isInitialized());
        assertTrue(layout.grid().isEmpty());
    }

    @Test
    public void invalidSnapshotIsRejectedEvenIfItParses() {
        store.appendUniqueToGrid(Collections.singletonList(A));
        // Same component twice violates the global uniqueness invariant.
        String duplicate = "{\"schema\":1,\"revision\":3,\"nextFolderId\":1,\"initialized\":true,"
                + "\"grid\":[{\"page\":0,\"slot\":0,\"component\":" + json(A) + "},"
                + "{\"page\":0,\"slot\":1,\"component\":" + json(A) + "}],"
                + "\"dock\":[],\"folders\":[]}";
        prefs().edit().putString(K_SNAPSHOT, duplicate).remove(K_BACKUP).commit();
        assertFalse(store.readLayout().isReadable());
    }

    @Test
    public void legacyMultiKeyLayoutIsReadAndMigratedOnWrite() {
        prefs().edit()
                .putString("grid_items", legacyRow(0, A) + "\n" + legacyRow(5, B) + "\n")
                .putString("dock_items", legacyRow(2, C))
                .putLong("next_folder_id", 1L)
                .commit();

        LandscapeStore.LayoutRead layout = store.readLayout();
        assertTrue(layout.isReadable());
        assertTrue(layout.isInitialized());
        assertEquals(A, store.getGridItem(grid(0)).key);
        assertEquals(B, store.getGridItem(grid(5)).key);
        assertEquals(C, layout.dock().get(0).key);

        store.removeGrid(grid(0));
        assertNotNull("first write persists the v5 snapshot", prefs().getString(K_SNAPSHOT, null));
        assertEquals(2, LandscapeStore.newInstanceForTests(context).listComponentKeys().size());
    }

    private SharedPreferences prefs() {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static ComponentKey app(String name) {
        return new ComponentKey("com.example." + name, "com.example." + name + ".Main", 0L);
    }

    private static GridPosition grid(int slot) {
        return new GridPosition(0, slot);
    }

    private static String json(ComponentKey key) {
        return "{\"package\":\"" + key.packageName + "\",\"class\":\"" + key.className
                + "\",\"userSerial\":" + key.userSerial + "}";
    }

    private static String legacyRow(int index, ComponentKey key) {
        return index + "\t" + b64(key.packageName) + "\t" + b64(key.className)
                + "\t" + key.userSerial;
    }

    private static String b64(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }
}
