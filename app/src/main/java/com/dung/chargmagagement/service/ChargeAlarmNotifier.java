package com.dung.chargmagagement.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.dung.chargmagagement.ChargApplication;
import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.alarm.AlarmRingActivity;
import com.dung.chargmagagement.model.alarm.ChargeAlarmChecker;

/**
 * Hiện thông báo cảnh báo sạc.
 *
 * <p>Tách khỏi service để phần quyết định (khi nào báo) và phần thể hiện (báo
 * bằng cách nào) không dính vào nhau.
 */
public final class ChargeAlarmNotifier {

    private static final String TAG = "ChargeAlarmNotifier";
    private static final int NOTIFICATION_ID = 2001;

    private final Context appContext;

    public ChargeAlarmNotifier(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Phát cảnh báo.
     *
     * @param type    loại cảnh báo
     * @param percent mức pin để ghép vào nội dung
     * @return đã thật sự hiện được thông báo hay chưa. Phía gọi <b>phải</b> xem giá
     *         trị này: nơi quyết định cảnh báo chỉ được đánh dấu "đã báo rồi" khi
     *         người dùng thật sự thấy, nếu không thì cảnh báo đó mất vĩnh viễn.
     */
    public boolean notifyAlarm(@NonNull ChargeAlarmChecker.AlarmType type, int percent) {
        if (type == ChargeAlarmChecker.AlarmType.NONE) return false;

        // Từ Android 13, không có quyền POST_NOTIFICATIONS thì thông báo bị chặn im lặng
        NotificationManagerCompat manager = NotificationManagerCompat.from(appContext);
        if (!manager.areNotificationsEnabled()) {
            Logger.d(TAG, "Người dùng đã tắt thông báo, bỏ qua cảnh báo");
            return false;
        }

        // Bắt đầu kêu chuông ngay, không chờ người dùng chạm vào thông báo
        AlarmPlayer.start(appContext);

        PendingIntent alarmIntent = PendingIntent.getActivity(
                appContext, type.ordinal(),
                AlarmRingActivity.createIntent(appContext, type, percent),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(appContext, ChargApplication.CHANNEL_ALARM)
                        .setSmallIcon(R.drawable.ic_battery)
                        .setContentTitle(appContext.getString(type.getTitleRes()))
                        .setContentText(appContext.getString(type.getMessageRes(), percent))
                        .setContentIntent(alarmIntent)
                        // Không dùng setFullScreenIntent nữa: khi có quyền, hệ thống
                        // mở thẳng màn báo động và *thay thế* thẻ thông báo, nên
                        // người dùng chỉ nghe chuông mà không hề thấy thông báo nào.
                        // Thẻ thông báo nổi (heads-up) dễ chịu hơn và vẫn nằm lại ở
                        // thanh thông báo để xem lại sau.
                        .setAutoCancel(true)
                        // Không setOngoing: người dùng phải vuốt bỏ được thông báo
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setDefaults(0)
                        // REMINDER chứ không phải ALARM: CATEGORY_ALARM cho phép
                        // thông báo xuyên qua chế độ Không làm phiền, mà đây chỉ là
                        // lời nhắc rút sạc – không đáng đánh thức người dùng
                        .setCategory(NotificationCompat.CATEGORY_REMINDER);

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
            return true;
        } catch (SecurityException e) {
            // Thiếu quyền thông báo trên Android 13+
            Logger.e(TAG, "Không hiện được cảnh báo", e);
            return false;
        }
    }

    /** Xoá cảnh báo đang hiện và tắt chuông (khi người dùng rút sạc hoặc bấm tắt). */
    public void cancel() {
        AlarmPlayer.stop();

        NotificationManager manager =
                (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }
}
