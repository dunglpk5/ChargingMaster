package com.dung.chargmagagement.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dung.chargmagagement.common.Logger;

/**
 * Nơi nhận cái hẹn giờ của báo động sạc.
 *
 * <p>Toàn bộ việc đọc pin, so ngưỡng và hẹn lần kế tiếp nằm ở
 * {@link ChargeAlarmScheduler}; receiver này chỉ là điểm hệ thống gọi vào. Công việc
 * chỉ gồm đọc SharedPreferences và một sticky intent nên đủ nhanh để làm thẳng trong
 * {@code onReceive}, không cần {@code goAsync}.
 */
public class ChargeAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ChargeAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ChargeAlarmScheduler.ACTION_CHECK.equals(intent.getAction())) return;

        Logger.d(TAG, "Tới hẹn kiểm tra báo động sạc");
        ChargeAlarmScheduler.check(context);
    }
}
