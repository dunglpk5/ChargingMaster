package com.dung.chargmagagement.model.alarm;

/**
 * Tính khoảng cách tới lần kiểm tra báo động kế tiếp.
 *
 * <p>Thay cho việc giữ một tiến trình sống để nhìn từng thay đổi của pin, app chỉ
 * hẹn giờ dậy đúng lúc sắp chạm ngưỡng: biết đang ở 62 % và mỗi phần trăm mất khoảng
 * một phút thì hẹn 18 phút nữa, chứ không cần thức suốt 18 phút đó.
 *
 * <p>Mỗi lần dậy lại ước tính lại từ đầu, nên sai số của lần trước không cộng dồn.
 * Trần {@link #MAX_DELAY_MS} giữ cho sai số tối đa bằng đúng một chu kỳ chờ, kể cả
 * khi tốc độ sạc đột ngột tăng gấp đôi.
 *
 * <p>Lớp thuần Java, không đụng Android nên kiểm thử được.
 */
public final class AlarmScheduleCalculator {

    /** Không hẹn dày hơn mức này, tránh đánh thức máy liên tục lúc gần ngưỡng. */
    public static final long MIN_DELAY_MS = 60_000L;

    /** Không hẹn thưa hơn mức này, để sai số tối đa chỉ bằng một chu kỳ. */
    public static final long MAX_DELAY_MS = 5 * 60_000L;

    /** Dùng khi chưa đo được tốc độ sạc (mẫu đầu tiên của phiên). */
    public static final long DEFAULT_DELAY_MS = 2 * 60_000L;

    /**
     * Chu kỳ kiểm tra nhiệt độ khi máy không cắm sạc.
     *
     * <p>Thưa hơn hẳn vì lúc này máy có thể đang ngủ sâu, mà hệ thống cũng không cho
     * đánh thức dày hơn khoảng 9 phút. Nhiệt độ pin đổi chậm nên mức này là đủ.
     */
    public static final long IDLE_TEMP_DELAY_MS = 10 * 60_000L;

    /**
     * Hẹn sớm hơn ước tính một chút.
     *
     * <p>Báo sớm vài chục giây thì người dùng vẫn rút sạc đúng lúc; báo muộn thì pin
     * đã vượt ngưỡng, tức là hỏng đúng thứ họ cần.
     */
    private static final float SAFETY_FACTOR = 0.9f;

    private AlarmScheduleCalculator() {
    }

    /**
     * Bao lâu nữa nên kiểm tra lại mức pin.
     *
     * @param percent       mức pin hiện tại
     * @param targetPercent ngưỡng cần bắt được
     * @param lastPercent   mức pin ở lần kiểm tra trước (âm nếu chưa có)
     * @param elapsedMs     thời gian đã trôi qua từ lần kiểm tra trước
     */
    public static long nextDelayMs(int percent, int targetPercent,
                                   int lastPercent, long elapsedMs) {
        final int remaining = targetPercent - percent;
        // Đã tới hoặc vượt ngưỡng: cứ kiểm tra lại sớm nhất có thể, phần quyết định
        // có báo hay không là việc của ChargeAlarmChecker
        if (remaining <= 0) return MIN_DELAY_MS;

        final int gained = percent - lastPercent;
        if (lastPercent < 0 || gained <= 0 || elapsedMs <= 0L) return DEFAULT_DELAY_MS;

        final long msPerPercent = elapsedMs / gained;
        return clamp((long) (remaining * msPerPercent * SAFETY_FACTOR));
    }

    private static long clamp(long delayMs) {
        return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, delayMs));
    }
}
