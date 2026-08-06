package com.dung.chargmagagement.model.device;

/**
 * Dung lượng đã dùng / tổng dung lượng của một tài nguyên (bộ nhớ trong hoặc RAM).
 */
public final class StorageInfo {

    public static final StorageInfo EMPTY = new StorageInfo(0L, 0L);

    private final long usedBytes;
    private final long totalBytes;

    public StorageInfo(long usedBytes, long totalBytes) {
        this.usedBytes = usedBytes;
        this.totalBytes = totalBytes;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getFreeBytes() {
        return Math.max(0L, totalBytes - usedBytes);
    }

    /** Tỉ lệ đã dùng (0..100) – con số lớn hiển thị ở header tab Công cụ. */
    public int getUsedPercent() {
        if (totalBytes <= 0L) return 0;
        return (int) Math.min(100, Math.round(usedBytes * 100d / totalBytes));
    }

    public boolean hasData() {
        return totalBytes > 0L;
    }
}
