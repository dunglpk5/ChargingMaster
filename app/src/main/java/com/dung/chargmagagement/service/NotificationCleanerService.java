package com.dung.chargmagagement.service;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.Logger;

/**
 * Dịch vụ đọc và xoá thông báo trên thanh trạng thái.
 *
 * <p>Android chỉ cho phép thao tác với thông báo qua {@link NotificationListenerService},
 * và người dùng phải tự cấp quyền trong Cài đặt (không có hộp thoại xin quyền
 * thông thường). Vì vậy màn hình gọi tới đây luôn phải kiểm tra
 * {@link #isEnabled(Context)} trước.
 *
 * <p>Hệ thống tự tạo và huỷ dịch vụ này, ta chỉ giữ một tham chiếu tĩnh để màn
 * hình gọi được các thao tác. Tham chiếu được xoá ở {@code onDestroy()} nên không
 * gây rò rỉ bộ nhớ.
 */
public class NotificationCleanerService extends NotificationListenerService {

    private static final String TAG = "NotificationCleaner";

    @Nullable
    private static NotificationCleanerService instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        Logger.d(TAG, "Đã kết nối dịch vụ đọc thông báo");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        instance = null;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /** Người dùng đã cấp quyền đọc thông báo cho app chưa. */
    public static boolean isEnabled(@NonNull Context context) {
        final String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;

        final ComponentName component =
                new ComponentName(context, NotificationCleanerService.class);
        return enabled.contains(component.flattenToString())
                || enabled.contains(component.flattenToShortString());
    }

    /**
     * Số thông báo đang hiển thị có thể xoá được.
     *
     * @return -1 nếu dịch vụ chưa sẵn sàng (chưa cấp quyền hoặc hệ thống chưa kết nối)
     */
    public static int countClearable() {
        final NotificationCleanerService service = instance;
        if (service == null) return -1;

        try {
            StatusBarNotification[] notifications = service.getActiveNotifications();
            if (notifications == null) return 0;

            int count = 0;
            for (StatusBarNotification notification : notifications) {
                // Thông báo thường trú (đang phát nhạc, service chạy nền…) không
                // xoá được; đếm cả chúng vào sẽ khiến người dùng bấm dọn mà số
                // không giảm về 0
                if (notification.isClearable()) count++;
            }
            return count;
        } catch (Exception e) {
            Logger.e(TAG, "Không đọc được danh sách thông báo", e);
            return -1;
        }
    }

    /**
     * Xoá toàn bộ thông báo xoá được.
     *
     * @return true nếu đã thực hiện, false nếu dịch vụ chưa sẵn sàng
     */
    public static boolean clearAll() {
        final NotificationCleanerService service = instance;
        if (service == null) return false;

        try {
            service.cancelAllNotifications();
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Không xoá được thông báo", e);
            return false;
        }
    }
}
