package com.dung.chargmagagement.model.device;

import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Sáu tab của màn Thông tin thiết bị.
 *
 * <p>Dùng enum thay vì 6 lớp Fragment riêng: cả sáu tab đều là danh sách
 * "nhãn – giá trị" nên chỉ cần một Fragment chung nhận vào một giá trị enum,
 * tránh sáu lớp gần như giống hệt nhau.
 */
public enum DetailSection {

    DEVICE(R.string.detail_tab_device),
    SYSTEM(R.string.detail_tab_system),
    CPU(R.string.detail_tab_cpu),
    DISPLAY(R.string.detail_tab_display),
    NETWORK(R.string.detail_tab_network),
    SENSOR(R.string.detail_tab_sensor);

    @StringRes
    private final int titleRes;

    DetailSection(@StringRes int titleRes) {
        this.titleRes = titleRes;
    }

    @StringRes
    public int getTitleRes() {
        return titleRes;
    }

    /** Lấy section theo vị trí tab, có chặn chỉ số ngoài phạm vi. */
    public static DetailSection fromPosition(int position) {
        DetailSection[] values = values();
        if (position < 0 || position >= values.length) return DEVICE;
        return values[position];
    }
}
