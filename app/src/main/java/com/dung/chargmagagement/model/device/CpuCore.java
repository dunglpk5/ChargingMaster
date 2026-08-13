package com.dung.chargmagagement.model.device;

import androidx.annotation.Nullable;

/**
 * Trạng thái của một nhân CPU: thông số cố định và số đo tức thời.
 *
 * <p>Đối tượng này thay đổi tại chỗ theo từng lần lấy mẫu thay vì tạo mới, vì
 * mỗi giây rưỡi lại dựng lại cả danh sách nhân là rác cấp phát vô ích.
 */
public class CpuCore {

    /** Chưa đo được lần nào; khác hẳn 0% là "nhân đang rảnh". */
    public static final int LOAD_UNKNOWN = -1;

    public final int index;

    /** Tên thiết kế nhân, ví dụ "Cortex-A55"; null nếu không tra được. */
    @Nullable
    public String name;

    public long minKhz;
    public long maxKhz;
    public long currentKhz;
    public int loadPercent = LOAD_UNKNOWN;

    public CpuCore(int index) {
        this.index = index;
    }

    /** Nhãn hiển thị đếm từ 1, khớp cách đánh số trong bản thiết kế. */
    public int getDisplayNumber() {
        return index + 1;
    }
}
