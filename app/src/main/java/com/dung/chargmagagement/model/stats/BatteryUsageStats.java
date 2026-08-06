package com.dung.chargmagagement.model.stats;

import com.dung.chargmagagement.model.battery.BatteryInfo;

/**
 * Toàn bộ số liệu của tab "Sử dụng pin", gom thành một đối tượng để tầng UI chỉ
 * cần một lần cập nhật thay vì nhiều callback rời rạc.
 */
public final class BatteryUsageStats {

    private final UsageRate combined;
    private final UsageRate screenOn;
    private final UsageRate screenOff;
    private final int dischargeSessionCount;

    private final int estimatedCapacityMah;
    private final int designCapacityMah;

    private final int chargeSessionCount;
    private final int totalChargedPercent;

    private BatteryUsageStats(Builder builder) {
        this.combined = builder.combined;
        this.screenOn = builder.screenOn;
        this.screenOff = builder.screenOff;
        this.dischargeSessionCount = builder.dischargeSessionCount;
        this.estimatedCapacityMah = builder.estimatedCapacityMah;
        this.designCapacityMah = builder.designCapacityMah;
        this.chargeSessionCount = builder.chargeSessionCount;
        this.totalChargedPercent = builder.totalChargedPercent;
    }

    public UsageRate getCombined() {
        return combined;
    }

    public UsageRate getScreenOn() {
        return screenOn;
    }

    public UsageRate getScreenOff() {
        return screenOff;
    }

    /** Số khoảng dùng pin đã tính vào thống kê (dòng "Dựa trên N phiên"). */
    public int getDischargeSessionCount() {
        return dischargeSessionCount;
    }

    public int getEstimatedCapacityMah() {
        return estimatedCapacityMah;
    }

    public int getDesignCapacityMah() {
        return designCapacityMah;
    }

    public int getChargeSessionCount() {
        return chargeSessionCount;
    }

    public int getTotalChargedPercent() {
        return totalChargedPercent;
    }

    public boolean hasCapacityEstimate() {
        return estimatedCapacityMah != BatteryInfo.UNKNOWN_INT
                && designCapacityMah != BatteryInfo.UNKNOWN_INT
                && designCapacityMah > 0;
    }

    /**
     * Độ chai pin: tỉ lệ dung lượng còn lại so với thiết kế (0..100).
     * Chặn trần ở 100 vì ước tính có thể vượt nhẹ do sai số đo.
     */
    public int getHealthPercent() {
        if (!hasCapacityEstimate()) return 0;
        return Math.min(100, Math.round(estimatedCapacityMah * 100f / designCapacityMah));
    }

    /** Số % pin nạp trung bình mỗi phiên sạc. */
    public float getAverageChargedPercentPerSession() {
        if (chargeSessionCount <= 0) return 0f;
        return (float) totalChargedPercent / chargeSessionCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UsageRate combined = UsageRate.EMPTY;
        private UsageRate screenOn = UsageRate.EMPTY;
        private UsageRate screenOff = UsageRate.EMPTY;
        private int dischargeSessionCount;
        private int estimatedCapacityMah = BatteryInfo.UNKNOWN_INT;
        private int designCapacityMah = BatteryInfo.UNKNOWN_INT;
        private int chargeSessionCount;
        private int totalChargedPercent;

        public Builder combined(UsageRate value) {
            this.combined = value;
            return this;
        }

        public Builder screenOn(UsageRate value) {
            this.screenOn = value;
            return this;
        }

        public Builder screenOff(UsageRate value) {
            this.screenOff = value;
            return this;
        }

        public Builder dischargeSessionCount(int value) {
            this.dischargeSessionCount = value;
            return this;
        }

        public Builder estimatedCapacityMah(int value) {
            this.estimatedCapacityMah = value;
            return this;
        }

        public Builder designCapacityMah(int value) {
            this.designCapacityMah = value;
            return this;
        }

        public Builder chargeSessionCount(int value) {
            this.chargeSessionCount = value;
            return this;
        }

        public Builder totalChargedPercent(int value) {
            this.totalChargedPercent = value;
            return this;
        }

        public BatteryUsageStats build() {
            return new BatteryUsageStats(this);
        }
    }
}
