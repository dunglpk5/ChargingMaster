package com.dung.chargmagagement.model.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.battery.BatteryCapacityProvider;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.dao.BatterySampleDao;
import com.dung.chargmagagement.model.dao.ChargingSessionDao;
import com.dung.chargmagagement.model.dao.ScreenSessionDao;
import com.dung.chargmagagement.model.db.AppDatabase;
import com.dung.chargmagagement.model.entity.BatterySampleEntity;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.entity.ScreenSessionEntity;
import com.dung.chargmagagement.model.stats.BatteryUsageStats;
import com.dung.chargmagagement.model.stats.LastChargeInfo;
import com.dung.chargmagagement.model.stats.UsageCalculator;
import com.dung.chargmagagement.model.stats.UsageRate;

import java.util.List;

/**
 * Tầng truy cập dữ liệu duy nhất cho UI.
 *
 * <p>Mọi hàm công khai đều bất đồng bộ: chạy trên {@code AppExecutors.disk()} rồi
 * trả kết quả về UI thread. Nhờ vậy Controller không bao giờ phải tự nghĩ tới
 * thread, và không có nguy cơ truy vấn Room trên main thread.
 */
public final class BatteryRepository {

    private static final String TAG = "BatteryRepository";

    /** Số ngày lịch sử được giữ lại; cũ hơn sẽ bị dọn. */
    private static final int RETENTION_DAYS = 60;

    /** Cửa sổ thống kê mặc định, khớp dòng "trong 7 ngày qua" của thiết kế. */
    public static final int STATS_WINDOW_DAYS = 7;

    private static volatile BatteryRepository instance;

    private final ChargingSessionDao sessionDao;
    private final BatterySampleDao sampleDao;
    private final ScreenSessionDao screenDao;
    private final BatteryCapacityProvider capacityProvider;
    private final AppExecutors executors;

    private BatteryRepository(@NonNull Context context) {
        AppDatabase db = AppDatabase.get(context);
        this.sessionDao = db.chargingSessionDao();
        this.sampleDao = db.batterySampleDao();
        this.screenDao = db.screenSessionDao();
        this.capacityProvider = new BatteryCapacityProvider(context);
        this.executors = AppExecutors.get();
    }

    public static BatteryRepository get(@NonNull Context context) {
        if (instance == null) {
            synchronized (BatteryRepository.class) {
                if (instance == null) {
                    instance = new BatteryRepository(context);
                }
            }
        }
        return instance;
    }

    // ==================== Ghi dữ liệu (gọi từ SessionRecorder) ====================

    @WorkerThread
    public long insertSessionSync(@NonNull ChargingSessionEntity session) {
        return sessionDao.insert(session);
    }

    @WorkerThread
    public void updateSessionSync(@NonNull ChargingSessionEntity session) {
        sessionDao.update(session);
    }

    @WorkerThread
    @Nullable
    public ChargingSessionEntity findOngoingSessionSync() {
        return sessionDao.findOngoing();
    }

    @WorkerThread
    @NonNull
    public List<ChargingSessionEntity> findAllOngoingSessionsSync() {
        return sessionDao.findAllOngoing();
    }

    /** Thời điểm đo cuối của một phiên (0 nếu chưa có điểm đo nào). */
    @WorkerThread
    public long findLastSampleTimeSync(long sessionId) {
        return sampleDao.getLastTimestampOfSession(sessionId);
    }

    @WorkerThread
    public void insertSampleSync(@NonNull BatterySampleEntity sample) {
        sampleDao.insert(sample);
    }

    @WorkerThread
    public long insertScreenSessionSync(@NonNull ScreenSessionEntity session) {
        return screenDao.insert(session);
    }

    @WorkerThread
    public void updateScreenSessionSync(@NonNull ScreenSessionEntity session) {
        screenDao.update(session);
    }

    @WorkerThread
    @Nullable
    public ScreenSessionEntity findOngoingScreenSessionSync() {
        return screenDao.findOngoing();
    }

    @WorkerThread
    @NonNull
    public List<ScreenSessionEntity> findAllOngoingScreenSessionsSync() {
        return screenDao.findAllOngoing();
    }

