package com.dung.chargmagagement.controller.tools;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.dung.chargmagagement.R;
import com.dung.chargmagagement.common.AppExecutors;
import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.controller.alarm.ChargeAlarmActivity;
import com.dung.chargmagagement.controller.detail.PhoneDetailActivity;
import com.dung.chargmagagement.controller.history.ChargeHistoryActivity;
import com.dung.chargmagagement.controller.power.ChargingScreenActivity;
import com.dung.chargmagagement.controller.power.CheckPowerActivity;
import com.dung.chargmagagement.controller.power.XChargeActivity;
import com.dung.chargmagagement.controller.vip.VipActivity;
import com.dung.chargmagagement.model.ui.ToolItem;

import java.io.File;

/**
 * Nơi duy nhất biết bấm vào một công cụ thì mở cái gì.
 *
 * <p>Tách khỏi màn hình vì cùng một danh sách công cụ xuất hiện ở hai chỗ: lưới rút gọn
 * trên tab Công cụ và màn "Tất cả chức năng". Để mỗi màn tự xử lý là hai bên có ngày
 * hành xử khác nhau cho cùng một biểu tượng.
 */
public final class ToolLauncher {

    private static final String TAG = "ToolLauncher";

    private ToolLauncher() {
    }

    public static void launch(@NonNull Context context, @NonNull ToolItem.Action action) {
        switch (action) {
            case DEVICE_INFO:
                PhoneDetailActivity.start(context);
                break;

            case CHARGE_DETECT:
                CheckPowerActivity.start(context);
                break;

            case CHARGE_HISTORY:
                ChargeHistoryActivity.start(context);
                break;

            case CHARGE_ALARM:
                ChargeAlarmActivity.start(context);
                break;

            case PHONE_TEMPERATURE:
                PhoneTemperatureActivity.start(context);
                break;

            case CPU_USAGE:
                CpuUsageActivity.start(context);
                break;

            case CLEAN_NOTIFICATION:
                NotificationCleanActivity.start(context);
                break;

            case CLEAN_CLIPBOARD:
                ClipboardCleanActivity.start(context);
                break;

            case MORE:
                AllToolsActivity.start(context);
                break;

            case NO_ADS:
            case PRIORITY_SUPPORT:
                VipActivity.start(context);
                break;

            case X_CHARGE:
                XChargeActivity.start(context);
                break;

            case MANAGE_APPS:
                openAppManagement(context);
                break;

            case SHORTCUT:
                pinChargingScreenShortcut(context);
                break;

            case CLEAR_CACHE:
                confirmClearCache(context);
                break;

            default:
                Toast.makeText(context, R.string.msg_coming_soon, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    /** Mở màn Quản lý ứng dụng của hệ thống, nơi liệt kê mọi app đã cài. */
    private static void openAppManagement(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            Toast.makeText(context, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.e(TAG, "Không mở được trang cài đặt", e);
            Toast.makeText(context, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Ghim một biểu tượng ra màn hình chính, bấm vào là mở màn sạc phủ toàn màn hình.
     *
     * <p>Dùng {@link ShortcutManagerCompat} thay vì gọi thẳng API: lớp compat tự lo
     * phần khác biệt giữa các phiên bản Android, kể cả launcher không hỗ trợ ghim (khi
     * đó {@code isRequestPinShortcutSupported} trả về false và ta báo cho người dùng
     * biết thay vì im lặng không làm gì).
     */
    private static void pinChargingScreenShortcut(@NonNull Context context) {
        final Resources res = context.getResources();
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.check_settings_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent launchIntent = new Intent(context, ChargingScreenActivity.class);
        // Lối tắt trên màn hình chính khởi chạy từ launcher nên phải tự mở task mới
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ShortcutInfoCompat shortcut =
                new ShortcutInfoCompat.Builder(context, "charging_screen")
                        .setShortLabel(res.getString(R.string.tools_shortcut))
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_heart))
                        .setIntent(launchIntent)
                        .build();

        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null);
    }

    /** Hỏi trước khi xoá: dữ liệu cache không quan trọng nhưng vẫn nên xin phép. */
    private static void confirmClearCache(@NonNull Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.tools_clear_cache)
                .setMessage(R.string.cache_clear_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> clearCache(context))
                .show();
    }

    /**
     * Xoá thư mục cache của riêng app.
     *
     * <p>Chỉ xoá {@code cacheDir} của chính mình – đây là toàn bộ những gì một app
     * thường được phép đụng vào; không có API công khai nào cho phép app dọn cache của
     * ứng dụng khác.
     */
    private static void clearCache(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        AppExecutors.get().execute(
                () -> deleteRecursively(appContext.getCacheDir()),
                success -> Toast.makeText(appContext, R.string.cache_cleared,
                        Toast.LENGTH_SHORT).show());
    }

    private static boolean deleteRecursively(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        return file.delete();
    }
}
