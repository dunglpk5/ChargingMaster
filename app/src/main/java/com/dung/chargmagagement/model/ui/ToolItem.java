package com.dung.chargmagagement.model.ui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * Một ô công cụ trong lưới 3 cột ở tab "Công cụ".
 */
public final class ToolItem {

    /**
     * Định danh công cụ. Dùng enum thay vì chuỗi để trình biên dịch bắt lỗi khi
     * thiếu nhánh xử lý lúc điều hướng.
     */
    public enum Action {
        // Nhóm VIP
        NO_ADS,
        CHARGE_ALARM,
        PRIORITY_SUPPORT,
        CHARGE_HISTORY,
        X_CHARGE,
        MORE,

        // Nhóm Phát hiện
        DEVICE_INFO,
        CLEAN_NOTIFICATION,
        CLEAN_CLIPBOARD,
        PHONE_TEMPERATURE,
        CHARGE_DETECT,
        CPU_USAGE,

        // Nhóm Công cụ
        MANAGE_APPS,
        SHORTCUT,
        CLEAR_CACHE
    }

    @NonNull
    private final Action action;

    @DrawableRes
    private final int iconRes;

    @StringRes
    private final int labelRes;

    @ColorRes
    private final int iconTintRes;

    public ToolItem(@NonNull Action action,
                    @DrawableRes int iconRes,
                    @StringRes int labelRes,
                    @ColorRes int iconTintRes) {
        this.action = action;
        this.iconRes = iconRes;
        this.labelRes = labelRes;
        this.iconTintRes = iconTintRes;
    }

    @NonNull
    public Action getAction() {
        return action;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @ColorRes
    public int getIconTintRes() {
        return iconTintRes;
    }
}
