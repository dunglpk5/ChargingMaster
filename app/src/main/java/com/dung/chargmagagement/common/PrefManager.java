package com.dung.chargmagagement.common;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Nơi duy nhất đọc/ghi SharedPreferences, tránh rải key khắp nơi trong code.
 */
public final class PrefManager {

    private static final String FILE_NAME = "charg_prefs";

    // ==== Danh sách key ====
    public static final String KEY_LANGUAGE = "language";              // "en" | "vi" | "" (theo hệ thống)
    public static final String KEY_DESIGN_CAPACITY = "design_capacity"; // mAh người dùng tự đặt
    public static final String KEY_FIRST_LAUNCH = "first_launch";

    /**
     * Ba phần của việc ghi lịch sử pin dưới nền, bật/tắt độc lập.
     *
     * <p>Tất cả mặc định <b>tắt</b>: dịch vụ nền phải do người dùng chủ động bật, không
     * tự chạy sau khi cài. Dịch vụ sống khi ít nhất một trong hai phần ghi dữ liệu
     * ({@code CHART}, {@code SCREEN}) đang bật; {@code DETAILS} chỉ đổi nội dung thông
     * báo chứ không tự khởi động gì.
     */
    public static final String KEY_LOG_CHART = "battery_logging_chart";
    public static final String KEY_LOG_SCREEN = "battery_logging_screen";
    public static final String KEY_LOG_DETAILS = "battery_logging_details";
    public static final String KEY_CURRENT_UNIT_DIVIDER = "current_unit_divider"; // hệ số quy đổi µA -> mA
    public static final String KEY_CURRENT_SIGN = "current_sign";       // 1 hoặc -1, hiệu chỉnh theo hãng

    /**
     * Dòng đo được gần nhất của từng chiều (mA).
     *
     * <p>Phần cứng chỉ báo <b>dòng thực của pin</b> – tức hiệu số giữa dòng nạp vào
     * và mức máy đang tiêu thụ. Lúc đang sạc, không có cách nào tách riêng phần tiêu
     * thụ ra khỏi con số đó. Vì vậy giá trị của mỗi chiều được nhớ lại từ lần đo gần
     * nhất, để màn Phát hiện sạc luôn có số cho cả hai ô thay vì bỏ trống một bên.
     */
    public static final String KEY_LAST_CHARGING_MA = "last_charging_ma";
    public static final String KEY_LAST_IDLE_MA = "last_idle_ma";

    private static volatile PrefManager instance;
    private final SharedPreferences prefs;

    private PrefManager(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public static PrefManager get(@NonNull Context context) {
        if (instance == null) {
            synchronized (PrefManager.class) {
                if (instance == null) {
                    instance = new PrefManager(context);
                }
            }
        }
        return instance;
    }

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    /**
     * Ghi ngay xuống đĩa thay vì để hệ thống ghi sau.
     *
     * <p>Dùng cho những giá trị được lưu ngay trước một hành động có thể giết tiến
     * trình - đổi ngôn ngữ chẳng hạn: {@code apply()} ghi bất đồng bộ, nếu hệ thống
     * dựng lại tiến trình trước khi ghi xong thì lựa chọn mất trắng.
     */
    public void putStringNow(String key, String value) {
        prefs.edit().putString(key, value).commit();
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }
}
