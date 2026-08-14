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

    /** Mọi khoảng chưa được chốt, mới nhất trước – xem {@code findAllOngoing} ở DAO phiên sạc. */
    @Query("SELECT * FROM screen_session WHERE end_time = 0 ORDER BY start_time DESC")
    List<ScreenSessionEntity> findAllOngoing();

    /**
     * Mọi khoảng đã kết thúc trong lúc dùng pin, <b>kể cả khoảng không tụt %</b>.
     *
     * <p>Trước đây truy vấn này còn đòi {@code start_percent > end_percent} và
     * khoảng phải dài trên một phút. Đó là sai: người dùng bật tắt màn hình hàng
     * chục lần trong thời gian pin tụt 1%, nên gần như mọi khoảng đều mở và đóng
     * ở cùng một mức phần trăm và bị loại sạch. Kết quả là mục "Sử dụng pin trung
     * bình" luôn trống.
     *
     * <p>Nguy hiểm hơn: 1% tụt được ghi trọn vào đúng khoảng chứa thời điểm nhảy
     * số, còn thời gian của các khoảng lân cận thì bị vứt đi, khiến tỉ lệ %/h bị
     * thổi phồng nhiều lần. Phải lấy hết rồi cộng dồn cả tử lẫn mẫu mới đúng.
     *
     * <p>Chỉ còn loại khoảng ngắn dưới 10 giây (nhiễu lấy mẫu) và khoảng có phần
     * trăm <i>tăng</i> trong lúc không sạc – dữ liệu đó chắc chắn hỏng.
     */
    @Query("SELECT * FROM screen_session WHERE end_time > 0 AND charging = 0 "
            + "AND screen_on = :screenOn AND end_percent <= start_percent "
            + "AND (end_time - start_time) >= 10000 AND start_time >= :from")
    List<ScreenSessionEntity> getDischargingSessions(boolean screenOn, long from);

    @Query("DELETE FROM screen_session WHERE end_time > 0 AND end_time < :before")
    int deleteOlderThan(long before);
}
