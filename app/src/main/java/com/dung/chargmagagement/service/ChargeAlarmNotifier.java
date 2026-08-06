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
     */
    public void notifyAlarm(@NonNull ChargeAlarmChecker.AlarmType type, int percent) {
        if (type == ChargeAlarmChecker.AlarmType.NONE) return;

        // Từ Android 13, không có quyền POST_NOTIFICATIONS thì thông báo bị chặn im lặng
        NotificationManagerCompat manager = NotificationManagerCompat.from(appContext);
        if (!manager.areNotificationsEnabled()) {
            Logger.d(TAG, "Người dùng đã tắt thông báo, bỏ qua cảnh báo");
            return;
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                appContext, 0,
                appContext.getPackageManager().getLaunchIntentForPackage(
                        appContext.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(appContext, ChargApplication.CHANNEL_ALARM)
                        .setSmallIcon(R.drawable.ic_battery)
                        .setContentTitle(appContext.getString(type.getTitleRes()))
                        .setContentText(appContext.getString(type.getMessageRes(), percent))
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM);

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            // Thiếu quyền thông báo trên Android 13+
            Logger.e(TAG, "Không hiện được cảnh báo", e);
        }
    }

    /** Xoá cảnh báo đang hiện (khi người dùng rút sạc). */
    public void cancel() {
        NotificationManager manager =
                (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }
}
