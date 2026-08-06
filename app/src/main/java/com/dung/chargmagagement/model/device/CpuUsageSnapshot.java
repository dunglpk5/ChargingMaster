package com.dung.chargmagagement.model.device;

/**
 * Một lần đọc bộ đếm thời gian CPU từ {@code /proc/stat}.
 *
 * <p>Nhân Linux không báo "CPU đang dùng bao nhiêu phần trăm", nó chỉ đếm tổng số
 * nhịp (jiffies) mà CPU đã ở từng trạng thái kể từ lúc khởi động. Muốn ra phần
 * trăm phải lấy <b>hai lần đọc cách nhau một khoảng</b> rồi tính tỉ lệ phần nhịp
 * bận trên tổng phần nhịp đã trôi qua giữa hai lần.
 */
public final class CpuUsageSnapshot {

    /** Tổng số nhịp ở trạng thái rảnh (idle + iowait). */
    private final long idle;

    /** Tổng số nhịp của mọi trạng thái. */
    private final long total;

    public CpuUsageSnapshot(long idle, long total) {
        this.idle = idle;
        this.total = total;
    }

    public long getIdle() {
        return idle;
    }

    public long getTotal() {
        return total;
    }

    /**
     * Phần trăm bận so với lần đọc trước.
     *
     * @param previous lần đọc trước đó; null hoặc không hợp lệ thì trả về 0
     * @return mức sử dụng 0..100
     */
    public int usagePercentSince(CpuUsageSnapshot previous) {
        if (previous == null) return 0;

        final long totalDelta = total - previous.total;
        final long idleDelta = idle - previous.idle;

        // Bộ đếm không lùi; nếu thấy lùi thì nhân vừa khởi động lại bộ đếm
        if (totalDelta <= 0 || idleDelta < 0) return 0;

        final long busyDelta = totalDelta - idleDelta;
        final int percent = (int) Math.round(busyDelta * 100d / totalDelta);
        return Math.max(0, Math.min(100, percent));
    }
}
