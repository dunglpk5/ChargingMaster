package com.dung.chargmagagement.model.device;

/**
 * Ước tính mức tải của một nhân CPU từ xung nhịp hiện tại.
 *
 * <p><b>Dùng khi nào:</b> cách đo chính xác là so hai lần đọc bộ đếm trong
 * {@code /proc/stat}. Một số ROM chặn ứng dụng đọc file đó; khi ấy vẫn còn
 * {@code scaling_cur_freq} trong sysfs, thường không bị chặn. Vị trí của xung
 * nhịp hiện tại trong dải [min, max] của chính nhân đó là con số thay thế hợp lý:
 * bộ điều phối xung nhịp của nhân Linux nâng tần số lên khi có việc và hạ xuống
 * khi rảnh.
 *
 * <p><b>Nhưng đây chỉ là ước tính.</b> Nhân chạy ở xung tối đa mà chỉ dùng một
 * phần năng lực vẫn cho ra 100%, nên con số này có xu hướng cao hơn tải thật.
 * Vì vậy giao diện phải nói rõ đang hiển thị số ước tính, không được trình bày
 * như thể đo được chính xác.
 *
 * <p>Logic thuần, không phụ thuộc Android nên kiểm thử được.
 */
public final class CpuLoadEstimator {

    /** Nhân đang ngủ (xung bằng 0) hoặc không đọc được. */
    public static final int UNKNOWN = -1;

    private CpuLoadEstimator() {
    }

    /**
     * @param currentKhz xung nhịp hiện tại
     * @param minKhz     xung nhịp thấp nhất của nhân
     * @param maxKhz     xung nhịp cao nhất của nhân
     * @return mức tải 0..100, hoặc {@link #UNKNOWN} nếu dữ liệu không dùng được
     */
    public static int estimatePercent(long currentKhz, long minKhz, long maxKhz) {
        if (maxKhz <= 0 || maxKhz <= minKhz) return UNKNOWN;

        // Xung bằng 0 nghĩa là nhân đang bị tắt hẳn để tiết kiệm điện – đó là
        // trạng thái rảnh thật sự, không phải lỗi đọc
        if (currentKhz <= 0) return 0;

        final float ratio = (currentKhz - minKhz) / (float) (maxKhz - minKhz);
        final int percent = Math.round(ratio * 100f);
        return Math.max(0, Math.min(100, percent));
    }
}
