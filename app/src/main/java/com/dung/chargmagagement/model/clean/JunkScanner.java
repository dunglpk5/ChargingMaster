package com.dung.chargmagagement.model.clean;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.Logger;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quét bộ nhớ trong và xếp tệp thừa vào các nhóm của {@link JunkCategory}.
 *
 * <p>Duyệt theo chiều rộng bằng hàng đợi tường minh chứ không đệ quy: cây thư
 * mục của người dùng có thể sâu bất thường (thư mục đồng bộ, thư mục lồng nhau
 * do ứng dụng khác tạo) và đệ quy sẽ tràn ngăn xếp.
 *
 * <p>Bộ nhớ đệm của <b>ứng dụng khác</b> không đọc được kể cả khi đã có quyền
 * quản lý toàn bộ tệp: từ Android 11 thư mục {@code Android/data} bị chặn ở mức
 * hệ thống. Nhóm "Bộ nhớ đệm ứng dụng" vì vậy chỉ gồm bộ đệm của chính ứng dụng
 * này – đó là toàn bộ những gì Android còn cho phép.
 */
public class JunkScanner {

    /** Giới hạn độ sâu; sâu hơn nữa hầu như chỉ còn dữ liệu riêng của ứng dụng. */
    private static final int MAX_DEPTH = 8;

    /** Số tệp giữa hai lần báo tiến độ, đủ thưa để không nghẽn luồng chính. */
    private static final int PROGRESS_EVERY = 400;

    private static final String TAG = "JunkScanner";

    /** Thư mục hệ thống chặn hoặc không nên đụng tới. */
    private static final String[] SKIPPED_DIRS = {"Android/data", "Android/obb"};

    public interface ProgressListener {
        /** Báo về luồng nền; phía nhận tự chuyển sang luồng chính nếu cần vẽ. */
        void onProgress(int scannedFiles, long foundBytes);
    }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** Dừng lượt quét đang chạy; luồng nền tự thoát ở vòng lặp kế tiếp. */
    public void cancel() {
        cancelled.set(true);
    }

    public void reset() {
        cancelled.set(false);
    }

    /**
     * Quét toàn bộ và trả về kết quả theo nhóm.
     *
     * <p>Bản đồ luôn đủ sáu nhóm, kể cả nhóm rỗng, để màn hình vẽ đúng sáu dòng
     * như bản thiết kế thay vì số dòng nhảy theo máy.
     */
    @WorkerThread
    @NonNull
    public Map<JunkCategory, JunkGroup> scan(@NonNull Context context,
                                             @Nullable ProgressListener listener) {
        Map<JunkCategory, JunkGroup> groups = new EnumMap<>(JunkCategory.class);
        for (JunkCategory category : JunkCategory.values()) {
            groups.put(category, new JunkGroup(category));
        }

        scanOwnCache(context, groups);
        scanExternalStorage(groups, listener);

        // Nhóm cho chọn từng tệp phải xếp tệp lớn lên đầu, người dùng cuộn danh
        // sách vài trăm dòng thì thứ đáng xoá nhất cần nằm ngay trên cùng
        for (JunkGroup group : groups.values()) {
            if (group.category.isPerFileSelection()) group.sortBySizeDesc();
        }
        return groups;
    }

    /** Bộ đệm của chính ứng dụng: luôn đọc được, không cần quyền nào. */
    private void scanOwnCache(@NonNull Context context,
                              @NonNull Map<JunkCategory, JunkGroup> groups) {
        final JunkGroup group = groups.get(JunkCategory.APP_CACHE);
        if (group == null) return;

        collectAll(context.getCacheDir(), group);
        collectAll(context.getExternalCacheDir(), group);
    }

    private void collectAll(@Nullable File root, @NonNull JunkGroup group) {
        if (root == null || !root.exists()) return;

        Deque<File> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty() && !cancelled.get()) {
            final File current = queue.poll();
            if (current == null) continue;

            if (current.isDirectory()) {
                final File[] children = current.listFiles();
                if (children != null) java.util.Collections.addAll(queue, children);
            } else {
                group.add(current, current.length());
            }
        }
    }

    private void scanExternalStorage(@NonNull Map<JunkCategory, JunkGroup> groups,
                                     @Nullable ProgressListener listener) {
        final File root = Environment.getExternalStorageDirectory();
        if (root == null || !root.canRead()) {
            Logger.d(TAG, "Không đọc được bộ nhớ ngoài, bỏ qua");
            return;
        }

        final int rootLength = root.getAbsolutePath().length();
        Deque<Entry> queue = new ArrayDeque<>();
        queue.add(new Entry(root, 0));

        int scanned = 0;
        long foundBytes = 0L;

        while (!queue.isEmpty() && !cancelled.get()) {
            final Entry entry = queue.poll();
            if (entry == null) continue;

            final File[] children = entry.file.listFiles();
            if (children == null) continue;

            for (File child : children) {
                if (cancelled.get()) return;

                if (child.isDirectory()) {
                    if (entry.depth + 1 <= MAX_DEPTH && !isSkipped(child, rootLength)) {
                        queue.add(new Entry(child, entry.depth + 1));
                    }
                    continue;
                }

                scanned++;
                foundBytes += classifyInto(child, rootLength, groups);

                if (listener != null && scanned % PROGRESS_EVERY == 0) {
                    listener.onProgress(scanned, foundBytes);
                }
            }
        }

        if (listener != null) listener.onProgress(scanned, foundBytes);
    }

    /** Xếp một tệp vào nhóm; trả về dung lượng đã tính, 0 nếu tệp không phải rác. */
    private long classifyInto(@NonNull File file, int rootLength,
                              @NonNull Map<JunkCategory, JunkGroup> groups) {
        final String path = file.getAbsolutePath();
        if (path.length() <= rootLength + 1) return 0L;

        final long size = file.length();
        final JunkCategory category = JunkClassifier.classify(
                path.substring(rootLength + 1), size,
                file.lastModified(), System.currentTimeMillis());
        if (category == null) return 0L;

        final JunkGroup group = groups.get(category);
        if (group == null) return 0L;

        group.add(file, size);
        return size;
    }

    private boolean isSkipped(@NonNull File dir, int rootLength) {
        final String path = dir.getAbsolutePath();
        if (path.length() <= rootLength + 1) return false;

        final String relative = path.substring(rootLength + 1);
        for (String skipped : SKIPPED_DIRS) {
            if (relative.equalsIgnoreCase(skipped)) return true;
        }
        return false;
    }

    /** Một mục trong hàng đợi duyệt, kèm độ sâu để cắt đúng ngưỡng. */
    private static class Entry {
        final File file;
        final int depth;

        Entry(@NonNull File file, int depth) {
            this.file = file;
            this.depth = depth;
        }
    }
}
