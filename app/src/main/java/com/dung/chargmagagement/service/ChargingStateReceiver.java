package com.dung.chargmagagement.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dung.chargmagagement.common.Logger;

/**
 * Receiver khai báo trong Manifest: bật/tắt {@link ChargingMonitorService} theo
 * trạng thái nguồn, kể cả khi app đã bị đóng.
 *
 * <p>{@code ACTION_POWER_CONNECTED} và {@code ACTION_POWER_DISCONNECTED} vẫn được
 * gửi tới manifest receiver trên Android 8+ (nằm trong danh sách miễn trừ giới hạn
 * broadcast ngầm), nên cách này hoạt động ổn định.
 */
public class ChargingStateReceiver extends BroadcastReceiver {

    private static final String TAG = "ChargingStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent == null ? null : intent.getAction();
        if (action == null) return;

        switch (action) {
            case Intent.ACTION_POWER_CONNECTED:
                Logger.d(TAG, "Cắm sạc -> bật service theo dõi");
                ChargingMonitorService.start(context);
                break;

            case Intent.ACTION_POWER_DISCONNECTED:
                Logger.d(TAG, "Rút sạc -> tắt service theo dõi");
                ChargingMonitorService.stop(context);
                break;

            case Intent.ACTION_BOOT_COMPLETED:
                // Sau khi khởi động lại máy, nếu đang cắm sạc thì tiếp tục theo dõi
                if (isPluggedNow(context)) {
                    ChargingMonitorService.start(context);
                }
                break;

            default:
                break;
        }
    }

    /** Kiểm tra nhanh trạng thái cắm nguồn qua sticky intent. */
    private boolean isPluggedNow(Context context) {
        Intent status = context.registerReceiver(null,
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) return false;
        return status.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }
}
