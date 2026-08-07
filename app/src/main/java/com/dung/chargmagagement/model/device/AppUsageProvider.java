package com.dung.chargmagagement.model.device;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Xếp hạng ứng dụng theo thời gian chạy tiền cảnh, dùng làm mốc ước tính mức tiêu
 * pin của từng ứng dụng.
 *
 * <p>Cần quyền đặc biệt {@code PACKAGE_USAGE_STATS}: người dùng phải tự bật trong
 * Cài đặt, không xin được bằng hộp thoại runtime. Xem {@link AppUsageItem} để biết
 * vì sao đây là ước tính chứ không phải số đo.
 */
public final class AppUsageProvider {

    private static final String TAG = "AppUsageProvider";

    /** Số ứng dụng nhiều nhất đưa lên danh sách. */
    public static final int MAX_ITEMS = 10;

    /** App dùng dưới một phút trong cả kỳ là nhiễu, không đáng hiển thị. */
    private static final long MIN_FOREGROUND_MS = 60_000L;

    private final Context appContext;

    public AppUsageProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Người dùng đã cấp quyền truy cập thông tin sử dụng hay chưa. */
    public boolean hasPermission() {
        AppOpsManager appOps = (AppOpsManager) appContext.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;

        // unsafeCheckOpNoThrow chỉ có từ API 29; bản cũ dùng checkOpNoThrow, hai
        // hàm này giống hệt nhau, chỉ đổi tên cho rõ nghĩa
        final int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.getPackageName());
        } else {
            mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.getPackageName());
        }

        // MODE_DEFAULT nghĩa là "theo quyền khai báo" – phải kiểm tra thêm
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return appContext.checkCallingOrSelfPermission(
                    "android.permission.PACKAGE_USAGE_STATS") == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Intent mở trang cấp quyền truy cập thông tin sử dụng. */
    @NonNull
    public static Intent buildPermissionIntent() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    /**
     * Danh sách ứng dụng dùng nhiều nhất trong khoảng thời gian cho trước.
     *
     * @param fromTime mốc bắt đầu (ms)
     * @return danh sách đã sắp xếp giảm dần, rỗng nếu chưa có quyền hoặc không có dữ liệu
     */
    @WorkerThread
    @NonNull
    public List<AppUsageItem> loadTopApps(long fromTime) {
        if (!hasPermission()) return new ArrayList<>();

        UsageStatsManager manager =
                (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return new ArrayList<>();

        final Map<String, UsageStats> stats;
        try {
            stats = manager.queryAndAggregateUsageStats(fromTime, System.currentTimeMillis());
        } catch (Exception e) {
            Logger.e(TAG, "Không đọc được thông tin sử dụng", e);
            return new ArrayList<>();
        }
        if (stats == null || stats.isEmpty()) return new ArrayList<>();

        return buildRanking(stats);
    }

    @WorkerThread
    @NonNull
    private List<AppUsageItem> buildRanking(@NonNull Map<String, UsageStats> stats) {
        final PackageManager packages = appContext.getPackageManager();

        // Lượt một: lọc và cộng tổng để tính được tỉ lệ phần trăm
        List<UsageStats> usable = new ArrayList<>();
        long totalMs = 0L;
        for (UsageStats entry : stats.values()) {
            final long foreground = entry.getTotalTimeInForeground();
            if (foreground < MIN_FOREGROUND_MS) continue;
            if (isSystemPackage(packages, entry.getPackageName())) continue;

            usable.add(entry);
            totalMs += foreground;
        }
        if (totalMs <= 0L) return new ArrayList<>();

        Collections.sort(usable, (a, b) ->
                Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        // Lượt hai: chỉ nạp nhãn và biểu tượng cho những app thật sự hiển thị –
        // đây là thao tác nặng nhất, làm cho cả trăm app là phí vô ích
        final int limit = Math.min(MAX_ITEMS, usable.size());
        List<AppUsageItem> result = new ArrayList<>(limit);

        for (int i = 0; i < limit; i++) {
            final UsageStats entry = usable.get(i);
            final long foreground = entry.getTotalTimeInForeground();
            result.add(new AppUsageItem(
                    entry.getPackageName(),
                    resolveLabel(packages, entry.getPackageName()),
                    resolveIcon(packages, entry.getPackageName()),
                    foreground,
                    foreground * 100f / totalMs));
        }
        return result;
    }

    /**
     * Bỏ qua ứng dụng hệ thống không có giao diện khởi chạy.
     *
     * <p>Giữ lại app hệ thống <i>có</i> icon trên màn hình chính (Chrome, Gmail…)
     * vì người dùng thật sự dùng chúng và chúng thật sự tốn pin.
     */
    private boolean isSystemPackage(@NonNull PackageManager packages, @NonNull String pkg) {
        if (pkg.equals(appContext.getPackageName())) return false;

        try {
            ApplicationInfo info = packages.getApplicationInfo(pkg, 0);
            final boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!system) return false;
            return packages.getLaunchIntentForPackage(pkg) == null;
        } catch (Exception e) {
            // Gói đã bị gỡ giữa chừng
            return true;
        }
    }

    @NonNull
    private String resolveLabel(@NonNull PackageManager packages, @NonNull String pkg) {
        try {
            return packages.getApplicationLabel(packages.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private android.graphics.drawable.Drawable resolveIcon(@NonNull PackageManager packages,
                                                           @NonNull String pkg) {
        try {
            return packages.getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }
}
