package com.dung.chargmagagement.model.device;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Mức sử dụng pin ước tính của một ứng dụng.
 *
 * <p><b>Vì sao là ước tính:</b> Android không mở API cho phép ứng dụng thường đọc
 * lượng điện từng app đã tiêu thụ – con số đó chỉ có trong phần Cài đặt của hệ
 * thống. Thứ ta đọc được là <b>thời gian chạy ở tiền cảnh</b> qua
 * {@code UsageStatsManager}. App chạy tiền cảnh càng lâu thì màn hình sáng càng
 * lâu và CPU làm việc càng nhiều, nên tỉ lệ thời gian là mốc thay thế hợp lý.
 *
 * <p>Nó không tính được app ngốn pin ở nền (đồng bộ, định vị) – hạn chế này phải
 * nói rõ trên giao diện thay vì để người dùng tưởng đây là số đo thật.
 */
public final class AppUsageItem {

    private final String packageName;
    private final String label;

    @Nullable
    private final Drawable icon;

    private final long foregroundMs;
    private final float sharePercent;

    public AppUsageItem(@NonNull String packageName, @NonNull String label,
                        @Nullable Drawable icon, long foregroundMs, float sharePercent) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.foregroundMs = foregroundMs;
        this.sharePercent = sharePercent;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    @Nullable
    public Drawable getIcon() {
        return icon;
    }

    public long getForegroundMs() {
        return foregroundMs;
    }

    /** Tỉ lệ so với tổng thời gian tiền cảnh của mọi app (0..100). */
    public float getSharePercent() {
        return sharePercent;
    }
}
