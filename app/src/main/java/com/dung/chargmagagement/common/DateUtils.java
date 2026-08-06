package com.dung.chargmagagement.common;

import java.util.Calendar;
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
     * Số giờ (dạng thập phân) của một khoảng thời gian.
     * Dùng để tính tốc độ tiêu hao %/h.
     */
    public static float toHours(long durationMs) {
        return durationMs / 3_600_000f;
    }
}
