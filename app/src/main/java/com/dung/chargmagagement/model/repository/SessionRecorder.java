package com.dung.chargmagagement.model.repository;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.entity.BatterySampleEntity;
import com.dung.chargmagagement.model.entity.ChargingSessionEntity;
import com.dung.chargmagagement.model.entity.ScreenSessionEntity;
import com.dung.chargmagagement.model.stats.UsageCalculator;

/**
 * Ghi lịch sử sạc và lịch sử màn hình vào database.
 *
 * <p>Nghe dữ liệu từ {@link BatteryMonitor} rồi quyết định lúc nào mở phiên, lúc
 * nào ghi điểm đo, lúc nào đóng phiên. Tất cả thao tác database đều đẩy sang
 * {@code AppExecutors.disk()} – vốn là executor đơn luồng – nên mọi trạng thái
 * nội bộ của lớp này chỉ bị đụng tới bởi đúng một thread, không cần khoá.
 *
 * <p><b>Về tiết chế ghi:</b> mẫu chỉ được lưu khi mức pin đổi hoặc sau
 * {@link #SAMPLE_MIN_INTERVAL_MS}. Lấy mẫu 2 giây một lần mà ghi hết thì một ngày
 * sinh ra hơn 40.000 dòng, trong khi biểu đồ theo giờ chỉ cần vài trăm điểm.
 *
 * <p><b>Về khoảng màn hình:</b> trạng thái màn hình được đọc ngay tại mỗi lần lấy
 * mẫu thay vì đăng ký thêm receiver SCREEN_ON/OFF. Sai số tối đa bằng một chu kỳ
 * lấy mẫu (2–5 giây), không đáng kể với con số %/h, đổi lại tiết kiệm được một
 * receiver chạy thường trú. Chỉ ghi nhận được khi tiến trình còn sống (app đang
 * mở hoặc đang sạc); các khoảng trống không làm sai số liệu vì đây là tỉ lệ
 * trung bình chứ không phải tổng tuyệt đối.
 */
public final class SessionRecorder implements BatteryMonitor.Listener {

    private static final String TAG = "SessionRecorder";

    /** Khoảng cách tối thiểu giữa hai điểm đo được lưu (ms). */
    private static final long SAMPLE_MIN_INTERVAL_MS = 60_000L;

    private static volatile SessionRecorder instance;

    private final BatteryRepository repository;
    private final PowerManager powerManager;
    private final AppExecutors executors;

    // ==== Trạng thái nội bộ: chỉ truy cập trên thread disk ====
    private ChargingSessionEntity activeSession;
    private ScreenSessionEntity activeScreenSession;
    private long lastSampleTime;
    private int lastSavedPercent = BatteryInfo.UNKNOWN_INT;
    private long currentSumMa;
    private int currentSampleCount;
    private boolean restored;

    private SessionRecorder(@NonNull Context context) {
        this.repository = BatteryRepository.get(context);
        this.powerManager = (PowerManager) context.getApplicationContext()
                .getSystemService(Context.POWER_SERVICE);
        this.executors = AppExecutors.get();
    }

