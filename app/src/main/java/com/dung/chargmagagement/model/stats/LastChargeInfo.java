package com.dung.chargmagagement.model.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.power.ChargeSpeed;

/**
 * Tóm tắt lần sạc gần nhất cho thẻ "Thông tin lần sạc cuối".
 *
 * <p>Gói cả trường hợp <b>chưa có phiên sạc nào</b>: khi đó {@link #hasSession()}
 * trả về false và tầng giao diện hiển thị dấu gạch thay vì số 0. Số 0 ở đây gây
 * hiểu nhầm nặng – "sạc 0 phút" khác hẳn "chưa từng ghi nhận lần sạc nào".
 */
public final class LastChargeInfo {

    /** Không có phiên sạc nào trong lịch sử. */
    public static final LastChargeInfo EMPTY = new LastChargeInfo(null, 0L);

    @Nullable
    private final ChargingSessionEntity session;

    /** Thời điểm kết thúc lần sạc đầy gần nhất; 0 nghĩa là chưa từng sạc đầy. */
    private final long lastFullChargeTime;

    public LastChargeInfo(@Nullable ChargingSessionEntity session, long lastFullChargeTime) {
        this.session = session;
        this.lastFullChargeTime = lastFullChargeTime;
    }

    public boolean hasSession() {
        return session != null;
    }

    /** Xếp loại tốc độ của lần sạc đó, dựa trên dòng nạp trung bình. */
    @NonNull
    public ChargeSpeed getSpeed() {
        return session == null
                ? ChargeSpeed.UNKNOWN
                : ChargeSpeed.fromCurrent(session.avgCurrentMa);
    }

    /** Nguồn điện đã dùng (AC, USB, không dây); chuỗi rỗng nếu không có phiên. */
    @NonNull
    public String getPlugType() {
        return session == null || session.plugType == null ? "" : session.plugType;
    }

    public long getDurationMs() {
        return session == null ? 0L : session.getDurationMs();
    }

    public int getStartPercent() {
        return session == null ? 0 : session.startPercent;
    }

    public int getEndPercent() {
        return session == null ? 0 : session.endPercent;
    }

    public boolean hasFullCharge() {
        return lastFullChargeTime > 0;
    }

    /** Đã bao lâu kể từ lần sạc đầy gần nhất (ms); 0 nếu chưa từng sạc đầy. */
    public long getTimeSinceFullChargeMs() {
        if (lastFullChargeTime <= 0) return 0L;
        return Math.max(0L, System.currentTimeMillis() - lastFullChargeTime);
    }
}
