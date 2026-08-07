package com.dung.chargmagagement.model.battery;

/**
 * Hiệu chỉnh giá trị dòng điện thô đọc từ hệ thống.
 *
 * <p><b>Vì sao cần lớp này:</b> Android không chuẩn hoá được cách các hãng báo cáo
 * {@code BATTERY_PROPERTY_CURRENT_NOW}:
 * <ul>
 *     <li><b>Đơn vị</b>: đa số trả về µA, một số máy (Xiaomi, một vài dòng Samsung)
 *         trả về thẳng mA → cùng dòng 2A có máy báo 2000000, máy báo 2000.</li>
 *     <li><b>Dấu</b>: chuẩn AOSP quy ước nạp vào là số dương, nhưng nhiều máy làm
 *         ngược lại → đang sạc mà báo -1500.</li>
 * </ul>
 * Lớp này là logic thuần (không phụ thuộc Android) nên kiểm thử được bằng unit test.
 * Kết quả hiệu chỉnh được lưu vào SharedPreferences để chỉ phải học một lần.
 */
public final class CurrentCalibration {

    /** Chưa xác định được hệ số quy đổi. */
    public static final int DIVIDER_UNKNOWN = 0;
    /** Giá trị thô là µA → chia 1000 ra mA. */
    public static final int DIVIDER_MICRO = 1000;
    /** Giá trị thô đã là mA. */
    public static final int DIVIDER_MILLI = 1;

    public static final int SIGN_UNKNOWN = 0;
    /** Dấu của hệ thống trùng quy ước AOSP (nạp = dương). */
    public static final int SIGN_NORMAL = 1;
    /** Hệ thống báo ngược (nạp = âm) → cần đảo dấu. */
    public static final int SIGN_INVERTED = -1;

    /**
     * Ngưỡng phân biệt đơn vị. Dòng sạc/xả thực tế của điện thoại nằm trong
     * khoảng 1..15000 mA; nếu giá trị tuyệt đối vượt ngưỡng này thì gần như chắc
     * chắn đơn vị là µA.
     */
    private static final int MICRO_THRESHOLD = 15_000;

    /** Dòng lớn nhất coi là hợp lệ sau khi quy đổi (mA) – lọc nhiễu sysfs. */
    private static final int MAX_PLAUSIBLE_MA = 15_000;

    private CurrentCalibration() {
    }

    /**
     * Suy ra hệ số quy đổi đơn vị từ một giá trị thô.
     *
     * @return {@link #DIVIDER_MICRO}, {@link #DIVIDER_MILLI} hoặc
     *         {@link #DIVIDER_UNKNOWN} nếu giá trị quá nhỏ để kết luận
     */
    public static int detectDivider(int rawValue) {
        final int abs = Math.abs(rawValue);
        if (abs > MICRO_THRESHOLD) return DIVIDER_MICRO;
        // Giá trị nhỏ có thể là mA thật, cũng có thể là µA lúc dòng gần 0.
        // Chỉ kết luận là mA khi đủ lớn để không nhầm với nhiễu quanh 0.
        if (abs >= 20) return DIVIDER_MILLI;
        return DIVIDER_UNKNOWN;
    }

    /**
     * Suy ra quy ước dấu dựa trên trạng thái sạc đã biết chắc từ hệ thống.
     *
     * @param rawValue giá trị thô
     * @param charging trạng thái sạc lấy từ ACTION_BATTERY_CHANGED (nguồn tin cậy)
     * @return {@link #SIGN_NORMAL}, {@link #SIGN_INVERTED} hoặc
     *         {@link #SIGN_UNKNOWN} nếu giá trị quanh 0 nên không kết luận được
     */
    public static int detectSign(int rawValue, boolean charging) {
        // Quanh 0 (vừa cắm sạc, pin đầy, đang trickle charge) không đủ tin cậy
        if (Math.abs(rawValue) < 20) return SIGN_UNKNOWN;

        final boolean rawPositive = rawValue > 0;
        if (charging) {
            return rawPositive ? SIGN_NORMAL : SIGN_INVERTED;
        }
        // Đang xả: quy ước chuẩn là âm
        return rawPositive ? SIGN_INVERTED : SIGN_NORMAL;
    }

    /**
     * Quy đổi giá trị thô về mA theo hệ số đã học.
     *
     * @param rawValue giá trị thô
     * @param divider  {@link #DIVIDER_MICRO} hoặc {@link #DIVIDER_MILLI}
     * @param sign     {@link #SIGN_NORMAL} hoặc {@link #SIGN_INVERTED}
     * @return dòng điện mA (dương = nạp), hoặc {@link BatteryInfo#UNKNOWN_INT}
     *         nếu chưa hiệu chỉnh xong hoặc giá trị không hợp lý
     */
    public static int normalize(int rawValue, int divider, int sign) {
        if (divider == DIVIDER_UNKNOWN || sign == SIGN_UNKNOWN) {
            return BatteryInfo.UNKNOWN_INT;
        }
        final int milliAmp = Math.round((float) rawValue / divider) * sign;
        if (Math.abs(milliAmp) > MAX_PLAUSIBLE_MA) {
            // Giá trị vô lý: thường do đọc nhầm node sysfs của thiết bị khác
            return BatteryInfo.UNKNOWN_INT;
        }
        return milliAmp;
    }

    /**
     * Quy đổi về mA, lấy <b>chiều</b> từ trạng thái sạc do hệ thống báo thay vì từ
     * dấu của giá trị thô.
     *
     * <p><b>Vì sao cần cách này:</b> việc học dấu ở {@link #detectSign} giả định máy
     * dùng dấu nhất quán cho cả hai chiều. Không ít máy trả về <b>trị tuyệt đối</b>
     * – luôn dương dù đang sạc hay đang xả. Với những máy đó, nếu lần học rơi vào
     * lúc đang xả thì hệ thống kết luận nhầm là "báo ngược", và từ đó về sau mọi
     * lần sạc đều ra số âm, giao diện chỉ còn hiện dấu gạch.
     *
     * <p>Trạng thái sạc lấy từ {@code ACTION_BATTERY_CHANGED} thì luôn đúng, nên
     * dùng nó quyết định chiều và chỉ lấy độ lớn từ giá trị thô là chắc chắn hơn
     * hẳn. Cách này cũng tự đúng với máy đang cắm sạc mà vẫn tụt pin: hệ thống báo
     * trạng thái xả, ta trả về số âm.
     *
     * @param rawValue giá trị thô đọc từ hệ thống
     * @param divider  hệ số đơn vị đã học
     * @param charging hệ thống đang báo nạp vào hay không
     * @return dòng điện mA (dương = nạp), hoặc {@link BatteryInfo#UNKNOWN_INT}
     */
    public static int normalizeWithKnownState(int rawValue, int divider, boolean charging) {
        if (divider == DIVIDER_UNKNOWN) return BatteryInfo.UNKNOWN_INT;

        final int magnitude = Math.abs(Math.round((float) rawValue / divider));
        if (magnitude > MAX_PLAUSIBLE_MA) return BatteryInfo.UNKNOWN_INT;

        return charging ? magnitude : -magnitude;
    }
}
