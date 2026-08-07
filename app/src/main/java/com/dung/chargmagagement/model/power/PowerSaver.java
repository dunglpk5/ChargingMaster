package com.dung.chargmagagement.model.power;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.Logger;

/**
 * Tắt tạm các thứ tốn điện trong lúc sạc, và trả lại nguyên trạng khi xong.
 *
 * <p><b>Phạm vi làm được:</b> Android chỉ cho ứng dụng thường can thiệp vào hai
 * thứ trong danh sách tiêu điện:
 * <ul>
 *     <li><b>Đồng bộ tự động</b> – tắt được bằng quyền {@code WRITE_SYNC_SETTINGS}
 *         (quyền thường, cấp sẵn khi cài).</li>
 *     <li><b>Độ sáng hệ thống</b> – cần quyền đặc biệt {@code WRITE_SETTINGS} do
 *         người dùng tự bật trong Cài đặt.</li>
 * </ul>
 * Wi-Fi, Bluetooth và định vị <b>không thể tắt bằng mã</b> từ Android 10 trở đi:
 * các hàm tương ứng đã bị vô hiệu hoá, gọi vào cũng không có tác dụng. Với ba mục
 * đó app chỉ có thể liệt kê để người dùng tự tắt.
 *
 * <p><b>Nguyên tắc quan trọng:</b> luôn chụp lại trạng thái cũ <i>trước khi</i> đổi,
 * và chỉ khôi phục đúng những gì mình đã đổi. Người dùng cho phép app tắt tạm để
 * sạc nhanh, không phải cho phép app định đoạt cấu hình máy của họ.
 */
public final class PowerSaver {

    private static final String TAG = "PowerSaver";

    /** Độ sáng tối thiểu trên thang 0–255; để 0 hẳn thì màn hình tối đen. */
    private static final int MIN_BRIGHTNESS = 10;

    private PowerSaver() {
    }

    /**
     * Trạng thái trước khi tối ưu, dùng để khôi phục.
     *
     * <p>Các cờ {@code *Changed} cho biết mục nào thật sự đã bị đổi – mục không
     * đổi thì khi khôi phục cũng không đụng tới.
     */
    public static final class SavedState {

        private boolean autoSyncWasOn;
        private boolean autoSyncChanged;

        private int previousBrightness;
        private int previousBrightnessMode;
        private boolean brightnessChanged;

        public boolean isAutoSyncChanged() {
            return autoSyncChanged;
        }

        public boolean isBrightnessChanged() {
            return brightnessChanged;
        }

        /** Có tối ưu được gì không. */
        public boolean hasAnyChange() {
            return autoSyncChanged || brightnessChanged;
        }
    }

    /** App có quyền đổi độ sáng hệ thống hay chưa. */
    public static boolean canWriteSystemSettings(@NonNull Context context) {
        return Settings.System.canWrite(context);
    }

    /**
     * Áp dụng tối ưu và trả về trạng thái cũ.
     * Gọi trên UI thread cũng được: các thao tác đều rất nhanh.
     */
    @NonNull
    public static SavedState apply(@NonNull Context context) {
        SavedState state = new SavedState();

        disableAutoSync(state);
        lowerSystemBrightness(context, state);

        return state;
    }

    private static void disableAutoSync(@NonNull SavedState state) {
        try {
            state.autoSyncWasOn = ContentResolver.getMasterSyncAutomatically();
            if (!state.autoSyncWasOn) return; // vốn đã tắt, không cần đụng vào

            ContentResolver.setMasterSyncAutomatically(false);
            state.autoSyncChanged = true;
        } catch (Exception e) {
            Logger.e(TAG, "Không tắt được đồng bộ tự động", e);
        }
    }

    /**
     * Hạ độ sáng hệ thống xuống mức tối thiểu.
     *
     * <p>Phải chuyển sang chế độ thủ công trước, vì khi máy đang để độ sáng tự
     * động thì cảm biến ánh sáng sẽ ghi đè lại giá trị ta vừa đặt.
     */
    private static void lowerSystemBrightness(@NonNull Context context,
                                              @NonNull SavedState state) {
        if (!canWriteSystemSettings(context)) return;

        try {
            ContentResolver resolver = context.getContentResolver();

            state.previousBrightness = Settings.System.getInt(
                    resolver, Settings.System.SCREEN_BRIGHTNESS, -1);
            state.previousBrightnessMode = Settings.System.getInt(
                    resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

            if (state.previousBrightness < 0) return;

            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MIN_BRIGHTNESS);

            state.brightnessChanged = true;
        } catch (Exception e) {
            Logger.e(TAG, "Không hạ được độ sáng hệ thống", e);
        }
    }

    /** Trả lại nguyên trạng những gì đã đổi. Truyền null thì không làm gì. */
    public static void restore(@NonNull Context context, @Nullable SavedState state) {
        if (state == null) return;

        if (state.autoSyncChanged) {
            try {
                ContentResolver.setMasterSyncAutomatically(state.autoSyncWasOn);
            } catch (Exception e) {
                Logger.e(TAG, "Không bật lại được đồng bộ tự động", e);
            }
            state.autoSyncChanged = false;
        }

        if (state.brightnessChanged && canWriteSystemSettings(context)) {
            try {
                ContentResolver resolver = context.getContentResolver();
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS,
                        state.previousBrightness);
                // Trả lại chế độ tự động sau cùng, nếu trước đó máy đang để tự động
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        state.previousBrightnessMode);
            } catch (Exception e) {
                Logger.e(TAG, "Không khôi phục được độ sáng", e);
            }
            state.brightnessChanged = false;
        }
    }
}
