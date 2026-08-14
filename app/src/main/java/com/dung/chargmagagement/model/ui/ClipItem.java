package com.dung.chargmagagement.model.ui;

import androidx.annotation.NonNull;

/**
 * Một mục trong bộ nhớ tạm, đã bóc thành chuỗi.
 *
 * <p>Giữ luôn vị trí trong {@code ClipData} gốc: Android không cho xoá lẻ một mục,
 * muốn bỏ đi một mục phải dựng lại cả bộ nhớ tạm từ những mục còn lại, và lúc đó
 * cần biết mục nào ở vị trí nào.
 */
public class ClipItem {

    public final int index;
    public final String label;
    public final String text;

    /** Thời điểm nội dung được đặt vào bộ nhớ tạm; 0 nếu máy không cung cấp. */
    public final long timestamp;

    public ClipItem(int index, @NonNull String label, @NonNull String text, long timestamp) {
        this.index = index;
        this.label = label;
        this.text = text;
        this.timestamp = timestamp;
    }
}
