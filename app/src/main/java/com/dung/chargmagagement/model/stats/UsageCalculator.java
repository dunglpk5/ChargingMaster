package com.dung.chargmagagement.model.stats;

import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.entity.ScreenSessionEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tính toán thống kê từ dữ liệu thô trong database.
 *
 * <p>Toàn bộ là hàm tĩnh không phụ thuộc Android hay Room nên kiểm thử được bằng
 * unit test thường – đây là phần logic dễ sai nhất của tab "Sử dụng pin".
 */
public final class UsageCalculator {

    /** Phiên sạc phải nạp được ít nhất bằng này % mới dùng để ước tính dung lượng. */
    public static final int MIN_GAINED_PERCENT_FOR_CAPACITY = 20;

    /** Số phiên gần nhất đưa vào ước tính dung lượng. */
    public static final int CAPACITY_SAMPLE_SIZE = 10;

    private UsageCalculator() {
    }

    /**
     * Tính tốc độ tiêu hao từ danh sách khoảng dùng pin.
     *
     * <p>Cộng dồn tổng % tụt và tổng giờ rồi mới chia, <b>không</b> lấy trung bình
     * của từng tỉ lệ: khoảng 5 phút và khoảng 5 tiếng phải có trọng số khác nhau.
     */
    public static UsageRate calculateRate(List<ScreenSessionEntity> sessions) {
        if (sessions == null || sessions.isEmpty()) return UsageRate.EMPTY;

        int totalDrop = 0;
        long totalMs = 0;
        for (ScreenSessionEntity session : sessions) {
            final int drop = session.startPercent - session.endPercent;
            if (drop <= 0) continue; // không tiêu hao thì không đưa vào mẫu
            totalDrop += drop;
            totalMs += session.getDurationMs();
        }
        if (totalMs <= 0) return UsageRate.EMPTY;

        return new UsageRate(totalDrop, DateUtils.toHours(totalMs));
    }

    /** Gộp hai nhóm màn bật và màn tắt thành tỉ lệ "sử dụng kết hợp". */
    public static UsageRate combine(UsageRate screenOn, UsageRate screenOff) {
        final int totalDrop = screenOn.getTotalPercentDrop() + screenOff.getTotalPercentDrop();
        final float totalHours = screenOn.getTotalHours() + screenOff.getTotalHours();
        if (totalHours <= 0f) return UsageRate.EMPTY;
        return new UsageRate(totalDrop, totalHours);
    }

    /**
     * Ước tính dung lượng thực của pin từ các phiên sạc.
     *
     * <p>Lấy <b>trung vị</b> thay vì trung bình: một phiên bị nhiễu (rút sạc giữa
     * chừng, dùng máy nặng lúc sạc) có thể lệch hàng nghìn mAh và sẽ kéo trung
     * bình đi rất xa, còn trung vị gần như không bị ảnh hưởng.
     *
     * @return dung lượng ước tính (mAh) hoặc {@link BatteryInfo#UNKNOWN_INT}
     *         nếu chưa đủ dữ liệu tin cậy
     */
    public static int estimateCapacity(List<ChargingSessionEntity> sessions) {
        if (sessions == null || sessions.isEmpty()) return BatteryInfo.UNKNOWN_INT;

        List<Integer> estimates = new ArrayList<>(sessions.size());
        for (ChargingSessionEntity session : sessions) {
            if (session.getGainedPercent() < MIN_GAINED_PERCENT_FOR_CAPACITY) continue;
            final int estimate = session.getEstimatedCapacityMah();
            if (estimate != BatteryInfo.UNKNOWN_INT && estimate > 0) {
                estimates.add(estimate);
            }
        }
        if (estimates.isEmpty()) return BatteryInfo.UNKNOWN_INT;

        Collections.sort(estimates);
        return estimates.get(estimates.size() / 2);
    }

    /**
     * Điện tích đã nạp trong một phiên (mAh) = dòng trung bình (mA) × số giờ.
     *
     * @return mAh, hoặc 0 nếu không đo được dòng điện trên máy này
     */
    public static float calculateChargedMah(int avgCurrentMa, long durationMs) {
        if (avgCurrentMa == BatteryInfo.UNKNOWN_INT || avgCurrentMa <= 0 || durationMs <= 0) {
            return 0f;
        }
        return avgCurrentMa * DateUtils.toHours(durationMs);
    }

    /**
     * Dung lượng giả định khi không cách nào xác định được dung lượng thật.
     * 4000 mAh là mức phổ biến nhất của điện thoại hiện nay.
     */
    public static final int FALLBACK_CAPACITY_MAH = 4000;

    /**
     * Ước tính thời gian sạc đầy còn lại (ms).
     *
     * @param currentPercent    mức pin hiện tại
     * @param currentMa         dòng nạp hiện tại (mA), phải dương
     * @param capacityMah       dung lượng pin (mAh)
     * @return thời gian còn lại (ms) hoặc 0 nếu không tính được
     */
    public static long estimateTimeToFull(int currentPercent, int currentMa, int capacityMah) {
        if (currentMa == BatteryInfo.UNKNOWN_INT || currentMa <= 0
                || capacityMah == BatteryInfo.UNKNOWN_INT || capacityMah <= 0
                || currentPercent >= 100) {
            return 0L;
        }
        final float remainingMah = capacityMah * (100 - currentPercent) / 100f;
        final float hours = remainingMah / currentMa;
        return Math.round(hours * 3_600_000f);
    }
}
