package com.dung.chargmagagement.model.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Collections;
import java.util.List;

/**
 * Một dòng trong màn Thông tin thiết bị.
 *
 * <p>Có ba kiểu dòng, tương ứng ba bố cục trong bản thiết kế:
 * <ul>
 *     <li>{@link Type#HEADER} – thẻ tiêu đề có logo và hai dòng chữ lớn
 *         (Device Name, Android 14, Processor Name).</li>
 *     <li>{@link Type#VALUE} – nhãn đậm ở trên, giá trị màu teal ở dưới.</li>
 *     <li>{@link Type#MULTI} – một nhãn kèm nhiều dòng giá trị, dùng cho mục
 *         "Running CPUs" liệt kê xung nhịp từng nhân.</li>
 * </ul>
 */
public final class DetailRow {

    public enum Type {
        HEADER,
        VALUE,
        MULTI
    }

    @NonNull
    private final Type type;

    /** Khoá phân biệt dòng, giữ nguyên giữa các lần cập nhật để DiffUtil so sánh. */
    @NonNull
    private final String key;

    @StringRes
    private final int labelRes;

    /**
     * Nhãn dạng chuỗi, dùng khi tên hàng là dữ liệu động không dịch được
     * (tên cảm biến do nhà sản xuất đặt). Rỗng nếu dùng {@link #labelRes}.
     */
    @NonNull
    private final String label;

    @NonNull
    private final String value;

    @DrawableRes
    private final int iconRes;

    /** Các dòng con của kiểu MULTI; rỗng với hai kiểu còn lại. */
    @NonNull
    private final List<String> lines;

    private DetailRow(@NonNull Type type,
                      @NonNull String key,
                      @StringRes int labelRes,
                      @NonNull String label,
                      @NonNull String value,
                      @DrawableRes int iconRes,
                      @NonNull List<String> lines) {
        this.type = type;
        this.key = key;
        this.labelRes = labelRes;
        this.label = label;
        this.value = value;
        this.iconRes = iconRes;
        this.lines = lines;
    }

    /** Thẻ tiêu đề: logo bên trái, chữ lớn bên phải. */
    public static DetailRow header(@NonNull String key,
                                   @StringRes int labelRes,
                                   @DrawableRes int iconRes,
                                   @NonNull String value) {
        return new DetailRow(Type.HEADER, key, labelRes, "", value, iconRes,
                Collections.emptyList());
    }

    /** Dòng thường: nhãn trên, giá trị dưới. */
    public static DetailRow value(@NonNull String key,
                                  @StringRes int labelRes,
                                  @Nullable String value) {
        return new DetailRow(Type.VALUE, key, labelRes, "", safe(value), 0,
                Collections.emptyList());
    }

    /** Dòng có nhãn động, dùng cho danh sách cảm biến. */
    public static DetailRow sensor(@NonNull String key,
                                   @Nullable String label,
                                   @Nullable String value) {
        return new DetailRow(Type.VALUE, key, 0, safe(label), safe(value), 0,
                Collections.emptyList());
    }

    /** Dòng nhiều giá trị: một nhãn, nhiều dòng con. */
    public static DetailRow multi(@NonNull String key,
                                  @StringRes int labelRes,
                                  @NonNull List<String> lines) {
        return new DetailRow(Type.MULTI, key, labelRes, "", "", 0, lines);
    }

    /** Chuỗi rỗng hoặc null đều hiển thị thành "unknown" như bản thiết kế. */
    private static String safe(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
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
    public String getValue() {
        return value;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    @NonNull
    public List<String> getLines() {
        return lines;
    }

    public boolean hasSameContent(@NonNull DetailRow other) {
        return type == other.type
                && labelRes == other.labelRes
                && label.equals(other.label)
                && iconRes == other.iconRes
                && value.equals(other.value)
                && lines.equals(other.lines);
    }
}
