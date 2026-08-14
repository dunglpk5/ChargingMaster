package com.dung.chargmagagement.model.clean;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kết quả quét của một nhóm: danh sách tệp, tổng dung lượng và lựa chọn xoá.
 *
 * <p>Có hai kiểu chọn, quyết định bởi {@link JunkCategory#isPerFileSelection()}:
 * nhóm thường dùng một ô tick cho cả nhóm, còn nhóm APK cũ và Tệp tin lớn thì
 * người dùng tự chọn từng tệp. Mọi phép tính dung lượng đều đi qua
 * {@link #getSelectedBytes()} nên phần còn lại của ứng dụng không cần biết nhóm
 * đang ở kiểu nào.
 */
public class JunkGroup {

    public final JunkCategory category;

    private final List<JunkFile> files = new ArrayList<>();
    private long totalBytes;
    private boolean selected;

    public JunkGroup(@NonNull JunkCategory category) {
        this.category = category;
        this.selected = category.isSafeByDefault();
    }

    public void add(@NonNull File file, long sizeBytes) {
        files.add(new JunkFile(file, sizeBytes));
        totalBytes += sizeBytes;
    }

    /** Xếp tệp lớn lên đầu để người dùng thấy ngay thứ đáng xoá nhất. */
    public void sortBySizeDesc() {
        Collections.sort(files, (a, b) -> Long.compare(b.sizeBytes, a.sizeBytes));
    }

    @NonNull
    public List<JunkFile> getFiles() {
        return files;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    // ==================== Lựa chọn ====================

    /** Ô tick của cả nhóm; nhóm chọn theo tệp thì không dùng tới. */
    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /** Có gì để xoá hay không, đúng cho cả hai kiểu chọn. */
    public boolean hasSelection() {
        if (isEmpty()) return false;
        if (!category.isPerFileSelection()) return selected;

        for (JunkFile file : files) {
            if (file.isSelected()) return true;
        }
        return false;
    }

    /** Số tệp đang được chọn. */
    public int getSelectedCount() {
        if (!category.isPerFileSelection()) return selected ? files.size() : 0;

        int count = 0;
        for (JunkFile file : files) {
            if (file.isSelected()) count++;
        }
        return count;
    }

    /** Dung lượng sẽ giải phóng nếu dọn ngay bây giờ. */
    public long getSelectedBytes() {
        if (!category.isPerFileSelection()) return selected ? totalBytes : 0L;

        long sum = 0L;
        for (JunkFile file : files) {
            if (file.isSelected()) sum += file.sizeBytes;
        }
        return sum;
    }

    /** Các tệp sẽ bị xoá. */
    @NonNull
    public List<File> getSelectedFiles() {
        List<File> result = new ArrayList<>();
        final boolean perFile = category.isPerFileSelection();

        for (JunkFile file : files) {
            if (perFile ? file.isSelected() : selected) {
                result.add(file.file);
            }
        }
        return result;
    }
}
