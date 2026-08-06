package com.dung.chargmagagement.model.battery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.Logger;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trung tâm theo dõi pin: gộp sự kiện hệ thống và việc lấy mẫu định kỳ thành
 * một luồng dữ liệu duy nhất cho tầng UI.
 *
 * <p><b>Tối ưu pin:</b>
 * <ul>
 *     <li>Chỉ chạy khi có ít nhất một listener đăng ký (màn hình đang hiển thị
 *         hoặc service đang chạy) – hết listener là dừng hẳn.</li>
 *     <li>Chu kỳ lấy mẫu giãn ra khi không sạc: đang sạc cần số liệu mượt,
 *         lúc dùng pin thì không.</li>
 *     <li>Mỗi lần lấy mẫu chỉ đọc 1 sticky intent + 1 file nhỏ, chạy ở thread riêng.</li>
 * </ul>
 *
 * <p>Đây là singleton vì trạng thái phiên đo phải dùng chung giữa các tab.
 */
public final class BatteryMonitor {

    private static final String TAG = "BatteryMonitor";

    /** Chu kỳ lấy mẫu khi đang sạc (ms) – đủ mượt cho biểu đồ thời gian thực. */
    private static final long INTERVAL_CHARGING_MS = 2_000L;
    /** Chu kỳ khi đang dùng pin (ms) – thưa hơn để tiết kiệm. */
    private static final long INTERVAL_DISCHARGING_MS = 5_000L;

    private static volatile BatteryMonitor instance;

    private final Context appContext;
    private final BatteryInfoProvider provider;
    private final AppExecutors executors;
    private final CurrentStats stats = new CurrentStats();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledFuture<?> samplingTask;
    private volatile BatteryInfo lastInfo;
    private volatile long currentIntervalMs = INTERVAL_DISCHARGING_MS;

    /** Nhận sự kiện cắm/rút sạc từ hệ thống để phản hồi tức thì. */
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) return;

            final boolean connected = Intent.ACTION_POWER_CONNECTED.equals(action);
            Logger.d(TAG, connected ? "Đã cắm sạc" : "Đã rút sạc");

            notifyPowerChanged(connected);
            // Lấy mẫu ngay để UI không phải chờ hết chu kỳ; đồng thời bắt đầu
            // phiên đo mới vì trạng thái nguồn vừa đổi
            executors.sampler().execute(() -> {
                stats.reset();
                sampleOnce();
            });
        }
    };

    private BatteryMonitor(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.provider = new BatteryInfoProvider(appContext);
        this.executors = AppExecutors.get();
    }

    public static BatteryMonitor get(@NonNull Context context) {
        if (instance == null) {
            synchronized (BatteryMonitor.class) {
                if (instance == null) {
                    instance = new BatteryMonitor(context);
                }
            }
        }
        return instance;
    }

    // ==================== API cho tầng UI ====================

    /**
     * Đăng ký nhận dữ liệu. Gọi ở {@code onResume()} của màn hình.
     * Listener được gọi lại ngay với dữ liệu gần nhất nếu đã có.
     */
    @MainThread
    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
        BatteryInfo cached = lastInfo;
        if (cached != null) {
            listener.onBatteryUpdated(cached, stats.getSmoothedMa());
        }
        start();
    }

    /** Huỷ đăng ký. Bắt buộc gọi ở {@code onPause()} để không rò rỉ và không hao pin. */
    @MainThread
    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            stop();
        }
    }

    /** Dữ liệu gần nhất, có thể null nếu chưa lấy mẫu lần nào. */
    public BatteryInfo getLastInfo() {
        return lastInfo;
    }

    /** Thống kê dòng điện của phiên hiện tại. */
    public CurrentStats getStats() {
        return stats;
    }

    /** Bắt đầu một phiên đo mới (ví dụ khi người dùng bấm nút PHÁT HIỆN). */
    public void resetSession() {
        executors.sampler().execute(stats::reset);
    }

    // ==================== Vòng đời nội bộ ====================

    private void start() {
        if (!running.compareAndSet(false, true)) return;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        // ContextCompat xử lý giúp yêu cầu bắt buộc khai báo cờ export từ Android 14
        ContextCompat.registerReceiver(appContext, powerReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);

        scheduleSampling(currentIntervalMs);
        Logger.d(TAG, "Bắt đầu theo dõi pin");
    }

    private void stop() {
        if (!running.compareAndSet(true, false)) return;

        try {
            appContext.unregisterReceiver(powerReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver đã được gỡ trước đó – bỏ qua
        }
        cancelSampling();
        Logger.d(TAG, "Dừng theo dõi pin");
    }

    private void scheduleSampling(long intervalMs) {
        cancelSampling();
        currentIntervalMs = intervalMs;
        samplingTask = executors.schedulePeriodic(this::sampleOnce, 0L, intervalMs);
    }

    private void cancelSampling() {
        if (samplingTask != null) {
            samplingTask.cancel(false);
            samplingTask = null;
        }
    }

    /** Một lần lấy mẫu, luôn chạy trên thread sampler. */
    private void sampleOnce() {
        try {
            final BatteryInfo info = provider.read();
            stats.addSample(info.getCurrentMa());
            lastInfo = info;

            final int smoothed = stats.getSmoothedMa();
            executors.runOnMain(() -> {
                for (Listener listener : listeners) {
                    listener.onBatteryUpdated(info, smoothed);
                }
            });

            rescheduleIfIntervalChanged();
        } catch (Exception e) {
            Logger.e(TAG, "Lỗi khi lấy mẫu pin", e);
        }
    }

    /** Đổi chu kỳ khi chuyển giữa trạng thái sạc và dùng pin. */
    private void rescheduleIfIntervalChanged() {
        final BatteryInfo info = lastInfo;
        if (info == null || !running.get()) return;

        final long expected = info.getPlugType().isPlugged()
                ? INTERVAL_CHARGING_MS
                : INTERVAL_DISCHARGING_MS;
        if (expected != currentIntervalMs) {
            scheduleSampling(expected);
        }
    }

    private void notifyPowerChanged(boolean connected) {
        executors.runOnMain(() -> {
            for (Listener listener : listeners) {
                listener.onPowerStateChanged(connected);
            }
        });
    }

    /** Callback trả về UI thread. */
    public interface Listener {

        /**
         * @param info       trạng thái pin vừa đọc
         * @param smoothedMa dòng điện đã làm mượt (mA), dùng để hiển thị
         */
        @MainThread
        void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa);

        /** Người dùng vừa cắm hoặc rút sạc. */
        @MainThread
        default void onPowerStateChanged(boolean connected) {
            // Mặc định không làm gì
        }
    }
}
