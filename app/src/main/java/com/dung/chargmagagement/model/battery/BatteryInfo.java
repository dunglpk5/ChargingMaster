package com.dung.chargmagagement.model.battery;

/**
 * Ảnh chụp trạng thái pin tại một thời điểm.
 *
 * <p>Đối tượng bất biến (immutable) để có thể truyền qua lại giữa thread nền và
 * UI thread mà không cần đồng bộ hoá.
 */
public final class BatteryInfo {

    /** Giá trị đánh dấu "không đọc được" cho các trường số nguyên. */
    public static final int UNKNOWN_INT = Integer.MIN_VALUE;

    private final int percent;              // 0..100
    private final PlugType plugType;
    private final BatteryHealth health;
    private final boolean charging;         // đang nạp điện (khác với "đã cắm")
    private final boolean full;
    private final float temperatureCelsius;
    private final float voltage;            // volt
    private final String technology;        // "Li-ion", "Li-poly"…
    private final int currentMa;            // dòng tức thời, dương = đang nạp
    private final int designCapacityMah;
    private final long timestamp;

    private BatteryInfo(Builder builder) {
        this.percent = builder.percent;
        this.plugType = builder.plugType;
        this.health = builder.health;
        this.charging = builder.charging;
        this.full = builder.full;
        this.temperatureCelsius = builder.temperatureCelsius;
        this.voltage = builder.voltage;
        this.technology = builder.technology;
        this.currentMa = builder.currentMa;
        this.designCapacityMah = builder.designCapacityMah;
        this.timestamp = builder.timestamp;
    }

    public int getPercent() {
        return percent;
    }

    public PlugType getPlugType() {
        return plugType;
    }

    public BatteryHealth getHealth() {
        return health;
    }

    public boolean isCharging() {
        return charging;
    }

    public boolean isFull() {
        return full;
    }

    public float getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public float getVoltage() {
        return voltage;
    }

    public String getTechnology() {
        return technology;
    }

    /** Dòng tức thời (mA). Dương = đang nạp, âm = đang xả. */
    public int getCurrentMa() {
        return currentMa;
    }

    public boolean hasCurrent() {
        return currentMa != UNKNOWN_INT;
    }

    public int getDesignCapacityMah() {
        return designCapacityMah;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /** Công suất sạc ước tính (W) = U(V) × I(A). */
    public float getPowerWatt() {
        if (!hasCurrent() || voltage <= 0) return 0f;
        return voltage * Math.abs(currentMa) / 1000f;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder giúp tránh constructor quá nhiều tham số dễ nhầm thứ tự. */
    public static final class Builder {
        private int percent;
        private PlugType plugType = PlugType.NONE;
        private BatteryHealth health = BatteryHealth.UNKNOWN;
        private boolean charging;
        private boolean full;
        private float temperatureCelsius;
        private float voltage;
        private String technology = "";
        private int currentMa = UNKNOWN_INT;
        private int designCapacityMah = UNKNOWN_INT;
        private long timestamp = System.currentTimeMillis();

        public Builder percent(int value) {
            this.percent = value;
            return this;
        }

        public Builder plugType(PlugType value) {
            this.plugType = value;
            return this;
        }

        public Builder health(BatteryHealth value) {
            this.health = value;
            return this;
        }

        public Builder charging(boolean value) {
            this.charging = value;
            return this;
        }

        public Builder full(boolean value) {
            this.full = value;
            return this;
        }

        public Builder temperatureCelsius(float value) {
            this.temperatureCelsius = value;
            return this;
        }

        public Builder voltage(float value) {
            this.voltage = value;
            return this;
        }

        public Builder technology(String value) {
            this.technology = value == null ? "" : value;
            return this;
        }

        public Builder currentMa(int value) {
            this.currentMa = value;
            return this;
        }

        public Builder designCapacityMah(int value) {
            this.designCapacityMah = value;
            return this;
        }

        public Builder timestamp(long value) {
            this.timestamp = value;
            return this;
        }

        public BatteryInfo build() {
            return new BatteryInfo(this);
        }
    }
}
