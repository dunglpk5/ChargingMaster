package com.dung.chargmagagement.service;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Nơi nhận cái hẹn giờ của báo động sạc.
 *
 * <p>Toàn bộ việc đọc pin, so ngưỡng và hẹn lần kế tiếp nằm ở
 * {@link ChargeAlarmScheduler}; receiver này chỉ là điểm hệ thống gọi vào.
 *
 * <p>Nhận thêm {@code ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED}: khi người
 * dùng vừa bật quyền hẹn giờ chính xác trong Cài đặt hệ thống, cái hẹn đang treo vẫn là
 * loại bị dồn – phải đặt lại ngay mới nổ đúng mốc, không đợi tới lúc họ mở app.
 */
public class ChargeAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ChargeAlarmReceiver";

    /**
     * Giữ tiến trình sống thêm chừng này sau khi xử lý xong.
     *
     * <p>{@code onReceive} trả về là app không còn thành phần nào đang chạy, hệ thống
     * được phép thu hồi tiến trình bất cứ lúc nào – và tiếng chuông đang phát sẽ tắt
     * giữa chừng. Thẻ thông báo thì không bị ảnh hưởng vì nó đã nằm ở hệ thống.
     */
    private static final long KEEP_ALIVE_MS = 2_500L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        if (!isHandled(intent.getAction())) return;

        Logger.d(TAG, "Nhận " + intent.getAction() + " -> kiểm tra báo động sạc");

        // goAsync giữ tiến trình sống tới khi finish(); không có nó thì tiếng chuông
        // có thể bị cắt ngay khi onReceive trả về
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();

        try {
            ChargeAlarmScheduler.check(appContext);
        } catch (Exception e) {
            Logger.e(TAG, "Kiểm tra báo động thất bại", e);
        }

        AppExecutors.get().schedule(pending::finish, KEEP_ALIVE_MS, TimeUnit.MILLISECONDS);
    }

    private static boolean isHandled(@NonNull String action) {
        if (ChargeAlarmScheduler.ACTION_CHECK.equals(action)) return true;

        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
                    .equals(action);
    }
}
