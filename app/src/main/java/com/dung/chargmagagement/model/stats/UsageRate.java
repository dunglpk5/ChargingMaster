package com.dung.chargmagagement.model.stats;

/**
 * Tốc độ tiêu hao pin của một nhóm khoảng thời gian (màn bật / màn tắt / kết hợp).
 *
 * <p>Tương ứng một ô trong mục "Sử dụng pin trung bình": dòng lớn là {@code %/h},
 * dòng nhỏ là "x % in y h".
 */
public final class UsageRate {

    /** Giá trị rỗng khi chưa có dữ liệu (hiển thị "0,0 %/h"). */
    public static final UsageRate EMPTY = new UsageRate(0, 0f);

    private final int totalPercentDrop;
    private final float totalHours;

    public UsageRate(int totalPercentDrop, float totalHours) {
        this.totalPercentDrop = totalPercentDrop;
        this.totalHours = totalHours;
    }

    public int getTotalPercentDrop() {
        return totalPercentDrop;
    }

    public float getTotalHours() {
        return totalHours;
    }

    /** Phần trăm pin tiêu hao mỗi giờ; 0 nếu chưa đủ dữ liệu. */
    public float getPercentPerHour() {
        if (totalHours <= 0f) return 0f;
        return totalPercentDrop / totalHours;
    }

    public boolean hasData() {
        return totalHours > 0f && totalPercentDrop > 0;
    }

    /**
     * Ước tính số giờ dùng được nếu pin đầy 100%.
     * Đây là con số ở mục "Ước tính thời gian pin đầy".
     */
    public float getEstimatedFullBatteryHours() {
        final float rate = getPercentPerHour();
        return rate <= 0f ? 0f : 100f / rate;
    }
}
