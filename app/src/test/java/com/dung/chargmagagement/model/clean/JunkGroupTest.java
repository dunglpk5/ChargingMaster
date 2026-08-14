package com.dung.chargmagagement.model.clean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

/** Kiểm tra hai kiểu chọn của một nhóm rác. */
public class JunkGroupTest {

    private static JunkGroup groupWithFiles(JunkCategory category, long... sizes) {
        JunkGroup group = new JunkGroup(category);
        for (int i = 0; i < sizes.length; i++) {
            group.add(new File("file" + i), sizes[i]);
        }
        return group;
    }

    // ==================== Nhóm tick cả nhóm ====================

    @Test
    public void wholeGroup_selectedByDefaultWhenSafe() {
        JunkGroup group = groupWithFiles(JunkCategory.JUNK_CACHE, 100L, 200L);

        assertTrue(group.isSelected());
        assertEquals(300L, group.getSelectedBytes());
        assertEquals(2, group.getSelectedFiles().size());
    }

    @Test
    public void wholeGroup_deselectedGivesNothing() {
        JunkGroup group = groupWithFiles(JunkCategory.JUNK_CACHE, 100L);
        group.setSelected(false);

        assertFalse(group.hasSelection());
        assertEquals(0L, group.getSelectedBytes());
        assertTrue(group.getSelectedFiles().isEmpty());
    }

    // ==================== Nhóm chọn từng tệp ====================

    @Test
    public void perFileGroup_startsWithNothingSelected() {
        JunkGroup group = groupWithFiles(JunkCategory.OBSOLETE_APK, 100L, 200L);

        assertFalse(group.hasSelection());
        assertEquals(0L, group.getSelectedBytes());
        assertEquals(0, group.getSelectedCount());
    }

    /** Chọn một tệp thì chỉ dung lượng tệp đó được tính, không phải cả nhóm. */
    @Test
    public void perFileGroup_countsOnlySelectedFiles() {
        JunkGroup group = groupWithFiles(JunkCategory.LARGE_FILES, 100L, 200L, 300L);
        group.getFiles().get(1).setSelected(true);

        assertTrue(group.hasSelection());
        assertEquals(200L, group.getSelectedBytes());
        assertEquals(1, group.getSelectedCount());
        assertEquals(1, group.getSelectedFiles().size());
        assertEquals(600L, group.getTotalBytes());
    }

    /** Ô tick cả nhóm không được ảnh hưởng tới nhóm chọn theo tệp. */
    @Test
    public void perFileGroup_ignoresGroupLevelFlag() {
        JunkGroup group = groupWithFiles(JunkCategory.OBSOLETE_APK, 100L);
        group.setSelected(true);

        assertFalse(group.hasSelection());
        assertEquals(0L, group.getSelectedBytes());
    }

    @Test
    public void sortBySizeDesc_putsLargestFirst() {
        JunkGroup group = groupWithFiles(JunkCategory.LARGE_FILES, 100L, 900L, 500L);
        group.sortBySizeDesc();

        assertEquals(900L, group.getFiles().get(0).sizeBytes);
        assertEquals(500L, group.getFiles().get(1).sizeBytes);
        assertEquals(100L, group.getFiles().get(2).sizeBytes);
    }

    @Test
    public void emptyGroup_hasNoSelection() {
        assertFalse(new JunkGroup(JunkCategory.JUNK_CACHE).hasSelection());
        assertFalse(new JunkGroup(JunkCategory.OBSOLETE_APK).hasSelection());
    }
}
