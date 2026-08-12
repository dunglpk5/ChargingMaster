package com.dung.chargmagagement.model.power;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Một bất thường phát hiện được trong lúc sạc.
 *
 * <p>Thứ tự khai báo cũng là <b>thứ tự ưu tiên hiển thị</b>: mục nào ảnh hưởng tới
 * an toàn hoặc tuổi thọ pin thì đứng trước mục chỉ ảnh hưởng tới tốc độ.
 */
public enum ChargeWarning {

    /** Pin nóng bất thường – vừa hại pin vừa làm bộ điều khiển tự giảm dòng nạp. */
    OVERHEAT(R.string.warn_overheat, R.color.state_danger),

    /** Cắm sạc mà pin vẫn tụt: nguồn cấp yếu hơn mức máy đang tiêu thụ. */
    DRAINING(R.string.warn_draining, R.color.state_danger),

    /** Dòng nạp thấp bất thường – thường do cáp kém hoặc củ sạc yếu. */
    LOW_CURRENT(R.string.warn_low_current, R.color.state_warning),

    /** Đang sạc qua cổng USB máy tính, vốn giới hạn dòng rất thấp. */
    USB_SOURCE(R.string.warn_usb_source, R.color.state_warning),

    /** Pin đã cao, sạc tiếp tới 100% làm pin lithium chai nhanh hơn. */
    NEARLY_FULL(R.string.warn_nearly_full, R.color.state_warning);

    @StringRes
    private final int messageRes;

    @ColorRes
    private final int colorRes;

    ChargeWarning(@StringRes int messageRes, @ColorRes int colorRes) {
        this.messageRes = messageRes;
        this.colorRes = colorRes;
    }

    @StringRes
    public int getMessageRes() {
        return messageRes;
    }

    @ColorRes
    public int getColorRes() {
        return colorRes;
    }
}
