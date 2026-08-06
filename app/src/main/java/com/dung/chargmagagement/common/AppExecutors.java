package com.dung.chargmagagement.common;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ điều phối thread dùng chung cho toàn app (thay cho Coroutine, thuần Java).
 *
 * <p>Ba pool tách biệt để công việc nặng không chặn công việc nhẹ:
 * <ul>
 *     <li>{@link #disk()}   – đọc/ghi Room, SharedPreferences, sysfs. Đơn luồng để tuần tự hoá I/O.</li>
 *     <li>{@link #network()}– gọi API. Pool nhỏ, giới hạn số kết nối song song.</li>
 *     <li>{@link #sampler()}– lấy mẫu pin theo chu kỳ (scheduled), tách riêng để không bị trễ.</li>
 * </ul>
 * Kết quả trả về UI bằng {@link #main()}.
 */
public final class AppExecutors {

    private static volatile AppExecutors instance;

    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final ScheduledExecutorService sampler;
    private final Handler mainHandler;

    private AppExecutors() {
        diskIO = Executors.newSingleThreadExecutor(named("charg-disk"));
        networkIO = Executors.newFixedThreadPool(3, named("charg-net"));
        sampler = Executors.newSingleThreadScheduledExecutor(named("charg-sampler"));
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static AppExecutors get() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors();
                }
            }
        }
        return instance;
    }

    public ExecutorService disk() {
        return diskIO;
    }

    public ExecutorService network() {
        return networkIO;
    }

    public ScheduledExecutorService sampler() {
        return sampler;
    }

    public Handler main() {
        return mainHandler;
    }

    /** Chạy tác vụ trên UI thread; nếu đang ở UI thread thì chạy ngay không post. */
    public void runOnMain(@NonNull Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }

    /**
     * Chạy một tác vụ nền rồi trả kết quả về UI thread.
     * Thay thế cho pattern async/await của Coroutine.
     *
     * @param work     phần việc chạy nền, trả về kết quả
     * @param callback nhận kết quả trên UI thread (có thể null nếu không cần)
     */
    public <T> void execute(@NonNull BackgroundTask<T> work, final Callback<T> callback) {
        diskIO.execute(() -> {
            try {
                final T result = work.run();
                if (callback != null) {
                    runOnMain(() -> callback.onResult(result));
                }
            } catch (final Exception e) {
                Logger.e("AppExecutors", "Tác vụ nền thất bại", e);
                if (callback != null) {
                    runOnMain(() -> callback.onError(e));
                }
            }
        });
    }

    /**
     * Lặp lại một tác vụ theo chu kỳ, dùng cho việc lấy mẫu dòng điện.
     * Nhớ huỷ {@link ScheduledFuture} khi màn hình dừng để tiết kiệm pin.
     */
    public ScheduledFuture<?> schedulePeriodic(@NonNull Runnable task,
                                               long initialDelayMs,
                                               long periodMs) {
        return sampler.scheduleWithFixedDelay(task, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    /** Đóng toàn bộ pool khi app kết thúc. */
    public void shutdown() {
        diskIO.shutdown();
        networkIO.shutdown();
        sampler.shutdownNow();
    }

    private static ThreadFactory named(final String prefix) {
        final AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            // Ưu tiên thấp hơn UI để không tranh CPU với việc vẽ giao diện
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }

    /** Phần việc chạy nền có trả về kết quả. */
    public interface BackgroundTask<T> {
        T run() throws Exception;
    }

    /** Nhận kết quả trên UI thread. */
    public interface Callback<T> {
        void onResult(T result);

        default void onError(Exception e) {
            // Mặc định bỏ qua; màn hình nào cần thì override
        }
    }
}
