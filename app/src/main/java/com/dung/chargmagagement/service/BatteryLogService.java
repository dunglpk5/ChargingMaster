package com.dung.chargmagagement.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.ChargApplication;
import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.common.PrefManager;
import com.dung.chargmagagement.controller.home.HomeActivity;
import com.dung.chargmagagement.model.alarm.AlarmSettings;
import com.dung.chargmagagement.model.alarm.ChargeAlarmChecker;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryInfoProvider;
import com.dung.chargmagagement.model.repository.SessionRecorder;

/**
 * Dịch vụ nền <b>duy nhất</b> của ứng dụng: ghi lại mức pin để dựng biểu đồ theo
 * ngày, kể cả khi người dùng đã thoát app.
 *
 * <p><b>Vì sao phải chạy thường trú:</b> biểu đồ pin 00:00–24:00 chỉ có nghĩa khi
 * dữ liệu liền mạch cả ngày. Nhưng phần lớn thời gian pin tụt lại nằm ngoài lúc app
 * đang mở – máy nằm trong túi, màn hình tắt. Không có tiến trình nào sống thì không
 * ai ghi được gì, và biểu đồ chỉ còn vài chấm rời rạc quanh lúc người dùng mở app.
 *
 * <p><b>Vì sao vẫn không hao pin:</b> service này <b>không hề lấy mẫu định kỳ</b>.
 * Nó chỉ đăng ký nhận {@code ACTION_BATTERY_CHANGED} – broadcast do chính hệ thống
 * phát mỗi khi mức pin, nhiệt độ hoặc trạng thái nguồn đổi. Giữa hai lần đổi, service
 * ngủ hoàn toàn: không timer, không wakelock, không vòng lặp. Chi phí gần như bằng
 * không, khác hẳn với cách hẹn giờ đọc pin mỗi vài giây.
 *
 * <p>Service cũng kiêm luôn việc kiểm tra báo động sạc, vì nó đã có sẵn dữ liệu –
 * dựng thêm một tiến trình nền thứ hai chỉ để làm việc đó là lãng phí.
 */
public class BatteryLogService extends Service {

    private static final String TAG = "BatteryLogService";
    private static final int NOTIFICATION_ID = 1001;

    /**
     * Khoảng cách tối thiểu giữa hai lần xử lý khi mức pin và nguồn điện không đổi.
     *
     * <p>{@code ACTION_BATTERY_CHANGED} còn phát cả khi chỉ nhiệt độ hoặc điện áp
     * nhúc nhích, lúc đang sạc có thể vài giây một lần. Những mẫu đó không thêm gì
     * cho biểu đồ nhưng vẫn tốn một lượt đọc sysfs và ghi database.
     */
    private static final long MIN_PROCESS_INTERVAL_MS = 15_000L;

    /** Notification chỉ vẽ lại tối đa mỗi ngần này, tránh làm hệ thống bận vô ích. */
    private static final long NOTIFY_INTERVAL_MS = 60_000L;

    private BatteryInfoProvider provider;
    private SessionRecorder recorder;
    private ChargeAlarmChecker alarmChecker;
    private ChargeAlarmNotifier alarmNotifier;
    private AppExecutors executors;

    private long lastProcessTime;
    private long lastNotifyTime;
    private int lastPercent = BatteryInfo.UNKNOWN_INT;
    private boolean lastPlugged;

