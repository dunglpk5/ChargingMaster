package com.dung.chargmagagement.model.device;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Định dạng xung nhịp CPU theo cách người dùng quen đọc.
 *
 * <p>Đổi đơn vị ở mốc 1 GHz và cắt phần thập phân thừa: 2.0 GHz hiện là "2 GHz"
 * chứ không phải "2,0 GHz". Dấu thập phân theo ngôn ngữ máy nên bản tiếng Việt
 * hiện "2,2 GHz" đúng như bản thiết kế.
 */
public final class ClockFormat {

    private static final long KHZ_PER_GHZ = 1_000_000L;
    private static final long KHZ_PER_MHZ = 1_000L;

    private ClockFormat() {
    }

    /** Chuỗi xung nhịp từ kHz; chuỗi rỗng nếu không đọc được giá trị. */
    @NonNull
    public static String format(long khz) {
        return format(khz, Locale.getDefault());
    }

    @NonNull
    public static String format(long khz, @NonNull Locale locale) {
        if (khz <= 0) return "";

        if (khz >= KHZ_PER_GHZ) {
            final float ghz = (float) khz / KHZ_PER_GHZ;
            // Làm tròn tới 0,1 rồi bỏ ".0" cho gọn
            final float rounded = Math.round(ghz * 10f) / 10f;
            return rounded == Math.rint(rounded)
                    ? String.format(locale, "%.0f GHz", rounded)
                    : String.format(locale, "%.1f GHz", rounded);
        }
        return String.format(locale, "%d MHz", Math.round((float) khz / KHZ_PER_MHZ));
    }
}
