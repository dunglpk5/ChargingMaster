package com.dung.chargmagagement.model.device;

import androidx.annotation.Nullable;

/**
 * Đổi mã "CPU part" trong {@code /proc/cpuinfo} thành tên nhân dễ đọc.
 *
 * <p>Mã này do ARM cấp và cố định theo thiết kế nhân, ví dụ {@code 0xd05} luôn là
 * Cortex-A55 trên mọi con chip. Nhờ vậy ta biết được máy dùng cụm nhân nào mà
 * không cần bảng tra riêng cho từng đời chip.
 *
 * <p>Các hãng tự thiết kế nhân (Qualcomm Kryo, Apple…) dùng mã riêng không công
 * bố, nên tra không ra sẽ trả {@code null} – phía gọi bỏ trống thay vì đoán bừa.
 */
public final class CpuPartName {

    private CpuPartName() {
    }

    /** Tên nhân theo mã part; null nếu không nằm trong danh mục ARM. */
    @Nullable
    public static String fromPart(int part) {
        switch (part) {
            // Nhân ARMv7 còn gặp trên máy cũ
            case 0xc07: return "Cortex-A7";
            case 0xc09: return "Cortex-A9";
            case 0xc0d: return "Cortex-A12";
            case 0xc0e: return "Cortex-A17";
            case 0xc0f: return "Cortex-A15";

            // Nhân tiết kiệm điện
            case 0xd01: return "Cortex-A32";
            case 0xd03: return "Cortex-A53";
            case 0xd04: return "Cortex-A35";
            case 0xd05: return "Cortex-A55";
            case 0xd46: return "Cortex-A510";
            case 0xd80: return "Cortex-A520";

            // Nhân hiệu năng
            case 0xd07: return "Cortex-A57";
            case 0xd08: return "Cortex-A72";
            case 0xd09: return "Cortex-A73";
            case 0xd0a: return "Cortex-A75";
            case 0xd0b: return "Cortex-A76";
            case 0xd0d: return "Cortex-A77";
            case 0xd41: return "Cortex-A78";
            case 0xd47: return "Cortex-A710";
            case 0xd4d: return "Cortex-A715";
            case 0xd81: return "Cortex-A720";
            case 0xd87: return "Cortex-A725";

            // Nhân đầu bảng
            case 0xd44: return "Cortex-X1";
            case 0xd48: return "Cortex-X2";
            case 0xd4e: return "Cortex-X3";
            case 0xd82: return "Cortex-X4";
            case 0xd85: return "Cortex-X925";

            default: return null;
        }
    }

    /**
     * Đọc mã part từ một dòng {@code /proc/cpuinfo}, ví dụ "0xd05".
     *
     * @return mã part, hoặc -1 nếu chuỗi không hợp lệ
     */
    public static int parsePart(@Nullable String raw) {
        if (raw == null) return -1;

        String value = raw.trim();
        if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }

        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
