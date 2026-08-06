package com.dung.chargmagagement.model.power;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Trạng thái bật/tắt của một tính năng tiêu điện tại thời điểm kiểm tra.
 */
public final class DrainStatus {

    @NonNull
    private final PowerDrainFeature feature;

    private final boolean active;

    public DrainStatus(@NonNull PowerDrainFeature feature, boolean active) {
        this.feature = feature;
        this.active = active;
    }

    @NonNull
    public PowerDrainFeature getFeature() {
        return feature;
    }

    /** Tính năng đang bật, tức là đang tiêu điện không cần thiết lúc sạc. */
    public boolean isActive() {
        return active;
    }

    /**
     * Tổng dòng điện tiết kiệm được nếu tắt hết các mục đang bật (mA).
     * Tách thành hàm tĩnh để kiểm thử được mà không cần Context.
     */
    public static int totalSavingMa(@NonNull List<DrainStatus> statuses) {
        int total = 0;
        for (DrainStatus status : statuses) {
            if (status.isActive()) {
                total += status.getFeature().getEstimatedSavingMa();
            }
        }
        return total;
    }

    /** Số tính năng đang bật. */
    public static int countActive(@NonNull List<DrainStatus> statuses) {
        int count = 0;
        for (DrainStatus status : statuses) {
            if (status.isActive()) count++;
        }
        return count;
    }

    /**
     * Ước tính thời gian sạc rút ngắn được (ms) nếu tắt hết các mục đang bật.
     *
     * <p>Ý tưởng: dòng nạp thực tế vào pin bằng dòng từ củ sạc trừ đi phần hệ thống
     * đang tiêu thụ. Tắt bớt tính năng làm phần tiêu thụ giảm, dòng vào pin tăng
     * lên, nên thời gian sạc đầy ngắn lại. Chênh lệch giữa hai lần tính chính là
     * thời gian tiết kiệm được.
     *
     * @param remainingMah điện tích còn cần nạp (mAh)
     * @param currentMa    dòng nạp đang đo được (mA), phải dương
     * @param savingMa     dòng tiết kiệm được nếu tắt hết (mA)
     * @return thời gian rút ngắn (ms), 0 nếu không tính được
     */
    public static long estimateTimeSavedMs(float remainingMah, int currentMa, int savingMa) {
        if (remainingMah <= 0f || currentMa <= 0 || savingMa <= 0) return 0L;

        final float hoursNow = remainingMah / currentMa;
        final float hoursAfter = remainingMah / (currentMa + savingMa);
        return Math.round((hoursNow - hoursAfter) * 3_600_000f);
    }
}