    /**
     * Nguồn dữ liệu duy nhất: hệ thống chủ động báo mỗi khi có gì đổi.
     *
     * <p>Phải đăng ký bằng code chứ không khai báo trong Manifest –
     * {@code ACTION_BATTERY_CHANGED} nằm trong danh sách broadcast <b>không</b> gửi
     * tới manifest receiver từ Android 8 trở đi.
     */
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            handleBroadcast(intent);
        }
    };

    /** Khởi động service ghi pin. Gọi lại khi đang chạy cũng không sao. */
    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, BatteryLogService.class);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            // Android 12+ ném ForegroundServiceStartNotAllowedException nếu app đang
            // ở nền và không thuộc diện miễn trừ. Lần mở app sau sẽ thử lại.
            Logger.e(TAG, "Không khởi động được service ghi pin", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Phải gọi startForeground sớm nhất có thể, nếu không hệ thống sẽ kill service
        startForegroundCompat(buildNotification(null));

        provider = new BatteryInfoProvider(this);
        recorder = SessionRecorder.get(this);
        alarmChecker = new ChargeAlarmChecker();
        alarmNotifier = new ChargeAlarmNotifier(this);
        executors = AppExecutors.get();

        ContextCompat.registerReceiver(this, batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        Logger.d(TAG, "Service ghi pin đã khởi động");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // START_STICKY: hệ thống dựng lại service sau khi thu hồi bộ nhớ
        return START_STICKY;
    }

    // ==================== Xử lý dữ liệu ====================

    /**
     * Quyết định có xử lý broadcast này không, rồi đẩy phần nặng sang thread nền.
     *
     * <p>Mức pin đổi hoặc cắm/rút sạc là <b>luôn</b> xử lý ngay – đó chính là những
     * điểm cần có trên biểu đồ. Các broadcast còn lại bị tiết chế theo thời gian.
     */
    private void handleBroadcast(@NonNull Intent intent) {
        final int percent = extractPercent(intent);
        final boolean plugged = intent.getIntExtra(
                android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;

        final long now = System.currentTimeMillis();
        final boolean changed = percent != lastPercent || plugged != lastPlugged;
        if (!changed && now - lastProcessTime < MIN_PROCESS_INTERVAL_MS) return;

        lastProcessTime = now;
        lastPercent = percent;
        lastPlugged = plugged;

        // parse() có đọc file sysfs nên bắt buộc chạy ngoài main thread
        executors.disk().execute(() -> process(intent));
    }

    private static int extractPercent(@NonNull Intent intent) {
        final int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
        return level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : BatteryInfo.UNKNOWN_INT;
    }

    private void process(@NonNull Intent intent) {
        try {
            final BatteryInfo info = provider.parse(intent);

            // Recorder khai báo nhận dữ liệu trên main thread (nó dùng chung với
            // tầng UI), nên trả về main rồi mới gọi; bản thân nó lại tự đẩy phần
            // ghi database sang thread disk
            executors.runOnMain(() -> recorder.onBatteryUpdated(info, info.getCurrentMa()));

            checkAlarm(info);
            updateNotification(info);
        } catch (Exception e) {
            Logger.e(TAG, "Xử lý mẫu pin thất bại", e);
        }
    }

    /**
     * Kiểm tra và phát cảnh báo nếu cần.
     *
     * <p>Đọc cấu hình mỗi lần thay vì nhớ sẵn: người dùng có thể vừa đổi thiết lập
     * ở màn Báo động sạc trong lúc service đang chạy. Đây là đọc SharedPreferences
     * đã nằm sẵn trong bộ nhớ nên chi phí không đáng kể.
     */
    private void checkAlarm(@NonNull BatteryInfo info) {
        AlarmSettings settings = AlarmSettings.load(PrefManager.get(this));
        if (!settings.hasAnyEnabled()) return;

        ChargeAlarmChecker.AlarmType type = alarmChecker.check(
                info.getPercent(),
                // Dùng "đang cắm nguồn" chứ không phải "đang nạp": khi pin gần đầy
                // nhiều máy báo trạng thái FULL và isCharging() trả về false, lúc đó
                // cảnh báo pin đầy sẽ không bao giờ phát
                info.getPlugType().isPlugged(),
                info.getTemperatureCelsius(),
                settings);

        alarmNotifier.notifyAlarm(type, info.getPercent());
    }

    // ==================== Notification ====================

    private void updateNotification(@NonNull BatteryInfo info) {
        final long now = System.currentTimeMillis();
        if (now - lastNotifyTime < NOTIFY_INTERVAL_MS) return;
        lastNotifyTime = now;

        startForegroundCompat(buildNotification(info));
    }

    private void startForegroundCompat(@NonNull Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Từ Android 10 phải khai báo loại foreground service
            startForeground(NOTIFICATION_ID, notification,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            : 0);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(@Nullable BatteryInfo info) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, HomeActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        final String content = info == null
                ? getString(R.string.notif_log_starting)
                : getString(R.string.notif_log_content, info.getPercent());

        return new NotificationCompat.Builder(this, ChargApplication.CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_battery)
                .setContentTitle(getString(R.string.notif_log_title))
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver đã được gỡ trước đó – bỏ qua
        }

        // Chốt các phiên đang mở để chúng không nằm lại với end_time = 0, vốn bị
        // mọi truy vấn thống kê loại ra vĩnh viễn
        if (recorder != null) recorder.finalizeOpenSessions();
        if (alarmNotifier != null) alarmNotifier.cancel();

        Logger.d(TAG, "Service ghi pin đã dừng");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Service chỉ chạy nền, không cần binding
        return null;
    }
}
