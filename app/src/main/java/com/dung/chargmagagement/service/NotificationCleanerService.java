package com.dung.chargmagagement.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.model.ui.NotificationItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /** Màn hình đang mở lắng nghe thay đổi để cập nhật danh sách ngay lập tức. */
    public interface Listener {
        void onNotificationsChanged();
    }

    @Nullable
    private static Listener listener;

    /**
     * Đăng ký nhận thông báo thay đổi.
     *
     * <p>Màn hình phải gỡ đăng ký ở {@code onPause()}: dịch vụ này sống lâu hơn
     * Activity rất nhiều, giữ tham chiếu lại là rò rỉ nguyên một màn hình.
     */
    public static void setListener(@Nullable Listener value) {
        listener = value;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        notifyChanged();
        Logger.d(TAG, "Đã kết nối dịch vụ đọc thông báo");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        notifyChanged();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) {
        notifyChanged();
    }

    /** Các callback của lớp này đã chạy sẵn trên main thread nên gọi thẳng được. */
    private static void notifyChanged() {
        final Listener current = listener;
        if (current != null) current.onNotificationsChanged();
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
     * Danh sách thông báo xoá được, mới nhất trước.
     *
     * <p>Trả về danh sách rỗng khi dịch vụ chưa sẵn sàng. Chỉ lấy thông báo
     * {@code isClearable()}: thông báo thường trú (đang phát nhạc, service chạy
     * nền…) không xoá được, liệt kê ra thì người dùng bấm dọn mà chúng vẫn nằm đó.
     */
    @NonNull
    public static List<NotificationItem> listClearable() {
        final NotificationCleanerService service = instance;
        if (service == null) return new ArrayList<>();

        try {
            StatusBarNotification[] notifications = service.getActiveNotifications();
            if (notifications == null) return new ArrayList<>();

            List<NotificationItem> items = new ArrayList<>(notifications.length);
            for (StatusBarNotification notification : notifications) {
                if (!notification.isClearable()) continue;
                items.add(service.toItem(notification));
            }

            Collections.sort(items, (a, b) -> Long.compare(b.postTime, a.postTime));
            return items;
        } catch (Exception e) {
            Logger.e(TAG, "Không đọc được danh sách thông báo", e);
            return new ArrayList<>();
        }
    }

    @NonNull
    private NotificationItem toItem(@NonNull StatusBarNotification notification) {
        final Notification content = notification.getNotification();
        final Bundle extras = content.extras;

        return new NotificationItem(
                notification.getKey(),
                notification.getPackageName(),
                resolveAppName(notification.getPackageName()),
                textOf(extras, Notification.EXTRA_TITLE),
                textOf(extras, Notification.EXTRA_TEXT),
                notification.getPostTime(),
                content.getLargeIcon());
    }

    /** Nhãn ứng dụng; lùi về tên gói nếu ứng dụng vừa bị gỡ. */
    @NonNull
    private String resolveAppName(@NonNull String packageName) {
        try {
            final PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    /** Các trường EXTRA có thể là CharSequence có định dạng, phải đổi sang chuỗi thường. */
    @NonNull
    private static String textOf(@Nullable Bundle extras, @NonNull String key) {
        if (extras == null) return "";

        final CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }

    /**
     * Xoá một thông báo cụ thể.
     *
     * @return true nếu đã thực hiện, false nếu dịch vụ chưa sẵn sàng
     */
    public static boolean cancel(@NonNull String key) {
        final NotificationCleanerService service = instance;
        if (service == null) return false;

        try {
            service.cancelNotification(key);
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Không xoá được thông báo", e);
            return false;
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
