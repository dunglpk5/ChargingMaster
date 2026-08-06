package com.dung.chargmagagement.model.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.dung.chargmagagement.model.entity.ScreenSessionEntity;

import java.util.List;

/**
 * Truy vấn các khoảng màn hình bật/tắt, phục vụ mục "Sử dụng pin trung bình".
 */
@Dao
public interface ScreenSessionDao {

    @Insert
    long insert(ScreenSessionEntity session);

    @Update
    void update(ScreenSessionEntity session);

    @Query("SELECT * FROM screen_session WHERE end_time = 0 ORDER BY start_time DESC LIMIT 1")
    @Nullable
    ScreenSessionEntity findOngoing();

    /**
     * Các khoảng đã kết thúc, đang dùng pin (không sạc), có tiêu hao thật sự.
     * Lọc luôn khoảng quá ngắn (dưới 1 phút) vì làm nhiễu tỉ lệ %/h.
     */
    @Query("SELECT * FROM screen_session WHERE end_time > 0 AND charging = 0 "
            + "AND screen_on = :screenOn AND start_percent > end_percent "
            + "AND (end_time - start_time) >= 60000 AND start_time >= :from")
    List<ScreenSessionEntity> getDischargingSessions(boolean screenOn, long from);

    @Query("DELETE FROM screen_session WHERE end_time > 0 AND end_time < :before")
    int deleteOlderThan(long before);
}
