package com.dung.chargmagagement.model.clean;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Kết quả quét của một nhóm: danh sách tệp, tổng dung lượng và trạng thái chọn. */
public class JunkGroup {

    public final JunkCategory category;

    private final List<File> files = new ArrayList<>();
    private long totalBytes;
    private boolean selected;

    public JunkGroup(@NonNull JunkCategory category) {
        this.category = category;
        this.selected = category.isSafeByDefault();
    }

    public void add(@NonNull File file, long sizeBytes) {
        files.add(file);
        totalBytes += sizeBytes;
    }

    public void clear() {
        files.clear();
        totalBytes = 0L;
        selected = category.isSafeByDefault();
    }

    @NonNull
    public List<File> getFiles() {
        return files;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
