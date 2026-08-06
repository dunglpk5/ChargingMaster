package com.dung.chargmagagement.model.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc mức sử dụng CPU từ {@code /proc/stat}.
 *
 * <p><b>Cảnh báo về độ khả dụng:</b> từ Android 8 trở đi, nhiều ROM chặn ứng dụng
 * đọc {@code /proc/stat} vì thông tin này từng bị lợi dụng để theo dõi hành vi
 * người dùng. Khi bị chặn, các hàm ở đây trả về danh sách rỗng và tầng UI phải
 * báo "không đọc được" thay vì hiển thị số 0 gây hiểu nhầm là CPU đang rảnh.
 */
public final class CpuUsageReader {

    private static final String PROC_STAT = "/proc/stat";

    private CpuUsageReader() {
    }

    /**
     * Đọc bộ đếm của CPU tổng và của từng nhân.
     *
     * @return danh sách: phần tử 0 là toàn bộ CPU, các phần tử sau là từng nhân;
     *         rỗng nếu không đọc được file
     */
    @WorkerThread
    @NonNull
    public static List<CpuUsageSnapshot> readSnapshots() {
        List<CpuUsageSnapshot> snapshots = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(PROC_STAT), 2048)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("cpu")) break; // các dòng cpu luôn nằm ở đầu file

                CpuUsageSnapshot snapshot = parseLine(line);
                if (snapshot != null) snapshots.add(snapshot);
            }
        } catch (Exception e) {
            // ROM chặn đọc: trả danh sách rỗng, phía gọi tự xử lý
            return new ArrayList<>();
        }
        return snapshots;
    }

    /**
     * Phân tích một dòng dạng:
     * {@code cpu0 12345 678 9012 345678 901 0 234 0 0 0}
     * theo thứ tự user, nice, system, idle, iowait, irq, softirq, steal…
     *
     * <p>Tách riêng và để mức truy cập gói để kiểm thử được mà không cần thiết bị.
     */
    @Nullable
    static CpuUsageSnapshot parseLine(@NonNull String line) {
        final String[] parts = line.trim().split("\\s+");
        // Cần ít nhất nhãn + 5 cột đầu mới tính được idle
        if (parts.length < 6 || !parts[0].startsWith("cpu")) return null;

        long total = 0L;
        long idle = 0L;

        for (int i = 1; i < parts.length; i++) {
            final long value;
            try {
                value = Long.parseLong(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            total += value;

            // Cột 4 là idle, cột 5 là iowait – cả hai đều tính là rảnh
            if (i == 4 || i == 5) idle += value;
        }
        return new CpuUsageSnapshot(idle, total);
    }
}
