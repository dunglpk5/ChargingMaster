package com.dung.chargmagagement.model.battery;

import android.os.BatteryManager;

import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Tình trạng pin do hệ thống báo về.
 */
public enum BatteryHealth {

    GOOD(R.string.health_good),
    OVERHEAT(R.string.health_overheat),
    DEAD(R.string.health_dead),
    OVER_VOLTAGE(R.string.health_over_voltage),
    COLD(R.string.health_cold),
    UNKNOWN(R.string.health_unknown);

    @StringRes
    private final int labelRes;

    BatteryHealth(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    public static BatteryHealth fromSystemValue(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return GOOD;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return OVERHEAT;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return DEAD;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return OVER_VOLTAGE;
            case BatteryManager.BATTERY_HEALTH_COLD:
                return COLD;
            default:
                // UNSPECIFIED_FAILURE và UNKNOWN gộp chung: không đủ tin cậy để hiển thị riêng
                return UNKNOWN;
        }
    }
}
