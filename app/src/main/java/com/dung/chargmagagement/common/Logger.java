package com.dung.chargmagagement.common;

import android.util.Log;

import com.dung.chargmagagement.BuildConfig;

/**
 * Bọc {@link Log} để log tự tắt ở bản release, tránh lộ thông tin và tốn I/O.
 */
public final class Logger {

    private static final String PREFIX = "Charg/";

    private Logger() {
    }

    public static void d(String tag, String message) {
        if (BuildConfig.LOG_ENABLED) Log.d(PREFIX + tag, message);
    }

    public static void w(String tag, String message) {
        if (BuildConfig.LOG_ENABLED) Log.w(PREFIX + tag, message);
    }

    /** Lỗi luôn được ghi kể cả release để phục vụ crash report. */
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(PREFIX + tag, message, throwable);
    }
}
