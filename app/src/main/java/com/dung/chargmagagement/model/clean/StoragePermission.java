package com.dung.chargmagagement.model.clean;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Quyền truy cập toàn bộ bộ nhớ, thứ bắt buộc phải có mới quét và xoá được tệp rác.
 *
 * <p>Android 11 đổi hẳn cách cấp quyền này: {@code READ_EXTERNAL_STORAGE} không
 * còn cho thấy tệp của ứng dụng khác, muốn duyệt cả bộ nhớ phải xin
 * {@code MANAGE_EXTERNAL_STORAGE} – một quyền đặc biệt, người dùng phải tự bật
 * trong màn Cài đặt chứ không có hộp thoại xin quyền như thường lệ.
 */
public final class StoragePermission {

    private StoragePermission() {
    }

    /**
     * Bộ quyền cần xin trên máy Android 10 trở xuống.
     *
     * <p>Phải có cả quyền ghi chứ không riêng quyền đọc: quét thì đọc là đủ,
     * nhưng xoá tệp thì không.
     */
    @NonNull
    public static String[] legacyPermissions() {
        return new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
    }

    /** Đang có đủ quyền duyệt và xoá tệp hay chưa. */
    public static boolean hasAccess(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        for (String permission : legacyPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Từ Android 11 phải mở màn Cài đặt, không xin được bằng hộp thoại. */
    public static boolean needsSettingsScreen() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * Intent mở màn Cài đặt để bật quyền.
     *
     * <p>Ưu tiên bản có kèm tên gói vì nó nhảy thẳng vào mục của ứng dụng này.
     * Một số ROM không xử lý được intent đó nên vẫn phải chuẩn bị bản chung.
     */
    @NonNull
    public static Intent buildSettingsIntent(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        return intent;
    }

    /** Bản dự phòng khi máy không mở được intent kèm tên gói. */
    @NonNull
    public static Intent buildFallbackSettingsIntent() {
        return new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
    }
}
