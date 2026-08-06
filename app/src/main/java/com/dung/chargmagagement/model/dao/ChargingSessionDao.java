package com.dung.chargmagagement.model.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.dung.chargmagagement.model.entity.ChargingSessionEntity;

import java.util.List;

/**
 * Truy vấn lịch sử phiên sạc.
 *
 * <p>Toàn bộ hàm ở đây là đồng bộ – tầng repository chịu trách nhiệm đưa chúng
 * sang thread nền qua {@code AppExecutors}. Cách này giữ DAO đơn giản và dễ test.
 */
@Dao
public interface ChargingSessionDao {

    @Insert
    long insert(ChargingSessionEntity session);

    @Update
    void update(ChargingSessionEntity session);

    /** Phiên chưa kết thúc (nếu có) – dùng để nối lại sau khi app bị kill. */
    @Query("SELECT * FROM charging_session WHERE end_time = 0 ORDER BY start_time DESC LIMIT 1")
    @Nullable
    ChargingSessionEntity findOngoing();

    @Query("SELECT * FROM charging_session WHERE id = :id")
    @Nullable
    ChargingSessionEntity findById(long id);

    /** Lịch sử sạc mới nhất trước, có phân trang để không nạp cả bảng. */
    @Query("SELECT * FROM charging_session WHERE end_time > 0 "
            + "ORDER BY start_time DESC LIMIT :limit OFFSET :offset")
    List<ChargingSessionEntity> getHistory(int limit, int offset);

    /** Các phiên nằm trong một khoảng thời gian (dùng cho bộ lọc theo ngày). */
    @Query("SELECT * FROM charging_session WHERE start_time >= :from AND start_time < :to "
            + "ORDER BY start_time DESC")
    List<ChargingSessionEntity> getSessionsBetween(long from, long to);

    @Query("SELECT COUNT(*) FROM charging_session WHERE end_time > 0 AND start_time >= :from")
    int countFinishedSince(long from);

    /** Tổng % pin đã nạp kể từ mốc thời gian, phục vụ mục "Pin đã sạc". */
    @Query("SELECT COALESCE(SUM(end_percent - start_percent), 0) FROM charging_session "
            + "WHERE end_time > 0 AND end_percent > start_percent AND start_time >= :from")
    int sumChargedPercentSince(long from);

    /**
     * Các phiên đủ dài để ước tính dung lượng pin đáng tin cậy.
     * Ngưỡng %: phiên nạp dưới ~20% cho sai số rất lớn.
     */
    @Query("SELECT * FROM charging_session WHERE end_time > 0 AND charged_mah > 0 "
            + "AND (end_percent - start_percent) >= :minGainedPercent "
            + "ORDER BY start_time DESC LIMIT :limit")
    List<ChargingSessionEntity> getSessionsForCapacityEstimate(int minGainedPercent, int limit);

    /** Dọn dữ liệu cũ để database không phình theo thời gian. */
    @Query("DELETE FROM charging_session WHERE end_time > 0 AND end_time < :before")
    int deleteOlderThan(long before);
}
