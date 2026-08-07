package com.dung.chargmagagement.common;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Tiện ích ngày tháng theo <b>múi giờ địa phương</b>.
 *
 * <p>Khoá ngày dạng {@code yyyyMMdd} (ví dụ 20260806) được dùng làm cột lọc trong
 * database: vừa đọc được bằng mắt, vừa so sánh/sắp xếp được như số nguyên.
 */
public final class DateUtils {

    public static final long DAY_MS = TimeUnit.DAYS.toMillis(1);

    private DateUtils() {
    }

    /** Khoá ngày của một mốc thời gian. */
    public static int toDayKey(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        return toDayKey(calendar);
    }

    public static int toDayKey(Calendar calendar) {
        return calendar.get(Calendar.YEAR) * 10000
                + (calendar.get(Calendar.MONTH) + 1) * 100
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    /** Khoá ngày của hôm nay. */
    public static int todayKey() {
        return toDayKey(System.currentTimeMillis());
    }

    /** Mốc 00:00 của ngày chứa timestamp. */
    public static long startOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /** Mốc 00:00 của ngày đầu tiên trong tháng chứa timestamp. */
    public static long startOfMonth(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startOfDay(timestamp));
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    /** Số phút tính từ 00:00 của ngày chứa timestamp (0..1439). */
    public static int minuteOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    /** Mốc 00:00 của ngày ứng với khoá {@code yyyyMMdd}. */
    public static long dayKeyToMillis(int dayKey) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(dayKey / 10000, (dayKey / 100) % 100 - 1, dayKey % 100);
        return calendar.getTimeInMillis();
    }

    /** Mốc thời gian của N ngày trước tính từ bây giờ. */
    public static long daysAgo(int days) {
        return System.currentTimeMillis() - days * DAY_MS;
    }

    /**
     * Ngày dạng ngắn theo thói quen của ngôn ngữ đang dùng, ví dụ "06/08/2026".
     *
     * <p>Tạo {@code SimpleDateFormat} mỗi lần gọi thay vì giữ sẵn một đối tượng
     * dùng chung: lớp đó không an toàn khi nhiều thread cùng dùng, mà các hàm ở
     * đây được gọi từ cả thread giao diện lẫn thread nền.
     */
    public static String formatDate(long millis) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(new java.util.Date(millis));
    }

    /** Giờ trong ngày, ví dụ "10:09". */
    public static String formatTime(long millis) {
        return new java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new java.util.Date(millis));
    }

    /**
     * Số giờ (dạng thập phân) của một khoảng thời gian.
     * Dùng để tính tốc độ tiêu hao %/h.
     */
    public static float toHours(long durationMs) {
        return durationMs / 3_600_000f;
    }
}
