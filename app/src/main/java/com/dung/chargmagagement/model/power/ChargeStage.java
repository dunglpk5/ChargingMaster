package com.dung.chargmagagement.model.power;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Ba giai đoạn của một chu trình sạc pin lithium.
 *
 * <p>Bộ điều khiển sạc không nạp đều một dòng từ đầu tới cuối:
 * <ul>
 *     <li><b>Nhanh</b> – dòng không đổi ở mức cao, kéo dài tới khoảng 80%. Đây là
 *         lúc pin nhận điện nhanh nhất.</li>
 *     <li><b>Chu kỳ</b> – điện áp không đổi, dòng giảm dần. Pin đầy chậm lại rõ rệt,
 *         đó là hành vi bình thường chứ không phải bộ sạc yếu đi.</li>
 *     <li><b>Nhỏ giọt</b> – bù phần tự phóng điện khi pin đã đầy.</li>
 * </ul>
 *
 * <p>Ranh giới các giai đoạn khác nhau đôi chút giữa các hãng; hai mốc dưới đây là
 * mức phổ biến. Logic thuần nên kiểm thử được không cần thiết bị.
 */
public enum ChargeStage {

    FAST(R.string.stage_fast, R.drawable.ic_voltage),
    CYCLE(R.string.stage_cycle, R.drawable.ic_current),
    TRICKLE(R.string.stage_trickle, R.drawable.ic_droplet);

    /** Hết giai đoạn dòng không đổi, chuyển sang điện áp không đổi. */
    public static final int FAST_END_PERCENT = 80;

    /** Pin coi như đã đầy, chỉ còn bù tự phóng. */
    public static final int TRICKLE_START_PERCENT = 100;

    @StringRes
    private final int labelRes;

    @DrawableRes
    private final int iconRes;

    ChargeStage(@StringRes int labelRes, @DrawableRes int iconRes) {
        this.labelRes = labelRes;
        this.iconRes = iconRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    /** Giai đoạn ứng với mức pin hiện tại. */
    @NonNull
    public static ChargeStage fromPercent(int percent) {
        if (percent >= TRICKLE_START_PERCENT) return TRICKLE;
        if (percent >= FAST_END_PERCENT) return CYCLE;
        return FAST;
    }
}
