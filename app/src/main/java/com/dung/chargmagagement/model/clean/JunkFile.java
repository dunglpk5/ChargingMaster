package com.dung.chargmagagement.model.clean;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Một tệp trong kết quả quét, kèm trạng thái được chọn xoá hay không.
 *
 * <p>Kích thước lưu lại ngay lúc quét thay vì hỏi lại {@link File#length()} mỗi
 * lần cần: hỏi lại là một lần chạm đĩa, mà danh sách có thể tới hàng nghìn tệp.
 */
public class JunkFile {

    public final File file;
    public final long sizeBytes;

    private boolean selected;

    public JunkFile(@NonNull File file, long sizeBytes) {
        this.file = file;
        this.sizeBytes = sizeBytes;
    }

    @NonNull
    public String getName() {
        return file.getName();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
