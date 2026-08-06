package com.dung.chargmagagement.model.power;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.PlugType;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các hạng mục đang làm chậm quá trình sạc và mở trang Cài đặt tương ứng.
 *
 * <p>Toàn bộ việc kiểm tra dùng API <b>không cần xin quyền lúc chạy</b>: đọc
 * {@link Settings.Global}/{@link Settings.Secure} hoặc gọi hàm trạng thái không
 * yêu cầu quyền. Riêng Bluetooth, từ Android 12 việc gọi
 * {@code BluetoothAdapter.isEnabled()} đòi quyền {@code BLUETOOTH_CONNECT}, nên ở
 * đây đọc thẳng cờ trong Settings.Global để khỏi phải hỏi người dùng một quyền
 * chẳng liên quan gì tới việc sạc.
 */
public final class PowerOptimizer {

    private static final String TAG = "PowerOptimizer";

    /** Cờ trạng thái Bluetooth trong Settings.Global (hằng số chính thức bị ẩn). */
    private static final String SETTING_BLUETOOTH_ON = "bluetooth_on";

    /** Ngưỡng coi là "độ sáng cao": trên 70% thang 0–255. */
    private static final int BRIGHTNESS_HIGH_THRESHOLD = 178;

    /** Ngưỡng coi là nóng, dùng cho hạng mục phát hiện nhiệt độ (℃). */
    private static final float TEMPERATURE_WARN_CELSIUS = 38f;

    private final Context appContext;

    public PowerOptimizer(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Quét trạng thái tất cả hạng mục.
     *
     * @param info trạng thái pin mới nhất; cần cho hạng mục nguồn điện và nhiệt độ.
     *             Có thể null khi chưa có số liệu, khi đó hai mục đó coi như đạt.
     */
    @WorkerThread
    @NonNull
    public List<DrainStatus> scan(@Nullable BatteryInfo info) {
        List<DrainStatus> results = new ArrayList<>();
        for (PowerDrainFeature feature : PowerDrainFeature.values()) {
            results.add(new DrainStatus(feature, isActive(feature, info)));
        }
        return results;
    }

    private boolean isActive(@NonNull PowerDrainFeature feature, @Nullable BatteryInfo info) {
        try {
            switch (feature) {
                case POWER_SOURCE:
                    return isSlowPowerSource(info);
                case LOCATION:
                    return isLocationOn();
                case HIGH_BRIGHTNESS:
                    return isBrightnessHigh();
                case BLUETOOTH:
                    return isBluetoothOn();
                case AUTO_SYNC:
                    return ContentResolver.getMasterSyncAutomatically();
                case TEMPERATURE:
                    return isTemperatureHigh(info);
                default:
                    return false;
            }
        } catch (Exception e) {
            // Một số ROM chặn hoặc không có thành phần tương ứng; coi như đã đạt
            Logger.d(TAG, "Không kiểm tra được " + feature.name() + ": "
                    + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Nguồn sạc chậm: đang cắm nhưng không phải củ sạc AC.
     * Cổng USB máy tính thường chỉ cấp 500 mA, chậm hơn hẳn củ sạc rời.
     */
    private boolean isSlowPowerSource(@Nullable BatteryInfo info) {
        if (info == null) return false;
        return info.getPlugType().isPlugged() && info.getPlugType() != PlugType.AC;
    }

    private boolean isTemperatureHigh(@Nullable BatteryInfo info) {
        return info != null && info.getTemperatureCelsius() >= TEMPERATURE_WARN_CELSIUS;
    }

    private boolean isBluetoothOn() {
        return Settings.Global.getInt(appContext.getContentResolver(), SETTING_BLUETOOTH_ON, 0) == 1;
    }

    /**
     * Trạng thái định vị.
     *
     * <p>{@code LocationManager.isLocationEnabled()} chỉ có từ Android 9; trên
     * Android 8.0–8.1 phải đọc {@code LOCATION_MODE} trong Settings.Secure,
     * giá trị 0 nghĩa là đã tắt hẳn.
     */
    private boolean isLocationOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager manager =
                    (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
            return manager != null && manager.isLocationEnabled();
        }
        return Settings.Secure.getInt(appContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
                != Settings.Secure.LOCATION_MODE_OFF;
    }

    /**
     * Độ sáng cao là thứ tốn điện nhất trong danh sách này.
     * Khi máy đang ở chế độ tự động thì không đánh giá, vì hệ thống đã tự điều tiết.
     */
    private boolean isBrightnessHigh() {
        ContentResolver resolver = appContext.getContentResolver();

        final int mode = Settings.System.getInt(resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) return false;

        final int brightness = Settings.System.getInt(resolver,
                Settings.System.SCREEN_BRIGHTNESS, 0);
        return brightness > BRIGHTNESS_HIGH_THRESHOLD;
    }

    /**
     * Intent mở trang Cài đặt của một hạng mục.
     *
     * @return null nếu hạng mục không dẫn tới Cài đặt hệ thống
     *         (xem {@link PowerDrainFeature#hasSettingsPage()})
     */
    @Nullable
    public Intent buildSettingsIntent(@NonNull PowerDrainFeature feature) {
        final String action = feature.getSettingsAction();
        if (action == null) return null;

        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /** Máy có xử lý được intent này không (tránh crash khi thiếu trang Cài đặt). */
    public boolean canOpen(@Nullable Intent intent) {
        return intent != null && intent.resolveActivity(appContext.getPackageManager()) != null;
    }
}
