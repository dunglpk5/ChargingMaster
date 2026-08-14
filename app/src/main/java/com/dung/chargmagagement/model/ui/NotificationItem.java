package com.dung.chargmagagement.model.ui;

import android.graphics.drawable.Icon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Một thông báo đang hiển thị, đã bóc ra khỏi {@code StatusBarNotification}.
 *
 * <p>Bóc sẵn ở tầng dịch vụ thay vì truyền nguyên đối tượng hệ thống lên giao diện:
 * {@code StatusBarNotification} chỉ đọc được khi dịch vụ còn kết nối, mà danh sách
 * thì tồn tại lâu hơn thế.
 */
public class NotificationItem {

    public final String key;
    public final String packageName;
    public final String appName;
    public final String title;
    public final String text;
    public final long postTime;

    /** Ảnh lớn của thông báo (ảnh đại diện người gửi); null thì dùng icon ứng dụng. */
    @Nullable
    public final Icon largeIcon;

    public NotificationItem(@NonNull String key, @NonNull String packageName,
                           @NonNull String appName, @NonNull String title,
                           @NonNull String text, long postTime, @Nullable Icon largeIcon) {
        this.key = key;
        this.packageName = packageName;
        this.appName = appName;
        this.title = title;
        this.text = text;
        this.postTime = postTime;
        this.largeIcon = largeIcon;
    }
}
