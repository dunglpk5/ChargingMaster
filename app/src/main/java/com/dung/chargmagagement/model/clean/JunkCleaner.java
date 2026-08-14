package com.dung.chargmagagement.model.clean;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.Collection;

/**
 * Xoá các tệp thuộc những nhóm người dùng đã tick.
 *
 * <p>Chỉ xoá đúng những tệp mà lượt quét đã liệt kê, không duyệt lại thư mục lúc
 * xoá. Nếu duyệt lại thì tệp người dùng vừa tạo trong lúc xem kết quả cũng bị
 * cuốn theo, trong khi họ chưa từng nhìn thấy nó trong danh sách.
 */
public final class JunkCleaner {

    private JunkCleaner() {
    }

    /**
     * Xoá các nhóm đang được chọn.
     *
     * @return tổng dung lượng đã giải phóng
     */
    @WorkerThread
    public static long clean(@NonNull Collection<JunkGroup> groups) {
        long freed = 0L;

        for (JunkGroup group : groups) {
            for (File file : group.getSelectedFiles()) {
                freed += deleteFile(file);
            }
        }
        return freed;
    }

    /** Dung lượng giải phóng được từ một tệp; 0 nếu xoá không thành công. */
    private static long deleteFile(@NonNull File file) {
        try {
            if (!file.exists() || !file.isFile()) return 0L;

            // Phải đọc kích thước trước khi xoá, sau đó tệp không còn để hỏi nữa
            final long size = file.length();
            return file.delete() ? size : 0L;
        } catch (SecurityException e) {
            return 0L;
        }
    }
}
