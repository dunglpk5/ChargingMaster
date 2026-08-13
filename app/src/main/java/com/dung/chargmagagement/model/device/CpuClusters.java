package com.dung.chargmagagement.model.device;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Mô tả kiến trúc CPU dạng "6x Cortex-A55 2 GHz 2x Cortex-A76 2,2 GHz".
 *
 * <p>Gộp các nhân liền nhau có cùng tên và cùng xung tối đa thành một cụm. Chỉ
 * gộp nhân <b>liền kề</b> vì nhân trong một cụm luôn được nhân Linux đánh số
 * liên tiếp; gộp toàn cục sẽ trộn hai cụm khác nhau tình cờ trùng thông số.
 */
public final class CpuClusters {

    private CpuClusters() {
    }

    @NonNull
    public static String describe(@NonNull List<CpuCore> cores, @NonNull Locale locale) {
        StringBuilder result = new StringBuilder();

        int index = 0;
        while (index < cores.size()) {
            final CpuCore head = cores.get(index);

            int count = 1;
            while (index + count < cores.size() && sameCluster(head, cores.get(index + count))) {
                count++;
            }

            appendCluster(result, head, count, locale);
            index += count;
        }
        return result.toString();
    }

    @NonNull
    public static String describe(@NonNull List<CpuCore> cores) {
        return describe(cores, Locale.getDefault());
    }

    private static boolean sameCluster(@NonNull CpuCore a, @NonNull CpuCore b) {
        if (a.maxKhz != b.maxKhz) return false;
        return a.name == null ? b.name == null : a.name.equals(b.name);
    }

    private static void appendCluster(@NonNull StringBuilder result, @NonNull CpuCore head,
                                      int count, @NonNull Locale locale) {
        final String clock = ClockFormat.format(head.maxKhz, locale);

        // Không có cả tên lẫn xung nhịp thì cụm này không nói lên điều gì, bỏ qua
        if (head.name == null && clock.isEmpty()) return;

        if (result.length() > 0) result.append(' ');
        result.append(count).append('x');

        if (head.name != null) result.append(' ').append(head.name);
        if (!clock.isEmpty()) result.append(' ').append(clock);
    }
}
