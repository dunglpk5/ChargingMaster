package com.dung.chargmagagement.model.power;

import android.provider.Settings;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Các hạng mục được kiểm tra ở màn Phát hiện sạc.
 *
 * <p><b>Lưu ý quan trọng:</b> từ Android 10 trở đi, ứng dụng <b>không được phép</b>
 * tự bật/tắt Wi-Fi, Bluetooth hay định vị. Vì vậy app chỉ phát hiện trạng thái rồi
 * mở đúng trang Cài đặt tương ứng để người dùng tự tắt.
 *
 * <p>Hai hạng mục không dẫn tới Cài đặt hệ thống:
 * <ul>
 *     <li>{@link #POWER_SOURCE} chỉ mang tính khuyến nghị (dùng củ sạc AC).</li>
 *     <li>{@link #TEMPERATURE} mở màn Nhiệt độ điện thoại của chính app.</li>
 * </ul>
 * Cả hai có {@code settingsAction} bằng null và được xử lý riêng ở tầng điều khiển.
 *
 * <p>{@code estimatedSavingMa} là mức tiêu thụ tham khảo khi tính năng đang bật,
 * dùng để so sánh tương đối giữa các mục chứ không phải số đo chính xác.
 */
public enum PowerDrainFeature {

    POWER_SOURCE(R.string.feature_power_source, R.string.feature_power_source_desc,
            R.drawable.ic_feature_usb, R.color.shield_red, null, 0),

    LOCATION(R.string.feature_location, R.string.feature_location_desc,
            R.drawable.ic_feature_location, R.color.shield_orange,
            Settings.ACTION_LOCATION_SOURCE_SETTINGS, 80),

    HIGH_BRIGHTNESS(R.string.feature_brightness, R.string.feature_brightness_desc,
            R.drawable.ic_feature_brightness, R.color.shield_orange,
            Settings.ACTION_DISPLAY_SETTINGS, 150),

    BLUETOOTH(R.string.feature_bluetooth, R.string.feature_bluetooth_desc,
            R.drawable.ic_feature_bluetooth, R.color.shield_yellow,
            Settings.ACTION_BLUETOOTH_SETTINGS, 25),

    AUTO_SYNC(R.string.feature_auto_sync, R.string.feature_auto_sync_desc,
            R.drawable.ic_feature_sync, R.color.shield_yellow,
            Settings.ACTION_SYNC_SETTINGS, 40),

    TEMPERATURE(R.string.feature_temperature, R.string.feature_temperature_desc,
            R.drawable.ic_feature_thermometer, R.color.shield_green, null, 0);

    @StringRes
    private final int labelRes;

    @StringRes
    private final int descriptionRes;

    @DrawableRes
    private final int iconRes;

    @ColorRes
    private final int shieldColorRes;

    @Nullable
    private final String settingsAction;

    private final int estimatedSavingMa;

    PowerDrainFeature(@StringRes int labelRes,
                      @StringRes int descriptionRes,
                      @DrawableRes int iconRes,
                      @ColorRes int shieldColorRes,
                      @Nullable String settingsAction,
                      int estimatedSavingMa) {
        this.labelRes = labelRes;
        this.descriptionRes = descriptionRes;
        this.iconRes = iconRes;
        this.shieldColorRes = shieldColorRes;
        this.settingsAction = settingsAction;
        this.estimatedSavingMa = estimatedSavingMa;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @StringRes
    public int getDescriptionRes() {
        return descriptionRes;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    @ColorRes
    public int getShieldColorRes() {
        return shieldColorRes;
    }

    /** Action mở trang Cài đặt; null nghĩa là hạng mục được xử lý trong app. */
    @Nullable
    public String getSettingsAction() {
        return settingsAction;
    }

    public boolean hasSettingsPage() {
        return settingsAction != null;
    }

    /** Lượng dòng điện tiết kiệm được ước tính khi tắt (mA). */
    public int getEstimatedSavingMa() {
        return estimatedSavingMa;
    }

    @NonNull
    @Override
    public String toString() {
        return name();
    }
}
