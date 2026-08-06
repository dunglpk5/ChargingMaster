package com.dung.chargmagagement.model.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.dung.chargmagagement.model.battery.BatteryInfo;

/**
 * Một phiên sạc: tính từ lúc cắm nguồn tới lúc rút.
 *
 * <p>Phiên đang diễn ra có {@code endTime == 0}; khi rút sạc bản ghi được cập nhật
 * chứ không tạo mới, nhờ vậy không mất dữ liệu nếu app bị hệ thống kill giữa chừng.
 */
@Entity(tableName = "charging_session",
        indices = {@Index("start_time")})
public class ChargingSessionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "start_time")
    public long startTime;

    /** 0 nghĩa là phiên chưa kết thúc. */
    @ColumnInfo(name = "end_time")
    public long endTime;

    @ColumnInfo(name = "start_percent")
    public int startPercent;

    @ColumnInfo(name = "end_percent")
    public int endPercent;

    /** Tên của {@code PlugType}: AC / USB / WIRELESS. */
    @ColumnInfo(name = "plug_type")
    @NonNull
    public String plugType = "NONE";

    @ColumnInfo(name = "avg_current_ma")
    public int avgCurrentMa = BatteryInfo.UNKNOWN_INT;

    @ColumnInfo(name = "max_current_ma")
    public int maxCurrentMa = BatteryInfo.UNKNOWN_INT;

    @ColumnInfo(name = "max_temperature")
    public float maxTemperature;

    /** Điện tích đã nạp ước tính (mAh) = dòng trung bình × thời lượng. */
    @ColumnInfo(name = "charged_mah")
    public float chargedMah;

    @ColumnInfo(name = "sample_count")
    public int sampleCount;

    /** Phiên đã kết thúc chưa. */
    public boolean isFinished() {
        return endTime > 0;
    }

    /** Thời lượng phiên (ms). */
    public long getDurationMs() {
        final long end = isFinished() ? endTime : System.currentTimeMillis();
        return Math.max(0, end - startTime);
    }

    /** Số phần trăm pin đã nạp được. */
    public int getGainedPercent() {
        return Math.max(0, endPercent - startPercent);
    }

    /**
     * Dung lượng pin thực tế suy ra từ phiên này (mAh).
     *
     * <p>Công thức: {@code chargedMah / %đã nạp × 100}. Chỉ đáng tin khi phiên đủ
     * dài; phiên ngắn có sai số lớn nên bị loại ở tầng thống kê.
     *
     * @return dung lượng ước tính hoặc {@link BatteryInfo#UNKNOWN_INT}
     */
    public int getEstimatedCapacityMah() {
        final int gained = getGainedPercent();
        if (gained <= 0 || chargedMah <= 0) return BatteryInfo.UNKNOWN_INT;
        return Math.round(chargedMah / gained * 100f);
    }
}
