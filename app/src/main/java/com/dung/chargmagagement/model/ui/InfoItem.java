package com.dung.chargmagagement.model.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Objects;

/**
 * Một dòng "nhãn – giá trị" có icon, dùng lại cho cả màn Trang chủ và
 * màn Thông tin thiết bị (Phase 6).
 *
 * <p>Bất biến để {@code DiffUtil} so sánh được chính xác: mỗi lần cập nhật ta tạo
 * danh sách mới, RecyclerView chỉ vẽ lại đúng dòng có giá trị thay đổi thay vì
 * toàn bộ 8 dòng – quan trọng vì màn hình này cập nhật 2 giây một lần.
 */
public final class InfoItem {

    /** Khoá phân biệt dòng, giữ nguyên giữa các lần cập nhật. */
    @NonNull
    private final String key;

    @DrawableRes
    private final int iconRes;

    @StringRes
    private final int labelRes;

    /**
     * Nhãn dạng chuỗi, dùng khi tên hàng là dữ liệu động không dịch được
     * (ví dụ tên cảm biến do nhà sản xuất đặt). Rỗng nếu dùng {@link #labelRes}.
     */
    @NonNull
    private final String label;

    @NonNull
    private final String value;

    /** Hàng có nhãn lấy từ string resource – dùng cho hầu hết trường hợp. */
    public InfoItem(@NonNull String key,
                    @DrawableRes int iconRes,
                    @StringRes int labelRes,
                    @NonNull String value) {
        this(key, iconRes, labelRes, "", value);
    }

    /** Hàng có nhãn động, không qua string resource. */
    public InfoItem(@NonNull String key,
                    @DrawableRes int iconRes,
                    @NonNull String label,
                    @NonNull String value) {
        this(key, iconRes, 0, label, value);
    }

    private InfoItem(@NonNull String key,
                     @DrawableRes int iconRes,
                     @StringRes int labelRes,
                     @NonNull String label,
                     @NonNull String value) {
        this.key = key;
        this.iconRes = iconRes;
        this.labelRes = labelRes;
        this.label = label;
        this.value = value;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    /** Có dùng string resource cho nhãn hay không. */
    public boolean hasLabelRes() {
        return labelRes != 0;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @NonNull
    public String getValue() {
        return value;
    }

    /** Hai dòng cùng khoá thì là cùng một dòng dữ liệu. */
    public boolean isSameItem(@NonNull InfoItem other) {
        return key.equals(other.key);
    }

    /** Nội dung có thay đổi không – quyết định việc vẽ lại. */
    public boolean hasSameContent(@NonNull InfoItem other) {
        return iconRes == other.iconRes
                && labelRes == other.labelRes
                && Objects.equals(label, other.label)
                && Objects.equals(value, other.value);
    }
}
