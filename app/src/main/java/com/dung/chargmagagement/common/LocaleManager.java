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
    public static final String LANG_EN = "en";
    public static final String LANG_VI = "vi";

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
        prefs.putString(PrefManager.KEY_LANGUAGE, languageTag);
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
