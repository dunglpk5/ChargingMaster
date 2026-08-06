package com.dung.chargmagagement.model.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Một điểm đo pin, dùng để vẽ biểu đồ mức pin theo giờ ở tab "Sử dụng pin".
 *
 * <p>Ghi có tiết chế (xem {@code SessionRecorder}): chỉ lưu khi mức pin đổi 1%
 * hoặc sau một khoảng thời gian nhất định, thay vì lưu mọi lần lấy mẫu. Nếu lưu
 * hết thì mỗi ngày sinh ra hàng chục nghìn dòng mà biểu đồ không đẹp hơn chút nào.
 */
@Entity(tableName = "battery_sample",
        indices = {@Index("timestamp"), @Index("session_id"), @Index("day_key")})
public class BatterySampleEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    /**
     * Ngày theo lịch địa phương dạng {@code yyyyMMdd} (ví dụ 20260806).
     *
     * <p>Tính sẵn lúc ghi thay vì chia timestamp trong SQL: SQLite không biết múi
     * giờ của người dùng, chia thẳng {@code timestamp / 86400000} sẽ ra ngày theo
     * UTC và lệch mất 7 tiếng ở Việt Nam – biểu đồ sẽ hiển thị sai ngày.
     */
    @ColumnInfo(name = "day_key")
    public int dayKey;

    /** Id phiên sạc tương ứng, 0 nếu điểm đo lúc đang dùng pin. */
    @ColumnInfo(name = "session_id")
    public long sessionId;

    public int percent;

    @ColumnInfo(name = "current_ma")
    public int currentMa;

    public float temperature;

    public float voltage;

    public boolean charging;

    @ColumnInfo(name = "screen_on")
    public boolean screenOn;
}
