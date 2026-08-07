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

        return BatteryUsageStats.builder()
                .screenOn(screenOn)
                .screenOff(screenOff)
                .combined(UsageCalculator.combine(screenOn, screenOff))
                .dischargeSessionCount(onSessions.size() + offSessions.size())
                .estimatedCapacityMah(UsageCalculator.estimateCapacity(forCapacity))
                .designCapacityMah(capacityProvider.getDesignCapacityMah())
                .chargeSessionCount(sessionDao.countFinishedSince(from))
                .totalChargedPercent(sessionDao.sumChargedPercentSince(from))
                .build();
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
        return estimated != BatteryInfo.UNKNOWN_INT
                ? estimated
                : capacityProvider.getDesignCapacityMah();
    }
}
