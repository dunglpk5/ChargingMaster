package com.dung.chargmagagement.model.battery;

import android.content.Context;
import android.os.BatteryManager;

import androidx.annotation.NonNull;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.common.PrefManager;

/**
 * Lấy dung lượng thiết kế của pin (mAh) – con số "6000 mAh" hiển thị ở màn Trang chủ.
 *
 * <p>Android không có API công khai cho việc này. Thứ tự thử:
 * <ol>
 *     <li>Giá trị người dùng tự nhập (màn "Đặt dung lượng thiết kế").</li>
 *     <li>Reflection vào {@code com.android.internal.os.PowerProfile} – cách các app
 *         cùng loại vẫn dùng, hoạt động trên phần lớn máy.</li>
 *     <li>Suy ra từ {@code BATTERY_PROPERTY_CHARGE_COUNTER} và mức pin hiện tại.</li>
 * </ol>
 * Kết quả được lưu lại để không phải tính lại mỗi lần mở app.
 */
public final class BatteryCapacityProvider {

    private static final String TAG = "CapacityProvider";
    private static final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";
    private static final String KEY_CACHED_CAPACITY = "cached_design_capacity";

    private final Context appContext;
    private final PrefManager prefs;

    public BatteryCapacityProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = PrefManager.get(context);
    }

    /**
     * @return dung lượng thiết kế (mAh) hoặc {@link BatteryInfo#UNKNOWN_INT} nếu không xác định được
     */
    public int getDesignCapacityMah() {
        // 1. Ưu tiên tuyệt đối giá trị người dùng nhập
        int userValue = prefs.getInt(PrefManager.KEY_DESIGN_CAPACITY, BatteryInfo.UNKNOWN_INT);
        if (userValue > 0) return userValue;

        // 2. Giá trị đã tính ở lần chạy trước
        int cached = prefs.getInt(KEY_CACHED_CAPACITY, BatteryInfo.UNKNOWN_INT);
        if (cached > 0) return cached;

        int resolved = readFromPowerProfile();
        if (resolved <= 0) {
            resolved = estimateFromChargeCounter();
        }

        if (resolved > 0) {
            prefs.putInt(KEY_CACHED_CAPACITY, resolved);
            return resolved;
        }
        return BatteryInfo.UNKNOWN_INT;
    }

    /** Người dùng tự đặt lại dung lượng thiết kế từ màn "Sử dụng pin". */
    public void setUserDesignCapacity(int mah) {
        prefs.putInt(PrefManager.KEY_DESIGN_CAPACITY, mah);
    }

    /** Đọc qua reflection PowerProfile – API nội bộ nên bọc try/catch cẩn thận. */
    private int readFromPowerProfile() {
        try {
            Class<?> profileClass = Class.forName(POWER_PROFILE_CLASS);
            Object profile = profileClass
                    .getConstructor(Context.class)
                    .newInstance(appContext);
            double capacity = (Double) profileClass
                    .getMethod("getBatteryCapacity")
                    .invoke(profile);
            return (int) Math.round(capacity);
        } catch (Throwable t) {
            // Android 14+ siết truy cập API ẩn, thất bại là bình thường
            Logger.d(TAG, "Không đọc được PowerProfile: " + t.getClass().getSimpleName());
            return BatteryInfo.UNKNOWN_INT;
        }
    }

    /**
     * Suy ra từ điện tích còn lại: capacity = charge_counter(µAh) / (mức pin %).
     * Kém chính xác hơn PowerProfile nhưng dùng được khi reflection bị chặn.
     */
    private int estimateFromChargeCounter() {
        BatteryManager manager = (BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
        if (manager == null) return BatteryInfo.UNKNOWN_INT;

        int chargeCounterUah = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        int percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        if (chargeCounterUah <= 0 || percent <= 0 || percent > 100) {
            return BatteryInfo.UNKNOWN_INT;
        }
        // µAh -> mAh rồi quy về 100%
        int estimated = Math.round(chargeCounterUah / 1000f * 100f / percent);
        return estimated > 0 ? estimated : BatteryInfo.UNKNOWN_INT;
    }
}
