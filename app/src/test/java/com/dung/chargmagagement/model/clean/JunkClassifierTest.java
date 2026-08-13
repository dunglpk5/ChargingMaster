package com.dung.chargmagagement.model.clean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Kiểm tra việc xếp tệp vào nhóm rác. */
public class JunkClassifierTest {

    private static final long SMALL = 1024L;
    private static final long HUGE = JunkClassifier.LARGE_FILE_THRESHOLD + 1;

    @Test
    public void classify_detectsCacheDirectories() {
        assertEquals(JunkCategory.JUNK_CACHE,
                JunkClassifier.classify("MyApp/cache/image.bin", SMALL));
        assertEquals(JunkCategory.JUNK_CACHE,
                JunkClassifier.classify("DCIM/.thumbnails/1234.jpg", SMALL));
    }

    @Test
    public void classify_detectsJunkSuffixes() {
        assertEquals(JunkCategory.JUNK_CACHE, JunkClassifier.classify("logs.tmp", SMALL));
        assertEquals(JunkCategory.JUNK_CACHE, JunkClassifier.classify("A/B/crash.log", SMALL));
    }

    @Test
    public void classify_detectsApkAndAdCache() {
        assertEquals(JunkCategory.OBSOLETE_APK, JunkClassifier.classify("app-release.apk", SMALL));
        assertEquals(JunkCategory.AD_CACHE, JunkClassifier.classify("applovin/x.dat", SMALL));
    }

    /** APK trong thư mục Tải xuống chỉ được tính một lần, ở nhóm APK cũ. */
    @Test
    public void classify_countsEachFileOnce() {
        assertEquals(JunkCategory.OBSOLETE_APK,
                JunkClassifier.classify("Download/game.apk", SMALL));
    }

    @Test
    public void classify_detectsDownloads() {
        assertEquals(JunkCategory.DOWNLOADS, JunkClassifier.classify("Download/note.pdf", SMALL));
        assertEquals(JunkCategory.DOWNLOADS,
                JunkClassifier.classify("Downloads/sub/note.pdf", SMALL));
    }

    /** Thư mục Tải xuống ưu tiên hơn ngưỡng tệp lớn, khớp thứ tự trong bản thiết kế. */
    @Test
    public void classify_prefersDownloadOverLargeFile() {
        assertEquals(JunkCategory.DOWNLOADS, JunkClassifier.classify("Download/film.mp4", HUGE));
    }

    @Test
    public void classify_detectsLargeFilesElsewhere() {
        assertEquals(JunkCategory.LARGE_FILES, JunkClassifier.classify("Movies/film.mp4", HUGE));
    }

    // ==================== Tệp lâu không dùng ====================

    private static final long NOW = 1_700_000_000_000L;
    private static final long OLD = NOW - JunkClassifier.STALE_AGE_MS - 1;

    @Test
    public void classify_detectsStaleFiles() {
        assertEquals(JunkCategory.STALE_FILES,
                JunkClassifier.classify("Misc/old.dat", SMALL, OLD, NOW));
    }

    @Test
    public void classify_ignoresRecentFiles() {
        assertNull(JunkClassifier.classify("Misc/new.dat", SMALL, NOW - 1000, NOW));
    }

    /** Ảnh và nhạc cũ vẫn là dữ liệu quý, không được coi là rác vì lâu không mở. */
    @Test
    public void classify_neverMarksMediaFoldersAsStale() {
        assertNull(JunkClassifier.classify("DCIM/Camera/IMG_0001.jpg", SMALL, OLD, NOW));
        assertNull(JunkClassifier.classify("Music/song.mp3", SMALL, OLD, NOW));
        assertNull(JunkClassifier.classify("Documents/cv.docx", SMALL, OLD, NOW));
    }

    /** Không đọc được mốc sửa đổi thì không đoán tuổi tệp. */
    @Test
    public void classify_ignoresStaleWhenTimestampMissing() {
        assertNull(JunkClassifier.classify("Misc/old.dat", SMALL, 0L, NOW));
    }

    @Test
    public void classify_ignoresOrdinaryFiles() {
        assertNull(JunkClassifier.classify("DCIM/Camera/IMG_0001.jpg", SMALL));
        assertNull(JunkClassifier.classify("Documents/cv.docx", SMALL));
    }

    /** Khớp cả đoạn đường dẫn: thư mục "cachet" không phải bộ đệm. */
    @Test
    public void classify_doesNotMatchPartialDirectoryName() {
        assertNull(JunkClassifier.classify("cachet/note.txt", SMALL));
    }

    /** Tên thư mục viết hoa vẫn phải nhận ra. */
    @Test
    public void classify_isCaseInsensitive() {
        assertEquals(JunkCategory.JUNK_CACHE, JunkClassifier.classify("App/CACHE/a.bin", SMALL));
        assertEquals(JunkCategory.OBSOLETE_APK, JunkClassifier.classify("Setup.APK", SMALL));
    }
}
