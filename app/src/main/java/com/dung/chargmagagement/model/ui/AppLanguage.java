package com.dung.chargmagagement.model.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.LocaleManager;

/**
 * Danh sách ngôn ngữ app hỗ trợ: mã, tên hiển thị và lá cờ, gom về một chỗ.
 *
 * <p>Trước đây danh sách này nằm rải ở hai nơi — ba mảng song song ở màn chọn ngôn
 * ngữ và một chuỗi {@code if/else} ở màn Cài đặt. Thêm ngôn ngữ mới mà quên sửa
 * nhánh {@code else} thì màn Cài đặt vẫn hiện "theo hệ thống" dù app đã đổi ngôn ngữ,
 * đúng lỗi đã gặp.
 *
 * <p>Thêm ngôn ngữ mới cần làm đủ bốn chỗ: một hằng số ở {@link LocaleManager}, một
 * dòng ở đây, thư mục {@code values-<mã>} kèm bản dịch, một dòng trong
 * {@code res/xml/locales_config.xml} và một mã trong {@code resourceConfigurations}
 * của {@code build.gradle.kts}.
 */
public enum AppLanguage {

    /*
     * Cờ chỉ là dấu hiệu nhận biết nhanh, không phải là "ngôn ngữ này thuộc nước đó":
     * tiếng Tây Ban Nha đâu chỉ có ở Tây Ban Nha, tiếng Ả Rập càng không của riêng
     * nước nào. Mục "theo hệ thống" dùng quả địa cầu.
     */
    SYSTEM(LocaleManager.LANG_SYSTEM, R.string.language_system, "🌐"),
    VIETNAMESE(LocaleManager.LANG_VI, R.string.language_vi, "🇻🇳"),
    ENGLISH(LocaleManager.LANG_EN, R.string.language_en, "🇬🇧"),
    SPANISH(LocaleManager.LANG_ES, R.string.language_es, "🇪🇸"),
    PORTUGUESE(LocaleManager.LANG_PT_BR, R.string.language_pt, "🇧🇷"),
    FRENCH(LocaleManager.LANG_FR, R.string.language_fr, "🇫🇷"),
    GERMAN(LocaleManager.LANG_DE, R.string.language_de, "🇩🇪"),
    RUSSIAN(LocaleManager.LANG_RU, R.string.language_ru, "🇷🇺"),
    INDONESIAN(LocaleManager.LANG_ID, R.string.language_in, "🇮🇩"),
    HINDI(LocaleManager.LANG_HI, R.string.language_hi, "🇮🇳"),
    CHINESE(LocaleManager.LANG_ZH_CN, R.string.language_zh, "🇨🇳"),
    JAPANESE(LocaleManager.LANG_JA, R.string.language_ja, "🇯🇵"),
    KOREAN(LocaleManager.LANG_KO, R.string.language_ko, "🇰🇷"),
    TURKISH(LocaleManager.LANG_TR, R.string.language_tr, "🇹🇷"),
    ARABIC(LocaleManager.LANG_AR, R.string.language_ar, "🇸🇦");

    @NonNull
    private final String tag;

    @StringRes
    private final int labelRes;

    @NonNull
    private final String flag;

    AppLanguage(@NonNull String tag, @StringRes int labelRes, @NonNull String flag) {
        this.tag = tag;
        this.labelRes = labelRes;
        this.flag = flag;
    }

    @NonNull
    public String getTag() {
        return tag;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @NonNull
    public String getFlag() {
        return flag;
    }

    /** Ngôn ngữ ứng với một mã đã lưu; {@link #SYSTEM} nếu mã lạ hoặc rỗng. */
    @NonNull
    public static AppLanguage fromTag(@Nullable String tag) {
        if (tag == null || tag.isEmpty()) return SYSTEM;

        for (AppLanguage language : values()) {
            if (language.tag.equals(tag)) return language;
        }
        return SYSTEM;
    }
}
