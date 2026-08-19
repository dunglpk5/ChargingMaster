package com.dung.chargmagagement.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.repository.ChargeSessionLogger;

/**
 * Receiver khai báo trong Manifest, lo việc <b>dựng lại</b> hai service nền ở những
 * thời điểm app không tự làm được.
 *
 * <p>{@link BatteryLogService} chỉ được khởi động chứ không bao giờ bị dừng ở đây –
 * nó phải chạy liên tục nếu người dùng đã bật. Báo động sạc thì gọi
 * {@link ChargeAlarmScheduler#check}: cắm sạc là bắt đầu hẹn giờ, rút sạc mà không có
 * cảnh báo quá nhiệt là huỷ hẹn cho đỡ tốn.
 *
 * <p>Ba mốc dưới đây đều nằm trong danh sách được phép khởi động foreground service
 * từ nền, nên đây là cách đáng tin để service sống lại sau khi khởi động máy hoặc sau
 * khi bị hệ thống thu hồi.
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
                Logger.d(TAG, "Nhận " + action + " -> rà lại ghi pin và báo động sạc");
                BatteryLogService.start(context);
                ChargeAlarmScheduler.check(context);
                logChargeSession(context, action);
                break;

            default:
                break;
        }
    }

    /**
     * Ghi phiên sạc bằng đúng hai mốc cắm/rút, không cần dịch vụ nền.
     *
     * <p>Chỉ làm khi {@link BatteryLogService} đang tắt: lúc nó bật thì
     * {@code SessionRecorder} đã ghi rồi, hai bên cùng ghi sẽ sinh hai bản ghi cho
     * cùng một lần sạc.
     */
    private void logChargeSession(@NonNull Context context, @NonNull String action) {
        if (BatteryLogService.isEnabled(context)) return;

        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            ChargeSessionLogger.onPlugged(context);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            ChargeSessionLogger.onUnplugged(context);
        }
        // BOOT_COMPLETED: không mở/đóng gì. Máy vừa bật thì không có phiên nào đang
        // dở, và nếu người dùng cắm sạc từ trước thì lần rút sắp tới sẽ không có phiên
        // để đóng – chấp nhận mất một phiên hơn là ghi một phiên sai mốc bắt đầu.
    }
}
