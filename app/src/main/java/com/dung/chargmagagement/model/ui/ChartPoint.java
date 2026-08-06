package com.dung.chargmagagement.model.ui;

/**
 * Một điểm trên biểu đồ mức pin trong ngày.
 */
public final class ChartPoint {

    /** Số phút tính từ 00:00 (0..1439) – toạ độ trục hoành. */
    private final int minuteOfDay;

    /** Mức pin 0..100 – toạ độ trục tung. */
    private final int percent;

    /** Lúc này có đang sạc không, dùng để tô màu khác đoạn đang nạp. */
    private final boolean charging;

    public ChartPoint(int minuteOfDay, int percent, boolean charging) {
        this.minuteOfDay = minuteOfDay;
        this.percent = percent;
        this.charging = charging;
    }

    public int getMinuteOfDay() {
        return minuteOfDay;
    }

    public int getPercent() {
        return percent;
    }

    public boolean isCharging() {
        return charging;
    }
}
