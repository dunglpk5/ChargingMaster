package com.dung.chargmagagement.model.battery;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Gom toàn bộ thông tin pin thành một {@link BatteryInfo}.
 *
 * <p>Nguồn dữ liệu chính là sticky broadcast {@code ACTION_BATTERY_CHANGED}: gọi
 * {@code registerReceiver(null, filter)} lấy được ngay giá trị mới nhất mà không
 * cần đăng ký receiver thường trú, nên rất nhẹ về pin.
 */
public final class BatteryInfoProvider {

    private final Context appContext;
    private final CurrentReader currentReader;
    private final BatteryCapacityProvider capacityProvider;

    public BatteryInfoProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.currentReader = new CurrentReader(appContext);
        this.capacityProvider = new BatteryCapacityProvider(appContext);
    }

    /** Đọc trạng thái pin hiện tại. Gọi trên thread nền (có đọc file sysfs). */
    @WorkerThread
    @NonNull
    public BatteryInfo read() {
        return parse(getStickyBatteryIntent());
    }

    /**
     * Dựng {@link BatteryInfo} từ intent BATTERY_CHANGED.
     * Tách riêng để {@code ChargingDetector} tái sử dụng được intent nó vừa nhận.
     */
    @WorkerThread
    @NonNull
    public BatteryInfo parse(@Nullable Intent intent) {
        BatteryInfo.Builder builder = BatteryInfo.builder()
                .designCapacityMah(capacityProvider.getDesignCapacityMah());

        if (intent == null) {
            return builder.build();
        }

        final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        final int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

        final boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING;

        builder.percent(level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : 0)
                .plugType(PlugType.fromSystemFlag(plugged))
                .charging(charging)
                .full(status == BatteryManager.BATTERY_STATUS_FULL)
                .health(BatteryHealth.fromSystemValue(
                        intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)))
                // Hệ thống trả nhiệt độ theo 1/10 độ C, điện áp theo mV
                .temperatureCelsius(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f)
                .voltage(normalizeVoltage(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)))
                .technology(intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY))
                .currentMa(currentReader.readMilliAmp(charging));

        return builder.build();
    }

    /**
     * Vài ROM trả điện áp bằng V thay vì mV (3.85 thay vì 3850).
     * Giá trị pin lithium luôn nằm trong khoảng 2.5–5.0 V nên phân biệt được bằng độ lớn.
     */
    private static float normalizeVoltage(int rawMilliVolt) {
        if (rawMilliVolt <= 0) return 0f;
        return rawMilliVolt > 100 ? rawMilliVolt / 1000f : rawMilliVolt;
    }

    /** Lấy sticky intent chứa trạng thái pin mới nhất (không đăng ký receiver). */
    @Nullable
    private Intent getStickyBatteryIntent() {
        return appContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public BatteryCapacityProvider getCapacityProvider() {
        return capacityProvider;
    }
}
