package com.dung.chargmagagement.model.clean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Xếp một tệp vào nhóm rác dựa trên đường dẫn và kích thước.
 *
 * <p>Lớp thuần logic, không đụng tới hệ thống tệp, nên kiểm thử được bằng JUnit
 * thường. Mọi quyết định "tệp này có phải rác không" nằm gọn ở đây thay vì rải
 * trong vòng lặp quét.
 */
public final class JunkClassifier {

    /** Ngưỡng gọi là "tệp lớn". 50 MB đủ lớn để đáng chú ý, đủ nhỏ để không bỏ sót. */
    public static final long LARGE_FILE_THRESHOLD = 50L * 1024 * 1024;

    /** Đuôi tệp do phần mềm sinh ra, xoá đi hệ thống tự tạo lại khi cần. */
    private static final String[] JUNK_SUFFIXES = {
            ".tmp", ".temp", ".log", ".bak", ".crash", ".dmp"
    };

    /** Tên thư mục chứa bộ đệm, so khớp theo từng đoạn đường dẫn. */
    private static final String[] CACHE_DIRS = {
            "cache", "caches", ".thumbnails", "temp", ".temp", "tmp", "log", "logs"
    };

    /** Thư mục do các SDK quảng cáo phổ biến tạo ra. */
    private static final String[] AD_DIRS = {
            ".adcache", "adcache", "ad_cache", ".mobvista700", "com.mbridge",
            ".unity3d", "unityads", ".ads", "applovin", "vungle", "ironsource",
            ".goadsdk", "bytedance", ".pangle"
    };

    /** Quá hạn này mà tệp không được sửa đổi thì coi là lâu không dùng. */
    public static final long STALE_AGE_MS = 90L * 24 * 60 * 60 * 1000;

    /**
     * Thư mục không bao giờ xét "lâu không dùng".
     *
     * <p>Ảnh cưới chụp năm ngoái vẫn là ảnh cưới. Tệp trong các thư mục này càng
     * cũ càng quý, gợi ý xoá chúng vì lâu không mở là sai hoàn toàn.
     */
    private static final String[] PROTECTED_DIRS = {
            "dcim", "pictures", "movies", "music", "documents", "audiobooks",
            "podcasts", "recordings", "ringtones", "alarms", "notifications"
    };

    private JunkClassifier() {
    }

    /**
     * Nhóm của một tệp; {@code null} nghĩa là tệp bình thường, không đụng tới.
     *
     * @param relativePath    đường dẫn tính từ gốc bộ nhớ, dùng dấu {@code /}
     * @param sizeBytes       kích thước tệp
     * @param lastModifiedMs  lần sửa đổi gần nhất
     * @param nowMs           mốc thời gian hiện tại, truyền vào để kiểm thử được
     */
    @Nullable
    public static JunkCategory classify(@NonNull String relativePath, long sizeBytes,
                                        long lastModifiedMs, long nowMs) {
        final String path = relativePath.toLowerCase(Locale.US).replace('\\', '/');
        final String[] segments = path.split("/");

        // Thứ tự kiểm tra chính là thứ tự ưu tiên khai báo trong JunkCategory:
        // một tệp .apk nằm trong thư mục Tải xuống được tính là APK cũ, không
        // tính thêm lần nữa ở nhóm Tải xuống
        if (matchesAnySegment(segments, AD_DIRS)) return JunkCategory.AD_CACHE;
        if (isJunk(path, segments)) return JunkCategory.JUNK_CACHE;
        if (path.endsWith(".apk")) return JunkCategory.OBSOLETE_APK;
        if (isUnder(segments, "download") || isUnder(segments, "downloads")) {
            return JunkCategory.DOWNLOADS;
        }
        if (sizeBytes >= LARGE_FILE_THRESHOLD) return JunkCategory.LARGE_FILES;
        if (isStale(segments, lastModifiedMs, nowMs)) return JunkCategory.STALE_FILES;

        return null;
    }

    /** Bản rút gọn cho nơi không quan tâm tới tuổi tệp. */
    @Nullable
    public static JunkCategory classify(@NonNull String relativePath, long sizeBytes) {
        return classify(relativePath, sizeBytes, 0L, 0L);
    }

    /**
     * Tệp lâu không đụng tới.
     *
     * <p>Chỉ tính khi biết chắc mốc thời gian: {@code lastModifiedMs} bằng 0 nghĩa
     * là hệ thống tệp không trả về được, và đoán mò tuổi của một tệp rồi đề nghị
     * xoá nó là điều không được phép làm.
     */
    private static boolean isStale(@NonNull String[] segments, long lastModifiedMs, long nowMs) {
        if (lastModifiedMs <= 0L || nowMs <= 0L) return false;
        if (segments.length > 1 && matchesFirstSegment(segments, PROTECTED_DIRS)) return false;

        return nowMs - lastModifiedMs >= STALE_AGE_MS;
    }

    private static boolean matchesFirstSegment(@NonNull String[] segments,
                                               @NonNull String[] candidates) {
        for (String candidate : candidates) {
            if (segments[0].equals(candidate)) return true;
        }
        return false;
    }

    private static boolean isJunk(@NonNull String path, @NonNull String[] segments) {
        for (String suffix : JUNK_SUFFIXES) {
            if (path.endsWith(suffix)) return true;
        }
        return matchesAnySegment(segments, CACHE_DIRS);
    }

    /** Khớp cả đoạn đường dẫn, không khớp chuỗi con: "cachet" không phải "cache". */
    private static boolean matchesAnySegment(@NonNull String[] segments,
                                             @NonNull String[] candidates) {
        // Bỏ phần tử cuối vì đó là tên tệp, không phải thư mục
        for (int i = 0; i < segments.length - 1; i++) {
            for (String candidate : candidates) {
                if (segments[i].equals(candidate)) return true;
            }
        }
        return false;
    }

    /** Tệp nằm ngay trong thư mục gốc mang tên cho trước hay bất kỳ cấp con nào. */
    private static boolean isUnder(@NonNull String[] segments, @NonNull String dirName) {
        return segments.length > 1 && segments[0].equals(dirName);
    }
}
