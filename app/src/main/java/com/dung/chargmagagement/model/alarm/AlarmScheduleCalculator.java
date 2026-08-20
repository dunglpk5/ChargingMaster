package com.dung.chargmagagement.model.alarm;

/**
 * Tính khoảng cách tới lần kiểm tra báo động kế tiếp.
 *
 * <p>Thay cho việc giữ một tiến trình sống để nhìn từng thay đổi của pin, app chỉ
 * hẹn giờ dậy đúng lúc sắp chạm ngưỡng: biết đang ở 62 % và mỗi phần trăm mất khoảng
 * một phút thì hẹn 18 phút nữa, chứ không cần thức suốt 18 phút đó.
 *
 * <p>Mỗi lần dậy lại ước tính lại từ đầu, nên sai số của lần trước không cộng dồn.
 * Trần {@link #MAX_DELAY_MS} giữ cho sai số tối đa bằng đúng một chu kỳ chờ, kể cả khi
 * tốc độ sạc đột ngột tăng gấp đôi. Càng gần ngưỡng thì nhịp càng siết lại
 * ({@link #CLOSE_DELAY_MS} rồi {@link #NEAR_DELAY_MS}), nên độ trễ cuối cùng của cảnh
 * báo chỉ còn cỡ hai chục giây.
 *
 * <p>Lớp thuần Java, không đụng Android nên kiểm thử được.
 */
public final class AlarmScheduleCalculator {

    /** Nhịp thường: không hẹn dày hơn mức này khi còn xa ngưỡng. */
    public static final long MIN_DELAY_MS = 60_000L;

    /**
     * Nhịp ở đoạn cuối, khi chỉ còn vài phần trăm nữa là tới ngưỡng.
     *
     * <p>Đây là chỗ quyết định cảnh báo có "đúng lúc" hay không. Còn 1 % mà vẫn chờ
     * một phút thì máy sạc nhanh đã vượt ngưỡng trước khi ta kịp nhìn. Đang cắm sạc
     * nên máy không vào Doze, hẹn dưới một phút là hợp lệ; và đoạn này chỉ dài vài
     * phút mỗi phiên nên tổng số lần đánh thức tăng không đáng kể.
     */
    public static final long NEAR_DELAY_MS = 20_000L;
    public static final long CLOSE_DELAY_MS = 45_000L;

    /** Còn bao nhiêu phần trăm thì coi là đã vào đoạn cuối. */
    private static final int NEAR_PERCENT = 1;
    private static final int CLOSE_PERCENT = 3;

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
        // Đã tới hoặc vượt ngưỡng: kiểm tra lại sớm nhất có thể, phần quyết định có
        // báo hay không là việc của ChargeAlarmChecker
        if (remaining <= 0) return NEAR_DELAY_MS;

        final int gained = percent - lastPercent;
        if (lastPercent < 0 || gained <= 0 || elapsedMs <= 0L) {
            // Chưa đo được tốc độ. Còn xa thì chờ mức mặc định, nhưng đã sát ngưỡng
            // thì phải theo nhịp đoạn cuối, không được chờ tới hai phút
            return remaining <= CLOSE_PERCENT ? floorFor(remaining) : DEFAULT_DELAY_MS;
        }

        final long msPerPercent = elapsedMs / gained;
        return clamp((long) (remaining * msPerPercent * SAFETY_FACTOR), remaining);
    }

    /**
     * Sàn thời gian chờ, siết dần khi còn ít phần trăm.
     * Đây là thứ quyết định độ trễ tối đa của cảnh báo.
     */
    private static long floorFor(int remaining) {
        if (remaining <= NEAR_PERCENT) return NEAR_DELAY_MS;
        if (remaining <= CLOSE_PERCENT) return CLOSE_DELAY_MS;
        return MIN_DELAY_MS;
    }

    private static long clamp(long delayMs, int remaining) {
        return Math.max(floorFor(remaining), Math.min(MAX_DELAY_MS, delayMs));
    }
}
