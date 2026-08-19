package com.dung.chargmagagement.model.battery;

import android.content.Context;
import android.os.BatteryManager;

import androidx.annotation.NonNull;

import com.dung.chargmagagement.common.Logger;

/**
 * Đọc bộ đếm cu-lông của phần cứng — tổng điện tích đã đi qua pin, tính bằng µAh.
 *
 * <p><b>Vì sao cần:</b> cách cũ ước tính lượng điện đã nạp bằng cách nhân dòng trung
 * bình với thời lượng phiên, nên sai số của từng mẫu cộng dồn suốt mấy tiếng sạc, và
 * phải lấy mẫu dày mới có con số tử tế. Bộ đếm này do chính chip pin giữ, nên chỉ cần
 * <b>hai lần đọc</b> — lúc cắm và lúc rút — là ra lượng nạp thật của cả phiên. Nhờ vậy
 * việc ước tính dung lượng pin không còn phụ thuộc vào dịch vụ nền nào.
 *
 * <p>Không phải máy nào cũng hỗ trợ: nhiều máy trả về 0 hoặc {@code Integer.MIN_VALUE}.
 * Những trường hợp đó trả về {@link #UNKNOWN} để tầng trên quay lại cách tính cũ.
 */
public final class ChargeCounter {

    private static final String TAG = "ChargeCounter";

    /** Máy không hỗ trợ hoặc chưa đọc được. */
    public static final long UNKNOWN = 0L;

    private ChargeCounter() {
    }

    /** Điện tích tích luỹ hiện tại (µAh), hoặc {@link #UNKNOWN}. */
    public static long readUah(@NonNull Context context) {
        BatteryManager manager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (manager == null) return UNKNOWN;

        try {
            final long value = manager.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            // Máy không hỗ trợ thường trả về Long.MIN_VALUE hoặc 0
            return value > 0L ? value : UNKNOWN;
        } catch (Exception e) {
            Logger.e(TAG, "Không đọc được bộ đếm cu-lông", e);
            return UNKNOWN;
        }
    }
}
