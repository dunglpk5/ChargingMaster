package com.dung.chargmagagement.model.battery;

/**
 * Bộ lọc và thống kê dòng điện theo cửa sổ trượt.
 *
 * <p>Giá trị đọc từ sysfs dao động khá mạnh giữa các lần lấy mẫu (±300 mA là
 * bình thường). Hiển thị thẳng sẽ nhảy số liên tục, nên ta lấy <b>trung vị</b>
 * của cửa sổ gần nhất: trung vị chống nhiễu đột biến tốt hơn trung bình.
 *
 * <p>Lớp này không đồng bộ hoá; chỉ dùng trong một thread (thread sampler).
 */
public final class CurrentStats {

    private static final int WINDOW_SIZE = 5;

    private final int[] window = new int[WINDOW_SIZE];
    private final int[] sortBuffer = new int[WINDOW_SIZE];
    private int count;
    private int writeIndex;

    private int minMa = Integer.MAX_VALUE;
    private int maxMa = Integer.MIN_VALUE;
    private long sumMa;
    private int totalSamples;

    /** Thêm một mẫu mới (mA). Bỏ qua mẫu không hợp lệ. */
    public void addSample(int milliAmp) {
        if (milliAmp == BatteryInfo.UNKNOWN_INT) return;

        window[writeIndex] = milliAmp;
        writeIndex = (writeIndex + 1) % WINDOW_SIZE;
        if (count < WINDOW_SIZE) count++;

        minMa = Math.min(minMa, milliAmp);
        maxMa = Math.max(maxMa, milliAmp);
        sumMa += milliAmp;
        totalSamples++;
    }

    /** Giá trị đã làm mượt để hiển thị, hoặc {@link BatteryInfo#UNKNOWN_INT} nếu chưa có mẫu. */
    public int getSmoothedMa() {
        if (count == 0) return BatteryInfo.UNKNOWN_INT;

        System.arraycopy(window, 0, sortBuffer, 0, count);
        // Cửa sổ chỉ 5 phần tử nên sắp xếp chèn là đủ nhanh, không cần Arrays.sort
        for (int i = 1; i < count; i++) {
            int value = sortBuffer[i];
            int j = i - 1;
            while (j >= 0 && sortBuffer[j] > value) {
                sortBuffer[j + 1] = sortBuffer[j];
                j--;
            }
            sortBuffer[j + 1] = value;
        }
        return sortBuffer[count / 2];
    }

    public int getMinMa() {
        return totalSamples == 0 ? BatteryInfo.UNKNOWN_INT : minMa;
    }

    public int getMaxMa() {
        return totalSamples == 0 ? BatteryInfo.UNKNOWN_INT : maxMa;
    }

    /** Trung bình toàn phiên, dùng để lưu vào lịch sử sạc ở Phase 3. */
    public int getAverageMa() {
        return totalSamples == 0 ? BatteryInfo.UNKNOWN_INT : (int) (sumMa / totalSamples);
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    /** Xoá sạch khi bắt đầu một phiên đo mới. */
    public void reset() {
        count = 0;
        writeIndex = 0;
        minMa = Integer.MAX_VALUE;
        maxMa = Integer.MIN_VALUE;
        sumMa = 0;
        totalSamples = 0;
    }
}
