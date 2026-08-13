package com.dung.chargmagagement.model.device;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Suy ra hãng sản xuất chip từ mã chip.
 *
 * <p>Android chỉ cho đọc tên hãng qua {@code Build.SOC_MANUFACTURER} từ API 31,
 * mà máy chạy Android 8–11 vẫn còn nhiều. Với các máy đó chỉ còn cách nhận dạng
 * theo tiền tố mã chip – mỗi hãng dùng một quy ước đặt tên riêng và ổn định.
 */
public final class CpuVendor {

    private CpuVendor() {
    }

    /** Tên hãng theo mã chip; null nếu không nhận ra quy ước nào. */
    @Nullable
    public static String fromChipName(@Nullable String chipName) {
        if (chipName == null) return null;

        final String name = chipName.trim().toUpperCase(Locale.US);
        if (name.isEmpty()) return null;

        // MediaTek: MT6789, MT8183…
        if (name.startsWith("MT") || name.contains("MEDIATEK") || name.contains("DIMENSITY")) {
            return "Mediatek";
        }
        // Qualcomm: SM8550, SDM845, MSM8998, QCOM…
        if (name.startsWith("SM") || name.startsWith("SDM") || name.startsWith("MSM")
                || name.startsWith("APQ") || name.startsWith("QCOM")
                || name.contains("QUALCOMM") || name.contains("SNAPDRAGON")) {
            return "Qualcomm";
        }
        if (name.contains("EXYNOS") || name.startsWith("UNIVERSAL") || name.startsWith("S5E")) {
            return "Samsung";
        }
        if (name.contains("KIRIN") || name.startsWith("HI")) {
            return "HiSilicon";
        }
        if (name.contains("TENSOR") || name.startsWith("GS")) {
            return "Google";
        }
        // Unisoc/Spreadtrum: SC9863A, T612, UMS512…
        if (name.contains("UNISOC") || name.contains("SPREADTRUM")
                || name.startsWith("SC") || name.startsWith("UMS")) {
            return "Unisoc";
        }
        return null;
    }
}
