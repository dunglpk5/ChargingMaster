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
    public static final String KEY_CURRENT_UNIT_DIVIDER = "current_unit_divider"; // hệ số quy đổi µA -> mA
    public static final String KEY_CURRENT_SIGN = "current_sign";       // 1 hoặc -1, hiệu chỉnh theo hãng

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