    public static SessionRecorder get(@NonNull Context context) {
        if (instance == null) {
            synchronized (SessionRecorder.class) {
                if (instance == null) {
                    instance = new SessionRecorder(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        // Chuyển ngay sang thread disk: từ đây trở đi không còn chạm UI thread
        executors.disk().execute(() -> handleSample(info, smoothedMa));
    }

    // ==================== Xử lý trên thread disk ====================

    @WorkerThread
    private void handleSample(@NonNull BatteryInfo info, int smoothedMa) {
        try {
            restoreIfNeeded();

            final boolean screenOn = powerManager != null && powerManager.isInteractive();
            final boolean plugged = info.getPlugType().isPlugged();

            updateChargingSession(info, smoothedMa, plugged);
            updateScreenSession(info, screenOn, plugged);
            saveSampleIfNeeded(info, smoothedMa, screenOn);
        } catch (Exception e) {
            Logger.e(TAG, "Ghi dữ liệu phiên thất bại", e);
        }
    }

    /**
     * Nối lại phiên còn dang dở sau khi tiến trình bị hệ thống kill.
     * Chỉ chạy một lần cho mỗi vòng đời tiến trình.
     */
    @WorkerThread
    private void restoreIfNeeded() {
        if (restored) return;
        restored = true;

        closeOrphanSessions();

        activeSession = repository.findOngoingSessionSync();
        activeScreenSession = repository.findOngoingScreenSessionSync();

        if (activeSession != null) {
            // Số liệu trung bình tích luỹ trong RAM đã mất; khôi phục từ bản ghi
            currentSampleCount = activeSession.sampleCount;
            currentSumMa = (long) activeSession.avgCurrentMa * Math.max(1, currentSampleCount);
            Logger.d(TAG, "Nối lại phiên sạc #" + activeSession.id);
        }
    }

    /**
     * Chốt các phiên sạc bị bỏ treo từ những lần chạy trước.
     *
     * <p>Chỉ giữ lại phiên mới nhất để nối tiếp; những phiên cũ hơn mà vẫn còn
     * {@code end_time = 0} là do tiến trình chết trước khi kịp đóng. Chúng không
     * bao giờ được đóng nữa nên sẽ không lọt vào màn Lịch sử sạc – tức là người
     * dùng mất trắng dữ liệu đó. Chốt bằng thời điểm đo cuối cùng thuộc phiên,
     * đây là mốc gần đúng nhất mà ta còn biết được.
     */
    @WorkerThread
    private void closeOrphanSessions() {
        final java.util.List<ChargingSessionEntity> ongoing =
                repository.findAllOngoingSessionsSync();
        if (ongoing.size() <= 1) return;

        // Bỏ qua phần tử đầu (mới nhất) vì restoreIfNeeded sẽ nối tiếp phiên đó
        for (int i = 1; i < ongoing.size(); i++) {
            final ChargingSessionEntity stale = ongoing.get(i);
            final long lastSample = repository.findLastSampleTimeSync(stale.id);

            stale.endTime = lastSample > stale.startTime ? lastSample : stale.startTime;
            stale.chargedMah = UsageCalculator.calculateChargedMah(
                    stale.avgCurrentMa, stale.getDurationMs());
            repository.updateSessionSync(stale);

            Logger.d(TAG, "Chốt phiên sạc bị bỏ treo #" + stale.id);
        }
    }

    // ==================== Phiên sạc ====================

    @WorkerThread
    private void updateChargingSession(BatteryInfo info, int smoothedMa, boolean plugged) {
        if (plugged && activeSession == null) {
            openChargingSession(info);
            return;
        }
        if (!plugged && activeSession != null) {
            closeChargingSession(info);
            return;
        }
        if (plugged) {
            accumulate(info, smoothedMa);
        }
    }

    @WorkerThread
    private void openChargingSession(BatteryInfo info) {
        ChargingSessionEntity session = new ChargingSessionEntity();
        session.startTime = info.getTimestamp();
        session.startPercent = info.getPercent();
        session.endPercent = info.getPercent();
        session.plugType = info.getPlugType().name();
        session.maxTemperature = info.getTemperatureCelsius();

        session.id = repository.insertSessionSync(session);
        activeSession = session;

        currentSumMa = 0;
        currentSampleCount = 0;
        Logger.d(TAG, "Mở phiên sạc #" + session.id + " tại " + info.getPercent() + "%");
    }

    /** Cập nhật các số liệu tích luỹ của phiên đang chạy. */
    @WorkerThread
    private void accumulate(BatteryInfo info, int smoothedMa) {
        final ChargingSessionEntity session = activeSession;
        if (session == null) return;

        session.endPercent = info.getPercent();
        session.maxTemperature = Math.max(session.maxTemperature, info.getTemperatureCelsius());

        if (smoothedMa != BatteryInfo.UNKNOWN_INT && smoothedMa > 0) {
            currentSumMa += smoothedMa;
            currentSampleCount++;
            session.avgCurrentMa = (int) (currentSumMa / currentSampleCount);
            session.maxCurrentMa = session.maxCurrentMa == BatteryInfo.UNKNOWN_INT
                    ? smoothedMa
                    : Math.max(session.maxCurrentMa, smoothedMa);
        }
        session.sampleCount = currentSampleCount;

        // Ghi đè bản ghi đang mở để dữ liệu không mất nếu tiến trình bị kill
        repository.updateSessionSync(session);
    }

    @WorkerThread
    private void closeChargingSession(BatteryInfo info) {
        final ChargingSessionEntity session = activeSession;
        if (session == null) return;

        session.endTime = info.getTimestamp();
        session.endPercent = info.getPercent();
        session.chargedMah = UsageCalculator.calculateChargedMah(
                session.avgCurrentMa, session.getDurationMs());
        repository.updateSessionSync(session);

        Logger.d(TAG, "Đóng phiên sạc #" + session.id
                + ": +" + session.getGainedPercent() + "%, "
                + Math.round(session.chargedMah) + " mAh");

        activeSession = null;
        currentSumMa = 0;
        currentSampleCount = 0;
    }

    // ==================== Khoảng màn hình bật/tắt ====================

    @WorkerThread
    private void updateScreenSession(BatteryInfo info, boolean screenOn, boolean plugged) {
        final ScreenSessionEntity current = activeScreenSession;

        if (current == null) {
            openScreenSession(info, screenOn, plugged);
            return;
        }

        if (current.screenOn != screenOn) {
            // Trạng thái màn hình đổi: chốt khoảng cũ, mở khoảng mới
            current.endTime = info.getTimestamp();
            current.endPercent = info.getPercent();
            repository.updateScreenSessionSync(current);
            openScreenSession(info, screenOn, plugged);
            return;
        }

        current.endPercent = info.getPercent();
        // Khoảng có cắm sạc dù chỉ một lúc cũng bị loại khỏi thống kê tiêu hao
        current.charging = current.charging || plugged;
        repository.updateScreenSessionSync(current);
    }

    @WorkerThread
    private void openScreenSession(BatteryInfo info, boolean screenOn, boolean plugged) {
        ScreenSessionEntity session = new ScreenSessionEntity();
        session.startTime = info.getTimestamp();
        session.startPercent = info.getPercent();
        session.endPercent = info.getPercent();
        session.screenOn = screenOn;
        session.charging = plugged;
        session.id = repository.insertScreenSessionSync(session);
        activeScreenSession = session;
    }

    // ==================== Điểm đo cho biểu đồ ====================

    @WorkerThread
    private void saveSampleIfNeeded(BatteryInfo info, int smoothedMa, boolean screenOn) {
        final long now = info.getTimestamp();
        final boolean percentChanged = info.getPercent() != lastSavedPercent;
        final boolean intervalPassed = now - lastSampleTime >= SAMPLE_MIN_INTERVAL_MS;

        if (!percentChanged && !intervalPassed) return;

        BatterySampleEntity sample = new BatterySampleEntity();
        sample.timestamp = now;
        sample.dayKey = DateUtils.toDayKey(now);
        sample.sessionId = activeSession != null ? activeSession.id : 0L;
        sample.percent = info.getPercent();
        sample.currentMa = smoothedMa;
        sample.temperature = info.getTemperatureCelsius();
        sample.voltage = info.getVoltage();
        sample.charging = info.isCharging();
        sample.screenOn = screenOn;

        repository.insertSampleSync(sample);

        lastSampleTime = now;
        lastSavedPercent = info.getPercent();
    }

    /**
     * Chốt các phiên đang mở khi biết chắc sẽ không còn nhận dữ liệu nữa
     * (service dừng). Tránh để lại bản ghi treo với {@code end_time = 0}.
     *
     * <p><b>Phải chốt cả phiên sạc, không chỉ khoảng màn hình.</b> Khi người dùng
     * rút sạc, {@code ChargingStateReceiver} dừng service gần như tức thì – thường
     * là trước cả lần lấy mẫu kế tiếp, nên {@code closeChargingSession} không bao
     * giờ được gọi. Phiên nằm lại với {@code end_time = 0}, mà truy vấn lịch sử
     * lại lọc {@code end_time > 0}, nên màn Lịch sử sạc trống rỗng vĩnh viễn.
     */
    public void finalizeOpenSessions() {
        executors.disk().execute(() -> {
            try {
                final long now = System.currentTimeMillis();

                if (activeSession != null && activeSession.endTime == 0) {
                    activeSession.endTime = now;
                    activeSession.chargedMah = UsageCalculator.calculateChargedMah(
                            activeSession.avgCurrentMa, activeSession.getDurationMs());
                    repository.updateSessionSync(activeSession);

                    Logger.d(TAG, "Chốt phiên sạc #" + activeSession.id
                            + ": +" + activeSession.getGainedPercent() + "%");

                    activeSession = null;
                    currentSumMa = 0;
                    currentSampleCount = 0;
                }

                if (activeScreenSession != null && activeScreenSession.endTime == 0) {
                    activeScreenSession.endTime = now;
                    repository.updateScreenSessionSync(activeScreenSession);
                    activeScreenSession = null;
                }
            } catch (Exception e) {
                Logger.e(TAG, "Chốt phiên đang mở thất bại", e);
            }
        });
    }
}
