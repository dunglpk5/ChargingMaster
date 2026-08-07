package com.dung.chargmagagement;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.DateUtils;
import com.dung.chargmagagement.common.LocaleManager;
import com.dung.chargmagagement.common.PrefManager;
import com.dung.chargmagagement.model.repository.BatteryRepository;

/**
 * Lớp Application của app.
 * Chỉ khởi tạo những thành phần thật sự cần ngay lúc mở app để giữ thời gian
 * khởi động ngắn; phần nặng (Room, Retrofit) được khởi tạo lazy khi lần đầu dùng.
 */
public class ChargApplication extends Application {

    /** Id kênh thông báo cho foreground service theo dõi phiên sạc. */
    public static final String CHANNEL_MONITOR = "charging_monitor";

    /**
     * Kênh cho báo động sạc – mức quan trọng cao để thông báo nổi lên màn hình.
     *
     * <p>Hậu tố "_v2" vì thuộc tính của một kênh <b>không sửa được sau khi đã tạo</b>:
     * kênh cũ mang âm thanh và rung mặc định của hệ thống, cộng thêm tiếng chuông
     * do {@code AlarmPlayer} phát là thành hai tiếng chồng nhau. Đổi id là cách duy
     * nhất để máy đã cài bản cũ cũng nhận được cấu hình mới.
     */
    public static final String CHANNEL_ALARM = "charging_alarm_v2";

    /** Kênh báo động của bản cũ, chỉ giữ tên để xoá đi. */
    private static final String CHANNEL_ALARM_LEGACY = "charging_alarm";

    /** Ngày gần nhất đã dọn dữ liệu cũ (dạng yyyyMMdd). */
    private static final String KEY_LAST_CLEANUP_DAY = "last_cleanup_day";

    private static ChargApplication instance;

    /** Truy cập context toàn cục (chỉ dùng cho tầng model, không giữ Activity). */
    public static ChargApplication get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Áp ngôn ngữ người dùng đã chọn trước khi bất kỳ màn nào được vẽ
        LocaleManager.applySavedLocale(PrefManager.get(this));

        createNotificationChannels();
        cleanupOldDataOncePerDay();
    }

    /**
     * Dọn lịch sử quá hạn, tối đa một lần mỗi ngày.
     * Chạy nền nên không ảnh hưởng thời gian mở app.
     */
    private void cleanupOldDataOncePerDay() {
        PrefManager prefs = PrefManager.get(this);
        final int today = DateUtils.todayKey();
        if (prefs.getInt(KEY_LAST_CLEANUP_DAY, 0) == today) return;

        prefs.putInt(KEY_LAST_CLEANUP_DAY, today);
        BatteryRepository.get(this).cleanupOldData();
    }

    /**
     * Tạo sẵn các kênh thông báo.
     *
     * <p>Kênh thông báo là bắt buộc từ Android 8.0 và minSdk của app đã là 26 nên
     * không cần kiểm tra phiên bản.
     */
    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel monitor = new NotificationChannel(
                CHANNEL_MONITOR,
                getString(R.string.notif_channel_monitor),
                // IMPORTANCE_LOW: không phát âm thanh, tránh làm phiền khi cắm sạc
                NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription(getString(R.string.notif_channel_monitor_desc));
        monitor.setShowBadge(false);
        manager.createNotificationChannel(monitor);

        manager.deleteNotificationChannel(CHANNEL_ALARM_LEGACY);

        NotificationChannel alarm = new NotificationChannel(
                CHANNEL_ALARM,
                getString(R.string.notif_channel_alarm),
                // IMPORTANCE_HIGH: cảnh báo phải nổi lên giữa màn hình (heads-up),
                // vì mục đích là gọi người dùng tới rút sạc
                NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription(getString(R.string.notif_channel_alarm_desc));
        // Chuông và rung do AlarmPlayer lo, kênh phải im để không kêu hai lần
        alarm.setSound(null, null);
        alarm.enableVibration(false);
        manager.createNotificationChannel(alarm);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        AppExecutors.get().shutdown();
    }
}
