package com.dung.chargmagagement.model.battery;

import android.os.BatteryManager;

import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Loại nguồn đang cắm, ánh xạ từ {@link BatteryManager#EXTRA_PLUGGED}.
 */
public enum PlugType {

    NONE(R.string.plug_none),
    AC(R.string.plug_ac),
    USB(R.string.plug_usb),
    WIRELESS(R.string.plug_wireless);

    @StringRes
    private final int labelRes;

    PlugType(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    /** Có đang cắm nguồn hay không. */
    public boolean isPlugged() {
        return this != NONE;
    }

    /** Chuyển giá trị cờ bit của hệ thống thành enum. */
    public static PlugType fromSystemFlag(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) return AC;
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) return USB;
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return WIRELESS;
        return NONE;
    }
}
