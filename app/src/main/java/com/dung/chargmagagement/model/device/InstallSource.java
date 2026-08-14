package com.dung.chargmagagement.model.device;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Xác định ứng dụng được cài từ đâu.
 *
 * <p>Dùng để biết máy có đang chặn "Cài đặt hạn chế" hay không. Từ Android 13,
 * quyền đọc thông báo và quyền trợ năng bị khoá với ứng dụng cài từ file APK –
 * đó chính là hai quyền phần mềm độc hại thèm muốn nhất. Ứng dụng tải từ cửa hàng
 * không bị khoá vì cửa hàng cài bằng session-based installer.
 */
public final class InstallSource {

    /** Các trình cài đặt được coi là cửa hàng chính thức. */
    private static final List<String> STORE_PACKAGES = Arrays.asList(
            "com.android.vending",              // Google Play
            "com.google.android.feedback",      // Play (bản cũ)
            "com.sec.android.app.samsungapps",  // Galaxy Store
            "com.huawei.appmarket",             // AppGallery
            "com.amazon.venezia",               // Amazon Appstore
            "com.xiaomi.market",                // GetApps
            "com.heytap.market",                // Oppo/Realme
            "com.vivo.appstore"
    );

    private InstallSource() {
    }

    /**
     * Máy này nhiều khả năng đang chặn quyền bằng "Cài đặt hạn chế".
     *
     * <p>Không có API nào hỏi thẳng được điều đó, nên suy ra từ hai điều kiện tạo
     * ra nó: Android 13 trở lên và ứng dụng không đến từ cửa hàng.
     */
    public static boolean isRestrictedSettingLikely(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false;
        return !isFromStore(context);
    }

    /** Ứng dụng có được cài từ một cửa hàng chính thức hay không. */
    public static boolean isFromStore(@NonNull Context context) {
        final String installer = getInstallerPackage(context);
        return installer != null && STORE_PACKAGES.contains(installer);
    }

    @Nullable
    private static String getInstallerPackage(@NonNull Context context) {
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return pm.getInstallSourceInfo(packageName).getInstallingPackageName();
            }
            return pm.getInstallerPackageName(packageName);
        } catch (Exception e) {
            // Không đọc được nguồn cài thì coi như không phải cửa hàng: thà hiện
            // thừa một dòng hướng dẫn còn hơn để người dùng bế tắc không hiểu vì sao
            return null;
        }
    }
}
