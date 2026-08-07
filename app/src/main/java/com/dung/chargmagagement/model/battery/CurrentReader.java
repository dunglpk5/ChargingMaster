package com.dung.chargmagagement.model.battery;

import android.content.Context;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.dung.chargmagagement.common.Logger;
import com.dung.chargmagagement.common.PrefManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Đọc dòng điện tức thời của pin.
 *
 * <p>Chiến lược 2 tầng:
 * <ol>
 *     <li>{@link BatteryManager#BATTERY_PROPERTY_CURRENT_NOW} – API chính thức,
 *         có mặt từ API 21 nhưng một số máy trả về 0 hoặc {@code Integer.MIN_VALUE}.</li>
 *     <li>Đọc trực tiếp node sysfs – nhiều ROM Trung Quốc chỉ báo đúng ở đây.
 *         Việc dò đường dẫn chỉ làm một lần rồi nhớ lại.</li>
 * </ol>
 * Giá trị thô sau đó đi qua {@link CurrentCalibration} để quy về mA có dấu chuẩn.
 *
 * <p>Luôn gọi trên thread nền vì có đọc file.
 */
public final class CurrentReader {

    private static final String TAG = "CurrentReader";

    /** Các node sysfs thường gặp, xếp theo mức độ phổ biến. */
    private static final String[] SYSFS_CANDIDATES = {
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/Battery/current_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/battery/batt_current_now",
            "/sys/class/power_supply/battery/BatteryAverageCurrent",
            "/sys/class/power_supply/battery/batt_current",
            "/sys/class/power_supply/battery/batt_chg_current",
    };

    /** Chỉ dò đường dẫn sysfs một lần cho cả vòng đời tiến trình. */
    private static volatile String cachedSysfsPath;
    private static volatile boolean sysfsProbed;

    private final BatteryManager batteryManager;
    private final PrefManager prefs;

    public CurrentReader(@NonNull Context context) {
        this.batteryManager = (BatteryManager) context.getApplicationContext()
                .getSystemService(Context.BATTERY_SERVICE);
        this.prefs = PrefManager.get(context);
    }

    /**
     * Đọc dòng điện đã hiệu chỉnh.
     *
     * @param charging trạng thái sạc lấy từ ACTION_BATTERY_CHANGED, dùng để học dấu
     * @return dòng điện mA (dương = nạp) hoặc {@link BatteryInfo#UNKNOWN_INT}
     */
    @WorkerThread
    public int readMilliAmp(boolean charging) {
        final int raw = readRaw();
        if (raw == BatteryInfo.UNKNOWN_INT) return BatteryInfo.UNKNOWN_INT;

        calibrateIfNeeded(raw);

        final int divider = prefs.getInt(PrefManager.KEY_CURRENT_UNIT_DIVIDER,
                CurrentCalibration.DIVIDER_UNKNOWN);

        // Chiều lấy từ trạng thái sạc của hệ thống, không lấy từ dấu của giá trị
        // thô: xem giải thích ở CurrentCalibration#normalizeWithKnownState.
        return CurrentCalibration.normalizeWithKnownState(raw, divider, charging);
    }

    /**
     * Học hệ số đơn vị, chỉ một lần rồi lưu lại.
     *
     * <p>Không còn học quy ước dấu: chiều nạp/xả nay lấy thẳng từ trạng thái sạc
     * của hệ thống, vốn đáng tin hơn hẳn dấu do driver pin báo về.
     */
    private void calibrateIfNeeded(int raw) {
        if (prefs.getInt(PrefManager.KEY_CURRENT_UNIT_DIVIDER,
                CurrentCalibration.DIVIDER_UNKNOWN) == CurrentCalibration.DIVIDER_UNKNOWN) {
            int divider = CurrentCalibration.detectDivider(raw);
            if (divider != CurrentCalibration.DIVIDER_UNKNOWN) {
                prefs.putInt(PrefManager.KEY_CURRENT_UNIT_DIVIDER, divider);
                Logger.d(TAG, "Đã học đơn vị dòng điện: divider=" + divider);
            }
        }
    }

    /** Lấy giá trị thô, ưu tiên API chính thức rồi mới tới sysfs. */
    @WorkerThread
    private int readRaw() {
        final int fromApi = readFromBatteryManager();
        if (fromApi != BatteryInfo.UNKNOWN_INT) return fromApi;
        return readFromSysfs();
    }

    private int readFromBatteryManager() {
        if (batteryManager == null) return BatteryInfo.UNKNOWN_INT;
        final int value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        // Máy không hỗ trợ thường trả về Integer.MIN_VALUE hoặc 0 cố định
        if (value == Integer.MIN_VALUE || value == 0) return BatteryInfo.UNKNOWN_INT;
        return value;
    }

    @WorkerThread
    private int readFromSysfs() {
        final String path = resolveSysfsPath();
        if (path == null) return BatteryInfo.UNKNOWN_INT;

        final Integer value = readIntFile(path);
        return value == null ? BatteryInfo.UNKNOWN_INT : value;
    }

    /** Dò node sysfs khả dụng đầu tiên; kết quả (kể cả không có) được nhớ lại. */
    private String resolveSysfsPath() {
        if (sysfsProbed) return cachedSysfsPath;

        synchronized (CurrentReader.class) {
            if (sysfsProbed) return cachedSysfsPath;
            for (String candidate : SYSFS_CANDIDATES) {
                File file = new File(candidate);
                if (file.exists() && file.canRead() && readIntFile(candidate) != null) {
                    cachedSysfsPath = candidate;
                    Logger.d(TAG, "Dùng node sysfs: " + candidate);
                    break;
                }
            }
            sysfsProbed = true;
            return cachedSysfsPath;
        }
    }

    /** Đọc một số nguyên từ file sysfs; trả về null nếu không đọc/parse được. */
    private static Integer readIntFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path), 64)) {
            String line = reader.readLine();
            if (line == null) return null;
            return Integer.parseInt(line.trim());
        } catch (Exception e) {
            // Không log lỗi ở đây: hàm này được gọi trong vòng dò, lỗi là chuyện bình thường
            return null;
        }
    }
}
