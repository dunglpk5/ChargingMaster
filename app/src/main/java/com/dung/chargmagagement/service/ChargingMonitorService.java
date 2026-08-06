package com.dung.chargmagagement.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.dung.chargmagagement.ChargApplication;
import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.FormatUtils;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.common.PrefManager;
import com.dung.chargmagagement.model.alarm.AlarmSettings;
import com.dung.chargmagagement.model.alarm.ChargeAlarmChecker;
import com.dung.chargmagagement.controller.home.HomeActivity;
import com.dung.chargmagagement.model.battery.BatteryInfo;
import com.dung.chargmagagement.model.battery.BatteryMonitor;
import com.dung.chargmagagement.model.repository.SessionRecorder;

/**
 * Foreground service theo dõi phiên sạc khi app không hiển thị.
 *
 * <p>Chỉ chạy trong lúc thiết bị đang cắm sạc: {@link ChargingStateReceiver} khởi
 * động service khi cắm và dừng khi rút. Nhờ vậy service không tiêu tốn pin lúc
 * máy đang chạy bằng pin – đúng nguyên tắc "chỉ đo khi có gì để đo".
 *
 * <p>Notification cập nhật tối đa mỗi {@link #NOTIFY_INTERVAL_MS} để không làm
 * hệ thống phải vẽ lại liên tục.
 */
public class ChargingMonitorService extends Service implements BatteryMonitor.Listener {

    private static final String TAG = "MonitorService";
    private static final int NOTIFICATION_ID = 1001;
    private static final long NOTIFY_INTERVAL_MS = 5_000L;

    private BatteryMonitor monitor;
    private SessionRecorder recorder;
    private ChargeAlarmChecker alarmChecker;
    private ChargeAlarmNotifier alarmNotifier;
    private long lastNotifyTime;

    /** Khởi động service (gọi khi phát hiện cắm sạc). */
    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, ChargingMonitorService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    /** Dừng service (gọi khi rút sạc). */
    public static void stop(@NonNull Context context) {
        context.stopService(new Intent(context, ChargingMonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Phải gọi startForeground sớm nhất có thể, nếu không hệ thống sẽ kill service
        startForegroundCompat(buildNotification(null, BatteryInfo.UNKNOWN_INT));

        monitor = BatteryMonitor.get(this);
        recorder = SessionRecorder.get(this);
        alarmChecker = new ChargeAlarmChecker();
        alarmNotifier = new ChargeAlarmNotifier(this);
        // Recorder đăng ký trước để không bỏ sót mẫu đầu tiên của phiên sạc
        monitor.addListener(recorder);
        monitor.addListener(this);
        Logger.d(TAG, "Service theo dõi sạc đã khởi động");
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // START_STICKY: nếu hệ thống thu hồi bộ nhớ, service được dựng lại khi rảnh
        return START_STICKY;
    }

    @Override
    public void onBatteryUpdated(@NonNull BatteryInfo info, int smoothedMa) {
        // Rút sạc trong lúc service đang chạy: tự dừng cho tiết kiệm
        if (!info.getPlugType().isPlugged()) {
            stopSelf();
            return;
        }

        checkAlarm(info);

        final long now = System.currentTimeMillis();
        if (now - lastNotifyTime < NOTIFY_INTERVAL_MS) return;
        lastNotifyTime = now;

        startForegroundCompat(buildNotification(info, smoothedMa));
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
                info.isCharging(),
                info.getTemperatureCelsius(),
                settings);

        alarmNotifier.notifyAlarm(type, info.getPercent());
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

    private Notification buildNotification(@Nullable BatteryInfo info, int smoothedMa) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, HomeActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = getString(R.string.app_name);
        String content = getString(R.string.value_placeholder);

        if (info != null) {
            title = getString(R.string.notif_charging_title, info.getPercent());
            content = smoothedMa != BatteryInfo.UNKNOWN_INT
                    ? getString(R.string.notif_charging_content,
                        smoothedMa, FormatUtils.formatTemperature(info.getTemperatureCelsius()))
                    : getString(info.getPlugType().getLabelRes());
        }

        return new NotificationCompat.Builder(this, ChargApplication.CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_battery)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public void onDestroy() {
        if (monitor != null) {
            monitor.removeListener(this);
            if (recorder != null) {
                monitor.removeListener(recorder);
                // Chốt khoảng màn hình đang mở để không để lại bản ghi treo
                recorder.finalizeOpenSessions();
            }
        }
        // Rút sạc là hết lý do giữ cảnh báo trên màn hình
        if (alarmNotifier != null) alarmNotifier.cancel();
        Logger.d(TAG, "Service theo dõi sạc đã dừng");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Service chỉ chạy nền, không cần binding
        return null;
    }
}
