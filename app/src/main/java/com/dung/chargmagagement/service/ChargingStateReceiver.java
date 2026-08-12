package com.dung.chargmagagement.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dung.chargmagagement.common.Logger;

/**
 * Receiver khai báo trong Manifest, lo việc <b>dựng lại</b> {@link BatteryLogService}
 * ở những thời điểm app không tự làm được.
 *
 * <p>Service ghi pin phải chạy liên tục nên receiver này chỉ khởi động chứ không bao
 * giờ dừng nó. Ba mốc dưới đây đều nằm trong danh sách được phép khởi động foreground
 * service từ nền, nên đây là cách đáng tin để service sống lại sau khi khởi động máy
 * hoặc sau khi bị hệ thống thu hồi.
 */
public class ChargingStateReceiver extends BroadcastReceiver {

    private static final String TAG = "ChargingStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent == null ? null : intent.getAction();
        if (action == null) return;

        switch (action) {
            case Intent.ACTION_POWER_CONNECTED:
            case Intent.ACTION_POWER_DISCONNECTED:
            case Intent.ACTION_BOOT_COMPLETED:
                Logger.d(TAG, "Nhận " + action + " -> đảm bảo service ghi pin đang chạy");
                BatteryLogService.start(context);
                break;

            default:
                break;
        }
    }
}
