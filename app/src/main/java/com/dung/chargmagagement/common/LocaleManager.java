package com.dung.chargmagagement.common;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Quản lý đa ngôn ngữ.
 *
 * <p>Dùng API per-app language của AndroidX ({@code setApplicationLocales}) thay vì
 * tự bọc Context: từ Android 13 hệ thống lưu lựa chọn giúp, còn dưới 13 AndroidX
 * tự backport qua SharedPreferences nội bộ. Ta vẫn lưu thêm một bản trong
 * {@link PrefManager} để biết người dùng đã chọn thủ công hay chưa.
 */
public final class LocaleManager {

    /** Giá trị nghĩa là "theo ngôn ngữ hệ thống". */
    public static final String LANG_SYSTEM = "";

    /**
     * Mã ngôn ngữ dạng BCP 47.
     *
     * <p>Thêm ngôn ngữ mới phải làm đủ ba chỗ, thiếu một là người dùng chọn xong vẫn
     * thấy tiếng Anh: hằng số ở đây, thư mục {@code values-<mã>} kèm bản dịch, và một
     * dòng trong {@code res/xml/locales_config.xml}.
     *
     * <p>Tên thư mục tài nguyên không phải lúc nào cũng trùng mã BCP 47: tiếng
     * Indonesia là {@code values-in} còn mã là {@code id}, tiếng Trung giản thể là
     * {@code values-zh-rCN} còn mã là {@code zh-CN}.
     */
    public static final String LANG_EN = "en";
    public static final String LANG_VI = "vi";
    public static final String LANG_ES = "es";
    public static final String LANG_PT_BR = "pt-BR";
    public static final String LANG_FR = "fr";
    public static final String LANG_DE = "de";
    public static final String LANG_RU = "ru";
    public static final String LANG_ID = "id";
    public static final String LANG_HI = "hi";
    public static final String LANG_ZH_CN = "zh-CN";
    public static final String LANG_JA = "ja";
    public static final String LANG_KO = "ko";
    public static final String LANG_TR = "tr";
    public static final String LANG_AR = "ar";

    private LocaleManager() {
    }

    /** Gọi một lần lúc Application khởi động. */
    public static void applySavedLocale(@NonNull PrefManager prefs) {
        applyLocale(prefs.getString(PrefManager.KEY_LANGUAGE, LANG_SYSTEM));
    }

    /**
     * Đổi ngôn ngữ và lưu lại lựa chọn. Các Activity đang mở sẽ được hệ thống
     * tạo lại tự động nên không cần recreate() thủ công.
     */
    public static void setLanguage(@NonNull PrefManager prefs, @NonNull String languageTag) {
        // Ghi đồng bộ: applyLocale() ngay bên dưới có thể khiến hệ thống dựng lại
        // tiến trình, ghi bất đồng bộ thì lựa chọn có nguy cơ chưa kịp xuống đĩa
        prefs.putStringNow(PrefManager.KEY_LANGUAGE, languageTag);
        applyLocale(languageTag);
    }

    public static String getCurrentLanguage(@NonNull PrefManager prefs) {
        return prefs.getString(PrefManager.KEY_LANGUAGE, LANG_SYSTEM);
    }

    private static void applyLocale(String languageTag) {
        LocaleListCompat locales = LANG_SYSTEM.equals(languageTag)
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(locales);
    }
}
