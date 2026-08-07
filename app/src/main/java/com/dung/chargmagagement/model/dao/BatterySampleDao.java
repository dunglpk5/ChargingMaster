package com.dung.chargmagagement.model.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.dung.chargmagagement.model.entity.BatterySampleEntity;

import java.util.List;

/**
 * Truy vấn các điểm đo pin (dữ liệu vẽ biểu đồ).
 */
@Dao
public interface BatterySampleDao {

    @Insert
    long insert(BatterySampleEntity sample);

    /**
     * Các điểm đo của một ngày (khoá {@code yyyyMMdd}), tăng dần theo thời gian
     * để vẽ biểu đồ mức pin 0:00–24:00.
     */
    @Query("SELECT * FROM battery_sample WHERE day_key = :dayKey ORDER BY timestamp ASC")
    List<BatterySampleEntity> getSamplesOfDay(int dayKey);

    /** Các điểm đo thuộc một phiên sạc, dùng cho màn chi tiết phiên. */
    @Query("SELECT * FROM battery_sample WHERE session_id = :sessionId ORDER BY timestamp ASC")
    List<BatterySampleEntity> getSamplesOfSession(long sessionId);

    /** Những ngày có dữ liệu trong tháng, để tô dấu trên lịch. */
    @Query("SELECT DISTINCT day_key FROM battery_sample "
            + "WHERE day_key >= :fromDayKey AND day_key <= :toDayKey ORDER BY day_key ASC")
    List<Integer> getDaysHavingData(int fromDayKey, int toDayKey);

    /**
     * Thời điểm đo cuối cùng của một phiên, hoặc 0 nếu phiên không có điểm đo nào.
     * Dùng để chốt thời điểm kết thúc cho phiên bị bỏ treo.
     */
    @Query("SELECT COALESCE(MAX(timestamp), 0) FROM battery_sample WHERE session_id = :sessionId")
    long getLastTimestampOfSession(long sessionId);

    @Query("SELECT COUNT(*) FROM battery_sample")
    int count();

    @Query("DELETE FROM battery_sample WHERE timestamp < :before")
    int deleteOlderThan(long before);
}
