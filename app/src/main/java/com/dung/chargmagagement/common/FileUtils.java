package com.dung.chargmagagement.common;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Đọc các node sysfs/procfs của nhân Linux.
 *
 * <p>Đây là nguồn duy nhất lấy được thông tin CPU trên Android. Việc đọc luôn có
 * thể thất bại (thiếu node, không đủ quyền, ROM tuỳ biến) nên mọi hàm đều trả về
 * {@code null} thay vì ném ngoại lệ – phía gọi chỉ cần kiểm tra null.
 */
public final class FileUtils {

    private FileUtils() {
    }

    /** Dòng đầu tiên của file, đã cắt khoảng trắng; null nếu không đọc được. */
    @WorkerThread
    @Nullable
    public static String readFirstLine(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path), 256)) {
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** Số nguyên dài trong file; null nếu không đọc hoặc không parse được. */
    @WorkerThread
    @Nullable
    public static Long readLong(String path) {
        String line = readFirstLine(path);
        if (line == null || line.isEmpty()) return null;
        try {
            return Long.parseLong(line);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Tìm giá trị của một khoá trong file dạng "khoá : giá trị" như /proc/cpuinfo.
     *
     * @param path    đường dẫn file
     * @param keyName tên khoá, so sánh không phân biệt hoa thường
     */
    @WorkerThread
    @Nullable
    public static String findValue(String path, String keyName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path), 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                final int separator = line.indexOf(':');
                if (separator < 0) continue;

                final String key = line.substring(0, separator).trim();
                if (key.equalsIgnoreCase(keyName)) {
                    final String value = line.substring(separator + 1).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
