package com.dung.chargmagagement.model.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.FileUtils;

import java.util.Locale;

/**
 * Đọc thông tin CPU từ procfs và sysfs.
 *
 * <p>Android không có API công khai cho tên chip hay xung nhịp, mọi app xem cấu
 * hình máy đều phải đọc trực tiếp các node của nhân Linux. Tên khoá trong
 * {@code /proc/cpuinfo} khác nhau giữa các dòng chip (Qualcomm dùng
 * "Hardware", MediaTek thường có "model name") nên phải thử lần lượt.
 */
public final class CpuInfoReader {

    private static final String PROC_CPUINFO = "/proc/cpuinfo";
    private static final String CPU_DIR = "/sys/devices/system/cpu/cpu";

    /** Các khoá có thể chứa tên chip, xếp theo mức độ hay gặp. */
    private static final String[] CHIP_NAME_KEYS = {
            "Hardware", "model name", "Processor", "chip name"
    };

    private CpuInfoReader() {
    }

    /** Số nhân CPU. */
    public static int getCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /** Tên chip; null nếu không đọc được node nào. */
    @WorkerThread
    @Nullable
    public static String getChipName() {
        for (String key : CHIP_NAME_KEYS) {
            String value = FileUtils.findValue(PROC_CPUINFO, key);
            if (value != null && !value.isEmpty()) return value;
        }
        return null;
    }

    /** Kiến trúc tập lệnh, ví dụ "AArch64". */
    @WorkerThread
    @Nullable
    public static String getArchitecture() {
        return FileUtils.findValue(PROC_CPUINFO, "Architecture");
    }

    /** Bộ điều phối xung nhịp của nhân 0 (performance, schedutil…). */
    @WorkerThread
    @Nullable
    public static String getGovernor() {
        return FileUtils.readFirstLine(CPU_DIR + "0/cpufreq/scaling_governor");
    }

    /**
     * Xung nhịp thấp nhất trong tất cả các nhân (kHz).
     * Máy dùng kiến trúc big.LITTLE có nhiều cụm nhân với dải xung khác nhau,
     * nên phải quét hết chứ không chỉ đọc nhân 0.
     */
    @WorkerThread
    public static long getMinFrequencyKhz() {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < getCoreCount(); i++) {
            Long value = FileUtils.readLong(CPU_DIR + i + "/cpufreq/cpuinfo_min_freq");
            if (value != null && value > 0) min = Math.min(min, value);
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    /** Xung nhịp cao nhất trong tất cả các nhân (kHz). */
    @WorkerThread
    public static long getMaxFrequencyKhz() {
        long max = 0L;
        for (int i = 0; i < getCoreCount(); i++) {
            Long value = FileUtils.readLong(CPU_DIR + i + "/cpufreq/cpuinfo_max_freq");
            if (value != null) max = Math.max(max, value);
        }
        return max;
    }

    /** Xung nhịp hiện tại của một nhân (kHz); 0 nếu nhân đang ngủ. */
    @WorkerThread
    public static long getCurrentFrequencyKhz(int core) {
        Long value = FileUtils.readLong(CPU_DIR + core + "/cpufreq/scaling_cur_freq");
        return value == null ? 0L : value;
    }

    /** Đổi kHz sang chuỗi GHz dễ đọc, ví dụ "2.40 GHz". */
    @NonNull
    public static String formatFrequency(long khz) {
        if (khz <= 0) return "-";
        return String.format(Locale.US, "%.2f GHz", khz / 1_000_000f);
    }
}
