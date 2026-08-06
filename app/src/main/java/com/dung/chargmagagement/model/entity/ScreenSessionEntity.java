package com.dung.chargmagagement.model.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Một khoảng thời gian màn hình bật hoặc tắt, kèm mức pin đầu/cuối.
 *
 * <p>Đây là dữ liệu để tính hai con số "Màn hình bật %/h" và "Màn hình tắt %/h"
 * trong bản thiết kế. Chỉ những khoảng <b>đang dùng pin</b> mới được tính; khoảng
 * đang cắm sạc bị bỏ qua vì mức pin tăng chứ không giảm.
 */
@Entity(tableName = "screen_session",
        indices = {@Index("start_time")})
public class ScreenSessionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "start_time")
    public long startTime;

    @ColumnInfo(name = "end_time")
    public long endTime;

    @ColumnInfo(name = "start_percent")
    public int startPercent;

    @ColumnInfo(name = "end_percent")
    public int endPercent;

    /** true = khoảng màn hình bật, false = màn hình tắt. */
    @ColumnInfo(name = "screen_on")
    public boolean screenOn;

    /** true nếu trong khoảng này có cắm sạc → loại khỏi thống kê tiêu hao. */
    public boolean charging;

    public long getDurationMs() {
        final long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return Math.max(0, end - startTime);
    }
}