    /** Điểm đo mới nhất, hoặc null nếu chưa từng ghi được điểm nào. */
    @WorkerThread
    @Nullable
    public BatterySampleEntity findLatestSampleSync() {
        return sampleDao.findLatest();
    }

    // ==================== Đọc dữ liệu cho UI ====================

    /** Lịch sử sạc (mới nhất trước) cho màn "Lịch sử sạc". */
    public void loadHistory(int limit, int offset,
                            @NonNull AppExecutors.Callback<List<ChargingSessionEntity>> callback) {
        executors.execute(() -> sessionDao.getHistory(limit, offset), callback);
    }

    /** Các điểm đo của một ngày để vẽ biểu đồ mức pin. */
    public void loadSamplesOfDay(int dayKey,
                                 @NonNull AppExecutors.Callback<List<BatterySampleEntity>> callback) {
        executors.execute(() -> sampleDao.getSamplesOfDay(dayKey), callback);
    }

    /** Những ngày có dữ liệu trong tháng, để đánh dấu trên lịch. */
    public void loadDaysHavingData(int fromDayKey, int toDayKey,
                                   @NonNull AppExecutors.Callback<List<Integer>> callback) {
        executors.execute(() -> sampleDao.getDaysHavingData(fromDayKey, toDayKey), callback);
    }

    /** Các phiên sạc của một ngày. */
    public void loadSessionsOfDay(long dayStartMs,
                                  @NonNull AppExecutors.Callback<List<ChargingSessionEntity>> callback) {
        executors.execute(
                () -> sessionDao.getSessionsBetween(dayStartMs, dayStartMs + DateUtils.DAY_MS),
                callback);
    }

    /**
     * Toàn bộ số liệu của tab "Sử dụng pin".
     * Gom nhiều truy vấn vào một lần chạy nền để chỉ đụng database một lượt.
     */
    public void loadUsageStats(@NonNull AppExecutors.Callback<BatteryUsageStats> callback) {
        executors.execute(this::computeUsageStats, callback);
    }

    /** Tóm tắt lần sạc gần nhất cho màn Giám sát pin. */
    public void loadLastChargeInfo(@NonNull AppExecutors.Callback<LastChargeInfo> callback) {
        executors.execute(() -> {
            List<ChargingSessionEntity> latest = sessionDao.getHistory(1, 0);
            if (latest.isEmpty()) return LastChargeInfo.EMPTY;

            return new LastChargeInfo(latest.get(0), sessionDao.findLastFullChargeTime());
        }, callback);
    }

    /** Bản đồng bộ cho nơi gọi đã tự chạy sẵn ở thread nền. */
    @WorkerThread
    @NonNull
    public BatteryUsageStats getUsageStatsSync() {
        return computeUsageStats();
    }

    @WorkerThread
    private BatteryUsageStats computeUsageStats() {
        final long from = DateUtils.daysAgo(STATS_WINDOW_DAYS);

        List<ScreenSessionEntity> onSessions = screenDao.getDischargingSessions(true, from);
        List<ScreenSessionEntity> offSessions = screenDao.getDischargingSessions(false, from);

        UsageRate screenOn = UsageCalculator.calculateRate(onSessions);
        UsageRate screenOff = UsageCalculator.calculateRate(offSessions);

        List<ChargingSessionEntity> forCapacity = sessionDao.getSessionsForCapacityEstimate(
                UsageCalculator.MIN_GAINED_PERCENT_FOR_CAPACITY,
                UsageCalculator.CAPACITY_SAMPLE_SIZE);

        // Phiên sạc đang chạy dở, để hiện hai thẻ "đang sạc hiện tại / đang sạc từ"
        ChargingSessionEntity active = sessionDao.findOngoing();

        return BatteryUsageStats.builder()
                .statsWindowDays(STATS_WINDOW_DAYS)
                .totalChargedMah(sessionDao.sumChargedMahSince(from))
                .firstSessionTime(sessionDao.findFirstSessionTime())
                .activeSession(
                        active == null ? 0L : active.startTime,
                        active == null ? 0 : active.getGainedPercent())
                .screenOn(screenOn)
                .screenOff(screenOff)
                .combined(UsageCalculator.combine(screenOn, screenOff))
                .dischargeSessionCount(onSessions.size() + offSessions.size())
                .estimatedCapacityMah(resolveEstimatedCapacity(forCapacity))
                .designCapacityMah(capacityProvider.getDesignCapacityMah())
                .chargeSessionCount(sessionDao.countFinishedSince(from))
                .totalChargedPercent(sessionDao.sumChargedPercentSince(from))
                .build();
    }

