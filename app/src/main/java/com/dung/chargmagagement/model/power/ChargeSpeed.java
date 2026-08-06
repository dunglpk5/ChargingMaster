package com.dung.chargmagagement.model.power;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.model.battery.BatteryInfo;

/**
 * Phân loại tốc độ sạc theo dòng nạp.
 *
 * <p>Ngưỡng dựa trên thực tế phổ biến của điện thoại Android:
 * <ul>
 *     <li>Dưới 500 mA: cắm vào cổng USB máy tính hoặc củ sạc yếu.</li>
 *     <li>500–1500 mA: củ sạc thường 5V/1A–5V/2A.</li>
 *     <li>1500–3000 mA: sạc nhanh phổ thông.</li>
 *     <li>Trên 3000 mA: sạc siêu nhanh.</li>
 * </ul>
 * Logic thuần, không phụ thuộc Android nên kiểm thử được.
 */
public enum ChargeSpeed {

    UNKNOWN(R.string.speed_unknown, R.color.text_secondary),
    SLOW(R.string.speed_slow, R.color.state_danger),
    NORMAL(R.string.speed_normal, R.color.state_warning),
    FAST(R.string.speed_fast, R.color.state_good),
    VERY_FAST(R.string.speed_very_fast, R.color.state_good);

    private static final int THRESHOLD_SLOW_MA = 500;
    private static final int THRESHOLD_NORMAL_MA = 1500;
    private static final int THRESHOLD_FAST_MA = 3000;

    @StringRes
    private final int labelRes;

    @ColorRes
    private final int colorRes;

    ChargeSpeed(@StringRes int labelRes, @ColorRes int colorRes) {
        this.labelRes = labelRes;
        this.colorRes = colorRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @ColorRes
    public int getColorRes() {
        return colorRes;
    }

    /**
     * Xếp loại theo dòng nạp.
     *
     * @param currentMa dòng điện đã hiệu chỉnh (mA); âm hoặc không đo được đều
     *                  trả về {@link #UNKNOWN} vì lúc đó máy không nạp điện
     */
    public static ChargeSpeed fromCurrent(int currentMa) {
        if (currentMa == BatteryInfo.UNKNOWN_INT || currentMa <= 0) return UNKNOWN;
        if (currentMa < THRESHOLD_SLOW_MA) return SLOW;
        if (currentMa < THRESHOLD_NORMAL_MA) return NORMAL;
        if (currentMa < THRESHOLD_FAST_MA) return FAST;
        return VERY_FAST;
    }
}
