package com.dung.chargmagagement.model.alarm;

import androidx.annotation.NonNull;

import com.dung.chargmagagement.common.PrefManager;

/**
 * Cấu hình báo động sạc do người dùng đặt.
 *
 * <p>Đối tượng bất biến, đọc một lần từ {@link PrefManager} rồi truyền vào phần
 * logic kiểm tra. Nhờ vậy {@link ChargeAlarmChecker} không cần biết tới
 * SharedPreferences và kiểm thử được bằng unit test thường.
 */
public final class AlarmSettings {

    // ==== Khoá lưu trữ ====
    private static final String KEY_THRESHOLD_ENABLED = "alarm_threshold_enabled";
    private static final String KEY_THRESHOLD_PERCENT = "alarm_threshold_percent";
    private static final String KEY_FULL_ENABLED = "alarm_full_enabled";
    private static final String KEY_OVERHEAT_ENABLED = "alarm_overheat_enabled";
    private static final String KEY_OVERHEAT_TEMP = "alarm_overheat_temp";

    /**
     * Ngưỡng mặc định 80%: sạc quá mức này thường xuyên làm pin lithium
     * chai nhanh hơn, nên đây là mốc nhắc nhở hợp lý.
     */
    public static final int DEFAULT_THRESHOLD_PERCENT = 80;
    public static final int MIN_THRESHOLD_PERCENT = 50;
    public static final int MAX_THRESHOLD_PERCENT = 95;

    /** Ngưỡng nhiệt độ mặc định (°C); trên mức này pin bắt đầu xuống cấp nhanh. */
    public static final int DEFAULT_OVERHEAT_TEMP = 43;
    public static final int MIN_OVERHEAT_TEMP = 38;
    public static final int MAX_OVERHEAT_TEMP = 55;

    private final boolean thresholdEnabled;
    private final int thresholdPercent;
    private final boolean fullEnabled;
    private final boolean overheatEnabled;
    private final int overheatTemp;

    public AlarmSettings(boolean thresholdEnabled, int thresholdPercent,
                         boolean fullEnabled, boolean overheatEnabled, int overheatTemp) {
        this.thresholdEnabled = thresholdEnabled;
        this.thresholdPercent = thresholdPercent;
        this.fullEnabled = fullEnabled;
        this.overheatEnabled = overheatEnabled;
        this.overheatTemp = overheatTemp;
    }

    /** Đọc cấu hình đang lưu. */
    @NonNull
    public static AlarmSettings load(@NonNull PrefManager prefs) {
        return new AlarmSettings(
                prefs.getBoolean(KEY_THRESHOLD_ENABLED, false),
                prefs.getInt(KEY_THRESHOLD_PERCENT, DEFAULT_THRESHOLD_PERCENT),
                prefs.getBoolean(KEY_FULL_ENABLED, false),
                prefs.getBoolean(KEY_OVERHEAT_ENABLED, false),
                prefs.getInt(KEY_OVERHEAT_TEMP, DEFAULT_OVERHEAT_TEMP));
    }

    /** Ghi cấu hình xuống bộ nhớ. */
    public void save(@NonNull PrefManager prefs) {
        prefs.putBoolean(KEY_THRESHOLD_ENABLED, thresholdEnabled);
        prefs.putInt(KEY_THRESHOLD_PERCENT, thresholdPercent);
        prefs.putBoolean(KEY_FULL_ENABLED, fullEnabled);
        prefs.putBoolean(KEY_OVERHEAT_ENABLED, overheatEnabled);
        prefs.putInt(KEY_OVERHEAT_TEMP, overheatTemp);
    }

    public boolean isThresholdEnabled() {
        return thresholdEnabled;
    }

    public int getThresholdPercent() {
        return thresholdPercent;
    }

    public boolean isFullEnabled() {
        return fullEnabled;
    }

    public boolean isOverheatEnabled() {
        return overheatEnabled;
    }

    public int getOverheatTemp() {
        return overheatTemp;
    }

    /** Có bật cảnh báo nào không – dùng để bỏ qua toàn bộ việc kiểm tra cho nhanh. */
    public boolean hasAnyEnabled() {
        return thresholdEnabled || fullEnabled || overheatEnabled;
    }

    /** Tạo bản sao với một giá trị thay đổi (dùng khi người dùng gạt công tắc). */
    public AlarmSettings withThreshold(boolean enabled, int percent) {
        return new AlarmSettings(enabled, clamp(percent, MIN_THRESHOLD_PERCENT, MAX_THRESHOLD_PERCENT),
                fullEnabled, overheatEnabled, overheatTemp);
    }

    public AlarmSettings withFull(boolean enabled) {
        return new AlarmSettings(thresholdEnabled, thresholdPercent,
                enabled, overheatEnabled, overheatTemp);
    }

    public AlarmSettings withOverheat(boolean enabled, int temp) {
        return new AlarmSettings(thresholdEnabled, thresholdPercent, fullEnabled,
                enabled, clamp(temp, MIN_OVERHEAT_TEMP, MAX_OVERHEAT_TEMP));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
