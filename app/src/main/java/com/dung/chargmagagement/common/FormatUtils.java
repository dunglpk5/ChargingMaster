package com.dung.chargmagagement.common;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Các hàm định dạng dữ liệu hiển thị dùng chung (nhiệt độ, dung lượng, thời gian…).
 */
public final class FormatUtils {

    private FormatUtils() {
    }

    /** Đổi độ C sang độ F. */
    public static float celsiusToFahrenheit(float celsius) {
        return celsius * 9f / 5f + 32f;
    }

    /** Ví dụ: "37.0℃/ 98℉" đúng như bản thiết kế. */
    public static String formatTemperature(float celsius) {
        return String.format(Locale.US, "%.1f℃/ %.0f℉", celsius, celsiusToFahrenheit(celsius));
    }

    /** Ví dụ: "3.85 V". */
    public static String formatVoltage(float volt) {
        return String.format(Locale.US, "%.1f V", volt);
    }

    /** Đổi byte sang chuỗi dễ đọc: "96.7GB". */
    public static String formatBytes(long bytes) {
        final double gb = bytes / (1024d * 1024d * 1024d);
        if (gb >= 1) return String.format(Locale.US, "%.1fGB", gb);
        final double mb = bytes / (1024d * 1024d);
        return String.format(Locale.US, "%.1fMB", mb);
    }

    /** Đổi mili giây sang "2h 15m" / "45m"; trả về "-" nếu không xác định. */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "-";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.US, "%dm", minutes);
    }

    /**
     * Đổi mili giây sang "13h 10m" / "5m 20s" / "46s".
     *
     * <p>Khác {@link #formatDuration}: bản này có cả giây, dùng cho những chặng
     * vừa mới bắt đầu – hiện "0m" trong phút đầu tiên trông như đồng hồ chết.
     */
    public static String formatDurationShort(long millis) {
        if (millis <= 0) return "0s";

        final long hours = TimeUnit.MILLISECONDS.toHours(millis);
        final long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        final long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) return String.format(Locale.US, "%dh %02dm", hours, minutes);
        if (minutes > 0) return String.format(Locale.US, "%dm %02ds", minutes, seconds);
        return String.format(Locale.US, "%ds", seconds);
    }

    /** Làm tròn phần trăm về số nguyên an toàn trong khoảng 0..100. */
    public static int clampPercent(float value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }
}