    /**
     * Dung lượng thực tế của pin, ưu tiên cách đo đáng tin hơn.
     *
     * <p>Cách chính xác nhất là đo qua các phiên sạc dài, nhưng phải chờ người dùng
     * sạc được một phiên nạp từ 20% trở lên – có thể mất vài ngày. Trong lúc chờ,
     * dùng bộ đếm điện tích của hệ thống: kém chính xác hơn nhưng có ngay lập tức,
     * và vẫn hơn hẳn việc để trống mục "Tình trạng pin" hàng ngày liền.
     *
     * <p>Chỉ dùng cách thứ hai khi dung lượng thiết kế là con số danh nghĩa thật.
     * Nếu dung lượng thiết kế cũng phải suy từ chính bộ đếm đó thì hai số bằng nhau,
     * chia ra luôn được 100% – con số vô nghĩa mà người dùng lại tin là thật.
     */
    @WorkerThread
    private int resolveEstimatedCapacity(@NonNull List<ChargingSessionEntity> forCapacity) {
        final int fromSessions = UsageCalculator.estimateCapacity(forCapacity);
        if (fromSessions != BatteryInfo.UNKNOWN_INT) return fromSessions;

        if (!capacityProvider.hasNominalDesignCapacity()) return BatteryInfo.UNKNOWN_INT;
        return capacityProvider.getCurrentFullCapacityMah();
    }

    // ==================== Dung lượng thiết kế ====================

    /** Người dùng nhập lại dung lượng thiết kế ở màn "Sử dụng pin". */
    public void setDesignCapacity(int mah) {
        capacityProvider.setUserDesignCapacity(mah);
    }

    public int getDesignCapacityMah() {
        return capacityProvider.getDesignCapacityMah();
    }

    // ==================== Bảo trì ====================

    /**
     * Dọn dữ liệu quá hạn. Gọi lúc khởi động app (một lần mỗi ngày là đủ).
     * Chạy nền, không có callback vì UI không cần biết kết quả.
     */
    public void cleanupOldData() {
        executors.disk().execute(() -> {
            try {
                final long before = DateUtils.daysAgo(RETENTION_DAYS);
                final int dayKeyBefore = DateUtils.toDayKey(before);
                int removed = sessionDao.deleteOlderThan(before)
                        + screenDao.deleteOlderThan(before)
                        + sampleDao.deleteOlderThan(before);
                Logger.d(TAG, "Đã dọn " + removed + " bản ghi cũ hơn " + RETENTION_DAYS
                        + " ngày (trước ngày " + dayKeyBefore + ")");
            } catch (Exception e) {
                Logger.e(TAG, "Dọn dữ liệu cũ thất bại", e);
            }
        });
    }

    /** Dung lượng ước tính hiện tại, dùng cho ước tính thời gian sạc đầy. */
    @WorkerThread
    public int getUsableCapacityMah() {
        List<ChargingSessionEntity> sessions = sessionDao.getSessionsForCapacityEstimate(
                UsageCalculator.MIN_GAINED_PERCENT_FOR_CAPACITY,
                UsageCalculator.CAPACITY_SAMPLE_SIZE);
        int estimated = UsageCalculator.estimateCapacity(sessions);
        // Chưa đủ dữ liệu đo thì tạm dùng dung lượng thiết kế
        if (estimated != BatteryInfo.UNKNOWN_INT) return estimated;

        int design = capacityProvider.getDesignCapacityMah();
        if (design != BatteryInfo.UNKNOWN_INT) return design;

        // Cả ba cách đều thất bại (Android 14+ chặn PowerProfile, charge counter
        // không có). Dùng dung lượng phổ thông làm mốc cuối: thời gian sạc còn lại
        // sai vài chục phút vẫn hữu ích hơn là bỏ trống một dấu gạch. Người dùng
        // sửa được bằng cách tự nhập dung lượng ở màn "Sử dụng pin".
        return UsageCalculator.FALLBACK_CAPACITY_MAH;
    }
}
