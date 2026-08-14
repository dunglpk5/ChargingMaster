package com.dung.chargmagagement.model.clean;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.dung.chargmagagement.R;

/**
 * Các nhóm tệp thừa mà màn Dọn dẹp liệt kê.
 *
 * <p>Thứ tự khai báo cũng là thứ tự hiển thị và thứ tự phân loại: một tệp chỉ
 * thuộc đúng một nhóm, nhóm nào đứng trước thì nhận trước. Nhờ vậy tổng dung
 * lượng của sáu nhóm cộng lại không đếm trùng tệp nào.
 *
 * <p>{@code safeByDefault} quyết định nhóm có được tick sẵn hay không. Chỉ những
 * nhóm mà hệ thống tự sinh ra và tự dựng lại được mới an toàn để xoá mà không
 * hỏi; tệp người dùng tải về hay tệp lớn thì phải do chính họ chọn.
 */
public enum JunkCategory {

    /** Bộ đệm rác nằm rải rác trong bộ nhớ: .tmp, .log, thư mục .thumbnails… */
    JUNK_CACHE(R.string.clean_junk_cache, R.drawable.ic_junk_cache, true),

    /** Bộ nhớ đệm của ứng dụng. */
    APP_CACHE(R.string.clean_app_cache, R.drawable.ic_app_cache, true),

    /** Tệp cài đặt .apk còn sót lại sau khi cài xong. */
    OBSOLETE_APK(R.string.clean_obsolete_apk, R.drawable.ic_apk_file, false, true),

    /** Bộ đệm quảng cáo do các SDK quảng cáo để lại. */
    AD_CACHE(R.string.clean_ad_cache, R.drawable.ic_ad_cache, true),

    /** Thư mục Tải xuống. */
    DOWNLOADS(R.string.clean_downloads, R.drawable.ic_download_file, false),

    /** Tệp đơn lẻ vượt ngưỡng lớn, thường là video và bản sao lưu. */
    LARGE_FILES(R.string.clean_large_files, R.drawable.ic_large_file, false, true),

    /** Tệp lâu không được mở hay sửa đổi. */
    STALE_FILES(R.string.clean_stale_files, R.drawable.ic_stale_file, false);

    @StringRes
    private final int labelRes;

    @DrawableRes
    private final int iconRes;

    private final boolean safeByDefault;
    private final boolean perFileSelection;

    JunkCategory(@StringRes int labelRes, @DrawableRes int iconRes, boolean safeByDefault) {
        this(labelRes, iconRes, safeByDefault, false);
    }

    JunkCategory(@StringRes int labelRes, @DrawableRes int iconRes,
                 boolean safeByDefault, boolean perFileSelection) {
        this.labelRes = labelRes;
        this.iconRes = iconRes;
        this.safeByDefault = safeByDefault;
        this.perFileSelection = perFileSelection;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    /** Nhóm có được tick sẵn khi quét xong hay không. */
    public boolean isSafeByDefault() {
        return safeByDefault;
    }

    /**
     * Nhóm này bắt người dùng chọn từng tệp thay vì tick cả nhóm.
     *
     * <p>Dành cho những nhóm mà mỗi tệp là một quyết định riêng: một bản .apk có
     * thể là rác, bản kế bên lại là bản cài đặt người dùng cố tình giữ. Gộp chúng
     * vào một ô tick là buộc người dùng chọn "xoá tất" hoặc "không xoá gì".
     */
    public boolean isPerFileSelection() {
        return perFileSelection;
    }
}
