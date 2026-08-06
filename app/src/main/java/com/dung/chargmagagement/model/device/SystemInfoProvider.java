package com.dung.chargmagagement.model.device;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.Logger;

/**
 * Đọc các chỉ số hệ thống hiển thị ở header tab "Công cụ": bộ nhớ trong và RAM.
 *
 * <p>Gọi trên thread nền: {@link StatFs} phải chạm vào hệ thống tập tin nên có thể
 * chậm vài chục mili giây trên máy yếu, đủ để gây giật khung hình nếu chạy ở UI.
 */
public final class SystemInfoProvider {

    private static final String TAG = "SystemInfoProvider";

    private final Context appContext;

    public SystemInfoProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Bộ nhớ trong của thiết bị.
     *
     * <p>Dùng phân vùng dữ liệu ({@code getDataDirectory}) chứ không phải thư mục
     * riêng của app: người dùng quan tâm tổng dung lượng máy, giống con số
     * "96.7GB/109.5GB" trong bản thiết kế.
     */
    @WorkerThread
    @NonNull
    public StorageInfo getStorageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            final long blockSize = stat.getBlockSizeLong();
            final long total = stat.getBlockCountLong() * blockSize;
            final long free = stat.getAvailableBlocksLong() * blockSize;
            return new StorageInfo(total - free, total);
        } catch (Exception e) {
            Logger.e(TAG, "Không đọc được dung lượng bộ nhớ", e);
            return StorageInfo.EMPTY;
        }
    }

    /** RAM đang dùng / tổng RAM. */
    @WorkerThread
    @NonNull
    public StorageInfo getRamInfo() {
        ActivityManager manager =
                (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return StorageInfo.EMPTY;

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memoryInfo);
        return new StorageInfo(memoryInfo.totalMem - memoryInfo.availMem, memoryInfo.totalMem);
    }
}
